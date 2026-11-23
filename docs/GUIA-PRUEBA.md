# Guía para descargar y probar el proyecto

Esta guía es para levantar en tu PC **toda la simulación** del proyecto final: el juego, la API, la base de datos, el balanceador, Loki y Grafana. Todo corre en Docker, así que **no necesitas instalar Java, Node ni PostgreSQL**.

> ⚠️ **Eres el primero en levantarlo completo.** El código se escribió en un PC sin Docker, así que nunca se ha ejecutado entero. Si algo falla, no pasa nada: anota el error (ver [Qué reportar](#7-qué-reportar)) y lo arreglamos.

Tiempo estimado: **15–25 minutos** la primera vez (casi todo es descarga). Después arranca en menos de 1 minuto.

---

## 1. Lo que necesitas instalar

| Programa | Para qué | Descarga |
|---|---|---|
| **Docker Desktop** | Ejecuta los 11 contenedores | https://www.docker.com/products/docker-desktop/ |
| **Git** | Descargar el código | https://git-scm.com/downloads |

**En Windows**, al instalar Docker Desktop deja marcada la opción **"Use WSL 2"**. Si te pide reiniciar, reinicia.

Después de instalar, **abre Docker Desktop** y espera a que abajo a la izquierda diga **"Engine running"** (la ballena en verde). Si Docker Desktop no está abierto, nada de lo que sigue funciona.

Comprueba que todo está bien abriendo **PowerShell** y escribiendo:

```powershell
docker --version
docker compose version
git --version
```

Los tres tienen que mostrar un número de versión.

**Recursos recomendados:** al menos **8 GB de RAM** en el PC y unos **5 GB libres** en disco.

---

## 2. Descargar el código

En PowerShell, ve a la carpeta donde quieras guardarlo (por ejemplo, Descargas) y clona el repositorio **en la rama `feat/docker-observabilidad`**, que es donde está la parte de Docker:

```powershell
cd $HOME\Downloads
git clone -b feat/docker-observabilidad https://github.com/jorgeStevenR/bomberman.git
cd bomberman
```

> Si más adelante esta rama se une a `main`, basta con `git clone https://github.com/jorgeStevenR/bomberman.git`.

---

## 3. Configurar las contraseñas (opcional)

El proyecto funciona con contraseñas de prueba por defecto. Si quieres usar las tuyas:

```powershell
copy .env.example .env
notepad .env
```

Cambia los valores y guarda. El archivo `.env` **nunca se sube a GitHub** (está en `.gitignore`).

---

## 4. Levantar todo

Desde la carpeta `bomberman`:

```powershell
docker compose up -d --build
```

La primera vez tarda varios minutos: descarga las imágenes (Nginx, PostgreSQL, Grafana…) y compila el servidor del juego en Java dentro de Docker.

Cuando termine, comprueba el estado:

```powershell
docker compose ps
```

Deberías ver **11 contenedores**. Espera hasta que los que tienen chequeo de salud digan **`(healthy)`**. Los servidores del juego tardan unos 30–40 segundos en arrancar.

| Contenedor | Qué es |
|---|---|
| `nginx` | Balanceador de carga y página del juego |
| `game-1`, `game-2`, `game-3` | Tres copias del servidor del juego (Java) |
| `api-1`, `api-2` | Dos copias de la API (Node.js) |
| `postgres` | Base de datos |
| `loki` | Guarda los logs de todos los contenedores |
| `alloy` | Recoge los logs y los envía a Loki |
| `grafana` | Muestra los logs en un dashboard |

---

## 5. Probar cada parte

### 5.1 El juego

1. Abre **http://localhost:8080**
2. Escribe tu nombre y crea una sala. Te dará un código, por ejemplo `ABCD`.
3. Abre **otra pestaña** (o pídele a alguien en tu misma red WiFi que entre a `http://IP-DE-TU-PC:8080`) y únete con ese código.
4. En la sala de espera, abajo, aparece **`Servidor: game-N`**: es la copia del servidor que atiende esa sala.
5. Empieza la partida y juega hasta que termine.

Controles: flechas o WASD para moverse, Espacio para bomba o disparo, E para el pico.

### 5.2 La base de datos

Cuando termine una partida, abre **http://localhost:8080/ranking.html**. Tiene que aparecer la partida, con el servidor que la jugó y los puntos de cada jugador.

También puedes ver los datos de la API directamente:

- http://localhost:8080/api/health → debe decir `"database":"UP"`
- http://localhost:8080/api/leaderboard
- http://localhost:8080/api/matches
- http://localhost:8080/api/stats

### 5.3 El balanceo de carga

Ejecuta el script de demostración:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo-balanceo.ps1
```

Lo que debes ver:

- **API:** las 10 peticiones se reparten entre `api-1` y `api-2`. Con poca carga, *least_conn* puede mandar varias seguidas a la misma; eso es normal.
- **Juego:** las 6 salas se reparten entre `game-1`, `game-2` y `game-3`, y las salas repetidas (`ALFA`, `BETA`) **siempre caen en la misma copia**.

### 5.4 Grafana

1. Abre **http://localhost:3001**
2. Usuario: **`admin`**. Contraseña: **`admin_bomberman`** (o la que pusiste en `.env`).
3. Se abre directamente el dashboard **"Bomberman — Logs y balanceo"**. Si no, ve a *Dashboards → Bomberman*.
4. Juega una partida o ejecuta el script de demostración, y en unos segundos se llenan los paneles: partidas, errores, reparto entre copias, códigos HTTP, latencia y logs.

Para buscar logs a mano, ve a **Explore**, elige **Loki** y prueba:

```
{level="ERROR"}
{service="api"} | json | status >= 400
{service="game-server"} |= "fin de la partida"
```

### 5.5 Simular fallos (para la presentación)

```powershell
docker compose stop api-1
```
La API sigue funcionando: Nginx manda todo a `api-2`. Compruébalo con el script o con http://localhost:8080/api/health.

```powershell
docker compose stop postgres
```
`/api/health` devuelve error 503 y en Grafana aparecen **errores** en rojo.

Para volver a la normalidad:

```powershell
docker compose start api-1 postgres
```

---

## 6. Problemas frecuentes

| Problema | Solución |
|---|---|
| `error during connect` o `cannot find the file specified` | Docker Desktop no está abierto. Ábrelo y espera a "Engine running". |
| `docker : The term 'docker' is not recognized` justo después de instalar Docker | La terminal se abrió antes de instalarlo. Cierra VS Code (o PowerShell) por completo y vuélvelo a abrir. |
| `request returned 500 Internal Server Error ... dockerDesktopLinuxEngine` | El motor de Docker se cayó (pasa a veces en el primer arranque). En Docker Desktop: icono del bicho (Troubleshoot) → **Restart**, o ciérralo y ábrelo. Espera "Engine running" y repite. |
| `Conflict. The container name "/bomberman-...-1" is already in use` | Se lanzaron dos `docker compose up` a la vez. Ejecuta `docker compose up -d` otra vez: reutiliza lo ya creado. |
| `port is already allocated` (8080 o 3001) | Otro programa usa ese puerto. Ciérralo, o cambia el número de la izquierda en `docker-compose.yml` (por ejemplo `"8090:80"`) y usa ese puerto en el navegador. |
| `Pool overlaps with other one on this address space` | Otra red de Docker usa `172.30.0.0/24`. Ejecuta `docker network prune` y vuelve a intentarlo. |
| `nginx` se reinicia en bucle | Mira el motivo con `docker compose logs nginx`. Suele ser que algún `game-N` o `api-N` no arrancó: revisa sus logs. |
| Un contenedor sale como `unhealthy` | `docker compose logs NOMBRE` (por ejemplo `docker compose logs game-1`) y busca la palabra `ERROR`. |
| Grafana no muestra datos | Pon el rango de tiempo arriba a la derecha en **"Last 15 minutes"** y genera tráfico (juega o ejecuta el script). Revisa `docker compose logs alloy`. |
| El ranking dice "No se pudo contactar con la API" | Revisa `docker compose ps`: `api-1`, `api-2` y `postgres` deben estar `healthy`. |
| Quiero empezar desde cero | `docker compose down -v` y luego `docker compose up -d --build`. **Borra** las partidas y los logs guardados. |

Comandos útiles:

```powershell
docker compose ps                     # estado de los contenedores
docker compose logs -f game-1         # logs en vivo de un contenedor (Ctrl+C para salir)
docker compose logs --tail 50 api-1   # últimas 50 líneas
docker compose restart nginx          # reiniciar un contenedor
```

---

## 7. Qué reportar

Como es la primera vez que se levanta completo, cuéntale a Jorge cómo te fue con esta lista:

- [ ] `docker compose up -d --build` terminó sin errores
- [ ] Los 11 contenedores aparecen en `docker compose ps` y los que tienen chequeo de salud están `(healthy)`
- [ ] El juego abre en http://localhost:8080 y se puede jugar con 2 pestañas
- [ ] La sala de espera muestra `Servidor: game-N`
- [ ] Al terminar una partida, aparece en http://localhost:8080/ranking.html
- [ ] El script `demo-balanceo.ps1` muestra el reparto entre copias
- [ ] Grafana abre con el dashboard y los paneles muestran datos
- [ ] Al parar `api-1`, la API sigue respondiendo

Si algo falla, envía:

1. El **paso** donde falló.
2. El **mensaje de error completo** (copiado como texto, no foto si es posible).
3. La salida de estos dos comandos:

```powershell
docker compose ps
docker compose logs --tail 80 NOMBRE-DEL-CONTENEDOR-QUE-FALLA
```

---

## 8. Apagar

```powershell
docker compose down        # apaga todo y conserva los datos
docker compose down -v     # apaga todo y borra la base de datos y los logs
```

Docker Desktop puede seguir abierto en segundo plano consumiendo memoria; ciérralo desde el icono de la bandeja del sistema si ya no lo usas.
