package com.bomberman.gameserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lee el bloque "game:" de application.yml.
 * Spring rellena este record automáticamente (server-id → serverId, etc.).
 */
@ConfigurationProperties(prefix = "game")
public record GameServerProperties(
        String serverId,
        int tickRate,
        int reconnectGraceSeconds,
        int maxMessagesPerSecond,
        String[] allowedOrigins,
        boolean cheatsEnabled,
        /** URL de la API de resultados (vacía = no se guardan, p. ej. en desarrollo sin Docker). */
        String apiUrl,
        /** Secreto compartido con la API: solo los game-servers pueden guardar partidas. */
        String apiToken) {
}
