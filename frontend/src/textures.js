import { PLAYER_COLORS, PIXEL } from './config.js';

/**
 * SPRITES PROPIOS generados por código (pixel art 16x16).
 *
 * Cada sprite es una lista de 16 textos de 16 caracteres. Cada carácter es un
 * píxel y la letra indica el color según una "paleta" ('.' = transparente).
 * Así no dependemos de imágenes con derechos de autor y cualquiera puede
 * modificar un dibujo editando letras.
 *
 * Al arrancar, BootScene llama a createTextures() y se dibuja todo en
 * texturas de Phaser (CanvasTexture). Después se usan como imágenes normales.
 */

// ---------------------------------------------------------------- jugador ----
// C = color del jugador, c = color oscuro, S = piel, W = blanco, Y = cinturón, B = botas, K = contorno
const PLAYER_FRONT = [
  '................',
  '.....KKKKKK.....',
  '....KCCCCCCK....',
  '...KCWCCCCCcK...',
  '...KCCCCCCCcK...',
  '...KKSSSSSSKK...',
  '...KSSKSSKSSK...',
  '...KSSKSSKSSK...',
  '....KSSSSSSK....',
  '...KKCCCCCCKK...',
  '..KWKCCYYCCKWK..',
  '..KWKCCCCCCKWK..',
  '...KKCCCCCCKK...',
  '....KCCKKCCK....',
  '....KBBK.KBBK...',
  '....KKKK.KKKK...',
];

const PLAYER_BACK = [
  '................',
  '.....KKKKKK.....',
  '....KCCCCCCK....',
  '...KCCCCCCCcK...',
  '...KCCCCCCCcK...',
  '...KCCCCCCCcK...',
  '...KcCCCCCCcK...',
  '...KKccccccKK...',
  '....KSSSSSSK....',
  '...KKCCCCCCKK...',
  '..KWKCCCCCCKWK..',
  '..KWKCCccCCKWK..',
  '...KKCCCCCCKK...',
  '....KCCKKCCK....',
  '....KBBK.KBBK...',
  '....KKKK.KKKK...',
];

const PLAYER_SIDE = [ // mirando a la derecha (para la izquierda se voltea)
  '................',
  '......KKKKK.....',
  '.....KCCCCCK....',
  '....KCCCCWCCK...',
  '....KCCCCCCCK...',
  '....KcCSSSSSK...',
  '....KcSSSKSSK...',
  '....KcSSSKSSK...',
  '.....KSSSSSK....',
  '.....KCCCCK.....',
  '....KCCWWCK.....',
  '....KCCWWCK.....',
  '.....KCCCCK.....',
  '.....KCCCCK.....',
  '.....KBBBBK.....',
  '.....KKKKKKK....',
];

// Piernas para los fotogramas de caminar (sustituyen las 3 últimas filas)
const LEGS_FRONT_A = ['....KCCKKCCK....', '...KBBK..KCCK...', '...KKK...KBBK...'];
const LEGS_FRONT_B = ['....KCCKKCCK....', '....KCCK..KBBK..', '....KBBK...KKK..'];
const LEGS_SIDE_A = ['....KCCKCCK.....', '...KBBK.KBBK....', '...KKK...KKK....'];

function withLegs(rows, legs) {
  return [...rows.slice(0, 13), ...legs];
}

// --------------------------------------------------------------- enemigo -----
const ENEMY_A = [
  '................',
  '......KKKK......',
  '....KKPPPPKK....',
  '...KPPWPPPPPK...',
  '..KPPWPPPPPPpK..',
  '..KPWWKPPWWKpK..',
  '..KPWWKPPWWKpK..',
  '..KPPPPPPPPPpK..',
  '..KPPPKKKKPPpK..',
  '..KPPPPPPPPPpK..',
  '..KPPPPPPPPPpK..',
  '..KpPPPPPPPPpK..',
  '..KppppppppppK..',
  '..KpKppKKppKpK..',
  '..KK.KK..KK.KK..',
  '................',
];
const ENEMY_B = [...ENEMY_A.slice(0, 13), '..KKppKKppKKpK..', '...KK..KK..KK...', '................'];

