package com.bomberman.gameserver.game;

/**
 * Un jugador dentro de una partida.
 *
 * Decisión de diseño: los métodos que CAMBIAN el estado (setPosition, loseLife,
 * applyPowerUp...) no son públicos, son "de paquete" (sin modificador).
 * Solo las clases del paquete "game" (es decir, Game) pueden modificarlos.
 * Así garantizamos que el estado solo cambia siguiendo las reglas del juego:
 * es la base del servidor autoritativo.
 */
public class Player {

    private final String id;
    private final String name;
    private final Position spawn;
    private final GameConfig config;

    private Position position;
    /** Hacia dónde mira (para que el cliente elija la animación correcta). */
    private Direction facing = Direction.DOWN;
    /** Dirección que el jugador QUIERE seguir (null = quieto). La envía el cliente. */
    private Direction moveIntent;

    private int lives;
    private boolean alive = true;
    private int bombRange;
    private int maxBombs;
    private int speedLevel = 0;
    private int score = 0;
    private boolean canKick = false;

    /** Maldición activa de la calavera (null = ninguna) y ticks que le quedan. */
    private Curse curse;
    private int curseTicks = 0;

    /** Mini tanque: golpes que aguanta (0 = va a pie), ticks que le quedan y recarga del cañón. */
    private int tankArmor = 0;
    private int tankTicks = 0;
    private int shootCooldown = 0;

    // ---- Modo pistolas ----
    private int hp;
    private int maxHp;
    private int weaponLevel = 0;
    /** El jugador mantiene pulsado "disparar" (igual que moveIntent, pero para el arma). */
    private boolean firing = false;
    private int fireCooldown = 0;
    private int mineCooldown = 0;

    /** Ticks que faltan para poder dar el siguiente paso. */
    private int moveCooldown = 0;
    /** Ticks de invulnerabilidad restantes (tras perder una vida). */
    private int invulnerableTicks = 0;

    Player(String id, String name, Position spawn, GameConfig config) {
        this.id = id;
        this.name = name;
        this.spawn = spawn;
        this.config = config;
        this.position = spawn;
        this.lives = config.startingLives;
        this.bombRange = config.startingBombRange;
        this.maxBombs = config.startingMaxBombs;
        this.maxHp = config.playerMaxHp;
        this.hp = maxHp;
    }

    // ----- Modo pistolas: vida, arma y pico -----

    public Weapon getWeapon() {
        return Weapon.forLevel(weaponLevel);
    }

    /**
     * Resta vida.
     * @return true si la vida llegó a 0 (entonces Game le quita una de sus vidas)
     */
    boolean takeDamage(int amount) {
        hp = Math.max(0, hp - amount);
        return hp == 0;
    }

