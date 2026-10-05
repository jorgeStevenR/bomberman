package com.bomberman.gameserver.game;

/**
 * Armas del modo pistolas. Cada power-up de arma sube 1 nivel:
 *
 *   nivel 0-2  → PISTOLA   (cada nivel dispara más rápido)
 *   nivel 3-5  → SUBFUSIL  (ráfagas muy rápidas, poco daño por bala)
 *   nivel 6-8  → ESCOPETA  (3 balas en abanico: al frente y a los dos lados)
 *   nivel 9+   → RIFLE     (mucho daño y la bala ATRAVIESA enemigos y jugadores)
 *
 * Las cadencias van en ticks entre disparo y disparo (20 ticks = 1 segundo).
 */
public enum Weapon {
    PISTOL(0, 25, false, 1, new int[]{9, 7, 5}),
    SMG(3, 12, false, 1, new int[]{4, 3, 2}),
    SHOTGUN(6, 22, false, 3, new int[]{14, 12, 10}),
    RIFLE(9, 45, true, 1, new int[]{12, 10, 8});

    /** Nivel a partir del cual se tiene esta arma. */
    public final int minLevel;
    public final int damage;
    /** Si la bala sigue tras dar a alguien. */
    public final boolean piercing;
    /** Cuántas balas dispara a la vez (la escopeta, 3 en abanico). */
    public final int lanes;
    private final int[] cooldowns;

    Weapon(int minLevel, int damage, boolean piercing, int lanes, int[] cooldowns) {
        this.minLevel = minLevel;
        this.damage = damage;
        this.piercing = piercing;
        this.lanes = lanes;
        this.cooldowns = cooldowns;
    }

    /** El arma que corresponde a un nivel (la "evolución"). */
    public static Weapon forLevel(int level) {
        Weapon result = PISTOL;
        for (Weapon w : values()) {
            if (level >= w.minLevel) result = w;
        }
        return result;
    }

    /** Ticks entre disparos para ese nivel: cada mejora dentro de un arma la hace más rápida. */
    public int cooldownTicks(int level) {
        int index = Math.min(level - minLevel, cooldowns.length - 1);
        return cooldowns[Math.max(0, index)];
    }
}