// ----------------------------------------------------------------- bomba -----
const BOMB_A = [
  '..........Y.....',
  '.........YRY....',
  '..........F.....',
  '.........F......',
  '......KKKFK.....',
  '....KKDDDDDKK...',
  '...KDDDDDDDDDK..',
  '..KDGGDDDDDDDDK.',
  '..KDGDDDDDDDDDK.',
  '..KDDDDDDDDDDDK.',
  '..KDDDDDDDDDDDK.',
  '..KDDDDDDDDDDDK.',
  '...KDDDDDDDDDK..',
  '....KKDDDDDKK...',
  '......KKKKK.....',
  '................',
];
const BOMB_B = ['................', '..........Y.....', ...BOMB_A.slice(2)];

// ---------------------------------------------------- iconos de power-up -----
const ICON_FIRE = [ // +alcance
  '....R.....',
  '...RR.....',
  '...ROR..R.',
  '..ROOR.RR.',
  '..ROYORRR.',
  '.ROOYYOOR.',
  '.ROYYYYOR.',
  '.ROYYYYOR.',
  '..ROYYOR..',
  '...RRRR...',
];
const ICON_BOMB = [ // +1 bomba
  '......Y...',
  '.....F....',
  '...KKFK...',
  '..KDDDDK..',
  '.KDGDDDDK.',
  '.KDDDDDDK.',
  '.KDDDDDDK.',
  '..KDDDDK..',
  '...KKKK...',
  '..........',
];
const ICON_SPEED = [ // +velocidad (rayo)
  '.....YYY..',
  '....YYY...',
  '...YYY....',
  '..YYYYYY..',
  '.....YYY..',
  '....YYY...',
  '...YYY....',
  '..YY......',
  '.YY.......',
  '..........',
];

const ICON_KICK = [ // patear bombas (bota)
  '...KKK....',
  '...KBK....',
  '...KBK....',
  '...KBK....',
  '...KBBK...',
  '..KBBBBKK.',
  '.KBBBBBBBK',
  '.KWWWWWWWK',
  '..KKKKKKK.',
  '..........',
];
const ICON_SKULL = [ // calavera (maldición)
  '..KKKKKK..',
  '.KWWWWWWK.',
  'KWWWWWWWWK',
  'KWKKWWKKWK',
  'KWKKWWKKWK',
  'KWWWKKWWWK',
  '.KWWWWWWK.',
  '..KWKWKWK.',
  '..KKKKKKK.',
  '..........',
];
const ICON_TANK = [ // mini tanque
  '..........',
  '...KKKK...',
  '..KGGGGKKK',
  '..KGGGGGGG',
  '..KGGGGKKK',
  'KKKKKKKKK.',
  'KDGDGDGDK.',
  'KGDGDGDGK.',
  'KKKKKKKKK.',
  '..........',
];

// ----------------------------------------------------------------- tanque ----
// Visto desde arriba, mirando a la DERECHA (se rota para las otras direcciones).
// C/c = color del jugador, T/t = orugas, G = cañón, S = cabeza del piloto.
const TANK_A = [
  '................',
  '.KKKKKKKKKKKK...',
  '.KTtTtTtTtTtK...',
  '.KKKKKKKKKKKK...',
  '..KCCCCCCCCK....',
  '..KCCKKKKCCK....',
  '..KCKccccKCKKKKK',
  '..KCKcSScKKGGGGK',
  '..KCKcSScKKGGGGK',
  '..KCKccccKCKKKKK',
  '..KCCKKKKCCK....',
  '..KCCCCCCCCK....',
  '.KKKKKKKKKKKK...',
  '.KtTtTtTtTtTK...',
  '.KKKKKKKKKKKK...',
  '................',
];
// Segundo fotograma: las orugas "giran" (se intercambian los tonos)
const TANK_B = TANK_A.map((row, y) => (y === 2 || y === 13)
  ? row.replace(/[Tt]/g, (ch) => (ch === 'T' ? 't' : 'T')) : row);

const SHELL = [ // bala del tanque (8x6)
  '..KKKK..',
  '.KOYYOK.',
  'KOYWWYOK',
  'KOYWWYOK',
  '.KOYYOK.',
  '..KKKK..',
];

