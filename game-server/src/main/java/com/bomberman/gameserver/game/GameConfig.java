package com.bomberman.gameserver.game;

/**
 * Todos los "números mágicos" del juego en un solo sitio.
 *
 * Decisión: usamos campos públicos con valores por defecto (en vez de getters/setters)
 * porque es una clase de configuración simple. Así, en los tests podemos escribir:
 *     GameConfig c = new GameConfig();
 *     c.enemyCount = 0;
 *
 * Los tiempos se miden en TICKS. El servidor ejecuta 20 ticks por segundo,
 * así que 20 ticks = 1 segundo, 60 ticks = 3 segundos, etc.
 */
public class GameConfig {

    /** Modo de juego de esta partida. */
    public GameMode mode = GameMode.CLASSIC;

    /**
     * Configuración lista para un modo. El modo pistolas cambia los power-ups
     * que salen (armas y botiquines en vez de bombas) y da vida a los enemigos.
     */
    public static GameConfig forMode(GameMode mode) {
        GameConfig c = new GameConfig();
        c.mode = mode;
        if (mode == GameMode.GUNS) {
            c.powerUpDropChance = 0.4;
            c.weightBombRange = 0;
            c.weightExtraBomb = 0;
            c.weightKick = 0;
            c.weightWeapon = 40;
            c.weightHealth = 30;
            c.weightSpeed = 15;
            c.weightSkull = 10;
            c.enemyHp = 60;
        }
        return c;
    }

    // --- Modo pistolas ---
    public int playerMaxHp = 100;
    public int healthPackHeal = 50;
    public int maxWeaponLevel = 11;
    /** Golpes de pico que aguanta un bloque. */
    public int blockHp = 3;
    /** Ticks entre picotazo y picotazo. */
    public int mineCooldownTicks = 5;
    /** El pico también sirve de arma cuerpo a cuerpo. */
    public int pickaxeDamage = 20;
    /** Vida de los enemigos (en el clásico, 1: cualquier explosión los mata). */
    public int enemyHp = 1;
    /** Daño al tocar un enemigo en el modo pistolas (en el clásico se pierde una vida). */
    public int enemyContactDamage = 25;
    /** Tras el toque de un enemigo, un momento de invulnerabilidad (si no, te quita vida cada tick). */
    public int contactInvulnerabilityTicks = 20;
    /** Daño del obús del tanque en el modo pistolas. */
    public int tankShellDamage = 60;

    // --- Bucle del juego ---
    public int ticksPerSecond = 20;
    /** Duración máxima de una partida (4 minutos: el mapa es grande). Al acabar gana quien tenga más puntos. */
    public int maxDurationTicks = 20 * 240;

    // --- Mapa ---
    // 41x33 = 1.353 casillas: unas 7 veces el mapa clásico de 15x13 (195 casillas).
    // La cámara sigue al jugador y hay un minimapa, porque ya no cabe en pantalla.
    public int width = 41;           // debe ser impar (muros en los bordes + pilares)
    public int height = 33;          // debe ser impar
    public double blockDensity = 0.6; // probabilidad de que una casilla libre tenga bloque
    /** Celdas alrededor de cada punto de aparición que quedan sin bloques. */
    public int spawnSafeRadius = 3;

    // --- Jugadores ---
    public int maxPlayers = 4;
    public int startingLives = 3;
    public int startingBombRange = 2;
    public int startingMaxBombs = 1;
    /** Ticks que tarda un jugador en avanzar una casilla (4 ticks = 0,2 s → 5 casillas/s). */
    public int baseMoveDelayTicks = 4;
    /** Lo más rápido que se puede llegar con power-ups de velocidad (2 ticks = 10 casillas/s). */
    public int minMoveDelayTicks = 2;
    public int maxBombRange = 8;
    public int maxBombsCap = 6;
    /** Tras perder una vida, el jugador es invulnerable un rato (2 s). */
    public int invulnerabilityTicks = 40;

    // --- Bombas y explosiones ---
    public int bombTimerTicks = 60;          // 3 segundos hasta explotar
    public int explosionDurationTicks = 10;  // las llamas duran 0,5 s

    // --- Power-ups ---
    public double powerUpDropChance = 0.3;   // 30 % de los bloques rotos sueltan algo
    /** Probabilidad relativa de cada power-up (se suman y se reparte): el tanque es el más raro. */
    public int weightBombRange = 26;
    public int weightExtraBomb = 26;
    public int weightSpeed = 20;
    public int weightKick = 12;
    public int weightSkull = 11;
    /** 0: el tanque no sale al azar, porque hay exactamente uno escondido (ver guaranteedTank). */
    public int weightTank = 0;
    public int weightWeapon = 0;   // solo en el modo pistolas
    public int weightHealth = 0;   // solo en el modo pistolas
    /** Esconde UN tanque por partida en un bloque cerca del centro del mapa. */
    public boolean guaranteedTank = true;
    /** Distancia máxima al centro del bloque donde se esconde el tanque. */
    public int tankHideRadius = 3;

    // --- Patear bombas ---
    /** Una bomba pateada avanza una casilla cada 2 ticks (10 casillas/s). */
    public int bombSlideDelayTicks = 2;

    // --- Calavera (maldiciones) ---
    public int curseDurationTicks = 20 * 10;      // 10 segundos
    public int slowMoveDelayTicks = 9;            // con la maldición "lento"

    // --- Mini tanque ---
    public int tankArmor = 3;                     // aguanta 3 golpes
    public int tankDurationTicks = 20 * 20;       // 20 segundos como máximo
    public int tankShootCooldownTicks = 16;       // un disparo cada 0,8 s

    // --- Muerte súbita ---
    /** En los últimos N segundos caen muros en espiral desde el borde (0 = desactivada). */
    public int suddenDeathSeconds = 45;
    /** Cada cuántos ticks caen muros. */
    public int suddenDeathIntervalTicks = 2;
    /**
     * Muros que caen cada vez. 0 = automático: se calcula para que la espiral
     * cubra todo el mapa justo cuando se acaba el tiempo (sea cual sea su tamaño).
     */
    public int suddenDeathWallsPerDrop = 0;

    // --- Enemigos ---
    public int enemyCount = 14;              // más enemigos para un mapa 7 veces más grande
    public int enemyMoveDelayTicks = 7;      // casi 3 casillas por segundo
    /** Distancia mínima entre un enemigo y los puntos de aparición al empezar. */
    public int enemyMinSpawnDistance = 6;
    /** Probabilidad de que un enemigo cambie de dirección aunque no choque. */
    public double enemyTurnChance = 0.25;

    // --- Puntuación (sirve para desempatar y para el ranking) ---
    public int scoreBlock = 10;
    public int scoreEnemy = 100;
    public int scorePlayerHit = 200;
}
