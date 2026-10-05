package com.bomberman.gameserver.game;

/**
 * Enemigo controlado por el servidor. Su "inteligencia" es simple a propósito:
 * camina recto y, si choca (o a veces al azar), elige otra dirección libre.
 * Si toca a un jugador, le hace daño. Muere con las explosiones (o a balazos
 * en el modo pistolas, donde tiene barra de vida).
 */
public class Enemy {

    private final int id;
    private final int maxHp;
    private int hp;
    private Position position;
    private Direction direction;
    private int moveCooldown;
    private boolean alive = true;

    Enemy(int id, Position position, Direction direction, int initialCooldown, int hp) {
        this.id = id;
        this.position = position;
        this.direction = direction;
        this.moveCooldown = initialCooldown;
        this.maxHp = hp;
        this.hp = hp;
    }

    void tickCooldown() {
        if (moveCooldown > 0) moveCooldown--;
    }

    boolean canMoveNow() {
        return moveCooldown == 0;
    }

    void moveTo(Position target, Direction dir, int delayTicks) {
        position = target;
        direction = dir;
        moveCooldown = delayTicks;
    }

    /** @return true si este golpe lo ha matado */
    boolean takeDamage(int amount) {
        hp = Math.max(0, hp - amount);
        if (hp == 0) {
            alive = false;
        }
        return !alive;
    }

    void kill() {
        hp = 0;
        alive = false;
    }

    public int getId() { return id; }
    public Position getPosition() { return position; }
    public Direction getDirection() { return direction; }
    public boolean isAlive() { return alive; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
}
