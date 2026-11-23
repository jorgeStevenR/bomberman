// API REST de Bomberman: guarda el resultado de cada partida en PostgreSQL
// y ofrece el ranking y el historial.
//
//   GET  /api/health       → estado de la API y de la conexión con la base de datos
//   GET  /api/leaderboard  → ranking de jugadores (victorias, puntos)
//   GET  /api/matches      → últimas partidas
//   GET  /api/stats        → totales (partidas, jugadores, por servidor y modo)
//   POST /api/matches      → guarda una partida (SOLO los game-servers, con token)
//
// Se ejecutan varias instancias (api-1, api-2) detrás de Nginx. Cada respuesta
// lleva la cabecera X-Served-By para ver qué instancia contestó.
const express = require('express');
const { pool, transaction } = require('./db');
const log = require('./logger');

const PORT = Number(process.env.PORT || 3000);
const INTERNAL_TOKEN = process.env.API_TOKEN || '';
const MODES = new Set(['CLASSIC', 'GUNS']);

const app = express();
app.disable('x-powered-by'); // no anunciamos la tecnología del servidor
app.use(express.json({ limit: '16kb' }));

// Cabecera con la instancia + un log por petición (lo que luego vemos en Grafana)
app.use((req, res, next) => {
  const start = process.hrtime.bigint();
  res.setHeader('X-Served-By', log.INSTANCE);
  res.on('finish', () => {
    const ms = Number(process.hrtime.bigint() - start) / 1e6;
    const level = res.statusCode >= 500 ? 'error' : res.statusCode >= 400 ? 'warn' : 'info';
    log[level]('Petición HTTP', {
      method: req.method,
      path: req.path,
      status: res.statusCode,
      duration_ms: Math.round(ms * 10) / 10,
    });
  });
  next();
});

/** Envuelve un handler async para que sus errores lleguen al manejador de errores. */
const wrap = (fn) => (req, res, next) => fn(req, res, next).catch(next);

app.get('/api/health', wrap(async (req, res) => {
  try {
    const { rows } = await pool.query('SELECT now() AS db_time');
    res.json({ status: 'UP', instance: log.INSTANCE, database: 'UP', dbTime: rows[0].db_time });
  } catch (err) {
    log.error('La base de datos no responde', { error: err.code || err.message });
    res.status(503).json({ status: 'DOWN', instance: log.INSTANCE, database: 'DOWN' });
  }
}));

app.get('/api/leaderboard', wrap(async (req, res) => {
  const limit = clampInt(req.query.limit, 1, 100, 20);
  const { rows } = await pool.query(
    `SELECT player_name                         AS name,
            COUNT(*)::int                       AS games,
            COUNT(*) FILTER (WHERE winner)::int AS wins,
            COALESCE(SUM(score), 0)::int        AS total_score,
            MAX(score)::int                     AS best_score
       FROM match_players
      GROUP BY player_name
      ORDER BY wins DESC, total_score DESC, name
      LIMIT $1`,
    [limit],
  );
  res.json(rows);
}));

app.get('/api/matches', wrap(async (req, res) => {
  const limit = clampInt(req.query.limit, 1, 100, 20);
  const { rows } = await pool.query(
    `SELECT m.id, m.room, m.server_id, m.mode, m.duration_seconds, m.winner_name, m.created_at,
            COALESCE(json_agg(json_build_object(
              'name', p.player_name, 'score', p.score, 'lives', p.lives,
              'alive', p.alive, 'winner', p.winner) ORDER BY p.score DESC)
              FILTER (WHERE p.id IS NOT NULL), '[]') AS players
       FROM matches m
       LEFT JOIN match_players p ON p.match_id = m.id
      GROUP BY m.id
      ORDER BY m.created_at DESC
      LIMIT $1`,
    [limit],
  );
  res.json(rows);
}));

