package com.bomberman.gameserver.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Motor de UNA partida. Contiene todas las reglas de Bomberman.
 *
 * Cómo se usa (en la fase 2 lo hará el "game loop" 20 veces por segundo):
 *   1. addPlayer(...) mientras la sala está en WAITING.
 *   2. start().
 *   3. Cuando llegan mensajes de los clientes: setMoveIntent(...) / placeBomb(...).
 *   4. tick() cada 50 ms. El tick avanza el mundo un paso.
 *   5. Leer el estado con los getters y enviarlo a los clientes.
 *
 * IMPORTANTE: esta clase NO es thread-safe a propósito. Todas las llamadas
 * deben hacerse desde un único hilo (el del game loop). Es más simple y más
 * rápido que llenar todo de "synchronized". En la fase 2 los mensajes de red
 * se meten en una cola y el game loop los aplica al principio de cada tick.
 *
 * El objeto Random se recibe desde fuera para que los tests puedan usar una
 * semilla fija y obtener siempre el mismo resultado.
 */
public class Game {

    private final GameConfig config;
    private final GameMap map;
    private final Random random;

    // LinkedHashMap mantiene el orden de llegada (jugador 1, 2, 3, 4).
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final List<Bomb> bombs = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Shell> shells = new ArrayList<>();
    private final Map<Position, PowerUp> powerUps = new HashMap<>();
    /** Modo pistolas: golpes de pico que lleva cada bloque. */
    private final Map<Position, Integer> blockDamage = new HashMap<>();
    private final List<GameEvent> events = new ArrayList<>();

    private GameStatus status = GameStatus.WAITING;
    private long tickCount = 0;
    private int nextBombId = 1;
    private int nextEnemyId = 1;
    private int nextShellId = 1;
    private int initialPlayerCount = 0;
    private boolean hadEnemies = false;
    /** Id del ganador; null si hay empate o si todos perdieron. */
    private String winnerId = null;

    /** Bloque donde está escondido el tanque de esta partida (null = ya salió o no hay). */
    private Position tankBlock;

    // Muerte súbita
    private boolean suddenDeath = false;
    private List<Position> suddenDeathOrder;
    private int suddenDeathIndex = 0;
    private int suddenDeathWallsPerDrop = 1;

    /** Crea una partida con un mapa generado al azar. */
    public Game(GameConfig config, Random random) {
        this(config,
                GameMap.generate(config.width, config.height, config.blockDensity, config.spawnSafeRadius, random),
                random);
    }

    /** Crea una partida con un mapa concreto (lo usan los tests). */
    public Game(GameConfig config, GameMap map, Random random) {
        this.config = config;
        this.map = map;
        this.random = random;
    }

    // =====================================================================
    //  Preparación de la partida
    // =====================================================================

    public Player addPlayer(String id, String name) {
        if (status != GameStatus.WAITING) {
            throw new IllegalStateException("La partida ya ha empezado");
        }
        if (players.containsKey(id)) {
            throw new IllegalArgumentException("El jugador ya está en la sala: " + id);
        }
        if (players.size() >= config.maxPlayers) {
            throw new IllegalStateException("La sala está llena");
        }
        Player player = new Player(id, name, freeSpawnPoint(), config);
        players.put(id, player);
        return player;
    }

    /** Busca una esquina que no esté ocupada por otro jugador. */
    private Position freeSpawnPoint() {
        for (Position spawn : map.getSpawnPoints()) {
            boolean used = players.values().stream().anyMatch(p -> p.getSpawn().equals(spawn));
            if (!used) {
                return spawn;
            }
        }
        throw new IllegalStateException("No quedan esquinas libres");
    }

    /**
     * Un jugador se va. En la sala de espera simplemente se le quita;
     * con la partida empezada cuenta como eliminado.
     */
    public void removePlayer(String id) {
        Player player = players.get(id);
        if (player == null) {
            return;
        }
        if (status == GameStatus.WAITING) {
            players.remove(id);
        } else if (player.isAlive()) {
            player.eliminate();
            events.add(new GameEvent(GameEvent.Type.PLAYER_ELIMINATED, player.getPosition(), id));
        }
    }

    /** Añade un enemigo en una posición concreta (útil para tests y niveles fijos). */
    public Enemy addEnemy(Position position) {
        Direction dir = Direction.values()[random.nextInt(4)];
        // Empiezan con el cooldown lleno para no moverse justo en el primer tick.
        Enemy enemy = new Enemy(nextEnemyId++, position, dir, config.enemyMoveDelayTicks, config.enemyHp);
        enemies.add(enemy);
        return enemy;
    }

