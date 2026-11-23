-- Esquema de la base de datos. PostgreSQL lo ejecuta automáticamente la PRIMERA vez
-- que arranca con un volumen vacío (carpeta /docker-entrypoint-initdb.d).

CREATE TABLE IF NOT EXISTS matches (
    id               BIGSERIAL PRIMARY KEY,
    room             VARCHAR(8)  NOT NULL,
    server_id        VARCHAR(64) NOT NULL,  -- qué réplica del game-server jugó la partida
    mode             VARCHAR(16) NOT NULL CHECK (mode IN ('CLASSIC', 'GUNS')),
    duration_seconds INTEGER     NOT NULL CHECK (duration_seconds >= 0),
    winner_name      VARCHAR(16),           -- NULL si nadie ganó (empate)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS match_players (
    id          BIGSERIAL PRIMARY KEY,
    match_id    BIGINT      NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    player_name VARCHAR(16) NOT NULL,
    score       INTEGER     NOT NULL,
    lives       INTEGER     NOT NULL,
    alive       BOOLEAN     NOT NULL,
    winner      BOOLEAN     NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_matches_created_at ON matches (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_match_players_name ON match_players (player_name);
CREATE INDEX IF NOT EXISTS idx_match_players_match ON match_players (match_id);
