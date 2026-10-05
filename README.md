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

## Modos de juego

El anfitrión lo elige en la sala de espera:

- **💣 Clásico:** bombas en cruz, reacciones en cadena, power-ups (alcance, bombas, velocidad, patada, calavera) y 3 vidas.
- **🔫 Pistolas y pico:** disparas manteniendo Espacio y picas bloques con E. Hay barra de vida y botiquines. Cada mejora de arma da más cadencia, y cada 3 el arma **evoluciona**: pistola → subfusil → escopeta (abanico de 3 balas) → rifle (atraviesa).

En los dos modos hay un **mini tanque** escondido cerca del centro del mapa, y **muerte súbita** en los últimos 45 s.

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
