package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Power-up de patear bombas. */
class KickTest {

    private Game game;
    private Player ana;
    private Player beto;

    @BeforeEach
    void setUp() {
        game = new Game(testConfig(), openMap(), new Random(1));
        ana = game.addPlayer("p1", "Ana");
        beto = game.addPlayer("p2", "Beto");
        game.start();
        // Bomba en (3,1) y Ana justo a su izquierda
        ana.setPosition(pos(3, 1));
        game.placeBomb("p1");
        ana.setPosition(pos(2, 1));
    }

    @Test
    void sinElPowerUpLaBombaSoloEstorba() {
        game.setMoveIntent("p1", Direction.RIGHT);
        ticks(game, 10);
        assertThat(game.getBombs().get(0).getPosition()).isEqualTo(pos(3, 1));
        assertThat(ana.getPosition()).isEqualTo(pos(2, 1));
    }

    @Test
    void conElPowerUpLaBombaSeDeslizaHastaChocarConElMuro() {
        ana.applyPowerUp(PowerUpType.KICK);
        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick();
        game.setMoveIntent("p1", null);
        assertThat(game.getBombs().get(0).isSliding()).isTrue();

        ticks(game, 20);
        Bomb bomb = game.getBombs().get(0);
        assertThat(bomb.getPosition()).isEqualTo(pos(7, 1)); // el muro está en x=8
        assertThat(bomb.isSliding()).isFalse();
        assertThat(ana.getPosition()).isEqualTo(pos(2, 1)); // quien patea no se mueve
    }

    @Test
    void laBombaPateadaSeParaDelanteDeUnJugador() {
        ana.applyPowerUp(PowerUpType.KICK);
        beto.setPosition(pos(5, 1));
        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick();
        game.setMoveIntent("p1", null);
        ticks(game, 20);
        assertThat(game.getBombs().get(0).getPosition()).isEqualTo(pos(4, 1));
    }
}
