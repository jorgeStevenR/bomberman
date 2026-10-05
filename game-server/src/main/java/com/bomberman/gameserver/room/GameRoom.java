package com.bomberman.gameserver.room;

import com.bomberman.gameserver.config.GameServerProperties;
import com.bomberman.gameserver.game.Game;
import com.bomberman.gameserver.game.GameConfig;
import com.bomberman.gameserver.game.GameMode;
import com.bomberman.gameserver.game.GameStatus;
import com.bomberman.gameserver.protocol.ServerMessages;
import com.bomberman.gameserver.protocol.ServerMessages.ErrorMessage;
import com.bomberman.gameserver.protocol.ServerMessages.Lobby;
import com.bomberman.gameserver.protocol.ServerMessages.LobbyPlayer;
import com.bomberman.gameserver.protocol.ServerMessages.Welcome;
import com.bomberman.gameserver.protocol.Snapshots;
import com.bomberman.gameserver.room.RoomCommand.Join;
import com.bomberman.gameserver.room.RoomCommand.Leave;
import com.bomberman.gameserver.room.RoomCommand.Move;
import com.bomberman.gameserver.room.RoomCommand.PlaceBomb;
import com.bomberman.gameserver.room.RoomCommand.Start;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Una sala: hasta 4 jugadores conectados por WebSocket y su partida.
 *
 * MODELO DE HILOS (importante para entender el código):
 *  - Los hilos de red (uno por mensaje que llega) solo llaman a submit(), que mete
 *    un comando en una cola thread-safe.
 *  - El método loop() lo ejecuta el planificador 20 veces por segundo. Es el ÚNICO
 *    que lee y modifica la partida y los jugadores de la sala.
 * Así no hace falta "synchronized" y es imposible que dos hilos cambien la partida
 * a la vez.
 */
public class GameRoom {

    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    /** Una sala sin nadie conectado durante 1 minuto se cierra. */
    private static final long EMPTY_ROOM_TIMEOUT_MS = 60_000;
    private static final long EMOTE_COOLDOWN_MS = 1_000;

    /** Datos de un jugador en la sala (su conexión puede ir y venir). */
    static final class PlayerSlot {
        final String id;
        final String name;
        final String reconnectToken;
        WebSocketSession session;
        long disconnectedAt = -1;
        boolean expired = false;
        long lastEmoteAt = 0;

        PlayerSlot(String id, String name, String reconnectToken, WebSocketSession session) {
            this.id = id;
            this.name = name;
            this.reconnectToken = reconnectToken;
            this.session = session;
        }

        boolean isConnected() {
            return session != null && session.isOpen();
        }
    }

    private final String code;
    private final GameConfig config;
    private final GameServerProperties props;
    private final ObjectMapper mapper;
    private final Random random = new Random();
    private final Queue<RoomCommand> commands = new ConcurrentLinkedQueue<>();

    // ---- Solo los toca el hilo del game loop ----
    private Game game; // null hasta que el anfitrión pulsa "empezar"
    private final Map<String, PlayerSlot> slots = new LinkedHashMap<>(); // playerId → jugador
    private final Map<String, String> playerBySession = new HashMap<>(); // sessionId → playerId
    private String hostId;
    /** Modo de la próxima partida (lo elige el anfitrión). */
    private GameMode mode = GameMode.CLASSIC;
    private long emptySince = -1;
    /** Versión del mapa enviada por última vez (-1 = hay que enviarlo sí o sí). */
    private int lastSentMapVersion = -1;

    // ---- Los leen otros hilos (métricas, limpieza) ----
    private volatile boolean closed = false;
    private volatile int connectedCount = 0;
    private volatile ScheduledFuture<?> task;

    public GameRoom(String code, GameConfig config, GameServerProperties props, ObjectMapper mapper) {
        this.code = code;
        this.config = config;
        this.props = props;
        this.mapper = mapper;
    }

    /** Llamado desde los hilos de red: solo encola. */
    public void submit(RoomCommand command) {
        if (!closed) {
            commands.add(command);
        }
    }

