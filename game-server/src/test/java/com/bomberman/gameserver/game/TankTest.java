package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** El mini tanque: aguanta 3 golpes, dispara balas que perforan bloques y aplasta enemigos. */
class TankTest {

    @Test
    void cogerElPowerUpTeSubeAlTanque() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();
        game.putPowerUp(pos(2, 1), PowerUpType.TANK);

        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick();

        assertThat(ana.isInTank()).isTrue();
        assertThat(ana.getTankArmor()).isEqualTo(3);
    }

    @Test
    void elTanqueAguantaTresGolpesSinPerderVidas() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");   // en (1,1)
        Player beto = game.addPlayer("p2", "Beto");
        game.start();
        ana.applyPowerUp(PowerUpType.TANK);

        for (int golpe = 1; golpe <= 3; golpe++) {
            beto.setPosition(pos(2, 1));
            game.placeBomb("p2");            // su explosión alcanza a Ana en (1,1)
            beto.setPosition(pos(7, 5));
            ticks(game, 60);
            assertThat(ana.getLives()).isEqualTo(3);
        }
        assertThat(ana.isInTank()).isFalse(); // al tercer golpe el tanque revienta

        beto.setPosition(pos(2, 1));
        game.placeBomb("p2");
        beto.setPosition(pos(7, 5));
        ticks(game, 60);
        assertThat(ana.getLives()).isEqualTo(2); // ya sin tanque, pierde vida
    }

    @Test
    void laBalaPerforaYRompeTodosLosBloquesDeSuCamino() {
        Game game = new Game(testConfig(), GameMap.fromAscii(
                "#########",
                "#.BB.B..#",
                "#.#.#.#.#",
                "#.......#",
                "#.#.#.#.#",
                "#.......#",
                "#########"), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();
        ana.applyPowerUp(PowerUpType.TANK);
        ana.setFacing(Direction.RIGHT);

        assertThat(game.placeBomb("p1")).isTrue(); // en el tanque, "bomba" = disparar
        ticks(game, 10);

        assertThat(game.getMap().getTile(pos(2, 1))).isEqualTo(Tile.EMPTY);
        assertThat(game.getMap().getTile(pos(3, 1))).isEqualTo(Tile.EMPTY);
        assertThat(game.getMap().getTile(pos(5, 1))).isEqualTo(Tile.EMPTY);
        assertThat(game.getShells()).isEmpty(); // chocó con el muro
        assertThat(game.getBombs()).isEmpty();  // y no puso ninguna bomba
    }

    @Test
    void laBalaMataEnemigos() {
        GameConfig config = testConfig();
        config.enemyMoveDelayTicks = 10_000;
        Game game = new Game(config, openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.addEnemy(pos(5, 3));
        game.start();
        ana.applyPowerUp(PowerUpType.TANK);
        ana.setPosition(pos(1, 3));
        ana.setFacing(Direction.RIGHT);

        game.placeBomb("p1");
        ticks(game, 5);

        assertThat(game.getEnemies()).isEmpty();
        assertThat(ana.getScore()).isEqualTo(100);
    }

    @Test
    void elTanqueAplastaALosEnemigosComoLaEstrellaDeMario() {
        GameConfig config = testConfig();
        config.enemyMoveDelayTicks = 10_000;
        Game game = new Game(config, openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.addPlayer("p2", "Beto");
        game.addEnemy(pos(2, 1));
        game.start();
        ana.applyPowerUp(PowerUpType.TANK);

        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick();

        assertThat(game.getEnemies()).isEmpty();
        assertThat(ana.getLives()).isEqualTo(3);
        assertThat(ana.getTankArmor()).isEqualTo(3);
    }

    @Test
    void elCanonTieneQueRecargar() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();
        ana.applyPowerUp(PowerUpType.TANK);
        ana.setFacing(Direction.RIGHT);

        assertThat(game.placeBomb("p1")).isTrue();
        assertThat(game.placeBomb("p1")).isFalse();
        ticks(game, 16);
        assertThat(game.placeBomb("p1")).isTrue();
    }

    @Test
    void hayUnTanqueEscondidoYSaleAlRomperSuBloque() {
        GameConfig config = testConfig();
        config.guaranteedTank = true;
        // Un solo bloque en el mapa: por fuerza el tanque está ahí
        Game game = new Game(config, GameMap.fromAscii(
                "#########",
                "#..B....#",
                "#.#.#.#.#",
                "#.......#",
                "#.#.#.#.#",
                "#.......#",
                "#########"), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        game.placeBomb("p1");
        ana.setPosition(pos(5, 5));
        ticks(game, 60);

        assertThat(game.getPowerUps()).extracting(PowerUp::type).containsExactly(PowerUpType.TANK);
    }

    @Test
    void soloSaleUnTanquePorPartida() {
        GameConfig config = testConfig();
        config.guaranteedTank = true;
        Game game = new Game(config, GameMap.fromAscii(
                "#########",
                "#.BBBBB.#",
                "#.#.#.#.#",
                "#.......#",
                "#.#.#.#.#",
                "#.......#",
                "#########"), new Random(3));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();
        // Rompemos todos los bloques de un disparo (la bala perfora)
        ana.applyPowerUp(PowerUpType.TANK);
        ana.setFacing(Direction.RIGHT);
        game.placeBomb("p1");
        ticks(game, 10);

        long tanks = game.getPowerUps().stream().filter(p -> p.type() == PowerUpType.TANK).count();
        assertThat(tanks).isEqualTo(1);
    }

    @Test
    void lasExplosionesNoDestruyenElTanqueDelSuelo() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();
        game.putPowerUp(pos(2, 1), PowerUpType.TANK);
        game.putPowerUp(pos(1, 2), PowerUpType.SPEED);

        game.placeBomb("p1");
        ana.setPosition(pos(5, 5));
        ticks(game, 60);

        // El tanque está blindado; el otro power-up se quema
        assertThat(game.getPowerUps()).extracting(PowerUp::type).containsExactly(PowerUpType.TANK);
    }

    @Test
    void elTrucoDaElTanqueAlInstante() {
        Game game = new Game(testConfig(), openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();

        game.grantPowerUp("p1", PowerUpType.TANK);

        assertThat(ana.isInTank()).isTrue();
        assertThat(game.drainEvents()).extracting(GameEvent::type).contains(GameEvent.Type.TANK_ON);
    }

    @Test
    void elTanqueSeAcabaConElTiempo() {
        GameConfig config = testConfig();
        config.tankDurationTicks = 10;
        Game game = new Game(config, openMap(), new Random(1));
        Player ana = game.addPlayer("p1", "Ana");
        game.start();
        ana.applyPowerUp(PowerUpType.TANK);

        ticks(game, 10);
        assertThat(ana.isInTank()).isFalse();
    }
}
