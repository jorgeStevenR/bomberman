// Conexión a PostgreSQL con un "pool": varias conexiones abiertas que se reutilizan.
// Abrir una conexión nueva por petición es lento; el pool es lo que haría
// también RDS Proxy delante de Aurora/RDS en AWS.
const { Pool } = require('pg');
const log = require('./logger');

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT || 5432),
  database: process.env.DB_NAME || 'bomberman',
  user: process.env.DB_USER || 'bomberman',
  password: process.env.DB_PASSWORD,
  max: 10,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 3_000,
});

pool.on('error', (err) => log.error('Error inesperado en el pool de PostgreSQL', { error: err.message }));

/** Ejecuta una función dentro de una transacción (todo o nada). */
async function transaction(fn) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

module.exports = { pool, transaction };