    // =====================================================================
    //  GAME LOOP: se ejecuta cada 50 ms (20 ticks/s)
    // =====================================================================

    public void loop() {
        // Si una excepción escapa de una tarea programada con scheduleAtFixedRate,
        // Java CANCELA la tarea en silencio y la sala se congela. Por eso capturamos todo.
        try {
            processCommands();                         // 1. aplicar intenciones de los jugadores

            if (game != null && game.getStatus() == GameStatus.RUNNING) {
                game.tick();                           // 2. avanzar el mundo
                expireDisconnectedPlayers();           // 3. quien no volvió a tiempo, fuera
                broadcast(Snapshots.state(game, connectedIds(), shouldSendMap())); // 4. enviar el estado a todos
                if (game.getStatus() == GameStatus.FINISHED) {
                    onGameOver();
                }
            }
            updateEmptyState();
        } catch (Exception e) {
            log.error("Error en el tick de la sala {}", code, e);
        }
    }

    private void processCommands() {
        RoomCommand cmd;
        while ((cmd = commands.poll()) != null) {
            // "switch" con patrones (Java 21): según el tipo de comando
            switch (cmd) {
                case Join j -> handleJoin(j);
                case Leave l -> handleLeave(l);
                case Start s -> handleStart(s);
                case Move m -> {
                    String pid = playerBySession.get(m.session().getId());
                    if (pid != null && isRunning()) {
                        game.setMoveIntent(pid, m.dir());
                    }
                }
                case PlaceBomb b -> {
                    String pid = playerBySession.get(b.session().getId());
                    if (pid != null && isRunning()) {
                        game.placeBomb(pid); // el juego valida si es legal
                    }
                }
                case RoomCommand.Emote e -> handleEmote(e);
                case RoomCommand.SetMode m -> {
                    String pid = playerBySession.get(m.session().getId());
                    if (pid == null || isRunning()) {
                        break;
                    }
                    if (!pid.equals(hostId)) {
                        send(m.session(), new ErrorMessage("NOT_HOST", "Solo el anfitrión puede elegir el modo"));
                    } else {
                        mode = m.mode();
                        broadcastLobby();
                    }
                }
                case RoomCommand.Fire f -> {
                    String pid = playerBySession.get(f.session().getId());
                    if (pid != null && isRunning()) {
                        game.setFiring(pid, f.on());
                    }
                }
                case RoomCommand.Mine mi -> {
                    String pid = playerBySession.get(mi.session().getId());
                    if (pid != null && isRunning()) {
                        game.mine(pid);
                    }
                }
                case RoomCommand.Cheat c -> {
                    String pid = playerBySession.get(c.session().getId());
                    if (pid != null && isRunning()) {
                        game.grantPowerUp(pid, c.powerUp());
                        log.info("Sala {}: TRUCO {} para {}", code, c.powerUp(), slots.get(pid).name);
                    }
                }
            }
        }
    }

    // =====================================================================
    //  Entrar, salir y reconectar
    // =====================================================================

    private void handleJoin(Join join) {
        WebSocketSession session = join.session();
        if (playerBySession.containsKey(session.getId())) {
            return; // JOIN repetido por la misma conexión: lo ignoramos
        }

        // ¿Es una RECONEXIÓN? El token solo lo conoce el jugador original.
        if (join.reconnectToken() != null) {
            for (PlayerSlot slot : slots.values()) {
                if (slot.reconnectToken.equals(join.reconnectToken())) {
                    reattach(slot, session);
                    return;
                }
            }
        }

        if (isRunning()) {
            rejectAndClose(session, "GAME_IN_PROGRESS", "La partida ya ha empezado");
            return;
        }
        if (slots.size() >= config.maxPlayers) {
            rejectAndClose(session, "ROOM_FULL", "La sala está llena (máximo " + config.maxPlayers + ")");
            return;
        }

        String playerId = "p-" + UUID.randomUUID().toString().substring(0, 8);
        String token = UUID.randomUUID().toString();
        PlayerSlot slot = new PlayerSlot(playerId, sanitizeName(join.name()), token, session);
        slots.put(playerId, slot);
        playerBySession.put(session.getId(), playerId);
        if (hostId == null) {
            hostId = playerId; // el primero en llegar es el anfitrión
        }
        log.info("Sala {}: entra {} ({})", code, slot.name, playerId);

        send(session, new Welcome(playerId, token, code, props.serverId(), props.tickRate()));
        broadcastLobby();
    }

