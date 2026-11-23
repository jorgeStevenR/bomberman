// Logger mínimo: cada línea es un objeto JSON escrito en la salida estándar.
//
// ¿Por qué JSON y por stdout?
//  - Docker guarda stdout de cada contenedor; Promtail lo lee y lo envía a Loki.
//  - En Grafana basta con "| json" para filtrar por campos (level, status, path...).
//  - En AWS sería igual: ECS manda stdout a CloudWatch Logs con el driver awslogs.
const os = require('os');

const SERVICE = process.env.SERVICE_NAME || 'api';
const INSTANCE = process.env.INSTANCE_ID || os.hostname();

function write(level, message, fields = {}) {
  const line = {
    '@timestamp': new Date().toISOString(),
    level,
    service: SERVICE,
    instance: INSTANCE,
    message,
    ...fields,
  };
  const out = level === 'ERROR' ? process.stderr : process.stdout;
  out.write(JSON.stringify(line) + '\n');
}

module.exports = {
  INSTANCE,
  info: (msg, fields) => write('INFO', msg, fields),
  warn: (msg, fields) => write('WARN', msg, fields),
  error: (msg, fields) => write('ERROR', msg, fields),
};
