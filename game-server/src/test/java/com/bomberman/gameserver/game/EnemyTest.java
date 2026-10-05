package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class EnemyTest {

    @Test
    void losEnemigosAparecenYNuncaAtraviesanMurosNiBloques() {
        GameConfig config = new GameConfig(); // mapa aleatorio con la configuración real
        Game game = new Game(config, new Random(123));
        game.addPlayer("p1", "Ana");
        game.start();

        assertThat(game.getEnemies()).hasSize(config.enemyCount);
        for (int i = 0; i < 1000 && game.getStatus() == GameStatus.RUNNING; i++) {
            game.tick();
            for (Enemy e : game.getEnemies()) {
                assertThat(game.getMap().isSolid(e.getPosition())).isFalse();
            }
        }
    }

    @Test
    void unaExplosionMataAlEnemigoYDaPuntos() {
        GameConfig config = testConfig();
        config.enemyMoveDelayTicks = 10_000; // el enemigo se queda quieto para el test
        Game game = new Game(config, openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.addEnemy(pos(3, 1));
        game.start();

        game.placeBomb("p1");
        ana.setPosition(pos(5, 5));
        ticks(game, 60);

        assertThat(game.getEnemies()).isEmpty();
        assertThat(ana.getScore()).isEqualTo(100);
    }

    @Test
    void tocarUnEnemigoQuitaUnaVida() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.addEnemy(pos(2, 1));
        game.start();

        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick(); // Ana pisa la casilla del enemigo

        assertThat(ana.getLives()).isEqualTo(2);
        assertThat(ana.getPosition()).isEqualTo(pos(1, 1));
    }

    @Test
    void losEnemigosNoAtraviesanBombas() {
        GameConfig config = testConfig();
        config.enemyMoveDelayTicks = 1;
        // Pasillo cerrado: el enemigo solo puede ir a izquierda o derecha
        Game game = new Game(config, GameMap.fromAscii(
                "#######",
                "#.....#",
                "#######"), new Random(5));
        Player ana = game.addPlayer("p1", "Ana"); // en (1,1)
        Enemy enemy = game.addEnemy(pos(5, 1));
        game.start();

        ana.setPosition(pos(3, 1));
        game.placeBomb("p1");
        ana.setPosition(pos(1, 1));

        for (int i = 0; i < 50; i++) {
            game.tick();
            assertThat(enemy.getPosition().x()).isGreaterThanOrEqualTo(4);
        }
    }
}
