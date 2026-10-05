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
        boolean cheatsEnabled) {
}
