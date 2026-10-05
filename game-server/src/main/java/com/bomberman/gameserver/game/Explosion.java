package com.bomberman.gameserver.game;

import java.util.Collections;
import java.util.Set;

/**
 * Las llamas de UNA bomba: el conjunto de casillas en cruz que está ardiendo.
 * Duran unos ticks; mientras existan, quien pise esas casillas recibe daño.
 */
public class Explosion {

    private final String ownerId;
    /** Casilla donde estaba la bomba (el cliente dibuja ahí el centro de la cruz). */
    private final Position center;
    private final Set<Position> cells;
    private int ticksLeft;

    Explosion(String ownerId, Position center, Set<Position> cells, int ticksLeft) {
        this.ownerId = ownerId;
        this.center = center;
        this.cells = cells;
        this.ticksLeft = ticksLeft;
    }

    void tick() {
        ticksLeft--;
    }

    public boolean isFinished() {
        return ticksLeft <= 0;
    }

    public String getOwnerId() { return ownerId; }
    public Position getCenter() { return center; }
    public Set<Position> getCells() { return Collections.unmodifiableSet(cells); }
    public int getTicksLeft() { return ticksLeft; }
}
