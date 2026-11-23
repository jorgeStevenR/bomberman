# Bomberman multijugador (Cloud Computing)

Bomberman multijugador en el navegador con **servidor autoritativo** en Java (Spring Boot + WebSockets) y frontend en **Phaser 3**. Es un proyecto universitario: la arquitectura imita un despliegue en AWS (ALB, ECS Fargate, Aurora, ElastiCache...) usando contenedores locales con Docker Compose.

> 🚧 Hechas: lógica + tests, WebSockets, frontend, API + PostgreSQL, Docker Compose con balanceo, y logs con Loki + Grafana. Pendientes: diagrama AWS, documento explicativo con la cotización, y la presentación.

## Simulación con Docker Compose (la de la entrega)

Requisitos: **Docker Desktop** (incluye Docker Compose). No hace falta Java ni Node.

👉 **Guía paso a paso para descargarlo y probarlo:** [docs/GUIA-PRUEBA.md](docs/GUIA-PRUEBA.md)

```powershell
copy .env.example .env      # opcional: cambia las contraseñas
docker compose up -d --build
```

La primera vez tarda unos minutos (descarga imágenes y compila el juego). Después:

| Qué | Dónde |
|---|---|
| Juego | http://localhost:8080 |
| Ranking (datos de PostgreSQL) | http://localhost:8080/ranking.html |
| API | http://localhost:8080/api/health, `/api/leaderboard`, `/api/matches`, `/api/stats` |
| Grafana (usuario `admin`, contraseña `admin_bomberman` o la de `.env`) | http://localhost:3001 |

### Contenedores (11)

| Contenedor | Papel | Equivalente en AWS |
|---|---|---|
| `nginx` | Balanceador + frontend estático | ALB + S3/CloudFront |
| `game-1`, `game-2`, `game-3` | Game-server Java (WebSocket) | Servicio ECS Fargate con Auto Scaling |
| `api-1`, `api-2` | API REST en Node.js | Servicio ECS Fargate |
| `postgres` | Base de datos | RDS PostgreSQL Multi-AZ |
| `alloy` | Agente que recoge los logs de Docker | Driver awslogs / FireLens |
| `loki` | Almacén central de logs | CloudWatch Logs |
| `grafana` | Dashboard de logs | CloudWatch Dashboards / Amazon Managed Grafana |

Solo `nginx` (8080) y `grafana` (3001) publican puertos. La red `backend` hace de **subnet privada**: la base de datos, la API y los game-servers no son accesibles desde fuera.

### Balanceo de carga

- **WebSocket del juego → hash consistente por código de sala** (`hash $arg_room consistent`). Todos los jugadores de una sala tienen que llegar al mismo game-server porque la partida vive en su memoria. En la sala de espera se ve abajo `Servidor: game-N`.
- **API REST → least_conn** (la instancia con menos peticiones en curso). La API no guarda estado, así que cualquier instancia sirve. La cabecera `X-Served-By` dice cuál respondió.

Demostración automática para la presentación:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo-balanceo.ps1
```

### Logs (Loki + Grafana)

Todos los servicios escriben logs **JSON por stdout** con un campo `level`. Alloy los lee del socket de Docker, les pone las etiquetas `service`, `container` y `level`, y los envía a Loki. Grafana carga solo la fuente de datos y el dashboard **"Bomberman — Logs y balanceo"**, que muestra:

- Partidas jugadas y partidas guardadas en PostgreSQL
- Errores y advertencias por servicio, con el detalle de cada línea
- Salas por réplica del game-server y peticiones por instancia de la API (balanceo)
- Códigos de respuesta HTTP, latencia p50/p95 de la API y volumen de logs

Consultas útiles en *Explore*: `{level="ERROR"}`, `{service="api"} | json | status >= 400`, `{service="game-server"} |= "fin de la partida"`.

### Probar fallos (para la presentación)

```powershell
docker compose stop api-1        # el balanceador manda todo a api-2
docker compose stop postgres     # /api/health devuelve 503 → aparece como ERROR en Grafana
docker compose start api-1 postgres
```

### Apagar

```powershell
docker compose down        # conserva los datos
docker compose down -v     # borra también la base de datos y los logs
```

## Cómo jugar en local sin Docker (desarrollo)

Requisitos: **Java 21+** y **Maven**. Así no se guardan resultados (no hay API).

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
game-server/     Servidor de partidas (Spring Boot, WebSocket, game loop a 20 Hz)
api/             API REST de resultados y ranking (Node.js + Express + PostgreSQL)
frontend/        Cliente Phaser 3 (JavaScript, sin compilación) + ranking.html
db/              Esquema de PostgreSQL (init.sql)
nginx/           Configuración del balanceador
observability/   Loki, Alloy y Grafana (fuente de datos + dashboard)
scripts/         Demo del balanceo
docs/            Protocolo WebSocket documentado
docker-compose.yml
```

- Protocolo de mensajes: [docs/PROTOCOLO.md](docs/PROTOCOLO.md)
- Licencias de los recursos gráficos y de sonido: [frontend/CREDITOS.md](frontend/CREDITOS.md)
