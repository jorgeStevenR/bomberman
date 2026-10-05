package com.bomberman.gameserver.game;

/** Una bomba colocada en el mapa con su cuenta atrás. Si la patean, se desliza. */
public class Bomb {

    private final int id;
    private final String ownerId;
    private Position position;
    /** Alcance: cuántas casillas llega la llama en cada dirección. Se fija al colocarla. */
    private final int range;
    private int ticksLeft;

    /** Dirección en la que se desliza tras una patada (null = quieta). */
    private Direction slideDirection;
    private int slideCooldown;

    Bomb(int id, String ownerId, Position position, int range, int ticksLeft) {
        this.id = id;
        this.ownerId = ownerId;
        this.position = position;
        this.range = range;
        this.ticksLeft = ticksLeft;
    }

    /** Avanza la cuenta atrás un tick. */
    void tick() {
        if (ticksLeft > 0) ticksLeft--;
    }

    /** Explota en el próximo tick (por ejemplo, si le da una bala). */
    void detonate() {
        ticksLeft = 0;
    }

    public boolean shouldExplode() {
        return ticksLeft <= 0;
    }

    // ---- Deslizamiento (patada) ----

    void startSliding(Direction dir) {
        slideDirection = dir;
        slideCooldown = 0;
    }

    void stopSliding() {
        slideDirection = null;
    }

    public boolean isSliding() {
        return slideDirection != null;
    }

    /** @return true si en este tick toca avanzar una casilla */
    boolean tickSlide() {
        if (slideCooldown > 0) {
            slideCooldown--;
            return false;
        }
        return true;
    }

    void slideTo(Position next, int delayTicks) {
        position = next;
        slideCooldown = delayTicks - 1;
    }

    public int getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public Position getPosition() { return position; }
    public int getRange() { return range; }
    public int getTicksLeft() { return ticksLeft; }
    public Direction getSlideDirection() { return slideDirection; }
}
