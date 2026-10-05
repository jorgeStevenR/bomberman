package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La calavera y sus maldiciones. */
class CurseTest {

    private Game game;
    private Player ana;
    private Player beto;

    @BeforeEach
    void setUp() {
        game = new Game(testConfig(), openMap(), new Random(1));
        ana = game.addPlayer("p1", "Ana");
        beto = game.addPlayer("p2", "Beto");
        game.start();
    }

    @Test
    void cogerLaCalaveraTeEchaUnaMaldicion() {
        game.putPowerUp(pos(2, 1), PowerUpType.SKULL);
        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick();

        assertThat(ana.getCurse()).isNotNull();
        assertThat(game.drainEvents()).extracting(GameEvent::type).contains(GameEvent.Type.CURSED);
    }

    @Test
    void conControlesInvertidosLaDerechaVaALaIzquierda() {
        ana.setPosition(pos(3, 3));
        ana.setCurse(Curse.REVERSED, 200);
        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick();
        assertThat(ana.getPosition()).isEqualTo(pos(2, 3));
    }

    @Test
    void conLentitudCaminaMuchoMasDespacio() {
        ana.setCurse(Curse.SLOW, 200);
        assertThat(ana.getMoveDelayTicks()).isEqualTo(12);
    }

    @Test
    void sinBombasNoPuedePonerNinguna() {
        ana.setCurse(Curse.NO_BOMBS, 200);
        assertThat(game.placeBomb("p1")).isFalse();
    }

    @Test
    void conDiarreaSueltaBombasSola() {
        ana.setCurse(Curse.DIARRHEA, 200);
        game.tick();
        assertThat(game.getBombs()).hasSize(1);
    }

    @Test
    void laMaldicionSeAcabaConElTiempo() {
        ana.setCurse(Curse.REVERSED, 5);
        ticks(game, 5);
        assertThat(ana.getCurse()).isNull();
    }

    @Test
    void laMaldicionSeContagiaAlTocarAOtroJugador() {
        ana.setPosition(pos(3, 3));
        beto.setPosition(pos(3, 3));
        ana.setCurse(Curse.REVERSED, 200);
        game.tick();
        assertThat(beto.getCurse()).isEqualTo(Curse.REVERSED);
    }
}
