package com.bomberman.gameserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del game-server: partidas en tiempo real por WebSocket.
 *
 * Paquetes:
 *  - game     → reglas del juego (Java puro, fase 1)
 *  - room     → salas y game loop
 *  - ws       → conexión WebSocket, validación y límite de mensajes
 *  - protocol → formato de los mensajes JSON
 *  - config   → configuración de Spring
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GameServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameServerApplication.class, args);
    }
}
