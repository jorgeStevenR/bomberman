package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Muerte súbita: en el último segundo de esta partida de prueba caen muros en espiral. */
class SuddenDeathTest {

    /** Partida de 100 ticks con muerte súbita en el último segundo (empieza en el tick 80). */
    private static GameConfig config() {
        GameConfig c = testConfig();
        c.maxDurationTicks = 100;
        c.suddenDeathSeconds = 1;
        c.suddenDeathIntervalTicks = 5;
        c.suddenDeathWallsPerDrop = 1; // de uno en uno para poder comprobar el orden
        return c;
    }

    @Test
    void losMurosCaenEnEspiralEmpezandoPorLaEsquina() {
        Game game = new Game(config(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.addPlayer("p2", "Beto");
        game.start();
        ana.setPosition(pos(3, 3)); // fuera del camino de los primeros muros

        ticks(game, 79);
        assertThat(game.isSuddenDeath()).isFalse();

        game.tick(); // tick 80: empieza y cae el primer muro
        assertThat(game.isSuddenDeath()).isTrue();
        assertThat(game.getMap().getTile(pos(1, 1))).isEqualTo(Tile.WALL);

        ticks(game, 5); // tick 85: el siguiente de la espiral
        assertThat(game.getMap().getTile(pos(2, 1))).isEqualTo(Tile.WALL);
    }

    @Test
    void quienQuedaDebajoDeUnMuroQuedaEliminado() {
        Game game = new Game(config(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana"); // en (1,1): ahí cae el primer muro
        game.addPlayer("p2", "Beto");
        game.start();
        ana.applyPowerUp(PowerUpType.TANK);       // ni el tanque la salva

        ticks(game, 80);

        assertThat(ana.isAlive()).isFalse();
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getWinnerId()).isEqualTo("p2");
    }
}
