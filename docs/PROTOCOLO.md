# Protocolo WebSocket del game-server

**Conexión:** `ws://HOST:8081/ws/game?room=CODIGO`. Con Nginx (fase 5) será `wss://HOST/ws/game?room=CODIGO`.

- `CODIGO`: de 4 a 8 letras mayúsculas o números. Si no es válido, el servidor responde `400` y no abre la conexión.
- El código va **en la URL** para que Nginx pueda enviar a todos los jugadores de la misma sala al mismo servidor (`hash $arg_room consistent`).
- Todos los mensajes son **JSON de texto** y todos tienen un campo `type`.
- El servidor es **autoritativo**: el cliente solo envía *intenciones*. Nunca envía posiciones, vidas ni puntos.

## Flujo típico

```
Cliente                                  Servidor
  │── abre ws://.../ws/game?room=ABCD ──▶│
  │── JOIN {name}                      ──▶│
  │◀── WELCOME {playerId, reconnectToken}─│
  │◀── LOBBY {players, hostId, WAITING} ──│  (cada vez que alguien entra o sale)
  │── START (solo el anfitrión)        ──▶│
  │◀── LOBBY {status: RUNNING}          ──│
  │◀── STATE  (20 veces por segundo)    ──│
  │── MOVE / STOP / PLACE_BOMB         ──▶│  (cuando el jugador pulsa teclas)
  │◀── STATE ...                        ──│
  │◀── GAME_OVER {winnerId, results}    ──│
  │◀── LOBBY {status: FINISHED}         ──│  (el anfitrión puede pulsar START otra vez)
```

## Cliente → Servidor

| type | Campos | Cuándo | Ejemplo |
|---|---|---|---|
| `JOIN` | `name`, `reconnectToken` (opcional) | Nada más abrir la conexión | `{"type":"JOIN","name":"Ana"}` |
| `START` | – | El anfitrión empieza la partida (o "volver a jugar") | `{"type":"START"}` |
| `MOVE` | `dir`: `UP` `DOWN` `LEFT` `RIGHT` | Al pulsar una tecla de dirección | `{"type":"MOVE","dir":"LEFT"}` |
| `STOP` | – | Al soltar todas las teclas de dirección | `{"type":"STOP"}` |
| `PLACE_BOMB` | – | Al pulsar espacio. A pie pone una bomba; en el tanque, **dispara** | `{"type":"PLACE_BOMB"}` |
| `EMOTE` | `emote`: 0 a 3 | Teclas 1–4 (😂 😡 👋 💀). Máximo 1 por segundo | `{"type":"EMOTE","emote":2}` |
| `SET_MODE` | `mode`: `CLASSIC` o `GUNS` | El anfitrión elige el modo (en la sala de espera o al terminar) | `{"type":"SET_MODE","mode":"GUNS"}` |
| `FIRE` | `on`: `true`/`false` | Modo pistolas: mantener o soltar el gatillo. El servidor dispara según la cadencia del arma | `{"type":"FIRE","on":true}` |
| `MINE` | – | Modo pistolas: golpe de pico a la casilla de enfrente (3 golpes rompen un bloque) | `{"type":"MINE"}` |
| `CHEAT` | `powerUp` | Truco de pruebas (tecla T). Solo funciona con `game.cheats-enabled=true` | `{"type":"CHEAT","powerUp":"TANK"}` |
| `PING` | `t` (hora del cliente en ms) | Cada 5 s, para medir la latencia | `{"type":"PING","t":1759697653000}` |

**Por qué `MOVE` + `STOP` y no "un mensaje por paso":** el cliente avisa cuando *empieza* y cuando *deja de* moverse. El servidor mueve al jugador a su ritmo (según la velocidad) mientras la intención esté activa. Así se envían poquísimos mensajes, y aunque alguien mande 100 `MOVE` por segundo no irá más rápido.

## Servidor → Cliente

### `WELCOME`: respuesta a JOIN
```json
{"type":"WELCOME","playerId":"p-52dc88c3","reconnectToken":"010cf5a4-695d-4fcf-ac2d-9743bb586749",
 "room":"DEMO","server":"local","tickRate":20}
```
- `reconnectToken`: el cliente lo guarda en `sessionStorage`. Si se corta la conexión, al volver envía `JOIN` con ese token y recupera **el mismo jugador**.
- `server`: qué réplica del game-server atiende la sala. Sirve para demostrar el balanceo en la fase 5.

### `LOBBY`: estado de la sala de espera
```json
{"type":"LOBBY","room":"DEMO","hostId":"p-52dc88c3","status":"WAITING","maxPlayers":4,
 "players":[{"id":"p-52dc88c3","name":"Ana","connected":true,"host":true}]}
```
`status`: `WAITING` (esperando), `RUNNING` (jugando) o `FINISHED` (terminada, se puede volver a jugar).

