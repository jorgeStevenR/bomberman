package com.bomberman.gameserver.game;

import static com.bomberman.gameserver.game.TestSupport.pos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import org.junit.jupiter.api.Test;

class GameMapTest {

    @Test
    void elMapaGeneradoTieneBordeDeMurosYPilares() {
        GameMap map = GameMap.generate(15, 13, 0.6, 3, new Random(42));

        for (int x = 0; x < 15; x++) {
            assertThat(map.getTile(pos(x, 0))).isEqualTo(Tile.WALL);
            assertThat(map.getTile(pos(x, 12))).isEqualTo(Tile.WALL);
        }
        for (int y = 0; y < 13; y++) {
            assertThat(map.getTile(pos(0, y))).isEqualTo(Tile.WALL);
            assertThat(map.getTile(pos(14, y))).isEqualTo(Tile.WALL);
        }
        // Pilares fijos en coordenadas pares
        assertThat(map.getTile(pos(2, 2))).isEqualTo(Tile.WALL);
        assertThat(map.getTile(pos(4, 6))).isEqualTo(Tile.WALL);
    }

    @Test
    void lasEsquinasDeAparicionQuedanLibresDeBloques() {
        // Densidad 1.0: todas las casillas posibles tienen bloque... salvo las zonas seguras
        GameMap map = GameMap.generate(15, 13, 1.0, 3, new Random(1));

        for (Position spawn : map.getSpawnPoints()) {
            for (int y = 0; y < 13; y++) {
                for (int x = 0; x < 15; x++) {
                    Position p = pos(x, y);
                    if (spawn.manhattan(p) <= 3) {
                        assertThat(map.getTile(p)).isNotEqualTo(Tile.BLOCK);
                    }
                }
            }
        }
        // Fuera de la zona segura sí hay bloques
        assertThat(map.getTile(pos(5, 1))).isEqualTo(Tile.BLOCK);
    }

    @Test
    void fromAsciiInterpretaMurosBloquesYSuelo() {
        GameMap map = GameMap.fromAscii(
                "###",
                "#B.",
                "###");
        assertThat(map.getTile(pos(0, 0))).isEqualTo(Tile.WALL);
        assertThat(map.getTile(pos(1, 1))).isEqualTo(Tile.BLOCK);
        assertThat(map.getTile(pos(2, 1))).isEqualTo(Tile.EMPTY);
        assertThat(map.isSolid(pos(-1, 0))).isTrue(); // fuera del mapa = sólido
    }

    @Test
    void rechazaTamanosPares() {
        assertThrows(IllegalArgumentException.class,
                () -> GameMap.generate(14, 13, 0.5, 3, new Random()));
    }
}
