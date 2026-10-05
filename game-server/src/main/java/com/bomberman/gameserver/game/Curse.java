package com.bomberman.gameserver.game;

/** Efectos malos de la calavera. Duran unos segundos y se contagian al tocar a otro jugador. */
public enum Curse {
    /** Las flechas van al revés. */
    REVERSED,
    /** Caminas lentísimo. */
    SLOW,
    /** Sueltas bombas sin parar, quieras o no. */
    DIARRHEA,
    /** No puedes poner bombas. */
    NO_BOMBS
}
