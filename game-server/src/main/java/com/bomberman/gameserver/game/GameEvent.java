package com.bomberman.gameserver.game;

/**
 * Algo que "pasó" durante un tick. El estado (posiciones, bombas...) se envía
 * completo en cada tick, pero hay cosas puntuales que el cliente necesita saber
 * para reproducir un sonido o una animación: "explotó una bomba aquí",
 * "se rompió este bloque", "alguien cogió un power-up".
 *
 * @param playerId jugador relacionado (puede ser null)
 */
public record GameEvent(Type type, Position position, String playerId) {

    public enum Type {
        GAME_STARTED,
        BOMB_PLACED,
        BOMB_KICKED,
        EXPLOSION,
        BLOCK_DESTROYED,
        POWERUP_SPAWNED,
        POWERUP_TAKEN,
        CURSED,
        TANK_ON,
        TANK_HIT,
        TANK_DESTROYED,
        SHOT_FIRED,
        PICKAXE_SWING,
        BLOCK_CRACKED,
        ENEMY_DAMAGED,
        PLAYER_DAMAGED,
        HEALED,
        WEAPON_UP,
        WEAPON_EVOLVED,
        PLAYER_HIT,
        PLAYER_ELIMINATED,
        ENEMY_KILLED,
        SUDDEN_DEATH,
        WALL_DROPPED,
        GAME_OVER
    }
}
