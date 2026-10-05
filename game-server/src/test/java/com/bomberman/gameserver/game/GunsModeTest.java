package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.openMap;
import static com.bomberman.gameserver.game.TestSupport.pos;
import static com.bomberman.gameserver.game.TestSupport.testConfig;
import static com.bomberman.gameserver.game.TestSupport.ticks;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Modo pistolas y pico: armas, evoluciones, pico, barra de vida y botiquines. */
class GunsModeTest {

    /** Config de test en modo pistolas: enemigos con 60 de vida y quietos. */
    private static GameConfig gunsConfig() {
        GameConfig c = testConfig();
        c.mode = GameMode.GUNS;
        c.enemyHp = 60;
        c.enemyMoveDelayTicks = 10_000;
        return c;
    }

    private static Game gameWith(GameConfig config, GameMap map) {
        Game game = new Game(config, map, new Random(1));
        game.addPlayer("p1", "Ana");
        game.addPlayer("p2", "Beto");
        return game;
    }

    private static final String[] ROW_WITH_BLOCK = {
            "#########",
            "#..B....#",
            "#.#.#.#.#",
            "#.......#",
            "#.#.#.#.#",
            "#.......#",
            "#########"};

    @Test
    void conLaPistolaHacenFaltaVariosDisparosParaMatarAUnEnemigo() {
        Game game = gameWith(gunsConfig(), openMap());
        Player ana = game.getPlayer("p1");
        game.addEnemy(pos(5, 1));
        game.start();
        ana.setFacing(Direction.RIGHT);

        game.setFiring("p1", true);   // mantiene el gatillo
        ticks(game, 3);
        assertThat(game.getEnemies()).hasSize(1); // 25 de daño: aún vive (60)

        ticks(game, 30);
        assertThat(game.getEnemies()).isEmpty();  // 3 balas de 25
        assertThat(ana.getScore()).isEqualTo(100);
    }

    @Test
    void alSoltarElGatilloDejaDeDisparar() {
        Game game = gameWith(gunsConfig(), openMap());
        game.start();
        game.getPlayer("p1").setFacing(Direction.DOWN);
        game.setFiring("p1", true);
        game.tick();
        game.setFiring("p1", false);
        ticks(game, 10); // la primera bala ya ha chocado

        assertThat(game.getShells()).isEmpty();
        ticks(game, 20);
        assertThat(game.getShells()).isEmpty();
    }

    @Test
    void lasBalasSeParanEnLosBloquesSinRomperlos() {
        Game game = gameWith(gunsConfig(), GameMap.fromAscii(ROW_WITH_BLOCK));
        game.start();
        game.getPlayer("p1").setFacing(Direction.RIGHT);

        game.placeBomb("p1"); // en modo pistolas, la acción es disparar
        ticks(game, 10);

        assertThat(game.getMap().getTile(pos(3, 1))).isEqualTo(Tile.BLOCK);
        assertThat(game.getShells()).isEmpty();
        assertThat(game.getBombs()).isEmpty();
    }

    @Test
    void elPicoRompeUnBloqueEnTresGolpes() {
        Game game = gameWith(gunsConfig(), GameMap.fromAscii(ROW_WITH_BLOCK));
        Player ana = game.getPlayer("p1");
        game.start();
        ana.setPosition(pos(2, 1));
        ana.setFacing(Direction.RIGHT);

        assertThat(game.mine("p1")).isTrue();
        assertThat(game.mine("p1")).isFalse(); // hay que esperar entre golpes
        assertThat(game.getBlockDamage()).containsEntry(pos(3, 1), 1);

        ticks(game, 5);
        game.mine("p1");
        ticks(game, 5);
        game.mine("p1");

        assertThat(game.getMap().getTile(pos(3, 1))).isEqualTo(Tile.EMPTY);
        assertThat(game.getBlockDamage()).isEmpty();
    }

