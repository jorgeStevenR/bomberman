package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BombAndExplosionTest {

    @Test
    void laBombaExplotaALosTresSegundos() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        assertThat(game.placeBomb("p1")).isTrue();
        ana.setPosition(pos(5, 5)); // la apartamos para que no le afecte

        ticks(game, 59);
        assertThat(game.getBombs()).hasSize(1);
        assertThat(game.getExplosions()).isEmpty();

        game.tick(); // tick 60 = 3 s
        assertThat(game.getBombs()).isEmpty();
        assertThat(game.getExplosions()).hasSize(1);
    }

    @Test
    void laExplosionEsUnaCruzLimitadaPorElAlcance() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        ana.setPosition(pos(3, 3));
        game.placeBomb("p1"); // alcance inicial = 2
        ana.setPosition(pos(7, 5));
        ticks(game, 60);

        Set<Position> cells = game.getExplosions().get(0).getCells();
        assertThat(cells).containsExactlyInAnyOrder(
                pos(3, 3),
                pos(3, 2), pos(3, 1),   // arriba
                pos(3, 4), pos(3, 5),   // abajo
                pos(2, 3), pos(1, 3),   // izquierda
                pos(4, 3), pos(5, 3));  // derecha
    }

    @Test
    void losMurosFrenanLaLlamaYSoloSeRompeElPrimerBloque() {
        Game game = new Game(testConfig(), GameMap.fromAscii(
                "#########",
                "#...BB..#",
                "#.#.#.#.#",
                "#.......#",
                "#.#.#.#.#",
                "#.......#",
                "#########"), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        ana.setBombRange(5);
        game.placeBomb("p1"); // en (1,1)
        ana.setPosition(pos(7, 5));
        ticks(game, 60);

        Set<Position> cells = game.getExplosions().get(0).getCells();
        assertThat(cells).contains(pos(4, 1)).doesNotContain(pos(5, 1));
        assertThat(cells).noneMatch(p -> p.x() == 0 || p.y() == 0); // no entra en los muros
        assertThat(game.getMap().getTile(pos(4, 1))).isEqualTo(Tile.EMPTY);
        assertThat(game.getMap().getTile(pos(5, 1))).isEqualTo(Tile.BLOCK);
        assertThat(ana.getScore()).isEqualTo(10);
    }

    @Test
    void reaccionEnCadena() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana"); // (1,1)
        Player beto = game.addPlayer("p2", "Beto"); // (7,5)
        game.start();

        game.placeBomb("p1");            // bomba A en (1,1), explota en el tick 60
        ana.setPosition(pos(7, 5));
        ticks(game, 30);

        beto.setPosition(pos(3, 1));
        game.placeBomb("p2");            // bomba B en (3,1), explotaría en el tick 90
        beto.setPosition(pos(5, 5));

        ticks(game, 30);                 // tick 60: A alcanza (3,1) y detona B
        assertThat(game.getBombs()).isEmpty();
        assertThat(game.getExplosions()).hasSize(2);
    }

    @Test
    void respetaElLimiteDeBombasSimultaneas() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        assertThat(game.placeBomb("p1")).isTrue();
        ana.setPosition(pos(3, 1));
        assertThat(game.placeBomb("p1")).isFalse(); // máximo 1 al empezar

        ana.setMaxBombs(2);
        assertThat(game.placeBomb("p1")).isTrue();
    }

    @Test
    void noSePuedenPonerDosBombasEnLaMismaCasilla() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();
        ana.setMaxBombs(3);

        assertThat(game.placeBomb("p1")).isTrue();
        assertThat(game.placeBomb("p1")).isFalse();
    }

    @Test
    void alSerAlcanzadoPierdeUnaVidaYReapareceInvulnerable() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        ana.setPosition(pos(3, 1));
        game.placeBomb("p1");
        ticks(game, 60); // se queda encima de su bomba

        assertThat(ana.getLives()).isEqualTo(2);
        assertThat(ana.getPosition()).isEqualTo(pos(1, 1)); // vuelve a su esquina
        assertThat(ana.isInvulnerable()).isTrue();

        // (1,1) sigue ardiendo, pero la invulnerabilidad le protege
        ticks(game, 10);
        assertThat(ana.getLives()).isEqualTo(2);
    }

    @Test
    void quienEntraEnLasLlamasRecibeDanoYElAtacanteSumaPuntos() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        Player beto = game.addPlayer("p2", "Beto");
        game.start();

        game.placeBomb("p1");          // en (1,1)
        ana.setPosition(pos(1, 5));
        beto.setPosition(pos(4, 1));
        ticks(game, 60);               // explota: arde (2,1) y (3,1)
        assertThat(beto.getLives()).isEqualTo(3);

        game.setMoveIntent("p2", Direction.LEFT);
        game.tick();                   // Beto entra en (3,1), que aún arde
        assertThat(beto.getLives()).isEqualTo(2);
        assertThat(ana.getScore()).isEqualTo(200);
    }

    @Test
    void unBloqueRotoSueltaUnPowerUpQueSePuedeRecoger() {
        GameConfig config = testConfig();
        config.powerUpDropChance = 1.0; // siempre suelta algo
        config.weightKick = 0;          // solo mejoras "numéricas" para poder comprobarlas
        config.weightSkull = 0;
        config.weightTank = 0;
        Game game = new Game(config, GameMap.fromAscii(
                "#########",
                "#..B....#",
                "#.#.#.#.#",
                "#.......#",
                "#.#.#.#.#",
                "#.......#",
                "#########"), new Random(7));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        game.placeBomb("p1");
        ana.setPosition(pos(5, 5));
        ticks(game, 60);

        // La llama de la misma explosión NO destruye el power-up recién nacido
        assertThat(game.getPowerUps()).hasSize(1);
        assertThat(game.getPowerUps().iterator().next().position()).isEqualTo(pos(3, 1));

        ticks(game, 10); // esperamos a que se apaguen las llamas
        int statsBefore = ana.getBombRange() + ana.getMaxBombs() + ana.getSpeedLevel();
        ana.setPosition(pos(4, 1));
        game.setMoveIntent("p1", Direction.LEFT);
        game.tick();

        assertThat(ana.getPosition()).isEqualTo(pos(3, 1));
        assertThat(game.getPowerUps()).isEmpty();
        assertThat(ana.getBombRange() + ana.getMaxBombs() + ana.getSpeedLevel()).isEqualTo(statsBefore + 1);
    }

    @Test
    void elPowerUpDeAlcanceHaceBombasMasLargas() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        ana.applyPowerUp(PowerUpType.BOMB_RANGE); // alcance 3
        game.placeBomb("p1");
        ana.setPosition(pos(5, 5));
        ticks(game, 60);

        assertThat(game.getExplosions().get(0).getCells()).contains(pos(4, 1)).doesNotContain(pos(5, 1));
    }
}
