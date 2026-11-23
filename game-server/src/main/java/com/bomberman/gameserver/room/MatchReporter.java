package com.bomberman.gameserver.room;

import com.bomberman.gameserver.config.GameServerProperties;
import com.bomberman.gameserver.game.Game;
import com.bomberman.gameserver.game.GameMode;
import com.bomberman.gameserver.game.Player;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envía el resultado de cada partida a la API (Node + PostgreSQL).
 *
 * El envío es ASÍNCRONO: el game loop no puede esperar a la red ni a la base de
 * datos (si tarda 200 ms, la partida se congelaría). Si la API falla, se registra
 * un WARN en los logs (lo veremos en Grafana) y la partida sigue sin problema.
 */
@Component
public class MatchReporter {

    private static final Logger log = LoggerFactory.getLogger(MatchReporter.class);

    public record PlayerResult(String name, int score, int lives, boolean alive, boolean winner) {
    }

    public record MatchResult(String room, String serverId, String mode, long durationSeconds,
                              String winnerName, List<PlayerResult> players) {
    }

    private final GameServerProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public MatchReporter(GameServerProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public boolean isEnabled() {
        return props.apiUrl() != null && !props.apiUrl().isBlank();
    }

    /** Construye el resultado a partir de la partida terminada y lo envía en segundo plano. */
    public void report(String room, GameMode mode, Game game, long durationSeconds) {
        if (!isEnabled()) {
            return;
        }
        String winnerId = game.getWinnerId();
        List<PlayerResult> players = game.getPlayers().stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed())
                .map(p -> new PlayerResult(p.getName(), p.getScore(), p.getLives(), p.isAlive(),
                        p.getId().equals(winnerId)))
                .toList();
        String winnerName = winnerId == null ? null : game.getPlayer(winnerId).getName();
        send(new MatchResult(room, props.serverId(), mode.name(), durationSeconds, winnerName, players));
    }

    void send(MatchResult result) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.apiUrl() + "/api/matches"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", props.apiToken() == null ? "" : props.apiToken())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(result)))
                    .build();
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.warn("Sala {}: no se pudo guardar la partida en la API: {}",
                                    result.room(), error.toString());
                        } else if (response.statusCode() >= 300) {
                            log.warn("Sala {}: la API respondió {} al guardar la partida",
                                    result.room(), response.statusCode());
                        } else {
                            log.info("Sala {}: partida guardada en la base de datos", result.room());
                        }
                    });
        } catch (Exception e) {
            log.warn("Sala {}: error preparando el envío a la API: {}", result.room(), e.toString());
        }
    }
}
