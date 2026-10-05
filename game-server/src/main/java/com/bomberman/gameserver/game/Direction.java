package com.bomberman.gameserver.game;

/**
 * Las 4 direcciones de movimiento.
 * dx/dy indican cuánto cambia la posición: en pantalla la Y crece hacia abajo,
 * por eso UP tiene dy = -1.
 */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /** La dirección contraria (la usa la maldición de controles invertidos). */
    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
}