    /** El jugador vuelve con otra conexión (recargó la página, se le cortó el WiFi...). */
    private void reattach(PlayerSlot slot, WebSocketSession session) {
        if (slot.session != null && slot.session != session) {
            playerBySession.remove(slot.session.getId());
            closeQuietly(slot.session); // si tenía otra pestaña abierta, la cerramos
        }
        slot.session = session;
        slot.disconnectedAt = -1;
        playerBySession.put(session.getId(), slot.id);
        lastSentMapVersion = -1; // el que vuelve necesita el mapa en el próximo STATE
        log.info("Sala {}: {} se ha reconectado", code, slot.name);

        send(session, new Welcome(slot.id, slot.reconnectToken, code, props.serverId(), props.tickRate()));
        broadcastLobby();
    }

    private void handleLeave(Leave leave) {
        String playerId = playerBySession.remove(leave.session().getId());
        if (playerId == null) {
            return;
        }
        PlayerSlot slot = slots.get(playerId);
        if (slot == null || slot.session != leave.session()) {
            return;
        }

        if (isRunning()) {
            // Durante la partida no lo eliminamos todavía: le damos unos segundos para volver.
            slot.session = null;
            slot.disconnectedAt = System.currentTimeMillis();
            game.setMoveIntent(playerId, null); // que no siga andando solo
            log.info("Sala {}: {} se ha desconectado (tiene {} s para volver)",
                    code, slot.name, props.reconnectGraceSeconds());
        } else {
            slots.remove(playerId);
            log.info("Sala {}: sale {}", code, slot.name);
            ensureHost();
            broadcastLobby();
        }
    }

    private void expireDisconnectedPlayers() {
        long now = System.currentTimeMillis();
        long graceMs = props.reconnectGraceSeconds() * 1000L;
        for (PlayerSlot slot : slots.values()) {
            if (!slot.expired && slot.session == null && slot.disconnectedAt > 0
                    && now - slot.disconnectedAt > graceMs) {
                slot.expired = true;
                game.removePlayer(slot.id); // cuenta como eliminado
                log.info("Sala {}: {} no volvió a tiempo, eliminado", code, slot.name);
            }
        }
    }

    /** Reenvía el emote a toda la sala. Máximo uno por segundo por jugador (anti-spam). */
    private void handleEmote(RoomCommand.Emote emote) {
        String playerId = playerBySession.get(emote.session().getId());
        PlayerSlot slot = playerId == null ? null : slots.get(playerId);
        long now = System.currentTimeMillis();
        if (slot == null || now - slot.lastEmoteAt < EMOTE_COOLDOWN_MS) {
            return;
        }
        slot.lastEmoteAt = now;
        broadcast(new ServerMessages.Emote(playerId, emote.emote()));
    }

    // =====================================================================
    //  Empezar y terminar la partida
    // =====================================================================

    private void handleStart(Start start) {
        String playerId = playerBySession.get(start.session().getId());
        if (playerId == null || isRunning()) {
            return;
        }
        if (!playerId.equals(hostId)) {
            send(start.session(), new ErrorMessage("NOT_HOST", "Solo el anfitrión puede empezar la partida"));
            return;
        }
        // Una partida nueva cada vez (también para "volver a jugar"), con mapa nuevo.
        slots.values().removeIf(s -> !s.isConnected());
        game = new Game(GameConfig.forMode(mode), random);
        for (PlayerSlot slot : slots.values()) {
            slot.expired = false;
            game.addPlayer(slot.id, slot.name);
        }
        game.start();
        lastSentMapVersion = -1; // partida nueva = mapa nuevo
        log.info("Sala {}: empieza la partida con {} jugadores", code, slots.size());
        broadcastLobby();
    }

