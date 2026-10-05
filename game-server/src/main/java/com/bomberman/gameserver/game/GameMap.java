package com.bomberman.gameserver.game;

import java.util.List;
import java.util.Random;

/**
 * La cuadrícula del nivel: qué hay en cada casilla (suelo, muro o bloque).
 *
 * Las bombas, jugadores, enemigos y power-ups NO se guardan aquí: los guarda
 * la clase Game. El mapa solo sabe de "terreno".
 */
public class GameMap {

    private final int width;
    private final int height;
    /** tiles[y][x]: primero la fila y luego la columna. */
    private final Tile[][] tiles;
    private int version = 0;

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = Tile.EMPTY;
            }
        }
    }

    /**
     * Genera un mapa clásico de Bomberman:
     *  - borde exterior de muros,
     *  - "pilares" fijos en las casillas con x e y pares,
     *  - bloques destructibles al azar, excepto cerca de las esquinas donde aparecen los jugadores.
     */
    public static GameMap generate(int width, int height, double blockDensity, int safeRadius, Random random) {
        if (width < 7 || height < 7 || width % 2 == 0 || height % 2 == 0) {
            throw new IllegalArgumentException("El mapa debe ser impar y de al menos 7x7");
        }
        GameMap map = new GameMap(width, height);
        List<Position> spawns = map.getSpawnPoints();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                boolean pillar = x % 2 == 0 && y % 2 == 0;
                if (border || pillar) {
                    map.tiles[y][x] = Tile.WALL;
                    continue;
                }
                Position p = new Position(x, y);
                boolean nearSpawn = spawns.stream().anyMatch(s -> s.manhattan(p) <= safeRadius);
                if (!nearSpawn && random.nextDouble() < blockDensity) {
                    map.tiles[y][x] = Tile.BLOCK;
                }
            }
        }
        return map;
    }

    /**
     * Crea un mapa a partir de texto. Muy útil para los tests:
     *   '#' = muro, 'B' = bloque, cualquier otro carácter = suelo.
     */
    public static GameMap fromAscii(String... rows) {
        GameMap map = new GameMap(rows[0].length(), rows.length);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                map.tiles[y][x] = switch (c) {
                    case '#' -> Tile.WALL;
                    case 'B' -> Tile.BLOCK;
                    default -> Tile.EMPTY;
                };
            }
        }
        return map;
    }

    /** Las 4 esquinas interiores: ahí aparecen los jugadores 1, 2, 3 y 4. */
    public List<Position> getSpawnPoints() {
        return List.of(
                new Position(1, 1),
                new Position(width - 2, height - 2),
                new Position(width - 2, 1),
                new Position(1, height - 2));
    }

    public boolean isInside(Position p) {
        return p.x() >= 0 && p.y() >= 0 && p.x() < width && p.y() < height;
    }

    public Tile getTile(Position p) {
        return tiles[p.y()][p.x()];
    }

    void setTile(Position p, Tile tile) {
        tiles[p.y()][p.x()] = tile;
        version++;
    }

    /** Aumenta cada vez que cambia una casilla: así sabemos si hay que reenviar el mapa. */
    public int getVersion() {
        return version;
    }

    /** Muro o bloque: no se puede pisar. Fuera del mapa también cuenta como sólido. */
    public boolean isSolid(Position p) {
        return !isInside(p) || getTile(p) != Tile.EMPTY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
