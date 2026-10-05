package com.bomberman.gameserver.game;

/** Tipos de mejora que pueden salir de un bloque roto. */
public enum PowerUpType {
    /** +1 casilla de alcance en las explosiones. (Clásico) */
    BOMB_RANGE,
    /** +1 bomba simultánea. (Clásico) */
    EXTRA_BOMB,
    /** El jugador camina más rápido. */
    SPEED,
    /** Permite patear bombas: al empujarlas se deslizan hasta chocar con algo. (Clásico) */
    KICK,
    /** ¡Cuidado! Maldición al azar durante unos segundos (y se contagia). */
    SKULL,
    /** Mini tanque: aguanta 3 golpes, dispara y aplasta enemigos (como la estrella de Mario). */
    TANK,
    /** +1 nivel de arma: más cadencia y, cada 3 niveles, evoluciona. (Pistolas) */
    WEAPON,
    /** Botiquín: cura vida. (Pistolas) */
    HEALTH
}
