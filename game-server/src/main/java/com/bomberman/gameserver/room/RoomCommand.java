package com.bomberman.gameserver.room;

import com.bomberman.gameserver.game.Direction;
import com.bomberman.gameserver.game.GameMode;
import com.bomberman.gameserver.game.PowerUpType;
import org.springframework.web.socket.WebSocketSession;

/**
 * Una orden pendiente para una sala. Los hilos de red NO tocan la partida:
 * solo crean uno de estos objetos y lo meten en la cola de la sala. El game loop
 * los saca y los aplica al principio de cada tick, siempre desde un solo hilo.
 *
 * "sealed" (Java 17+) significa que solo existen estos tipos de comando; así el
 * compilador comprueba que el "switch" de GameRoom los trata todos.
 */
public sealed interface RoomCommand {

    WebSocketSession session();

    record Join(WebSocketSession session, String name, String reconnectToken) implements RoomCommand {
    }

    record Leave(WebSocketSession session) implements RoomCommand {
    }

    record Start(WebSocketSession session) implements RoomCommand {
    }

    /** dir = null significa "parar". */
    record Move(WebSocketSession session, Direction dir) implements RoomCommand {
    }

    record PlaceBomb(WebSocketSession session) implements RoomCommand {
    }

    /** El anfitrión elige el modo de juego (solo en la sala de espera o al terminar). */
    record SetMode(WebSocketSession session, GameMode mode) implements RoomCommand {
    }

    /** Modo pistolas: mantener (true) o soltar (false) el gatillo. */
    record Fire(WebSocketSession session, boolean on) implements RoomCommand {
    }

    /** Modo pistolas: golpe de pico. */
    record Mine(WebSocketSession session) implements RoomCommand {
    }

    /** Truco de pruebas: dar un power-up directamente (solo con game.cheats-enabled=true). */
    record Cheat(WebSocketSession session, PowerUpType powerUp) implements RoomCommand {
    }

    /** emote: número de 0 a 3 (el cliente decide qué dibujo es cada uno). */
    record Emote(WebSocketSession session, int emote) implements RoomCommand {
    }
}
