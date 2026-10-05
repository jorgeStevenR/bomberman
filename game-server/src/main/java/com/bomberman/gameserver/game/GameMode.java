package com.bomberman.gameserver.game;

/** Modos de juego. El anfitrión lo elige en la sala de espera. */
public enum GameMode {
    /** Bomberman de toda la vida: bombas en cruz, cadenas, 3 vidas. */
    CLASSIC,
    /** Pistolas y pico: disparas, picas bloques, tienes barra de vida y tu arma evoluciona. */
    GUNS
}