const HEART = [
  '.RR.RR..',
  'RRRRRRR.',
  'RWRRRRR.',
  'RRRRRRR.',
  '.RRRRR..',
  '..RRR...',
  '...R....',
];

const BASE_PALETTE = {
  K: '#14141c', W: '#ffffff', S: '#f6c9a0', Y: '#facc15', B: '#6b3f22',
  P: '#a855f7', p: '#6b21a8',
  D: '#1f2937', G: '#64748b', F: '#c08040', R: '#ef4444', O: '#fb923c',
};

// ------------------------------------------------------------- utilidades ----

/** Pinta un dibujo de texto en un canvas, píxel a píxel. */
function paint(ctx, rows, palette, offsetX = 0, offsetY = 0) {
  rows.forEach((row, y) => {
    [...row].forEach((ch, x) => {
      const color = palette[ch];
      if (color) {
        ctx.fillStyle = color;
        ctx.fillRect(offsetX + x, offsetY + y, 1, 1);
      }
    });
  });
}

/**
 * Crea una textura con varios fotogramas de 16x16 puestos uno al lado del otro.
 * Cada fotograma se registra con su número (0, 1, 2...) para las animaciones.
 */
function makeSheet(scene, key, frames, palette) {
  const tex = scene.textures.createCanvas(key, PIXEL * frames.length, PIXEL);
  const ctx = tex.getContext();
  frames.forEach((rows, i) => {
    paint(ctx, rows, palette, i * PIXEL, 0);
    tex.add(i, 0, i * PIXEL, 0, PIXEL, PIXEL);
  });
  tex.refresh();
}

/** Crea una textura de 16x16 dibujándola con una función libre. */
function makeTile(scene, key, drawFn, w = PIXEL, h = PIXEL) {
  const tex = scene.textures.createCanvas(key, w, h);
  drawFn(tex.getContext());
  tex.refresh();
}

function rect(ctx, color, x, y, w, h) {
  ctx.fillStyle = color;
  ctx.fillRect(x, y, w, h);
}

// ------------------------------------------------------------ escenario ------

function drawFloor(ctx, shadow) {
  rect(ctx, '#4d8f3f', 0, 0, 16, 16);
  // pequeñas manchas de hierba (siempre en el mismo sitio)
  [[2, 3], [9, 5], [13, 11], [5, 12], [11, 1], [7, 9]].forEach(([x, y]) => rect(ctx, '#5ea84e', x, y, 2, 1));
  [[4, 7], [12, 7], [1, 13]].forEach(([x, y]) => rect(ctx, '#437f37', x, y, 1, 1));
  rect(ctx, '#46843a', 15, 0, 1, 16);
  rect(ctx, '#46843a', 0, 15, 16, 1);
  // sombra que proyecta un muro o bloque que está justo encima
  if (shadow) rect(ctx, 'rgba(0,0,0,0.28)', 0, 0, 16, 4);
}

function drawWall(ctx) {
  rect(ctx, '#7c8494', 0, 0, 16, 16);
  rect(ctx, '#aab2c0', 0, 0, 16, 2);  // brillo arriba
  rect(ctx, '#aab2c0', 0, 0, 2, 16);  // brillo izquierda
  rect(ctx, '#4b515c', 0, 14, 16, 2); // sombra abajo
  rect(ctx, '#4b515c', 14, 0, 2, 16); // sombra derecha
  rect(ctx, '#8f97a7', 4, 4, 8, 8);
  rect(ctx, '#6b7280', 4, 11, 8, 1);
  rect(ctx, '#6b7280', 11, 4, 1, 8);
}

function drawBlock(ctx) {
  rect(ctx, '#6b3a1f', 0, 0, 16, 16); // cemento
  for (let row = 0; row < 4; row++) {
    const y = row * 4;
    const offset = row % 2 === 0 ? 0 : -4;
    for (let x = offset; x < 16; x += 8) {
      const bx = Math.max(0, x);
      const bw = Math.min(x + 7, 16) - bx;
      if (bw <= 0) continue;
      rect(ctx, '#c8743c', bx, y, bw, 3);   // ladrillo
      rect(ctx, '#e3995e', bx, y, bw, 1);   // brillo
      rect(ctx, '#a35a2b', bx, y + 2, bw, 1); // sombra
    }
  }
  rect(ctx, '#4a2412', 0, 15, 16, 1);
  rect(ctx, '#4a2412', 15, 0, 1, 16);
}

