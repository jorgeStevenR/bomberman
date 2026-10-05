package com.bomberman.gameserver.game;

/** Tipo de casilla del mapa. */
public enum Tile {
    /** Suelo libre: se puede caminar. */
    EMPTY,
    /** Muro fijo: indestructible, bloquea movimiento y explosiones. */
    WALL,
    /** Bloque destructible: bloquea, pero una explosión lo rompe (y puede soltar un power-up). */
    BLOCK
}
