package com.bomberman.gameserver.config;

import com.bomberman.gameserver.ws.GameWebSocketHandler;
import com.bomberman.gameserver.ws.RoomHandshakeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Registra el endpoint WebSocket /ws/game.
 *
 * Usamos WebSocket "crudo" con un TextWebSocketHandler, NO STOMP. Motivos:
 *  - STOMP es un protocolo de mensajería tipo "publicar/suscribirse" (canales,
 *    destinos, cabeceras). Es genial para chats o notificaciones, pero aquí cada
 *    conexión pertenece a una única sala y solo hay 6 tipos de mensaje.
 *  - Cada mensaje STOMP lleva cabeceras de texto extra: más bytes 20 veces por segundo.
 *  - Con WebSocket crudo controlamos exactamente cuándo y qué se envía (el game loop),
 *    y el cliente es un simple "new WebSocket(url)" sin librerías.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler handler;
    private final GameServerProperties props;

    public WebSocketConfig(GameWebSocketHandler handler, GameServerProperties props) {
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/game")
                .addInterceptors(new RoomHandshakeInterceptor())
                .setAllowedOriginPatterns(props.allowedOrigins());
    }

    /** Límites del servidor WebSocket (Tomcat). */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8 * 1024); // mensajes de más de 8 KB cierran la conexión
        container.setMaxSessionIdleTimeout(120_000L);    // 2 min sin actividad → se cierra
        return container;
    }
}
