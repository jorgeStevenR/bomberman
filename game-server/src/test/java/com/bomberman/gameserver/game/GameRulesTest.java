package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Victoria, derrota y reglas de la sala. */
class GameRulesTest {

    @Test
    void ganaElUltimoJugadorVivo() {
        GameConfig config = testConfig();
        config.startingLives = 1;
        Game game = new Game(config, openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        Player beto = game.addPlayer("p2", "Beto");
        game.start();

        game.placeBomb("p1");
        ana.setPosition(pos(5, 5));
        beto.setPosition(pos(2, 1)); // dentro de la cruz
        ticks(game, 60);

        assertThat(beto.isAlive()).isFalse();
        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getWinnerId()).isEqualTo("p1");
        assertThat(game.drainEvents())
                .extracting(GameEvent::type)
                .contains(GameEvent.Type.PLAYER_ELIMINATED, GameEvent.Type.GAME_OVER);
    }

    @Test
    void abandonarLaPartidaCuentaComoEliminado() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        game.addPlayer("p1", "Ana");
        game.addPlayer("p2", "Beto");
        game.start();

        game.removePlayer("p2");
        game.tick();

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getWinnerId()).isEqualTo("p1");
    }

    @Test
    void eliminarATodosLosEnemigosTerminaLaPartida() {
        GameConfig config = testConfig();
        config.enemyMoveDelayTicks = 10_000;
        Game game = new Game(config, openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.addEnemy(pos(3, 1));
        game.start();

        game.placeBomb("p1");
        ana.setPosition(pos(5, 5));
        ticks(game, 60);

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getWinnerId()).isEqualTo("p1");
    }

    @Test
    void unJugadorSoloPierdeSiSeQuedaSinVidas() {
        GameConfig config = testConfig();
        config.startingLives = 1;
        Game game = new Game(config, openMap(), new Random(1));
        game.addPlayer("p1", "Ana");
        game.start();

        game.placeBomb("p1"); // y se queda encima...
        ticks(game, 60);

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getWinnerId()).isNull();
    }

    @Test
    void alAcabarElTiempoGanaQuienTieneMasPuntos() {
        GameConfig config = testConfig();
        config.maxDurationTicks = 100;
        Game game = new Game(config, GameMap.fromAscii(
                "#########",
                "#..B....#",
                "#.#.#.#.#",
                "#.......#",
                "#.#.#.#.#",
                "#.......#",
                "#########"), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.addPlayer("p2", "Beto");
        game.start();

        game.placeBomb("p1"); // rompe el bloque de (3,1): +10 puntos
        ana.setPosition(pos(5, 3));
        ticks(game, 100);

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.getWinnerId()).isEqualTo("p1");
    }

    @Test
    void trasTerminarElMundoYaNoAvanza() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        game.addPlayer("p1", "Ana");
        game.addPlayer("p2", "Beto");
        game.start();
        game.removePlayer("p2");
        game.tick();
        long ticksAlTerminar = game.getTickCount();

        ticks(game, 10);
        assertThat(game.getTickCount()).isEqualTo(ticksAlTerminar);
        assertThat(game.placeBomb("p1")).isFalse();
    }

    @Test
    void laSalaAdmiteComoMaximoCuatroJugadoresYNoSeEntraConLaPartidaEmpezada() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        game.addPlayer("p1", "A");
        game.addPlayer("p2", "B");
        game.addPlayer("p3", "C");
        game.addPlayer("p4", "D");
        assertThrows(IllegalStateException.class, () -> game.addPlayer("p5", "E"));

        Game other = new Game(testConfig(), openMap(), new Random(1));
        other.addPlayer("p1", "A");
        other.start();
        assertThrows(IllegalStateException.class, () -> other.addPlayer("p2", "B"));
    }

    @Test
    void cadaJugadorApareceEnUnaEsquinaDistinta() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player a = game.addPlayer("p1", "A");
        Player b = game.addPlayer("p2", "B");
        game.removePlayer("p1"); // en la sala de espera se libera su esquina
        Player c = game.addPlayer("p3", "C");

        assertThat(b.getPosition()).isEqualTo(pos(7, 5));
        assertThat(c.getPosition()).isEqualTo(a.getSpawn());
    }
}
