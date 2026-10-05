package com.bomberman.gameserver.protocol;

import com.bomberman.gameserver.game.Game;
import com.bomberman.gameserver.game.GameMap;
import com.bomberman.gameserver.game.Player;
import com.bomberman.gameserver.game.Position;
import com.bomberman.gameserver.protocol.ServerMessages.BombView;
import com.bomberman.gameserver.protocol.ServerMessages.CrackView;
import com.bomberman.gameserver.protocol.ServerMessages.EnemyView;
import com.bomberman.gameserver.protocol.ServerMessages.ExplosionView;
import com.bomberman.gameserver.protocol.ServerMessages.GameOver;
import com.bomberman.gameserver.protocol.ServerMessages.PlayerView;
import com.bomberman.gameserver.protocol.ServerMessages.PowerUpView;
import com.bomberman.gameserver.protocol.ServerMessages.ResultView;
import com.bomberman.gameserver.protocol.ServerMessages.ShellView;
import com.bomberman.gameserver.protocol.ServerMessages.State;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Convierte el estado interno del juego en los mensajes que viajan por la red. */
public final class Snapshots {

    private Snapshots() {
    }

    /**
     * Foto completa del mundo.
     *
     * Decisión: enviamos el estado COMPLETO cada tick (jugadores, bombas, enemigos...).
     * Así el cliente es muy simple y si se pierde un mensaje el siguiente lo arregla todo.
     *
     * EXCEPCIÓN: el mapa. En 41x33 son ~1.400 caracteres que casi nunca cambian;
     * enviarlo 20 veces por segundo sería desperdiciar ancho de banda (y aumenta el lag
     * por internet). Por eso solo va cuando cambia o, como "foto de seguridad", una vez
     * por segundo (para quien se acaba de reconectar). Si no va, "map" es null.
     *
     * @param connectedIds jugadores con conexión abierta (para pintar "desconectado")
     * @param includeMap   si hay que incluir el mapa en este mensaje
     */
    public static State state(Game game, Set<String> connectedIds, boolean includeMap) {
        int ticksLeft = game.getConfig().maxDurationTicks - (int) game.getTickCount();
        int timeLeft = Math.max(0, ticksLeft / game.getConfig().ticksPerSecond);

        List<PlayerView> players = game.getPlayers().stream()
                .map(p -> playerView(p, connectedIds.contains(p.getId())))
                .toList();
        List<BombView> bombs = game.getBombs().stream()
                .map(b -> new BombView(b.getId(), b.getPosition().x(), b.getPosition().y(),
                        b.getRange(), b.getTicksLeft(), b.isSliding(), game.getConfig().bombSlideDelayTicks))
                .toList();
        List<ExplosionView> explosions = game.getExplosions().stream()
                .map(e -> new ExplosionView(e.getCenter(), e.getCells(), e.getTicksLeft()))
                .toList();
        List<EnemyView> enemies = game.getEnemies().stream()
                .map(e -> new EnemyView(e.getId(), e.getPosition().x(), e.getPosition().y(), e.getDirection(),
                        game.getConfig().enemyMoveDelayTicks, e.getHp(), e.getMaxHp()))
                .toList();
        List<PowerUpView> powerUps = game.getPowerUps().stream()
                .map(p -> new PowerUpView(p.position().x(), p.position().y(), p.type()))
                .toList();
        List<ShellView> shells = game.getShells().stream()
                .map(s -> new ShellView(s.getId(), s.getPosition().x(), s.getPosition().y(), s.getDirection(), s.getKind()))
                .toList();
        List<CrackView> cracks = game.getBlockDamage().entrySet().stream()
                .map(e -> new CrackView(e.getKey().x(), e.getKey().y(), e.getValue()))
                .toList();

        return new State(game.getTickCount(), game.getStatus(), game.getConfig().mode, timeLeft, game.isSuddenDeath(),
                includeMap ? mapRows(game.getMap()) : null, players, bombs, explosions, enemies, shells, powerUps,
                cracks, game.drainEvents());
    }

    private static PlayerView playerView(Player p, boolean connected) {
        return new PlayerView(p.getId(), p.getName(), p.getPosition().x(), p.getPosition().y(),
                p.getFacing(), p.getMoveIntent() != null, p.getLives(), p.isAlive(),
                p.getBombRange(), p.getMaxBombs(), p.getSpeedLevel(), p.getMoveDelayTicks(),
                p.getScore(), p.isInvulnerable(), connected, p.canKick(), p.getCurse(),
                p.getTankArmor(), (p.getTankTicks() + 19) / 20,
                p.getHp(), p.getMaxHp(), p.getWeaponLevel(), p.getWeapon());
    }

    /** El mapa como filas de texto: '#' muro, 'B' bloque, '.' suelo. Compacto y fácil de leer. */
    static List<String> mapRows(GameMap map) {
        List<String> rows = new ArrayList<>(map.getHeight());
        for (int y = 0; y < map.getHeight(); y++) {
            StringBuilder row = new StringBuilder(map.getWidth());
            for (int x = 0; x < map.getWidth(); x++) {
                row.append(switch (map.getTile(new Position(x, y))) {
                    case WALL -> '#';
                    case BLOCK -> 'B';
                    case EMPTY -> '.';
                });
            }
            rows.add(row.toString());
        }
        return rows;
    }

    /** Clasificación final, de más a menos puntos. */
    public static GameOver gameOver(Game game) {
        List<ResultView> results = game.getPlayers().stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed())
                .map(p -> new ResultView(p.getId(), p.getName(), p.getScore(), p.getLives(), p.isAlive()))
                .toList();
        String winnerId = game.getWinnerId();
        String winnerName = winnerId == null ? null : game.getPlayer(winnerId).getName();
        return new GameOver(winnerId, winnerName, results);
    }
}
