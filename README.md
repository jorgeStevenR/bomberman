# Bomberman multijugador (Cloud Computing)

Bomberman multijugador en el navegador con **servidor autoritativo** en Java (Spring Boot + WebSockets) y frontend en **Phaser 3**. Es un proyecto universitario: la arquitectura imita un despliegue en AWS (ALB, ECS Fargate, Aurora, ElastiCache...) usando contenedores locales con Docker Compose.

> 🚧 En desarrollo. Hechas: fase 1 (lógica + tests), fase 2 (WebSockets) y fase 3 (frontend). Pendientes: API + PostgreSQL, Docker Compose, CI y la documentación para la defensa.

## Cómo jugar en local

Requisitos: **Java 21+** y **Maven**.

```bash
cd game-server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Abre `http://localhost:8081`. Desde otro PC de la misma red WiFi, usa `http://IP-DE-TU-PC:8081`.

| Tecla | Acción |
|---|---|
| Flechas / WASD | Moverse |
| Espacio | Bomba (modo clásico) / disparar (modo pistolas o tanque) |
| E | Pico (modo pistolas) |
| 1–4 | Emotes |
| M | Silenciar |
| T | Truco: tanque (solo con el perfil `dev`) |

## Tests

```bash
cd game-server
mvn test
```

## Estructura

```
game-server/   Servidor de partidas (Spring Boot, WebSocket, game loop a 20 Hz)
frontend/      Cliente Phaser 3 (JavaScript, sin compilación)
docs/          Protocolo WebSocket documentado
```

- Protocolo de mensajes: [docs/PROTOCOLO.md](docs/PROTOCOLO.md)
- Licencias de los recursos gráficos y de sonido: [frontend/CREDITOS.md](frontend/CREDITOS.md)