    void heal(int amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    /** @return true si el arma ha EVOLUCIONADO (pistola → subfusil → escopeta → rifle) */
    boolean upgradeWeapon() {
        Weapon before = getWeapon();
        weaponLevel = Math.min(config.maxWeaponLevel, weaponLevel + 1);
        return getWeapon() != before;
    }

    boolean canFireNow() {
        return fireCooldown == 0;
    }

    void startFireCooldown(int ticks) {
        fireCooldown = ticks;
    }

    boolean canMineNow() {
        return mineCooldown == 0;
    }

    void startMineCooldown() {
        mineCooldown = config.mineCooldownTicks;
    }

    void setFiring(boolean firing) {
        this.firing = firing;
    }

    void setInvulnerableTicks(int ticks) {
        invulnerableTicks = Math.max(invulnerableTicks, ticks);
    }

    void setWeaponLevel(int level) {
        weaponLevel = level;
    }

    /** Ticks entre paso y paso. Cada power-up de velocidad lo reduce en 1 (con un mínimo). */
    public int getMoveDelayTicks() {
        if (curse == Curse.SLOW) {
            return config.slowMoveDelayTicks;
        }
        return Math.max(config.minMoveDelayTicks, config.baseMoveDelayTicks - speedLevel);
    }

    /** Aplica el efecto de un power-up respetando los máximos. (La calavera la gestiona Game.) */
    void applyPowerUp(PowerUpType type) {
        switch (type) {
            case BOMB_RANGE -> bombRange = Math.min(config.maxBombRange, bombRange + 1);
            case EXTRA_BOMB -> maxBombs = Math.min(config.maxBombsCap, maxBombs + 1);
            case SPEED -> {
                int maxSpeedLevel = config.baseMoveDelayTicks - config.minMoveDelayTicks;
                speedLevel = Math.min(maxSpeedLevel, speedLevel + 1);
            }
            case KICK -> canKick = true;
            case TANK -> {
                tankArmor = config.tankArmor;
                tankTicks = config.tankDurationTicks;
                shootCooldown = 0;
            }
            case SKULL, WEAPON, HEALTH -> {
                // Los gestiona Game (necesitan azar o generan eventos)
            }
        }
    }

    void setCurse(Curse curse, int ticks) {
        this.curse = curse;
        this.curseTicks = ticks;
    }

    /**
     * Resta una vida. Si le quedan vidas, reaparece con invulnerabilidad.
     * @param respawnAt dónde reaparece (normalmente su esquina)
     * @return true si el jugador ha quedado eliminado
     */
    boolean loseLife(Position respawnAt) {
        lives--;
        moveIntent = null;
        firing = false;
        curse = null;
        if (lives <= 0) {
            alive = false;
            hp = 0;
            return true;
        }
        hp = maxHp; // reaparece con la vida llena
        position = respawnAt;
        invulnerableTicks = config.invulnerabilityTicks;
        return false;
    }

    /**
     * El tanque recibe un golpe en lugar del jugador.
     * @return true si el tanque ha quedado destruido
     */
    boolean damageTank() {
        tankArmor--;
        // Invulnerable un momento: si no, la misma explosión (que dura varios ticks)
        // le quitaría todo el blindaje de golpe.
        invulnerableTicks = config.invulnerabilityTicks;
        if (tankArmor <= 0) {
            exitTank();
            return true;
        }
        return false;
    }

    void exitTank() {
        tankArmor = 0;
        tankTicks = 0;
    }

    /** Elimina al jugador directamente (abandona la partida o le cae un muro encima). */
    void eliminate() {
        lives = 0;
        hp = 0;
        alive = false;
        moveIntent = null;
        firing = false;
        exitTank();
    }

    public boolean isInvulnerable() {
        return invulnerableTicks > 0;
    }

    public boolean isInTank() {
        return tankArmor > 0;
    }

    // ----- Métodos de paquete usados por Game en cada tick -----

    /**
     * Avanza los temporizadores del jugador.
     * @return true si el tanque se acaba de terminar por tiempo
     */
    boolean tickTimers() {
        if (invulnerableTicks > 0) invulnerableTicks--;
        if (moveCooldown > 0) moveCooldown--;
        if (shootCooldown > 0) shootCooldown--;
        if (fireCooldown > 0) fireCooldown--;
        if (mineCooldown > 0) mineCooldown--;
        if (curseTicks > 0 && --curseTicks == 0) curse = null;
        if (tankTicks > 0 && --tankTicks == 0) {
            exitTank();
            return true;
        }
        return false;
    }

    boolean canMoveNow() {
        return moveCooldown == 0;
    }

    boolean canShootNow() {
        return isInTank() && shootCooldown == 0;
    }

    void startShootCooldown() {
        shootCooldown = config.tankShootCooldownTicks;
    }

    void moveTo(Position target, Direction dir) {
        position = target;
        facing = dir;
        moveCooldown = getMoveDelayTicks();
    }

    void setPosition(Position position) {
        this.position = position;
    }

    void setFacing(Direction facing) {
        this.facing = facing;
    }

    void setMoveIntent(Direction moveIntent) {
        this.moveIntent = moveIntent;
    }

    void setBombRange(int bombRange) {
        this.bombRange = bombRange;
    }

    void setMaxBombs(int maxBombs) {
        this.maxBombs = maxBombs;
    }

    void addScore(int points) {
        score += points;
    }

    // ----- Getters públicos (los usará la capa de red para enviar el estado) -----

    public String getId() { return id; }
    public String getName() { return name; }
    public Position getSpawn() { return spawn; }
    public Position getPosition() { return position; }
    public Direction getFacing() { return facing; }
    public Direction getMoveIntent() { return moveIntent; }
    public int getLives() { return lives; }
    public boolean isAlive() { return alive; }
    public int getBombRange() { return bombRange; }
    public int getMaxBombs() { return maxBombs; }
    public int getSpeedLevel() { return speedLevel; }
    public int getScore() { return score; }
    public boolean canKick() { return canKick; }
    public Curse getCurse() { return curse; }
    public int getTankArmor() { return tankArmor; }
    public int getTankTicks() { return tankTicks; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getWeaponLevel() { return weaponLevel; }
    public boolean isFiring() { return firing; }
}