// --------------------------------------------------------------- llamas ------
// Se dibujan "mirando a la derecha"; para otras direcciones se rota el sprite.
const FLAME = { outer: '#ff5a00', mid: '#ffb000', inner: '#fff3b0', core: '#ffffff' };

function drawFlameCenter(ctx, thin) {
  const t = thin ? 1 : 0;
  rect(ctx, FLAME.outer, 0, 3 + t, 16, 10 - 2 * t); rect(ctx, FLAME.outer, 3 + t, 0, 10 - 2 * t, 16);
  rect(ctx, FLAME.mid, 0, 5 + t, 16, 6 - 2 * t); rect(ctx, FLAME.mid, 5 + t, 0, 6 - 2 * t, 16);
  rect(ctx, FLAME.inner, 2, 7, 12, 2); rect(ctx, FLAME.inner, 7, 2, 2, 12);
  rect(ctx, FLAME.core, 5, 5, 6, 6);
}

function drawFlameArm(ctx, thin) {
  const t = thin ? 1 : 0;
  rect(ctx, FLAME.outer, 0, 3 + t, 16, 10 - 2 * t);
  rect(ctx, FLAME.mid, 0, 5 + t, 16, 6 - 2 * t);
  rect(ctx, FLAME.inner, 0, 7, 16, 2);
}

function drawFlameEnd(ctx, thin) {
  const t = thin ? 1 : 0;
  rect(ctx, FLAME.outer, 0, 3 + t, 12, 10 - 2 * t);
  rect(ctx, FLAME.outer, 12, 4 + t, 2, 8 - 2 * t);
  rect(ctx, FLAME.outer, 14, 6, 1, 4);
  rect(ctx, FLAME.mid, 0, 5 + t, 12, 6 - 2 * t);
  rect(ctx, FLAME.mid, 12, 6, 1, 4);
  rect(ctx, FLAME.inner, 0, 7, 11, 2);
}

/** Textura con 2 fotogramas (normal y "fino") para que la llama parpadee. */
function makeFlame(scene, key, drawFn) {
  const tex = scene.textures.createCanvas(key, 32, 16);
  const ctx = tex.getContext();
  drawFn(ctx, false);
  ctx.save();
  ctx.translate(16, 0);
  drawFn(ctx, true);
  ctx.restore();
  tex.add(0, 0, 0, 0, 16, 16);
  tex.add(1, 0, 16, 0, 16, 16);
  tex.refresh();
}

// ------------------------------------------------------------- power-ups -----

function makePowerUp(scene, key, icon, glow) {
  makeTile(scene, key, (ctx) => {
    rect(ctx, '#fbbf24', 1, 1, 14, 14); // marco dorado
    rect(ctx, '#fde68a', 1, 1, 14, 1);
    rect(ctx, '#b45309', 1, 14, 14, 1);
    rect(ctx, glow, 2, 2, 12, 12);      // fondo de color
    rect(ctx, 'rgba(255,255,255,0.25)', 2, 2, 12, 3);
    paint(ctx, icon, BASE_PALETTE, 3, 3);
  });
}

// ------------------------------------------------------- función principal ---