    @Test
    void elArmaEvolucionaCadaTresMejoras() {
        Game game = gameWith(gunsConfig(), openMap());
        Player ana = game.getPlayer("p1");
        game.start();

        assertThat(ana.getWeapon()).isEqualTo(Weapon.PISTOL);
        for (int i = 0; i < 3; i++) game.grantPowerUp("p1", PowerUpType.WEAPON);
        assertThat(ana.getWeapon()).isEqualTo(Weapon.SMG);
        assertThat(game.drainEvents()).extracting(GameEvent::type).contains(GameEvent.Type.WEAPON_EVOLVED);

        for (int i = 0; i < 3; i++) game.grantPowerUp("p1", PowerUpType.WEAPON);
        assertThat(ana.getWeapon()).isEqualTo(Weapon.SHOTGUN);
        for (int i = 0; i < 3; i++) game.grantPowerUp("p1", PowerUpType.WEAPON);
        assertThat(ana.getWeapon()).isEqualTo(Weapon.RIFLE);
    }

    @Test
    void cadaMejoraDentroDeUnArmaDisparaMasRapido() {
        assertThat(Weapon.PISTOL.cooldownTicks(1)).isLessThan(Weapon.PISTOL.cooldownTicks(0));
        assertThat(Weapon.PISTOL.cooldownTicks(2)).isLessThan(Weapon.PISTOL.cooldownTicks(1));
        assertThat(Weapon.SMG.cooldownTicks(3)).isLessThan(Weapon.PISTOL.cooldownTicks(2));
    }

    @Test
    void laEscopetaDisparaTresBalasEnAbanico() {
        Game game = gameWith(gunsConfig(), openMap());
        Player ana = game.getPlayer("p1");
        game.start();
        ana.setWeaponLevel(6);
        ana.setPosition(pos(3, 3));
        ana.setFacing(Direction.RIGHT);

        game.placeBomb("p1");
        assertThat(game.getShells()).hasSize(3);
    }

    @Test
    void elRifleAtraviesaAVariosEnemigos() {
        GameConfig config = gunsConfig();
        config.enemyHp = 40;
        Game game = gameWith(config, openMap());
        Player ana = game.getPlayer("p1");
        game.addEnemy(pos(4, 3));
        game.addEnemy(pos(6, 3));
        game.start();
        ana.setWeaponLevel(9);
        ana.setPosition(pos(1, 3));
        ana.setFacing(Direction.RIGHT);

        game.placeBomb("p1");
        ticks(game, 8);
        assertThat(game.getEnemies()).isEmpty();
    }

    @Test
    void losGolpesQuitanVidaDeLaBarraYAlVaciarseSePierdeUnaVida() {
        Game game = gameWith(gunsConfig(), openMap());
        Player ana = game.getPlayer("p1");
        game.addEnemy(pos(2, 1));
        game.start();

        game.setMoveIntent("p1", Direction.RIGHT);
        game.tick(); // toca al enemigo: -25
        assertThat(ana.getHp()).isEqualTo(75);
        assertThat(ana.getLives()).isEqualTo(3);

        game.setMoveIntent("p1", null);
        ana.takeDamage(60); // le quedan 15
        ticks(game, 25);    // pasa la invulnerabilidad y el enemigo le vuelve a dar
        assertThat(ana.getLives()).isEqualTo(2);
        assertThat(ana.getHp()).isEqualTo(100); // reaparece con la barra llena
    }

    @Test
    void elBotiquinCuraSinPasarseDelMaximo() {
        Game game = gameWith(gunsConfig(), openMap());
        Player ana = game.getPlayer("p1");
        game.start();

        ana.takeDamage(70);
        game.grantPowerUp("p1", PowerUpType.HEALTH);
        assertThat(ana.getHp()).isEqualTo(80);
        game.grantPowerUp("p1", PowerUpType.HEALTH);
        assertThat(ana.getHp()).isEqualTo(100);
    }

    @Test
    void sinMunicionNoSePuedeDisparar() {
        Game game = gameWith(gunsConfig(), openMap());
        game.start();
        game.getPlayer("p1").setCurse(Curse.NO_BOMBS, 200);
        assertThat(game.placeBomb("p1")).isFalse();
    }

    @Test
    void enElModoClasicoNoHayPico() {
        Game game = gameWith(testConfig(), openMap());
        game.start();
        assertThat(game.mine("p1")).isFalse();
    }
}
