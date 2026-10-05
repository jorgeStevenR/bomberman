package com.bomberman.gameserver.ws;

import com.bomberman.gameserver.config.GameServerProperties;
import com.bomberman.gameserver.game.Direction;
import com.bomberman.gameserver.game.GameMode;
import com.bomberman.gameserver.game.PowerUpType;
import com.bomberman.gameserver.protocol.ClientMessage;
import com.bomberman.gameserver.protocol.ServerMessages.ErrorMessage;
import com.bomberman.gameserver.protocol.ServerMessages.Pong;
import com.bomberman.gameserver.room.RoomCommand;
import com.bomberman.gameserver.room.RoomManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Recibe los mensajes JSON de los navegadores y los convierte en comandos para la sala.
 *
 * Esta clase NO toca la partida: valida, limita y reenvía. La lógica está en
 * GameRoom (cuándo) y en Game (qué está permitido).
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    /** Ningún mensaje legítimo del cliente ocupa más de esto. */
    private static final int MAX_PAYLOAD_CHARS = 512;
    /** Si un cliente acumula tantos mensajes rechazados, lo desconectamos. */
    private static final long MAX_REJECTED_BEFORE_KICK = 300;

    private final RoomManager roomManager;
    private final ObjectMapper mapper;
    private final GameServerProperties props;

    // Por cada conexión: su versión "segura para hilos" y su limitador
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public GameWebSocketHandler(RoomManager roomManager, ObjectMapper mapper, GameServerProperties props) {
        this.roomManager = roomManager;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Un WebSocketSession NO admite dos envíos a la vez desde hilos distintos.
        // El decorador los pone en cola. Si un cliente va muy lento y se le acumulan
        // más de 256 KB, se descartan los mensajes viejos (DROP): en un juego no
        // importa perder un STATE antiguo, el siguiente trae el mundo completo.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, 2_000, 256 * 1024, ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
        sessions.put(session.getId(), safe);
        limiters.put(session.getId(), new RateLimiter(props.maxMessagesPerSecond(), 1_000));
        log.debug("Conexión abierta {} en sala {}", session.getId(), roomCode(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
        WebSocketSession session = sessions.get(rawSession.getId());
        RateLimiter limiter = limiters.get(rawSession.getId());
        if (session == null || limiter == null) {
            return;
        }

        // 1. Límite de mensajes por segundo (anti-spam / anti-trampas)
        if (!limiter.tryAcquire(System.currentTimeMillis())) {
            if (limiter.getTotalRejected() > MAX_REJECTED_BEFORE_KICK) {
                log.warn("Desconectando {} por exceso de mensajes", session.getId());
                close(session, CloseStatus.POLICY_VIOLATION);
            }
            return;
        }

        // 2. Tamaño máximo
        if (message.getPayloadLength() > MAX_PAYLOAD_CHARS) {
            sendError(session, "BAD_MESSAGE", "Mensaje demasiado grande");
            return;
        }

        // 3. JSON válido
        ClientMessage msg;
        try {
            msg = mapper.readValue(message.getPayload(), ClientMessage.class);
        } catch (JsonProcessingException e) {
            sendError(session, "BAD_MESSAGE", "JSON no válido");
            return;
        }
        if (msg.type() == null) {
            sendError(session, "BAD_MESSAGE", "Falta el campo 'type'");
            return;
        }

        // 4. Según el tipo, creamos el comando para la sala
        String room = roomCode(session);
        switch (msg.type()) {
            case "JOIN" -> roomManager.getOrCreate(room)
                    .submit(new RoomCommand.Join(session, msg.name(), msg.reconnectToken()));
            case "START" -> submit(room, new RoomCommand.Start(session));
            case "MOVE" -> {
                Direction dir = parseDirection(msg.dir());
                if (dir == null) {
                    sendError(session, "BAD_MESSAGE", "Dirección no válida: " + msg.dir());
                } else {
                    submit(room, new RoomCommand.Move(session, dir));
                }
            }
            case "STOP" -> submit(room, new RoomCommand.Move(session, null));
            case "PLACE_BOMB" -> submit(room, new RoomCommand.PlaceBomb(session));
            case "EMOTE" -> {
                if (msg.emote() == null || msg.emote() < 0 || msg.emote() > 3) {
                    sendError(session, "BAD_MESSAGE", "Emote no válido");
                } else {
                    submit(room, new RoomCommand.Emote(session, msg.emote()));
                }
            }
            case "SET_MODE" -> {
                GameMode mode = parseMode(msg.mode());
                if (mode == null) {
                    sendError(session, "BAD_MESSAGE", "Modo no válido: " + msg.mode());
                } else {
                    submit(room, new RoomCommand.SetMode(session, mode));
                }
            }
            case "FIRE" -> submit(room, new RoomCommand.Fire(session, Boolean.TRUE.equals(msg.on())));
            case "MINE" -> submit(room, new RoomCommand.Mine(session));
            case "CHEAT" -> {
                // Los trucos los decide el SERVIDOR: si no están activados, se rechazan.
                // Así ningún jugador puede darse un tanque en una partida normal.
                PowerUpType type = parsePowerUp(msg.powerUp());
                if (!props.cheatsEnabled()) {
                    sendError(session, "CHEATS_DISABLED", "Los trucos están desactivados en este servidor");
                } else if (type == null) {
                    sendError(session, "BAD_MESSAGE", "Power-up no válido: " + msg.powerUp());
                } else {
                    submit(room, new RoomCommand.Cheat(session, type));
                }
            }
            case "PING" -> send(session, new Pong(msg.t(), System.currentTimeMillis()));
            default -> sendError(session, "BAD_MESSAGE", "Tipo desconocido: " + msg.type());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession rawSession, CloseStatus status) {
        WebSocketSession session = sessions.remove(rawSession.getId());
        limiters.remove(rawSession.getId());
        if (session != null) {
            submit(roomCode(rawSession), new RoomCommand.Leave(session));
        }
        log.debug("Conexión cerrada {} ({})", rawSession.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Error de transporte en {}: {}", session.getId(), exception.getMessage());
    }

    // ---------- utilidades ----------

    private void submit(String room, RoomCommand command) {
        roomManager.find(room).ifPresent(r -> r.submit(command));
    }

    private static String roomCode(WebSocketSession session) {
        return (String) session.getAttributes().get(RoomHandshakeInterceptor.ROOM_ATTRIBUTE);
    }

    private static Direction parseDirection(String dir) {
        if (dir == null) {
            return null;
        }
        try {
            return Direction.valueOf(dir.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static GameMode parseMode(String name) {
        if (name == null) {
            return null;
        }
        try {
            return GameMode.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PowerUpType parsePowerUp(String name) {
        if (name == null) {
            return null;
        }
        try {
            return PowerUpType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void sendError(WebSocketSession session, String code, String text) {
        send(session, new ErrorMessage(code, text));
    }

    private void send(WebSocketSession session, Object message) {
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
        } catch (IOException | IllegalStateException e) {
            log.debug("No se pudo enviar a {}: {}", session.getId(), e.getMessage());
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // ya cerrada
        }
    }
}
