package com.bomberman.gameserver.game;

/**
 * Un proyectil: la bala de una pistola (modo pistolas) o el obús del tanque.
 * Avanza una casilla por tick en línea recta.
 */
public class Shell {

    /** BULLET = arma normal; TANK = obús del tanque (rompe bloques). */
    public enum Kind { BULLET, TANK }

    private final int id;
    private final String ownerId;
    private final Direction direction;
    private final Kind kind;
    private final int damage;
    /** Atraviesa enemigos y jugadores (rifle). */
    private final boolean piercing;
    private Position position;

    Shell(int id, String ownerId, Position position, Direction direction, Kind kind, int damage, boolean piercing) {
        this.id = id;
        this.ownerId = ownerId;
        this.position = position;
        this.direction = direction;
        this.kind = kind;
        this.damage = damage;
        this.piercing = piercing;
    }

    void moveTo(Position p) {
        position = p;
    }

    /** Solo el obús del tanque rompe (y atraviesa) bloques. */
    public boolean breaksBlocks() {
        return kind == Kind.TANK;
    }

    public int getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public Direction getDirection() { return direction; }
    public Position getPosition() { return position; }
    public Kind getKind() { return kind; }
    public int getDamage() { return damage; }
    public boolean isPiercing() { return piercing; }
}
