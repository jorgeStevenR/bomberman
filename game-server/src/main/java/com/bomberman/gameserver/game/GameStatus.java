package com.bomberman.gameserver.game;

public enum GameStatus {
    /** Sala de espera: los jugadores se están uniendo. */
    WAITING,
    /** Partida en curso. */
    RUNNING,
    /** Partida terminada (hay ganador o empate). */
    FINISHED
}
