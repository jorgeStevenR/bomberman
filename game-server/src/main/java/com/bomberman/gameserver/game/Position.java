package com.bomberman.gameserver.game;

/**
 * Una celda de la cuadrícula (columna x, fila y).
 *
 * Es un "record": Java genera automáticamente constructor, getters x()/y(),
 * equals() y hashCode(). Gracias a equals/hashCode podemos usar Position
 * como clave de un HashMap o dentro de un HashSet (lo hacemos con los power-ups
 * y las celdas de las explosiones).
 */
public record Position(int x, int y) {

    /** Devuelve la celda vecina en la dirección indicada. */
    public Position move(Direction dir) {
        return new Position(x + dir.dx, y + dir.dy);
    }

    /** Devuelve la celda a "distance" pasos en la dirección indicada. */
    public Position move(Direction dir, int distance) {
        return new Position(x + dir.dx * distance, y + dir.dy * distance);
    }

    /** Distancia "Manhattan": pasos necesarios moviéndose solo en horizontal/vertical. */
    public int manhattan(Position other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }
}