    private void onGameOver() {
        broadcast(Snapshots.gameOver(game));
        log.info("Sala {}: fin de la partida, gana {}", code, game.getWinnerId());
        // (Fase 4: aquí enviaremos el resultado a la API para guardarlo en PostgreSQL.)

        // Quien sigue desconectado ya no vuelve a esta sala.
        slots.values().removeIf(s -> !s.isConnected());
        ensureHost();
        broadcastLobby(); // estado FINISHED: el anfitrión puede pulsar "volver a jugar"
    }

    // =====================================================================
    //  Utilidades
    // =====================================================================

    /** El mapa se envía si cambió desde el último envío, y además una vez por segundo por seguridad. */
    private boolean shouldSendMap() {
        int version = game.getMap().getVersion();
        boolean send = version != lastSentMapVersion || game.getTickCount() % props.tickRate() == 0;
        lastSentMapVersion = version;
        return send;
    }

    private boolean isRunning() {
        return game != null && game.getStatus() == GameStatus.RUNNING;
    }

    private GameStatus status() {
        return game == null ? GameStatus.WAITING : game.getStatus();
    }

    /** Si el anfitrión se fue, el siguiente jugador conectado pasa a serlo. */
    private void ensureHost() {
        PlayerSlot host = hostId == null ? null : slots.get(hostId);
        if (host == null || !host.isConnected()) {
            hostId = slots.values().stream()
                    .filter(PlayerSlot::isConnected)
                    .map(s -> s.id)
                    .findFirst()
                    .orElse(null);
        }
    }

    private Set<String> connectedIds() {
        return slots.values().stream()
                .filter(PlayerSlot::isConnected)
                .map(s -> s.id)
                .collect(Collectors.toSet());
    }

    private void updateEmptyState() {
        connectedCount = (int) slots.values().stream().filter(PlayerSlot::isConnected).count();
        if (connectedCount > 0) {
            emptySince = -1;
        } else if (emptySince < 0) {
            emptySince = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - emptySince > EMPTY_ROOM_TIMEOUT_MS) {
            closed = true;
        }
    }

    private void broadcastLobby() {
        ensureHost();
        List<LobbyPlayer> players = slots.values().stream()
                .map(s -> new LobbyPlayer(s.id, s.name, s.isConnected(), s.id.equals(hostId)))
                .toList();
        broadcast(new Lobby(code, hostId, status(), mode, config.maxPlayers, players));
    }

    /** Envía el mismo mensaje a todos. Se convierte a JSON una sola vez. */
    private void broadcast(Object message) {
        String json = toJson(message);
        for (PlayerSlot slot : slots.values()) {
            if (slot.isConnected()) {
                sendRaw(slot.session, json);
            }
        }
    }

    private void send(WebSocketSession session, Object message) {
        sendRaw(session, toJson(message));
    }

    private void sendRaw(WebSocketSession session, String json) {
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException e) {
            // Conexión rota: no pasa nada, el evento de desconexión llegará por su lado.
            log.debug("No se pudo enviar a {}: {}", session.getId(), e.getMessage());
        }
    }

    private String toJson(Object message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo convertir a JSON", e);
        }
    }

    private void rejectAndClose(WebSocketSession session, String code, String message) {
        send(session, new ErrorMessage(code, message));
        closeQuietly(session);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException ignored) {
            // ya estaba cerrada
        }
    }

    /** Nombre limpio: solo letras, números, espacios, _ y -, máximo 16 caracteres. */
    private String sanitizeName(String name) {
        String clean = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
        if (clean.length() > 16) {
            clean = clean.substring(0, 16);
        }
        return clean.isEmpty() ? "Jugador " + (slots.size() + 1) : clean;
    }

    // ---- Usado por RoomManager ----

    public String getCode() { return code; }
    public boolean isClosed() { return closed; }
    public int getConnectedCount() { return connectedCount; }

    void setTask(ScheduledFuture<?> task) {
        this.task = task;
    }

    void stop() {
        closed = true;
        if (task != null) {
            task.cancel(false);
        }
    }
}
