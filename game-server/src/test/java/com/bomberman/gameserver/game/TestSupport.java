package com.bomberman.gameserver.game;

/**
 * Utilidades compartidas por los tests.
 *
 * Usamos mapas pequeños escritos "a mano" para saber exactamente qué hay
 * en cada casilla y poder comprobar las reglas con precisión.
 */
final class TestSupport {

    private TestSupport() {
    }

    /**
     * Mapa abierto de 9x7 (sin bloques). Esquinas de aparición:
     * jugador 1 = (1,1), jugador 2 = (7,5), jugador 3 = (7,1), jugador 4 = (1,5).
     *
     *   x: 012345678
     *      #########   y=0
     *      #.......#   y=1
     *      #.#.#.#.#   y=2
     *      #.......#   y=3
     *      #.#.#.#.#   y=4
     *      #.......#   y=5
     *      #########   y=6
     */
    static GameMap openMap() {
        return GameMap.fromAscii(
                "#########",
                "#.......#",
                "#.#.#.#.#",
                "#.......#",
                "#.#.#.#.#",
                "#.......#",
                "#########");
    }

    /** Configuración predecible: sin enemigos aleatorios, sin power-ups y sin muerte súbita. */
    static GameConfig testConfig() {
        GameConfig c = new GameConfig();
        c.enemyCount = 0;
        c.powerUpDropChance = 0.0;
        c.suddenDeathSeconds = 0;
        c.guaranteedTank = false; // el tanque escondido tiene sus propios tests
        // Velocidades fijas para que los tests no dependan de los ajustes de "feeling"
        c.baseMoveDelayTicks = 6;
        c.minMoveDelayTicks = 3;
        c.slowMoveDelayTicks = 12;
        c.enemyMoveDelayTicks = 10;
        return c;
    }

    /** Ejecuta n ticks seguidos. */
    static void ticks(Game game, int n) {
        for (int i = 0; i < n; i++) {
            game.tick();
        }
    }

    static Position pos(int x, int y) {
        return new Position(x, y);
    }
}
