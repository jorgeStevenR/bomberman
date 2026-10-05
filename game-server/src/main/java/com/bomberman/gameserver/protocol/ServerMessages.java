package com.bomberman.gameserver.protocol;

import com.bomberman.gameserver.game.Curse;
import com.bomberman.gameserver.game.Direction;
import com.bomberman.gameserver.game.GameMode;
import com.bomberman.gameserver.game.Shell;
import com.bomberman.gameserver.game.Weapon;
import com.bomberman.gameserver.game.GameEvent;
import com.bomberman.gameserver.game.GameStatus;
import com.bomberman.gameserver.game.Position;
import com.bomberman.gameserver.game.PowerUpType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;
import java.util.List;

/**
 * Todos los mensajes que el servidor envía al navegador, en un solo archivo
 * para que coincida con la documentación (docs/PROTOCOLO.md).
 *
 * Son "records": Jackson los convierte a JSON automáticamente usando sus campos.
 * El método type() anotado con @JsonProperty añade el campo "type" a cada mensaje.
 *
 * Usamos "vistas" (PlayerView, BombView...) en vez de enviar directamente las
 * clases del juego: así decidimos exactamente qué ve el cliente y podemos cambiar
 * la lógica interna sin romper el protocolo.
 */
public final class ServerMessages {

    private ServerMessages() {
    }

    /** Respuesta a JOIN: quién eres y el token para reconectarte. */
    public record Welcome(String playerId, String reconnectToken, String room, String server, int tickRate) {
        @JsonProperty("type")
        public String type() { return "WELCOME"; }
    }

    /** Sala de espera: quién está, quién es el anfitrión y en qué estado está la sala. */
    public record Lobby(String room, String hostId, GameStatus status, GameMode mode, int maxPlayers,
                        List<LobbyPlayer> players) {
        @JsonProperty("type")
        public String type() { return "LOBBY"; }
    }

    public record LobbyPlayer(String id, String name, boolean connected, boolean host) {
    }

    /** Foto completa del mundo. Se envía en cada tick (20 veces por segundo). */
    public record State(
            long tick,
            GameStatus status,
            GameMode mode,
            int timeLeft,
            boolean suddenDeath,
            List<String> map,
            List<PlayerView> players,
            List<BombView> bombs,
            List<ExplosionView> explosions,
            List<EnemyView> enemies,
            List<ShellView> shells,
            List<PowerUpView> powerUps,
            List<CrackView> cracks,
            List<GameEvent> events) {
        @JsonProperty("type")
        public String type() { return "STATE"; }
    }

    /**
     * @param curse      maldición activa (REVERSED, SLOW, DIARRHEA, NO_BOMBS) o null
     * @param tankArmor  golpes que aguanta el tanque (0 = va a pie)
     * @param tankSeconds segundos de tanque que le quedan
     */
    public record PlayerView(
            String id, String name, int x, int y, Direction facing, boolean moving,
            int lives, boolean alive, int bombRange, int maxBombs, int speedLevel,
            int moveDelayTicks, int score, boolean invulnerable, boolean connected,
            boolean canKick, Curse curse, int tankArmor, int tankSeconds,
            int hp, int maxHp, int weaponLevel, Weapon weapon) {
    }

    /** Modo pistolas: bloque agrietado por el pico (hits = golpes recibidos). */
    public record CrackView(int x, int y, int hits) {
    }

    /** moveDelayTicks: velocidad a la que se desliza si la patean (para interpolar). */
    public record BombView(int id, int x, int y, int range, int ticksLeft, boolean sliding, int moveDelayTicks) {
    }

    /** kind: BULLET (bala de arma) o TANK (obús del tanque, que rompe bloques). */
    public record ShellView(int id, int x, int y, Direction dir, Shell.Kind kind) {
    }

    /** Un jugador pulsó un emote (1-4): el cliente muestra un globito sobre su personaje. */
    public record Emote(String playerId, int emote) {
        @JsonProperty("type")
        public String type() { return "EMOTE"; }
    }

    public record ExplosionView(Position center, Collection<Position> cells, int ticksLeft) {
    }

    public record EnemyView(int id, int x, int y, Direction dir, int moveDelayTicks, int hp, int maxHp) {
    }

    public record PowerUpView(int x, int y, PowerUpType type) {
    }

    /** Fin de la partida con la clasificación. */
    public record GameOver(String winnerId, String winnerName, List<ResultView> results) {
        @JsonProperty("type")
        public String type() { return "GAME_OVER"; }
    }

    public record ResultView(String id, String name, int score, int lives, boolean alive) {
    }

    /** Algo salió mal (sala llena, mensaje incorrecto...). */
    public record ErrorMessage(String code, String message) {
        @JsonProperty("type")
        public String type() { return "ERROR"; }
    }

    /** Respuesta a PING: el cliente calcula su latencia con "t". */
    public record Pong(Long t, long serverTime) {
        @JsonProperty("type")
        public String type() { return "PONG"; }
    }
}
