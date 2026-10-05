package com.bomberman.gameserver.room;

import com.bomberman.gameserver.config.GameServerProperties;
import com.bomberman.gameserver.game.GameConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Guarda las salas activas de ESTE servidor y les da "latido" (el game loop).
 *
 * Decisión: un pequeño grupo de hilos (ScheduledThreadPool) mueve TODAS las salas,
 * en vez de crear un hilo por sala. Con scheduleAtFixedRate Java garantiza que el
 * tick de una misma sala nunca se ejecuta dos veces a la vez, aunque haya varios hilos.
 */
@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    private final GameServerProperties props;
    private final ObjectMapper mapper;

    public RoomManager(GameServerProperties props, ObjectMapper mapper, MeterRegistry metrics) {
        this.props = props;
        this.mapper = mapper;

        // Métricas visibles en /actuator/metrics (equivalente a métricas de CloudWatch)
        Gauge.builder("bomberman.rooms.active", rooms, Map::size)
                .description("Salas activas en este servidor")
                .register(metrics);
        Gauge.builder("bomberman.players.connected", this, RoomManager::connectedPlayers)
                .description("Jugadores conectados en este servidor")
                .register(metrics);

        // Cada 10 s eliminamos las salas que se quedaron vacías
        scheduler.scheduleAtFixedRate(this::cleanup, 10, 10, TimeUnit.SECONDS);
        log.info("Game server '{}' listo ({} ticks/s)", props.serverId(), props.tickRate());
    }

    /** Devuelve la sala con ese código, creándola si no existe. */
    public GameRoom getOrCreate(String code) {
        // compute() es atómico: si dos jugadores entran a la vez, solo se crea una sala.
        return rooms.compute(code, (key, existing) -> {
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
            if (existing != null) {
                existing.stop();
            }
            return createRoom(key);
        });
    }

    public Optional<GameRoom> find(String code) {
        return Optional.ofNullable(rooms.get(code));
    }

    private GameRoom createRoom(String code) {
        GameRoom room = new GameRoom(code, new GameConfig(), props, mapper);
        long periodMs = 1000L / props.tickRate();
        room.setTask(scheduler.scheduleAtFixedRate(room::loop, periodMs, periodMs, TimeUnit.MILLISECONDS));
        log.info("Sala {} creada en el servidor '{}'", code, props.serverId());
        // (Fase 5: aquí registraremos en Redis que la sala vive en este servidor.)
        return room;
    }

    private void cleanup() {
        for (GameRoom room : rooms.values()) {
            if (room.isClosed() && rooms.remove(room.getCode(), room)) {
                room.stop();
                log.info("Sala {} cerrada por estar vacía", room.getCode());
            }
        }
    }

    private double connectedPlayers() {
        return rooms.values().stream().mapToInt(GameRoom::getConnectedCount).sum();
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