### `STATE`: foto completa del mundo (20 por segundo)
Este ejemplo es real, capturado del servidor:
```json
{"type":"STATE","tick":3,"status":"RUNNING","timeLeft":179,
 "map":["###############","#......BBB....#","#.#.#.#B#B#.#.#", "... (13 filas)"],
 "players":[{"id":"p-52dc88c3","name":"Ana","x":1,"y":1,"facing":"DOWN","moving":false,
             "lives":3,"alive":true,"bombRange":2,"maxBombs":1,"speedLevel":0,"moveDelayTicks":6,
             "score":0,"invulnerable":false,"connected":true}],
 "bombs":[{"id":1,"x":1,"y":1,"range":2,"ticksLeft":59}],
 "explosions":[],
 "enemies":[{"id":1,"x":5,"y":8,"dir":"UP","moveDelayTicks":10},{"id":2,"x":5,"y":6,"dir":"DOWN","moveDelayTicks":10}],
 "powerUps":[],
 "events":[{"type":"BOMB_PLACED","position":{"x":1,"y":1},"playerId":"p-52dc88c3"}]}
```
- `map`: una cadena por fila. `#` es muro, `B` bloque y `.` suelo. **Solo se envía cuando cambia** (y una vez por segundo como copia de seguridad); en los demás STATE vale `null` y el cliente usa el último que recibió.
- `suddenDeath`: `true` en los últimos 30 s, cuando caen muros en espiral.
- `players[]` también trae:
  - `canKick` (puede patear bombas);
  - `curse` (maldición activa: `REVERSED`, `SLOW`, `DIARRHEA`, `NO_BOMBS` o `null`);
  - `tankArmor` (golpes que aguanta el tanque; 0 = a pie);
  - `tankSeconds` (segundos de tanque que le quedan).
- `bombs[]` también trae `sliding` (si la han pateado) y `moveDelayTicks` (velocidad al deslizarse).
- `shells[]`: proyectiles, `{"id":1,"x":4,"y":1,"dir":"RIGHT","kind":"BULLET"}`. `kind` es `BULLET` (arma) o `TANK` (obús que rompe bloques).
- `mode`: `CLASSIC` o `GUNS`. En el modo pistolas, además:
  - `players[]` trae `hp`, `maxHp`, `weaponLevel` y `weapon` (`PISTOL`, `SMG`, `SHOTGUN` o `RIFLE`);
  - `enemies[]` trae `hp` y `maxHp`;
  - `cracks[]` lista los bloques golpeados con el pico: `{"x":3,"y":5,"hits":2}`.
- `explosions[]`: `{"center":{"x":3,"y":3},"cells":[{"x":3,"y":3},{"x":3,"y":2},...],"ticksLeft":9}`
- `powerUps[].type`: `BOMB_RANGE`, `EXTRA_BOMB`, `SPEED`, `KICK`, `SKULL` o `TANK`.
- `events[]`: lo que pasó en este tick, para sonidos y animaciones:
  - partida: `GAME_STARTED`, `SUDDEN_DEATH`, `WALL_DROPPED`, `GAME_OVER`;
  - bombas: `BOMB_PLACED`, `BOMB_KICKED`, `EXPLOSION`, `BLOCK_DESTROYED`;
  - power-ups: `POWERUP_SPAWNED`, `POWERUP_TAKEN`, `CURSED`;
  - tanque: `TANK_ON`, `TANK_HIT`, `TANK_DESTROYED`, `SHOT_FIRED`;
  - daño: `PLAYER_HIT`, `PLAYER_ELIMINATED`, `ENEMY_KILLED`.

### `EMOTE`
```json
{"type":"EMOTE","playerId":"p-52dc88c3","emote":0}
```
Se reenvía a toda la sala; el cliente muestra un globito sobre el jugador.

**Por qué se envía el estado completo y no solo los cambios:** son unos 1–2 KB por mensaje, unos 40 KB/s por jugador, que no es nada en una red local. A cambio el cliente es muy simple, y si se pierde un mensaje o alguien se reconecta, el siguiente `STATE` lo arregla todo.

### `GAME_OVER`
```json
{"type":"GAME_OVER","winnerId":"p-52dc88c3","winnerName":"Ana",
 "results":[{"id":"p-52dc88c3","name":"Ana","score":320,"lives":2,"alive":true},
            {"id":"p-9a01bc22","name":"Beto","score":40,"lives":0,"alive":false}]}
```
Si hay empate o pierden todos, `winnerId` es `null`.

### `ERROR`
```json
{"type":"ERROR","code":"ROOM_FULL","message":"La sala está llena (máximo 4)"}
```

| code | Significado | ¿Cierra la conexión? |
|---|---|---|
| `ROOM_FULL` | Ya hay 4 jugadores | Sí |
| `GAME_IN_PROGRESS` | La partida ya empezó y no tienes token de reconexión | Sí |
| `NOT_HOST` | Solo el anfitrión puede empezar | No |
| `BAD_MESSAGE` | JSON inválido, tipo desconocido, dirección incorrecta o mensaje > 512 caracteres | No |

### `PONG`
```json
{"type":"PONG","t":1759697653000,"serverTime":1759697653012}
```
Latencia ≈ `Date.now() - t`.

## Desconexión y reconexión

- **En la sala de espera:** si te desconectas, sales de la sala. Si el anfitrión se va, el siguiente jugador pasa a ser el anfitrión.
- **Durante la partida:** tu personaje se queda quieto y los demás te ven como `connected:false`. Tienes **15 s** para volver con tu `reconnectToken`. Si no vuelves, quedas eliminado.
- Si abres el juego en otra pestaña con el mismo token, la pestaña vieja se cierra. Solo puede haber una conexión por jugador.

## Protecciones

| Protección | Valor | Dónde |
|---|---|---|
| Mensajes por segundo y conexión | 30 (los demás se descartan; más de 300 descartados → desconexión) | `RateLimiter` |
| Tamaño máximo de un mensaje | 512 caracteres (8 KB → Tomcat cierra la conexión) | `GameWebSocketHandler`, `WebSocketConfig` |
| Código de sala | `^[A-Z0-9]{4,8}$` | `RoomHandshakeInterceptor` |
| Nombre | Letras, números, espacio, `_` y `-`; máximo 16 | `GameRoom.sanitizeName` |
| Movimientos ilegales | Ignorados (muros, bloques, bombas, velocidad máxima) | `Game` |
| Cliente lento | Se descartan los `STATE` viejos acumulados (> 256 KB) | `ConcurrentWebSocketSessionDecorator` |