export function createTextures(scene) {
  // Escenario
  makeTile(scene, 'floor', (ctx) => drawFloor(ctx, false));
  makeTile(scene, 'floor-shadow', (ctx) => drawFloor(ctx, true));
  makeTile(scene, 'wall', drawWall);
  makeTile(scene, 'block', drawBlock);

  // Un juego de sprites por color de jugador.
  // Fotogramas: 0-2 de frente, 3-5 de espaldas, 6-7 de lado.
  PLAYER_COLORS.forEach((color, i) => {
    const palette = { ...BASE_PALETTE, C: color.main, c: color.dark };
    makeSheet(scene, `player${i}`, [
      PLAYER_FRONT, withLegs(PLAYER_FRONT, LEGS_FRONT_A), withLegs(PLAYER_FRONT, LEGS_FRONT_B),
      PLAYER_BACK, withLegs(PLAYER_BACK, LEGS_FRONT_A), withLegs(PLAYER_BACK, LEGS_FRONT_B),
      PLAYER_SIDE, withLegs(PLAYER_SIDE, LEGS_SIDE_A),
    ], palette);
  });

  makeSheet(scene, 'enemy', [ENEMY_A, ENEMY_B], BASE_PALETTE);
  makeSheet(scene, 'bomb', [BOMB_A, BOMB_B], BASE_PALETTE);

  makeFlame(scene, 'flame-center', drawFlameCenter);
  makeFlame(scene, 'flame-arm', drawFlameArm);
  makeFlame(scene, 'flame-end', drawFlameEnd);

  makePowerUp(scene, 'pu-BOMB_RANGE', ICON_FIRE, '#7f1d1d');
  makePowerUp(scene, 'pu-EXTRA_BOMB', ICON_BOMB, '#1e3a8a');
  makePowerUp(scene, 'pu-SPEED', ICON_SPEED, '#14532d');
  makePowerUp(scene, 'pu-KICK', ICON_KICK, '#0e7490');
  makePowerUp(scene, 'pu-SKULL', ICON_SKULL, '#3b0764');
  makePowerUp(scene, 'pu-TANK', ICON_TANK, '#78350f');
  makeTile(scene, 'skull', (ctx) => paint(ctx, ICON_SKULL, BASE_PALETTE), 10, 10);

  // Un tanque por color de jugador (2 fotogramas: orugas en movimiento)
  PLAYER_COLORS.forEach((color, i) => {
    const palette = { ...BASE_PALETTE, C: color.main, c: color.dark, T: '#1e293b', t: '#64748b', G: '#94a3b8' };
    makeSheet(scene, `tank${i}`, [TANK_A, TANK_B], palette);
  });
  makeTile(scene, 'shell', (ctx) => paint(ctx, SHELL, { ...BASE_PALETTE, O: '#f97316' }), 8, 6);

  makeTile(scene, 'heart', (ctx) => paint(ctx, HEART, BASE_PALETTE), 8, 7);
  makeTile(scene, 'heart-empty', (ctx) => paint(ctx, HEART, { R: '#475569', W: '#64748b' }), 8, 7);

  // Partícula blanca (se tiñe del color que haga falta)
  makeTile(scene, 'pixel', (ctx) => rect(ctx, '#ffffff', 0, 0, 2, 2), 2, 2);
}

/** Animaciones (se crean una vez y las usan todas las escenas). */
export function createAnimations(scene) {
  const frames = (key, list) => list.map((frame) => ({ key, frame }));

  PLAYER_COLORS.forEach((_, i) => {
    const key = `player${i}`;
    scene.anims.create({ key: `${key}-walk-down`, frames: frames(key, [1, 0, 2, 0]), frameRate: 10, repeat: -1 });
    scene.anims.create({ key: `${key}-walk-up`, frames: frames(key, [4, 3, 5, 3]), frameRate: 10, repeat: -1 });
    scene.anims.create({ key: `${key}-walk-side`, frames: frames(key, [7, 6]), frameRate: 8, repeat: -1 });
  });
  PLAYER_COLORS.forEach((_, i) => {
    scene.anims.create({ key: `tank${i}-roll`, frames: frames(`tank${i}`, [0, 1]), frameRate: 10, repeat: -1 });
  });
  scene.anims.create({ key: 'enemy-wobble', frames: frames('enemy', [0, 1]), frameRate: 4, repeat: -1 });
  scene.anims.create({ key: 'bomb-fuse', frames: frames('bomb', [0, 1]), frameRate: 8, repeat: -1 });
  ['center', 'arm', 'end'].forEach((part) => {
    scene.anims.create({ key: `flame-${part}`, frames: frames(`flame-${part}`, [0, 1]), frameRate: 14, repeat: -1 });
  });
}

/** Fotograma "quieto" para cada dirección. */
export const STAND_FRAME = { DOWN: 0, UP: 3, LEFT: 6, RIGHT: 6 };

/** Ángulo para los sprites dibujados "mirando a la derecha" (tanque, bala, llamas). */
export const DIR_ANGLE = { RIGHT: 0, DOWN: 90, LEFT: 180, UP: -90 };