    public void start() {
        if (status != GameStatus.WAITING) {
            throw new IllegalStateException("La partida ya ha empezado");
        }
        if (players.isEmpty()) {
            throw new IllegalStateException("No hay jugadores");
        }
        for (int i = 0; i < config.enemyCount; i++) {
            Position pos = randomEnemyPosition();
            if (pos != null) {
                addEnemy(pos);
            }
        }
        if (config.guaranteedTank) {
            tankBlock = chooseTankBlock();
        }
        initialPlayerCount = players.size();
        hadEnemies = !enemies.isEmpty();
        status = GameStatus.RUNNING;
        events.add(new GameEvent(GameEvent.Type.GAME_STARTED, null, null));
    }

    /**
     * Elige el bloque que esconde el tanque: uno al azar a poca distancia del centro
     * (así nadie lo tiene más cerca que los demás). Si no hay ninguno tan cerca,
     * el bloque más cercano al centro. Si el mapa no tiene bloques, no hay tanque.
     */
    private Position chooseTankBlock() {
        Position center = new Position(map.getWidth() / 2, map.getHeight() / 2);
        List<Position> near = new ArrayList<>();
        Position closest = null;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Position p = new Position(x, y);
                if (map.getTile(p) != Tile.BLOCK) continue;
                if (p.manhattan(center) <= config.tankHideRadius) near.add(p);
                if (closest == null || p.manhattan(center) < closest.manhattan(center)) closest = p;
            }
        }
        return near.isEmpty() ? closest : near.get(random.nextInt(near.size()));
    }

    /** Una casilla libre lejos de las esquinas, para no matar a nadie nada más empezar. */
    private Position randomEnemyPosition() {
        List<Position> candidates = new ArrayList<>();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Position p = new Position(x, y);
                boolean farFromSpawns = map.getSpawnPoints().stream()
                        .allMatch(s -> s.manhattan(p) >= config.enemyMinSpawnDistance);
                if (!map.isSolid(p) && farFromSpawns && enemyAt(p) == null) {
                    candidates.add(p);
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    // =====================================================================
    //  Intenciones de los jugadores (lo único que pueden pedir los clientes)
    // =====================================================================

    /** El jugador quiere moverse en esa dirección (null = parar). */
    public void setMoveIntent(String playerId, Direction direction) {
        Player player = players.get(playerId);
        if (player != null && player.isAlive()) {
            player.setMoveIntent(direction);
        }
    }

    /**
     * El jugador pulsa el botón de acción (espacio): a pie pone una bomba (clásico)
     * o dispara su arma (pistolas); subido al tanque dispara el cañón.
     * El servidor comprueba que sea legal; si no, lo ignora.
     * @return true si se colocó la bomba o se disparó
     */
    public boolean placeBomb(String playerId) {
        if (status != GameStatus.RUNNING) {
            return false;
        }
        Player player = players.get(playerId);
        if (player == null || !player.isAlive()) {
            return false;
        }
        if (player.isInTank()) {
            return shoot(player);
        }
        if (player.getCurse() == Curse.NO_BOMBS) {
            return false; // maldición: sin bombas / sin munición
        }
        return config.mode == GameMode.GUNS ? fireWeapon(player) : dropBomb(player);
    }

    /**
     * Modo pistolas: el jugador mantiene (true) o suelta (false) el gatillo.
     * Mientras lo mantenga, el servidor dispara cada vez que el arma esté lista:
     * así la cadencia la controla el servidor y nadie puede disparar más rápido.
     */
    public void setFiring(String playerId, boolean firing) {
        Player player = players.get(playerId);
        if (player != null && player.isAlive()) {
            player.setFiring(firing);
        }
    }

    /**
     * Modo pistolas: un golpe de pico a la casilla de enfrente.
     * Rompe bloques en varios golpes y también hace daño cuerpo a cuerpo.
     */
    public boolean mine(String playerId) {
        Player player = players.get(playerId);
        if (status != GameStatus.RUNNING || config.mode != GameMode.GUNS || player == null
                || !player.isAlive() || player.isInTank() || !player.canMineNow()) {
            return false;
        }
        player.startMineCooldown();
        Position target = player.getPosition().move(player.getFacing());
        events.add(new GameEvent(GameEvent.Type.PICKAXE_SWING, target, player.getId()));

        Enemy enemy = enemyAt(target);
        if (enemy != null) {
            damageEnemy(enemy, config.pickaxeDamage, player.getId());
            return true;
        }
        for (Player other : players.values()) {
            if (other != player && other.isAlive() && other.getPosition().equals(target) && !other.isInvulnerable()) {
                damagePlayer(other, config.pickaxeDamage, player.getId());
                return true;
            }
        }
        if (map.isInside(target) && map.getTile(target) == Tile.BLOCK) {
            int hits = blockDamage.merge(target, 1, Integer::sum);
            if (hits >= config.blockHp) {
                destroyBlock(target, player.getId());
                dropFromBlock(target);
            } else {
                events.add(new GameEvent(GameEvent.Type.BLOCK_CRACKED, target, player.getId()));
            }
        }
        return true;
    }

    /**
     * Dispara el arma actual (respetando su cadencia).
     * La escopeta dispara en ABANICO: al frente y a los dos lados. (Primero probamos
     * 3 carriles paralelos, pero en un mapa de Bomberman los carriles de al lado casi
     * siempre chocan con un pilar a la primera casilla; los tests lo detectaron.)
     */
    private boolean fireWeapon(Player player) {
        if (!player.canFireNow() || player.getCurse() == Curse.NO_BOMBS) {
            return false;
        }
        Weapon weapon = player.getWeapon();
        player.startFireCooldown(weapon.cooldownTicks(player.getWeaponLevel()));
        events.add(new GameEvent(GameEvent.Type.SHOT_FIRED, player.getPosition(), player.getId()));

        Direction dir = player.getFacing();
        List<Direction> directions = new ArrayList<>(List.of(dir));
        if (weapon.lanes == 3) {
            Direction side = (dir == Direction.UP || dir == Direction.DOWN) ? Direction.LEFT : Direction.UP;
            directions.add(side);
            directions.add(side.opposite());
        }
        for (Direction d : directions) {
            Shell bullet = new Shell(nextShellId++, player.getId(), player.getPosition(), d,
                    Shell.Kind.BULLET, weapon.damage, weapon.piercing);
            if (advanceShell(bullet)) {
                shells.add(bullet);
            }
        }
        return true;
    }

    private boolean dropBomb(Player player) {
        Position pos = player.getPosition();
        if (bombAt(pos) != null) {
            return false; // ya hay una bomba aquí
        }
        long activeBombs = bombs.stream().filter(b -> b.getOwnerId().equals(player.getId())).count();
        if (activeBombs >= player.getMaxBombs()) {
            return false; // ha alcanzado su límite de bombas simultáneas
        }
        bombs.add(new Bomb(nextBombId++, player.getId(), pos, player.getBombRange(), config.bombTimerTicks));
        events.add(new GameEvent(GameEvent.Type.BOMB_PLACED, pos, player.getId()));
        return true;
    }

    /** Disparo del tanque en la dirección a la que mira. */
    private boolean shoot(Player player) {
        if (!player.canShootNow()) {
            return false; // el cañón se está recargando
        }
        player.startShootCooldown();
        events.add(new GameEvent(GameEvent.Type.SHOT_FIRED, player.getPosition(), player.getId()));
        Shell shell = new Shell(nextShellId++, player.getId(), player.getPosition(), player.getFacing(),
                Shell.Kind.TANK, config.tankShellDamage, false);
        if (advanceShell(shell)) { // primer paso inmediato: sale del cañón
            shells.add(shell);
        }
        return true;
    }

    // =====================================================================
    //  El TICK: avanza el mundo un paso (1/20 de segundo)
    // =====================================================================

    public void tick() {
        if (status != GameStatus.RUNNING) {
            return;
        }
        tickCount++;
        updatePlayers();        // 1. mover jugadores, patadas y power-ups
        spreadCurses();         // 2. la calavera se contagia
        updateEnemies();        // 3. mover enemigos
        updateSlidingBombs();   // 4. bombas pateadas
        updateShells();         // 5. balas del tanque
        updateBombs();          // 6. cuentas atrás y explosiones (con reacción en cadena)
        updateSuddenDeath();    // 7. muerte súbita: caen muros
        updateExplosions();     // 8. las llamas hacen daño y se van apagando
        checkEnemyContacts();   // 9. enemigos que tocan jugadores (o tanques que aplastan enemigos)
        checkEndConditions();   // 10. ¿alguien ha ganado?
    }

    // ---------- 1. Jugadores ----------

    private void updatePlayers() {
        for (Player player : players.values()) {
            if (!player.isAlive()) {
                continue;
            }
            boolean tankExpired = player.tickTimers();
            if (tankExpired) {
                events.add(new GameEvent(GameEvent.Type.TANK_DESTROYED, player.getPosition(), player.getId()));
            }

            Direction intent = player.getMoveIntent();
            if (intent != null && player.canMoveNow()) {
                // Maldición de controles invertidos: el servidor gira la intención
                Direction dir = player.getCurse() == Curse.REVERSED ? intent.opposite() : intent;
                Position target = player.getPosition().move(dir);
                Bomb bomb = bombAt(target);

                if (bomb != null && player.canKick() && !bomb.isSliding() && canBombEnter(target.move(dir))) {
                    // ¡Patada! La bomba sale deslizándose y el jugador se queda donde está
                    bomb.startSliding(dir);
                    player.setFacing(dir);
                    events.add(new GameEvent(GameEvent.Type.BOMB_KICKED, target, player.getId()));
                } else if (canPlayerEnter(target)) {
                    player.moveTo(target, dir);
                    pickUpPowerUp(player);
                } else {
                    // No puede avanzar, pero sí girarse (el cliente lo dibuja mirando a la pared).
                    player.setFacing(dir);
                }
            }

            boolean diarrhea = player.getCurse() == Curse.DIARRHEA;
            if (config.mode == GameMode.GUNS) {
                // Gatillo mantenido (o maldición "diarrea" = gatillo atascado): dispara si está listo
                if (player.isFiring() || diarrhea) {
                    if (player.isInTank()) shoot(player);
                    else fireWeapon(player);
                }
            } else if (diarrhea && !player.isInTank()) {
                dropBomb(player); // maldición "diarrea": suelta bombas sin parar
            }
        }
    }

    /**
     * Validación del movimiento en el servidor: el cliente solo dice "quiero ir
     * a la derecha"; es el servidor quien decide si se puede. No se puede atravesar
     * muros, bloques ni bombas. (Sí se puede SALIR de la casilla de tu propia bomba,
     * porque solo comprobamos la casilla de destino.)
     */
    private boolean canPlayerEnter(Position target) {
        return !map.isSolid(target) && bombAt(target) == null;
    }

    private void pickUpPowerUp(Player player) {
        PowerUp powerUp = powerUps.remove(player.getPosition());
        if (powerUp != null) {
            applyPickup(player, powerUp.type());
        }
    }

    /**
     * Truco de pruebas: da un power-up directamente, igual que si lo hubiera recogido.
     * Game no sabe nada de "trucos": quien decide si está permitido es el servidor
     * (GameRoom solo lo llama si game.cheats-enabled=true).
     */
    public void grantPowerUp(String playerId, PowerUpType type) {
        Player player = players.get(playerId);
        if (status == GameStatus.RUNNING && player != null && player.isAlive()) {
            applyPickup(player, type);
        }
    }

    private void applyPickup(Player player, PowerUpType type) {
        events.add(new GameEvent(GameEvent.Type.POWERUP_TAKEN, player.getPosition(), player.getId()));
        switch (type) {
            case SKULL -> {
                Curse curse = Curse.values()[random.nextInt(Curse.values().length)];
                player.setCurse(curse, config.curseDurationTicks);
                events.add(new GameEvent(GameEvent.Type.CURSED, player.getPosition(), player.getId()));
            }
            case TANK -> {
                player.applyPowerUp(PowerUpType.TANK);
                events.add(new GameEvent(GameEvent.Type.TANK_ON, player.getPosition(), player.getId()));
            }
            case WEAPON -> {
                boolean evolved = player.upgradeWeapon();
                events.add(new GameEvent(evolved ? GameEvent.Type.WEAPON_EVOLVED : GameEvent.Type.WEAPON_UP,
                        player.getPosition(), player.getId()));
            }
            case HEALTH -> {
                player.heal(config.healthPackHeal);
                events.add(new GameEvent(GameEvent.Type.HEALED, player.getPosition(), player.getId()));
            }
            default -> player.applyPowerUp(type);
        }
    }

    // ---------- 2. Contagio de la calavera ----------

    /** Si un jugador maldito comparte casilla con otro sano, le pasa la maldición. */
    private void spreadCurses() {
        List<Player> alive = players.values().stream().filter(Player::isAlive).toList();
        for (Player sick : alive) {
            if (sick.getCurse() == null) {
                continue;
            }
            for (Player other : alive) {
                if (other != sick && other.getCurse() == null && other.getPosition().equals(sick.getPosition())) {
                    other.setCurse(sick.getCurse(), config.curseDurationTicks);
                    events.add(new GameEvent(GameEvent.Type.CURSED, other.getPosition(), other.getId()));
                }
            }
        }
    }

    // ---------- 3. Enemigos ----------

    private void updateEnemies() {
        for (Enemy enemy : enemies) {
            enemy.tickCooldown();
            if (!enemy.isAlive() || !enemy.canMoveNow()) {
                continue;
            }
            // Direcciones a las que puede ir desde aquí
            List<Direction> options = new ArrayList<>();
            for (Direction d : Direction.values()) {
                if (canEnemyEnter(enemy.getPosition().move(d))) {
                    options.add(d);
                }
            }
            if (options.isEmpty()) {
                continue; // encerrado: se queda quieto
            }
            Direction dir = enemy.getDirection();
            boolean blocked = !options.contains(dir);
            if (blocked || random.nextDouble() < config.enemyTurnChance) {
                dir = options.get(random.nextInt(options.size()));
            }
            enemy.moveTo(enemy.getPosition().move(dir), dir, config.enemyMoveDelayTicks);
        }
    }

    private boolean canEnemyEnter(Position target) {
        return !map.isSolid(target) && bombAt(target) == null;
    }

    // ---------- 4. Bombas pateadas ----------

    private void updateSlidingBombs() {
        for (Bomb bomb : bombs) {
            if (!bomb.isSliding() || !bomb.tickSlide()) {
                continue;
            }
            Position next = bomb.getPosition().move(bomb.getSlideDirection());
            if (canBombEnter(next)) {
                bomb.slideTo(next, config.bombSlideDelayTicks);
            } else {
                bomb.stopSliding(); // chocó con algo: se queda ahí
            }
        }
    }

    /** Una bomba deslizándose se para ante muros, bloques, otras bombas, jugadores y enemigos. */
    private boolean canBombEnter(Position p) {
        return !map.isSolid(p) && bombAt(p) == null && enemyAt(p) == null
                && players.values().stream().noneMatch(pl -> pl.isAlive() && pl.getPosition().equals(p));
    }

    // ---------- 5. Balas (pistolas y tanque) ----------

    private void updateShells() {
        shells.removeIf(shell -> !advanceShell(shell));
    }

    /**
     * Avanza un proyectil una casilla.
     *  - El obús del TANQUE perfora bloques: los rompe todos hasta chocar con un muro.
     *  - Las BALAS normales se paran en los bloques (para eso está el pico).
     *  - Al dar a un enemigo o jugador le hace daño; solo el rifle sigue después.
     * @return true si el proyectil sigue volando
     */
    private boolean advanceShell(Shell shell) {
        Position next = shell.getPosition().move(shell.getDirection());
        if (!map.isInside(next) || map.getTile(next) == Tile.WALL) {
            return false;
        }
        if (map.getTile(next) == Tile.BLOCK) {
            if (!shell.breaksBlocks()) {
                return false;
            }
            destroyBlock(next, shell.getOwnerId());
            dropFromBlock(next);
        }
        shell.moveTo(next);

        Enemy enemy = enemyAt(next);
        if (enemy != null) {
            damageEnemy(enemy, shell.getDamage(), shell.getOwnerId());
            if (!shell.isPiercing()) return false;
        }
        for (Player player : players.values()) {
            if (player.isAlive() && !player.getId().equals(shell.getOwnerId()) && player.getPosition().equals(next)) {
                if (!player.isInvulnerable()) {
                    damagePlayer(player, shell.getDamage(), shell.getOwnerId());
                }
                if (!shell.isPiercing()) return false;
            }
        }
        Bomb bomb = bombAt(next);
        if (bomb != null) {
            bomb.detonate(); // la bala hace explotar la bomba en este mismo tick
            return false;
        }
        return true;
    }

    // ---------- 6. Bombas y explosiones en cadena ----------

    private void updateBombs() {
        // Cola de bombas que deben explotar en este tick.
        Deque<Bomb> toExplode = new ArrayDeque<>();
        for (Bomb bomb : bombs) {
            bomb.tick();
            if (bomb.shouldExplode()) {
                toExplode.add(bomb);
            }
        }

        // Bloques rotos en este tick: sirven para que dos llamas no "atraviesen"
        // el mismo bloque y para soltar los power-ups al final.
        Set<Position> destroyedBlocks = new HashSet<>();

        // Mientras haya bombas en la cola, explotan. Si una llama toca otra bomba,
        // esa bomba se añade a la cola: así funciona la REACCIÓN EN CADENA.
        while (!toExplode.isEmpty()) {
            Bomb bomb = toExplode.poll();
            if (!bombs.remove(bomb)) {
                continue; // ya había explotado antes en esta misma cadena
            }
            Set<Position> cells = computeBlast(bomb, destroyedBlocks, toExplode);
            explosions.add(new Explosion(bomb.getOwnerId(), bomb.getPosition(), cells, config.explosionDurationTicks));
            events.add(new GameEvent(GameEvent.Type.EXPLOSION, bomb.getPosition(), bomb.getOwnerId()));
        }

        // Los power-ups aparecen DESPUÉS de resolver toda la cadena, para que
        // las llamas del mismo tick no los destruyan nada más nacer.
        for (Position p : destroyedBlocks) {
            dropFromBlock(p);
        }
    }

    /**
     * Calcula las casillas en CRUZ que alcanza una bomba.
     * Desde el centro avanza en las 4 direcciones hasta "range" casillas:
     *   - un MURO detiene la llama (y no arde),
     *   - un BLOQUE arde, se rompe y detiene la llama,
     *   - una BOMBA arde y se detona (reacción en cadena); la llama sigue.
     *   - un power-up que esté en el suelo se destruye.
     */
    private Set<Position> computeBlast(Bomb bomb, Set<Position> destroyedBlocks, Deque<Bomb> toExplode) {
        Set<Position> cells = new HashSet<>();
        Position center = bomb.getPosition();
        cells.add(center);
        burnPowerUp(center);

        for (Direction dir : Direction.values()) {
            for (int i = 1; i <= bomb.getRange(); i++) {
                Position p = center.move(dir, i);
                if (!map.isInside(p) || map.getTile(p) == Tile.WALL) {
                    break;
                }
                if (destroyedBlocks.contains(p)) {
                    cells.add(p); // bloque que otra bomba acaba de romper: arde pero frena
                    break;
                }
                if (map.getTile(p) == Tile.BLOCK) {
                    cells.add(p);
                    destroyBlock(p, bomb.getOwnerId());
                    destroyedBlocks.add(p);
                    break;
                }
                cells.add(p);
                burnPowerUp(p);
                Bomb other = bombAt(p);
                if (other != null && !toExplode.contains(other)) {
                    toExplode.add(other);
                }
            }
        }
        return cells;
    }

    private void destroyBlock(Position p, String ownerId) {
        map.setTile(p, Tile.EMPTY);
        blockDamage.remove(p);
        addScore(ownerId, config.scoreBlock);
        events.add(new GameEvent(GameEvent.Type.BLOCK_DESTROYED, p, ownerId));
    }

    /** Al romperse un bloque: si era el del tanque, sale el tanque; si no, quizá un power-up al azar. */
    private void dropFromBlock(Position p) {
        if (p.equals(tankBlock)) {
            tankBlock = null;
            powerUps.put(p, new PowerUp(p, PowerUpType.TANK));
            events.add(new GameEvent(GameEvent.Type.POWERUP_SPAWNED, p, null));
        } else if (random.nextDouble() < config.powerUpDropChance) {
            powerUps.put(p, new PowerUp(p, randomPowerUpType()));
            events.add(new GameEvent(GameEvent.Type.POWERUP_SPAWNED, p, null));
        }
    }

    /** Las llamas destruyen los power-ups del suelo... salvo el tanque, que está blindado. */
    private void burnPowerUp(Position p) {
        PowerUp powerUp = powerUps.get(p);
        if (powerUp != null && powerUp.type() != PowerUpType.TANK) {
            powerUps.remove(p);
        }
    }

    /** Elige un power-up según su "peso": cuanto más peso, más probable (el tanque es el más raro). */
    private PowerUpType randomPowerUpType() {
        PowerUpType[] types = {PowerUpType.BOMB_RANGE, PowerUpType.EXTRA_BOMB, PowerUpType.SPEED,
                PowerUpType.KICK, PowerUpType.SKULL, PowerUpType.TANK, PowerUpType.WEAPON, PowerUpType.HEALTH};
        int[] weights = {config.weightBombRange, config.weightExtraBomb, config.weightSpeed,
                config.weightKick, config.weightSkull, config.weightTank, config.weightWeapon, config.weightHealth};
        int total = 0;
        for (int w : weights) total += w;
        int roll = random.nextInt(Math.max(1, total));
        for (int i = 0; i < types.length; i++) {
            roll -= weights[i];
            if (roll < 0) return types[i];
        }
        return PowerUpType.BOMB_RANGE;
    }

    // ---------- 7. Muerte súbita ----------

    /**
     * En los últimos segundos cae un muro cada pocos ticks, en espiral desde el
     * borde hacia el centro. Quien esté debajo queda eliminado. Evita que alguien
     * se esconda en una esquina hasta que se acabe el tiempo.
     */
    private void updateSuddenDeath() {
        if (config.suddenDeathSeconds <= 0) {
            return;
        }
        long start = config.maxDurationTicks - (long) config.suddenDeathSeconds * config.ticksPerSecond;
        if (tickCount < start) {
            return;
        }
        if (!suddenDeath) {
            suddenDeath = true;
            // Recorrido en espiral sin los pilares (ya son muro)
            suddenDeathOrder = spiralOrder().stream().filter(p -> map.getTile(p) != Tile.WALL).toList();
            suddenDeathWallsPerDrop = config.suddenDeathWallsPerDrop > 0
                    ? config.suddenDeathWallsPerDrop
                    : wallsPerDropToFillMap(suddenDeathOrder.size());
            events.add(new GameEvent(GameEvent.Type.SUDDEN_DEATH, null, null));
        }
        if ((tickCount - start) % config.suddenDeathIntervalTicks != 0) {
            return;
        }
        // Siguientes casillas de la espiral
        for (int i = 0; i < suddenDeathWallsPerDrop && suddenDeathIndex < suddenDeathOrder.size(); i++) {
            Position p = suddenDeathOrder.get(suddenDeathIndex++);
            if (map.getTile(p) != Tile.WALL) {
                dropWall(p);
            }
        }
    }

    /**
     * Cuántos muros deben caer cada vez para tapar todo el mapa al 90 % del tiempo
     * de muerte súbita. Así funciona igual en un mapa pequeño que en uno enorme.
     */
    private int wallsPerDropToFillMap(int cells) {
        int drops = config.suddenDeathSeconds * config.ticksPerSecond / config.suddenDeathIntervalTicks;
        return Math.max(1, (int) Math.ceil(cells / (drops * 0.9)));
    }

    /** Casillas interiores recorridas en espiral: anillo exterior primero, hacia el centro. */
    private List<Position> spiralOrder() {
        List<Position> order = new ArrayList<>();
        int x0 = 1, y0 = 1, x1 = map.getWidth() - 2, y1 = map.getHeight() - 2;
        while (x0 <= x1 && y0 <= y1) {
            for (int x = x0; x <= x1; x++) order.add(new Position(x, y0));                // arriba →
            for (int y = y0 + 1; y <= y1; y++) order.add(new Position(x1, y));            // derecha ↓
            if (y1 > y0) for (int x = x1 - 1; x >= x0; x--) order.add(new Position(x, y1)); // abajo ←
            if (x1 > x0) for (int y = y1 - 1; y > y0; y--) order.add(new Position(x0, y));  // izquierda ↑
            x0++; y0++; x1--; y1--;
        }
        return order;
    }

    private void dropWall(Position p) {
        map.setTile(p, Tile.WALL);
        blockDamage.remove(p);
        bombs.removeIf(b -> b.getPosition().equals(p));
        shells.removeIf(s -> s.getPosition().equals(p));
        powerUps.remove(p);
        for (Enemy enemy : enemies) {
            if (enemy.isAlive() && enemy.getPosition().equals(p)) {
                enemy.kill();
                events.add(new GameEvent(GameEvent.Type.ENEMY_KILLED, p, null));
            }
        }
        for (Player player : players.values()) {
            if (player.isAlive() && player.getPosition().equals(p)) {
                // Aplastado: eliminado directamente (ni el tanque te salva)
                events.add(new GameEvent(GameEvent.Type.PLAYER_HIT, p, player.getId()));
                player.eliminate();
                events.add(new GameEvent(GameEvent.Type.PLAYER_ELIMINATED, p, player.getId()));
            }
        }
        events.add(new GameEvent(GameEvent.Type.WALL_DROPPED, p, null));
    }

    // ---------- 8. Daño de las llamas ----------

    private void updateExplosions() {
        // Juntamos todas las casillas que arden y quién puso la bomba (para dar puntos).
        Map<Position, String> burning = new HashMap<>();
        for (Explosion ex : explosions) {
            for (Position cell : ex.getCells()) {
                burning.putIfAbsent(cell, ex.getOwnerId());
            }
        }

        for (Player player : players.values()) {
            if (player.isAlive() && !player.isInvulnerable() && burning.containsKey(player.getPosition())) {
                hitPlayer(player, burning.get(player.getPosition()));
            }
        }

        for (Enemy enemy : enemies) {
            if (enemy.isAlive() && burning.containsKey(enemy.getPosition())) {
                killEnemy(enemy, burning.get(enemy.getPosition()));
            }
        }
        enemies.removeIf(e -> !e.isAlive());

        // Las llamas se van apagando
        for (Explosion ex : explosions) {
            ex.tick();
        }
        explosions.removeIf(Explosion::isFinished);
    }

    private void killEnemy(Enemy enemy, String killerId) {
        enemy.kill();
        addScore(killerId, config.scoreEnemy);
        events.add(new GameEvent(GameEvent.Type.ENEMY_KILLED, enemy.getPosition(), killerId));
    }

    /** Daño a un enemigo (balas y pico). En el clásico tienen 1 de vida: cualquier golpe los mata. */
    private void damageEnemy(Enemy enemy, int amount, String attackerId) {
        if (enemy.takeDamage(amount)) {
            addScore(attackerId, config.scoreEnemy);
            events.add(new GameEvent(GameEvent.Type.ENEMY_KILLED, enemy.getPosition(), attackerId));
        } else {
            events.add(new GameEvent(GameEvent.Type.ENEMY_DAMAGED, enemy.getPosition(), attackerId));
        }
    }

    // ---------- 9. Contacto con enemigos ----------

    private void checkEnemyContacts() {
        for (Player player : players.values()) {
            if (!player.isAlive()) {
                continue;
            }
            Enemy enemy = enemyAt(player.getPosition());
            if (enemy == null) {
                continue;
            }
            if (player.isInTank()) {
                killEnemy(enemy, player.getId()); // ¡el tanque lo aplasta! (como la estrella de Mario)
            } else if (!player.isInvulnerable()) {
                damagePlayer(player, config.enemyContactDamage, null);
                // Un momento de invulnerabilidad: si no, el enemigo le quitaría vida en cada tick
                if (player.isAlive()) player.setInvulnerableTicks(config.contactInvulnerabilityTicks);
            }
        }
        enemies.removeIf(e -> !e.isAlive());
    }

    /**
     * Daño a un jugador, sea cual sea el modo:
     *  - en tanque: el tanque absorbe el golpe;
     *  - CLÁSICO: cualquier golpe quita una vida;
     *  - PISTOLAS: resta vida de la barra; al llegar a 0 pierde una vida y reaparece con la barra llena.
     */
    private void damagePlayer(Player player, int amount, String attackerId) {
        if (config.mode == GameMode.GUNS && !player.isInTank()) {
            boolean empty = player.takeDamage(amount);
            if (!empty) {
                events.add(new GameEvent(GameEvent.Type.PLAYER_DAMAGED, player.getPosition(), player.getId()));
                return;
            }
        }
        hitPlayer(player, attackerId);
    }

    /**
     * Un jugador pierde una vida. "attackerId" es quien puso la bomba o disparó (null si fue un enemigo).
     * Si va en tanque, el tanque absorbe el golpe.
     */
    private void hitPlayer(Player player, String attackerId) {
        Position where = player.getPosition();
        if (player.isInTank()) {
            boolean destroyed = player.damageTank();
            events.add(new GameEvent(GameEvent.Type.TANK_HIT, where, player.getId()));
            if (destroyed) {
                events.add(new GameEvent(GameEvent.Type.TANK_DESTROYED, where, player.getId()));
            }
            return;
        }
        events.add(new GameEvent(GameEvent.Type.PLAYER_HIT, where, player.getId()));
        if (attackerId != null && !attackerId.equals(player.getId())) {
            addScore(attackerId, config.scorePlayerHit);
        }
        boolean eliminated = player.loseLife(findRespawn(player.getSpawn()));
        if (eliminated) {
            events.add(new GameEvent(GameEvent.Type.PLAYER_ELIMINATED, where, player.getId()));
        }
    }

    /** Su esquina; o, si la muerte súbita ya la tapó, la casilla libre más cercana. */
    private Position findRespawn(Position spawn) {
        if (!map.isSolid(spawn) && bombAt(spawn) == null) {
            return spawn;
        }
        Position best = spawn;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Position p = new Position(x, y);
                int d = p.manhattan(spawn);
                if (d < bestDistance && !map.isSolid(p) && bombAt(p) == null) {
                    best = p;
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    // ---------- 10. Victoria y derrota ----------

    private void checkEndConditions() {
        List<Player> alive = players.values().stream().filter(Player::isAlive).toList();

        if (alive.isEmpty()) {
            finish(null);                                  // todos muertos: derrota / empate
        } else if (initialPlayerCount >= 2 && alive.size() == 1) {
            finish(alive.get(0).getId());                  // último jugador en pie
        } else if (hadEnemies && enemies.isEmpty()) {
            finish(bestScore(alive));                      // todos los enemigos eliminados
        } else if (tickCount >= config.maxDurationTicks) {
            finish(bestScore(alive));                      // se acabó el tiempo
        }
    }

    /** El jugador vivo con más puntos; null si hay empate en el primer puesto. */
    private String bestScore(List<Player> alive) {
        List<Player> sorted = alive.stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed())
                .toList();
        if (sorted.size() > 1 && sorted.get(0).getScore() == sorted.get(1).getScore()) {
            return null;
        }
        return sorted.get(0).getId();
    }

    private void finish(String winner) {
        status = GameStatus.FINISHED;
        winnerId = winner;
        events.add(new GameEvent(GameEvent.Type.GAME_OVER, null, winner));
    }

    // =====================================================================
    //  Utilidades
    // =====================================================================

    private void addScore(String playerId, int points) {
        Player p = playerId == null ? null : players.get(playerId);
        if (p != null) {
            p.addScore(points);
        }
    }

    public Bomb bombAt(Position p) {
        for (Bomb b : bombs) {
            if (b.getPosition().equals(p)) return b;
        }
        return null;
    }

    public Enemy enemyAt(Position p) {
        for (Enemy e : enemies) {
            if (e.isAlive() && e.getPosition().equals(p)) return e;
        }
        return null;
    }

    /** Devuelve los eventos ocurridos desde la última llamada y vacía la lista. */
    public List<GameEvent> drainEvents() {
        List<GameEvent> copy = new ArrayList<>(events);
        events.clear();
        return copy;
    }

    // ----- Getters (solo lectura) -----

    public GameConfig getConfig() { return config; }
    public GameMap getMap() { return map; }
    public GameStatus getStatus() { return status; }
    public long getTickCount() { return tickCount; }
    public String getWinnerId() { return winnerId; }
    public boolean isSuddenDeath() { return suddenDeath; }
    public Player getPlayer(String id) { return players.get(id); }
    public Collection<Player> getPlayers() { return Collections.unmodifiableCollection(players.values()); }
    public List<Bomb> getBombs() { return Collections.unmodifiableList(bombs); }
    public List<Explosion> getExplosions() { return Collections.unmodifiableList(explosions); }
    public List<Enemy> getEnemies() { return Collections.unmodifiableList(enemies); }
    public List<Shell> getShells() { return Collections.unmodifiableList(shells); }
    public Collection<PowerUp> getPowerUps() { return Collections.unmodifiableCollection(powerUps.values()); }
    /** Bloques agrietados por el pico y cuántos golpes llevan (el cliente dibuja las grietas). */
    public Map<Position, Integer> getBlockDamage() { return Collections.unmodifiableMap(blockDamage); }

    /** Coloca un power-up concreto (para tests y para probar a mano). */
    void putPowerUp(Position p, PowerUpType type) {
        powerUps.put(p, new PowerUp(p, type));
    }
}
