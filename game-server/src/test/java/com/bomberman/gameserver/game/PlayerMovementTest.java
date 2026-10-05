package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerMovementTest {

    private Game game;
    private Player ana;

    @BeforeEach
    void setUp() {
        game = new Game(testConfig(), openMap(), new Random(1));
        ana = game.addPlayer("p1", "Ana"); // aparece en (1,1)
        game.start();
    }

    @Test
    void avanzaUnaCasillaCadaMoveDelayTicks() {
        game.setMoveIntent("p1", Direction.RIGHT);

        game.tick(); // primer paso inmediato
        assertThat(ana.getPosition()).isEqualTo(pos(2, 1));

        ticks(game, 5); // aún en espera (delay = 6 ticks)
        assertThat(ana.getPosition()).isEqualTo(pos(2, 1));

        game.tick();
        assertThat(ana.getPosition()).isEqualTo(pos(3, 1));
    }

    @Test
    void noAtraviesaMuros() {
        game.setMoveIntent("p1", Direction.UP); // arriba hay muro
        ticks(game, 10);
        assertThat(ana.getPosition()).isEqualTo(pos(1, 1));
        assertThat(ana.getFacing()).isEqualTo(Direction.UP); // pero sí se gira
    }

    @Test
    void noAtraviesaBloques() {
        Game g = new Game(testConfig(), GameMap.fromAscii(
                "#####",
                "#.B.#",
                "#.#.#",
                "#...#",
                "#####"), new Random(1));
        Player p = g.addPlayer("p1", "Ana");
        g.start();

        g.setMoveIntent("p1", Direction.RIGHT);
        ticks(g, 10);
        assertThat(p.getPosition()).isEqualTo(pos(1, 1));
    }

    @Test
    void puedeSalirDeSuBombaPeroNoVolverAEntrar() {
        game.placeBomb("p1");
        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick();
        assertThat(ana.getPosition()).isEqualTo(pos(2, 1));

        game.setMoveIntent("p1", Direction.LEFT);
        ticks(game, 10);
        assertThat(ana.getPosition()).isEqualTo(pos(2, 1)); // la bomba bloquea
    }

    @Test
    void conIntencionNullSeQuedaQuieto() {
        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick();
        game.setMoveIntent("p1", null);
        ticks(game, 20);
        assertThat(ana.getPosition()).isEqualTo(pos(2, 1));
    }

    @Test
    void elPowerUpDeVelocidadReduceElDelayHastaUnMinimo() {
        assertThat(ana.getMoveDelayTicks()).isEqualTo(6);
        ana.applyPowerUp(PowerUpType.SPEED);
        assertThat(ana.getMoveDelayTicks()).isEqualTo(5);
        for (int i = 0; i < 10; i++) {
            ana.applyPowerUp(PowerUpType.SPEED);
        }
        assertThat(ana.getMoveDelayTicks()).isEqualTo(3); // mínimo configurado
    }
}