app.get('/api/stats', wrap(async (req, res) => {
  const [totals, byServer, byMode] = await Promise.all([
    pool.query(`SELECT (SELECT COUNT(*) FROM matches)::int AS matches,
                       (SELECT COUNT(DISTINCT player_name) FROM match_players)::int AS players`),
    pool.query(`SELECT server_id, COUNT(*)::int AS matches FROM matches GROUP BY server_id ORDER BY server_id`),
    pool.query(`SELECT mode, COUNT(*)::int AS matches FROM matches GROUP BY mode ORDER BY mode`),
  ]);
  res.json({ ...totals.rows[0], byServer: byServer.rows, byMode: byMode.rows });
}));

// Solo los game-servers pueden guardar partidas: comparten un token secreto.
// Además, Nginx bloquea POST /api/matches desde fuera (defensa en profundidad).
app.post('/api/matches', wrap(async (req, res) => {
  if (!INTERNAL_TOKEN || req.get('X-Internal-Token') !== INTERNAL_TOKEN) {
    log.warn('Intento de guardar partida sin token válido', { ip: req.ip });
    return res.status(401).json({ error: 'No autorizado' });
  }
  const error = validateMatch(req.body);
  if (error) {
    log.warn('Partida rechazada por datos inválidos', { reason: error });
    return res.status(400).json({ error });
  }

  const m = req.body;
  const id = await transaction(async (client) => {
    const { rows } = await client.query(
      `INSERT INTO matches (room, server_id, mode, duration_seconds, winner_name)
       VALUES ($1, $2, $3, $4, $5) RETURNING id`,
      [m.room, m.serverId, m.mode, m.durationSeconds, m.winnerName || null],
    );
    const matchId = rows[0].id;
    for (const p of m.players) {
      await client.query(
        `INSERT INTO match_players (match_id, player_name, score, lives, alive, winner)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [matchId, p.name, p.score, p.lives, p.alive, p.winner],
      );
    }
    return matchId;
  });

  log.info('Partida guardada', {
    match_id: id, room: m.room, game_server: m.serverId, mode: m.mode,
    winner: m.winnerName || null, players: m.players.length,
  });
  res.status(201).json({ id });
}));

app.use((req, res) => res.status(404).json({ error: 'No encontrado' }));

// Manejador de errores: nunca devolvemos detalles internos al cliente.
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  if (err.type === 'entity.parse.failed' || err.type === 'entity.too.large') {
    return res.status(400).json({ error: 'Cuerpo de la petición inválido' });
  }
  log.error('Error no controlado', { error: err.message, path: req.path });
  res.status(500).json({ error: 'Error interno' });
});

function clampInt(value, min, max, fallback) {
  const n = Number.parseInt(value, 10);
  return Number.isNaN(n) ? fallback : Math.min(max, Math.max(min, n));
}

function validateMatch(m) {
  if (!m || typeof m !== 'object') return 'Falta el cuerpo';
  if (typeof m.room !== 'string' || !/^[A-Z0-9]{4,8}$/.test(m.room)) return 'room inválido';
  if (typeof m.serverId !== 'string' || m.serverId.length > 64) return 'serverId inválido';
  if (!MODES.has(m.mode)) return 'mode inválido';
  if (!Number.isInteger(m.durationSeconds) || m.durationSeconds < 0) return 'durationSeconds inválido';
  if (!Array.isArray(m.players) || m.players.length < 1 || m.players.length > 4) return 'players inválido';
  for (const p of m.players) {
    if (typeof p.name !== 'string' || p.name.length < 1 || p.name.length > 16) return 'nombre de jugador inválido';
    if (!Number.isInteger(p.score) || !Number.isInteger(p.lives)) return 'puntuación inválida';
    if (typeof p.alive !== 'boolean' || typeof p.winner !== 'boolean') return 'alive/winner inválido';
  }
  return null;
}

const server = app.listen(PORT, () => log.info(`API escuchando en el puerto ${PORT}`));

// Docker manda SIGTERM al parar el contenedor: cerramos ordenadamente.
process.on('SIGTERM', () => {
  log.info('Recibido SIGTERM, cerrando la API');
  server.close(() => pool.end().then(() => process.exit(0)));
});
