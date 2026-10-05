// Constantes compartidas por todo el frontend.

/** Los sprites se dibujan a 16x16 píxeles y se muestran 3 veces más grandes (estilo retro). */
export const PIXEL = 16;
export const SCALE = 3;
export const TILE = PIXEL * SCALE; // 48 px por casilla en pantalla

export const MAP_W = 15;
export const MAP_H = 13;
export const HUD_HEIGHT = 80;

export const GAME_WIDTH = MAP_W * TILE;               // 720
export const GAME_HEIGHT = MAP_H * TILE + HUD_HEIGHT; // 704

/** Un color por jugador (por orden de llegada a la sala). */
export const PLAYER_COLORS = [
  { main: '#3b82f6', dark: '#1d4ed8', hex: 0x3b82f6, name: 'Azul' },
  { main: '#ef4444', dark: '#b91c1c', hex: 0xef4444, name: 'Rojo' },
  { main: '#22c55e', dark: '#15803d', hex: 0x22c55e, name: 'Verde' },
  { main: '#eab308', dark: '#a16207', hex: 0xeab308, name: 'Amarillo' },
];

/** Profundidad de dibujo: lo de número mayor se pinta encima. */
export const DEPTH = { FLOOR: 0, SOLID: 10, POWERUP: 20, BOMB: 30, FLAME: 40, ENTITY: 50, FX: 1000 };

/**
 * URL del WebSocket. Usamos el MISMO host que sirvió la página:
 *  - en desarrollo: http://localhost:8081  → ws://localhost:8081/ws/game
 *  - con Nginx:     https://192.168.1.20   → wss://192.168.1.20/ws/game
 * Así no hay que cambiar nada al pasar a Docker ni para jugar en la red local.
 */
export function wsUrl(room) {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${location.host}/ws/game?room=${encodeURIComponent(room)}`;
}

/** Convierte una casilla (x, y) en el centro de esa casilla en píxeles de pantalla. */
export function tileToWorld(x, y) {
  return { x: x * TILE + TILE / 2, y: HUD_HEIGHT + y * TILE + TILE / 2 };
}

export const FONT = '"Trebuchet MS", Verdana, sans-serif';

/** Emotes de las teclas 1, 2, 3 y 4. */
export const EMOTES = ['😂', '😡', '👋', '💀'];

/** Texto que ve el jugador al recibir cada maldición de la calavera. */
export const CURSE_TEXT = {
  REVERSED: '¡Controles invertidos!',
  SLOW: '¡Lentitud!',
  DIARRHEA: '¡Diarrea de bombas!',
  NO_BOMBS: '¡Sin bombas!',
};

/** En el modo pistolas, dos maldiciones se llaman distinto. */
export const CURSE_TEXT_GUNS = {
  DIARRHEA: '¡Gatillo atascado!',
  NO_BOMBS: '¡Sin munición!',
};

export const WEAPON_NAMES = { PISTOL: 'PISTOLA', SMG: 'SUBFUSIL', SHOTGUN: 'ESCOPETA', RIFLE: 'RIFLE' };

export const MODE_NAMES = { CLASSIC: '💣 Clásico', GUNS: '🔫 Pistolas y pico' };
