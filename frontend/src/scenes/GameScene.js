import {
  CURSE_TEXT, CURSE_TEXT_GUNS, DEPTH, EMOTES, HUD_HEIGHT, PLAYER_COLORS, SCALE, TILE, WEAPON_NAMES, tileToWorld,
} from '../config.js';
import { GridInterpolator } from '../interpolation.js';
import { LocalPredictor } from '../prediction.js';
import { net } from '../net.js';
import { sfx } from '../sound.js';
import { DIR_ANGLE, STAND_FRAME } from '../textures.js';
import { textStyle } from '../ui.js';

const OPPOSITE = { UP: 'DOWN', DOWN: 'UP', LEFT: 'RIGHT', RIGHT: 'LEFT' };

/** Dónde se dibuja el arma respecto al centro del personaje, según hacia dónde mira. */
const GUN_OFFSET = { RIGHT: [16, 8], LEFT: [-16, 8], UP: [12, -6], DOWN: [-12, 12] };

/** Barra de vida: fondo oscuro + relleno verde→amarillo→rojo según lo que quede. */
function drawBar(g, x, y, width, height, fraction) {
  const f = Math.max(0, Math.min(1, fraction));
  const color = f > 0.6 ? 0x22c55e : f > 0.3 ? 0xeab308 : 0xef4444;
  g.fillStyle(0x0b1020, 0.85).fillRect(x - 1, y - 1, width + 2, height + 2);
  g.fillStyle(color, 1).fillRect(x, y, width * f, height);
}

/** Teclas de emote: 1-4 de la fila de números o del teclado numérico. */
const EMOTE_KEYS = { Digit1: 0, Digit2: 1, Digit3: 2, Digit4: 3, Numpad1: 0, Numpad2: 1, Numpad3: 2, Numpad4: 3 };

/** Teclas → dirección. Usamos e.code (posición física) para que funcione con teclado español. */
const KEY_TO_DIR = {
  ArrowUp: 'UP', KeyW: 'UP', ArrowDown: 'DOWN', KeyS: 'DOWN',
  ArrowLeft: 'LEFT', KeyA: 'LEFT', ArrowRight: 'RIGHT', KeyD: 'RIGHT',
};
const CAPTURED_KEYS = 'UP,DOWN,LEFT,RIGHT,SPACE';

/**
 * Escena principal de la partida.
 *
 * Importante: el cliente NO decide nada. Cada STATE del servidor dice dónde está
 * cada cosa; esta escena solo lo DIBUJA bonito:
 *   - onState() (20 veces/s): crea, actualiza o borra sprites según el estado.
 *   - update()  (60 veces/s): interpola posiciones y anima (latido de bombas, parpadeo...).
 */
export class GameScene extends Phaser.Scene {
  constructor() {
    super('Game');
  }

  create() {
    this.mapRows = null;
    this.floor = new Map();    // "x,y" → imagen del suelo
    this.solids = new Map();   // "x,y" → imagen de muro o bloque
    this.players = new Map();  // id → { sprite, label, interp, data, color }
    this.enemies = new Map();
    this.bombs = new Map();
    this.powerUps = new Map(); // "x,y" → { sprite, glow }
    this.flames = new Map();   // "cx,cy" → [sprites]
    this.shells = new Map();   // id → { sprite, interp }
    this.cracks = new Map();   // "x,y" → imagen de grietas (modo pistolas)
    this.mode = 'CLASSIC';
    this.mineTimer = null;
    this.firing = false;
    this.msPerTick = 1000 / net.tickRate;
    this.finished = false;
    this.lastWallSound = 0;
    // Phaser REUTILIZA el mismo objeto escena al reiniciarla: hay que limpiar todo aquí
    this.lastTick = undefined;

    // CÁMARA: el mapa es más grande que la pantalla, así que la cámara de esta escena
    // ocupa solo la zona bajo el HUD y sigue a nuestro jugador. Le damos un objeto
    // invisible ("focus") que movemos cada fotograma a la posición interpolada.
    const { width, height } = this.scale;
    this.cameras.main.setViewport(0, HUD_HEIGHT, width, height - HUD_HEIGHT);
    this.focus = this.add.zone(width / 2, HUD_HEIGHT + height / 2, 1, 1);

    this.setupInput();
    this.scene.launch('Hud');

    net.on('state', this.onState, this);
    net.on('emote', this.onEmote, this);
    net.on('error', this.onServerError, this);
    this.closing = false;
    this.events.once('shutdown', () => {
      this.closing = true;
      net.off('state', this.onState, this);
      net.off('emote', this.onEmote, this);
      net.off('error', this.onServerError, this);
      this.game.events.off('blur', this.releaseKeys, this);
      this.input.keyboard.off('keydown', this.onKeyDown, this);
      this.input.keyboard.off('keyup', this.onKeyUp, this);
      this.input.keyboard.removeCapture(CAPTURED_KEYS);
      this.scene.stop('Hud');
    });

    if (net.lastState) this.onState(net.lastState);
  }

  // ================================================================ ENTRADA ===

  setupInput() {
    // Pila de direcciones pulsadas: si sueltas una tecla y aún tienes otra
    // pulsada, sigues en esa dirección en vez de pararte.
    this.held = [];
    const kb = this.input.keyboard;
    kb.addCapture(CAPTURED_KEYS); // que las flechas/espacio no muevan la página
    // Métodos con nombre (no funciones anónimas) para poder quitarlos al cerrar la escena;
    // si no, en cada revancha se acumularían y cada tecla se enviaría varias veces.
    kb.on('keydown', this.onKeyDown, this);
    kb.on('keyup', this.onKeyUp, this);
    // Si cambias de ventana con una tecla pulsada, el navegador no avisa del keyup
    this.game.events.on('blur', this.releaseKeys, this);
  }

  onKeyDown(e) {
    if (e.code === 'KeyM') { sfx.toggleMute(); return; }
    if (e.code in EMOTE_KEYS) {
      if (!e.repeat) net.send({ type: 'EMOTE', emote: EMOTE_KEYS[e.code] });
      return;
    }
    if (this.finished) return;
    if (e.code === 'KeyT') {
      // Truco de pruebas: el SERVIDOR decide si lo acepta (solo con el perfil "dev")
      if (!e.repeat) net.send({ type: 'CHEAT', powerUp: 'TANK' });
      return;
    }
    if (e.code === 'Space') {
      if (e.repeat) return;
      // Clásico: una bomba por pulsación. Pistolas: mantener = disparar sin parar
      // (la cadencia la controla el servidor según el arma).
      if (this.mode === 'GUNS') {
        this.firing = true;
        net.send({ type: 'FIRE', on: true });
      } else {
        net.send({ type: 'PLACE_BOMB' });
      }
      return;
    }
    if (e.code === 'KeyE' && this.mode === 'GUNS') {
      if (e.repeat || this.mineTimer) return;
      // Mantener E = picar repetidamente. Enviamos un MINE cada 260 ms (el servidor
      // también limita la frecuencia; no usamos la autorrepetición del teclado
      // porque mandaría ~30 mensajes por segundo).
      net.send({ type: 'MINE' });
      this.mineTimer = this.time.addEvent({ delay: 260, loop: true, callback: () => net.send({ type: 'MINE' }) });
      return;
    }
    const dir = KEY_TO_DIR[e.code];
    if (!dir || e.repeat) return; // e.repeat = autorrepetición del teclado: la ignoramos
    this.held = this.held.filter((d) => d !== dir);
    this.held.push(dir);
    net.send({ type: 'MOVE', dir });
  }

  onKeyUp(e) {
    if (e.code === 'Space' && this.firing) {
      this.firing = false;
      net.send({ type: 'FIRE', on: false });
      return;
    }
    if (e.code === 'KeyE') {
      this.stopMining();
      return;
    }
    const dir = KEY_TO_DIR[e.code];
    if (!dir) return;
    this.held = this.held.filter((d) => d !== dir);
    net.send(this.held.length ? { type: 'MOVE', dir: this.held[this.held.length - 1] } : { type: 'STOP' });
  }

  stopMining() {
    if (this.mineTimer) {
      this.mineTimer.remove();
      this.mineTimer = null;
    }
  }

  releaseKeys() {
    this.stopMining();
    if (this.firing) {
      this.firing = false;
      net.send({ type: 'FIRE', on: false });
    }
    if (this.held.length) {
      this.held = [];
      net.send({ type: 'STOP' });
    }
  }

  // ========================================================= LLEGA UN STATE ===

  onState(s) {
    if (this.closing) return; // la escena se está cerrando
    // Seguridad extra: si el contador de ticks vuelve atrás es una partida NUEVA
    // (revancha). Reiniciamos la escena para empezar con el mapa limpio.
    if (this.lastTick !== undefined && s.tick < this.lastTick) {
      this.scene.restart();
      return;
    }
    this.lastTick = s.tick;
    const now = this.time.now;

    // El mapa solo viene cuando cambia (o una vez por segundo). Si aún no lo
    // tenemos (acabamos de entrar), esperamos al siguiente que lo traiga.
    if (s.map) {
      if (!this.mapRows) this.buildMap(s.map);
      else this.updateMap(s.map);
    }
    if (!this.mapRows) return;
    this.mode = s.mode || 'CLASSIC';

    this.syncCracks(s.cracks || []);
    this.syncPowerUps(s.powerUps);
    this.syncBombs(s.bombs, now);
    this.syncFlames(s.explosions);
    this.syncShells(s.shells || [], now);
    this.syncEnemies(s.enemies, now);
    this.syncPlayers(s.players, now);
    this.playEvents(s.events);

    // El estado manda: "terminada" solo si el servidor dice FINISHED
    this.finished = s.status === 'FINISHED';
    if (this.finished) this.releaseKeys();
  }

  // ------------------------------------------------------------------ mapa ---

  buildMap(rows) {
    this.mapRows = rows;
    // Límites de la cámara = tamaño del mapa (no se ve nada fuera de él)
    const cam = this.cameras.main;
    cam.setBounds(0, HUD_HEIGHT, rows[0].length * TILE, rows.length * TILE);
    cam.startFollow(this.focus, true, 0.15, 0.15);
    rows.forEach((row, y) => {
      [...row].forEach((ch, x) => {
        const w = tileToWorld(x, y);
        const floor = this.add.image(w.x, w.y, this.floorTexture(rows, x, y)).setScale(SCALE).setDepth(DEPTH.FLOOR);
        this.floor.set(`${x},${y}`, floor);
        if (ch === '#' || ch === 'B') {
          const img = this.add.image(w.x, w.y, ch === '#' ? 'wall' : 'block').setScale(SCALE).setDepth(DEPTH.SOLID);
          this.solids.set(`${x},${y}`, img);
        }
      });
    });
  }

  /**
   * Compara el mapa nuevo con el anterior:
   *  - un bloque que ya no está → se rompe,
   *  - un muro nuevo (muerte súbita) → cae desde arriba.
   */
  updateMap(rows) {
    rows.forEach((row, y) => {
      for (let x = 0; x < row.length; x++) {
        const before = this.mapRows[y][x];
        if (before === 'B' && row[x] === '.') {
          this.breakBlock(x, y);
        } else if (before !== '#' && row[x] === '#') {
          this.dropWall(x, y);
        } else {
          continue;
        }
        this.refreshFloor(rows, x, y);
        this.refreshFloor(rows, x, y + 1); // la casilla de abajo cambia de sombra
      }
    });
    this.mapRows = rows;
  }

  /** Muerte súbita: un muro cae del cielo y aplasta lo que haya debajo. */
  dropWall(x, y) {
    const key = `${x},${y}`;
    const old = this.solids.get(key);
    if (old) old.destroy(); // si había un bloque, el muro lo sustituye
    const w = tileToWorld(x, y);
    const wall = this.add.image(w.x, w.y - TILE * 3, 'wall').setScale(SCALE).setDepth(DEPTH.FX - 1).setAlpha(0.3);
    this.solids.set(key, wall);
    this.tweens.add({
      targets: wall, y: w.y, alpha: 1, duration: 220, ease: 'Quad.in',
      onComplete: () => {
        wall.setDepth(DEPTH.SOLID);
        this.burst(w.x, w.y + TILE / 2, [0x94a3b8, 0x64748b], 8, 150);
      },
    });
    if (this.time.now - this.lastWallSound > 120) { // que no suene 20 veces seguidas
      this.lastWallSound = this.time.now;
      sfx.wallDrop();
    }
  }


  floorTexture(rows, x, y) {
    const above = y > 0 ? rows[y - 1][x] : '#';
    return above === '#' || above === 'B' ? 'floor-shadow' : 'floor';
  }

  refreshFloor(rows, x, y) {
    const img = this.floor.get(`${x},${y}`);
    if (img) img.setTexture(this.floorTexture(rows, x, y));
  }

  breakBlock(x, y) {
    const key = `${x},${y}`;
    const img = this.solids.get(key);
    if (!img) return;
    this.solids.delete(key);
    // El bloque se encoge y gira mientras salen trozos de ladrillo
    this.tweens.add({
      targets: img, scale: SCALE * 0.3, angle: 25, alpha: 0, duration: 260, ease: 'Quad.in',
      onComplete: () => img.destroy(),
    });
    this.burst(img.x, img.y, [0xc8743c, 0x6b3a1f, 0xe3995e], 14, 400);
  }

  // -------------------------------------------------------------- power-ups ---

  syncPowerUps(list) {
    const seen = new Set();
    list.forEach((p) => {
      const key = `${p.x},${p.y}`;
      seen.add(key);
      if (this.powerUps.has(key)) return;
      const w = tileToWorld(p.x, p.y);
      // Halo que "brilla" detrás del power-up
      const glow = this.add.circle(w.x, w.y, 20, 0xfff3b0, 0.35).setDepth(DEPTH.POWERUP).setBlendMode('ADD');
      const sprite = this.add.image(w.x, w.y, `pu-${p.type}`).setScale(0).setDepth(DEPTH.POWERUP + 1);
      this.tweens.add({ targets: sprite, scale: SCALE * 0.85, duration: 300, ease: 'Back.out' });
      this.tweens.add({ targets: sprite, y: w.y - 3, duration: 700, yoyo: true, repeat: -1, ease: 'Sine.inOut' });
      this.tweens.add({ targets: glow, alpha: 0.05, scale: 1.3, duration: 600, yoyo: true, repeat: -1 });
      this.powerUps.set(key, { sprite, glow });
    });
    for (const [key, pu] of this.powerUps) {
      if (!seen.has(key)) {
        pu.sprite.destroy();
        pu.glow.destroy();
        this.powerUps.delete(key);
      }
    }
  }

  // ----------------------------------------------------------------- bombas ---

  syncBombs(list, now) {
    const seen = new Set();
    list.forEach((b) => {
      seen.add(b.id);
      let entry = this.bombs.get(b.id);
      if (!entry) {
        const w = tileToWorld(b.x, b.y);
        const sprite = this.add.sprite(w.x, w.y, 'bomb', 0).setDepth(DEPTH.BOMB).play('bomb-fuse');
        entry = { sprite, total: b.ticksLeft + 1, ticksLeft: b.ticksLeft, phase: 0, pop: 0,
                  interp: new GridInterpolator(b.x, b.y) };
        this.tweens.add({ targets: entry, pop: 1, duration: 200, ease: 'Back.out' });
        this.bombs.set(b.id, entry);
      }
      entry.ticksLeft = b.ticksLeft;
      // Si la patean, se desliza suavemente igual que los jugadores
      entry.interp.setTarget(b.x, b.y, b.moveDelayTicks * this.msPerTick, now);
    });
    for (const [id, entry] of this.bombs) {
      if (!seen.has(id)) {
        entry.sprite.destroy(); // ha explotado: ahora la dibujan las llamas
        this.bombs.delete(id);
      }
    }
  }

  // ------------------------------------------------------- balas del tanque ---

  syncShells(list, now) {
    const seen = new Set();
    list.forEach((s) => {
      seen.add(s.id);
      let entry = this.shells.get(s.id);
      if (!entry) {
        const w = tileToWorld(s.x, s.y);
        const isTank = s.kind !== 'BULLET';
        const sprite = this.add.image(w.x, w.y, isTank ? 'shell' : 'bullet')
          .setScale(isTank ? SCALE : SCALE * 0.8).setAngle(DIR_ANGLE[s.dir]).setDepth(DEPTH.FLAME + 1);
        // Estela de chispas solo para el obús del tanque. Las balas normales van sin
        // estela: con el subfusil hay muchas a la vez y cada estela cuesta rendimiento.
        const trail = isTank ? this.add.particles(0, 0, 'pixel', {
          follow: sprite, speed: 20, scale: { start: 2.5, end: 0 }, lifespan: 250,
          frequency: 15, tint: [0xffb000, 0xff5a00, 0xffffff],
        }).setDepth(DEPTH.FLAME) : null;
        entry = { sprite, trail, isTank, interp: new GridInterpolator(s.x, s.y) };
        this.shells.set(s.id, entry);
      }
      entry.interp.setTarget(s.x, s.y, this.msPerTick, now); // una casilla por tick
    });
    for (const [id, entry] of this.shells) {
      if (!seen.has(id)) {
        this.burst(entry.sprite.x, entry.sprite.y, [0xffffff, 0xffb000], entry.isTank ? 10 : 3, entry.isTank ? 200 : 90);
        entry.sprite.destroy();
        if (entry.trail) {
          entry.trail.stop();
          this.time.delayedCall(300, () => entry.trail.destroy());
        }
        this.shells.delete(id);
      }
    }
  }

  // ----------------------------------------------------------------- llamas ---

  syncFlames(explosions) {
    const seen = new Set();
    explosions.forEach((ex) => {
      const key = `${ex.center.x},${ex.center.y}`;
      seen.add(key);
      if (!this.flames.has(key)) this.flames.set(key, this.createFlames(ex));
    });
    for (const [key, sprites] of this.flames) {
      if (!seen.has(key)) {
        this.tweens.add({ targets: sprites, alpha: 0, scale: SCALE * 0.6, duration: 150, onComplete: () => sprites.forEach((s) => s.destroy()) });
        this.flames.delete(key);
      }
    }
  }

  /**
   * Dibuja la cruz: "center" en la bomba, "arm" en el tramo y "end" en la punta.
   * Los sprites miran a la derecha; los giramos según la dirección.
   */
  createFlames(ex) {
    const cells = new Set(ex.cells.map((c) => `${c.x},${c.y}`));
    return ex.cells.map((c) => {
      let part = 'center';
      let angle = 0;
      if (c.x !== ex.center.x || c.y !== ex.center.y) {
        const dx = Math.sign(c.x - ex.center.x);
        const dy = Math.sign(c.y - ex.center.y);
        part = cells.has(`${c.x + dx},${c.y + dy}`) ? 'arm' : 'end';
        angle = DIR_ANGLE[dx === 1 ? 'RIGHT' : dx === -1 ? 'LEFT' : dy === 1 ? 'DOWN' : 'UP'];
      }
      const w = tileToWorld(c.x, c.y);
      const sprite = this.add.sprite(w.x, w.y, `flame-${part}`, 0)
        .setScale(SCALE * 0.4).setAngle(angle).setDepth(DEPTH.FLAME).play(`flame-${part}`);
      this.tweens.add({ targets: sprite, scale: SCALE, duration: 90, ease: 'Back.out' });
      return sprite;
    });
  }

  // -------------------------------------------------------------- enemigos ---

  syncEnemies(list, now) {
    const seen = new Set();
    list.forEach((en) => {
      seen.add(en.id);
      let entry = this.enemies.get(en.id);
      if (!entry) {
        const w = tileToWorld(en.x, en.y);
        const sprite = this.add.sprite(w.x, w.y, 'enemy', 0).setScale(SCALE).play('enemy-wobble');
        const hpBar = this.add.graphics().setDepth(DEPTH.FX);
        entry = { sprite, hpBar, interp: new GridInterpolator(en.x, en.y), data: en };
        this.enemies.set(en.id, entry);
      }
      entry.interp.setTarget(en.x, en.y, en.moveDelayTicks * this.msPerTick, now);
      entry.data = en;
    });
    for (const [id, entry] of this.enemies) {
      if (!seen.has(id)) {
        // Muerto: se infla, se pone blanco y desaparece
        this.enemies.delete(id);
        entry.hpBar.destroy();
        entry.sprite.stop().setTintFill(0xffffff);
        this.tweens.add({
          targets: entry.sprite, scale: SCALE * 1.6, alpha: 0, duration: 350,
          onComplete: () => entry.sprite.destroy(),
        });
        this.burst(entry.sprite.x, entry.sprite.y, [0xa855f7, 0xffffff], 12, 250);
      }
    }
  }

  // ------------------------------------------------------------- jugadores ---

  syncPlayers(list, now) {
    list.forEach((p, i) => {
      let entry = this.players.get(p.id);
      if (!entry) {
        const w = tileToWorld(p.x, p.y);
        const color = PLAYER_COLORS[i];
        const isMe = p.id === net.playerId;
        const sprite = this.add.sprite(w.x, w.y, `player${i}`, 0).setScale(SCALE);
        const label = this.add.text(w.x, w.y, isMe ? `▼ ${p.name}` : p.name, textStyle(13, color.main))
          .setOrigin(0.5).setDepth(DEPTH.FX);
        // Extras: el tanque (oculto hasta que lo coja), su blindaje y la calavera de maldición
        const tank = this.add.sprite(w.x, w.y, `tank${i}`, 0).setScale(SCALE).setVisible(false);
        const armor = this.add.text(w.x, w.y, '', textStyle(12, '#93c5fd')).setOrigin(0.5).setDepth(DEPTH.FX);
        const skull = this.add.image(w.x, w.y, 'skull').setScale(2).setDepth(DEPTH.FX).setVisible(false);
        // Modo pistolas: el arma en la mano y la barra de vida
        const gun = this.add.image(w.x, w.y, 'gun-PISTOL').setScale(SCALE * 0.75).setVisible(false);
        const hpBar = this.add.graphics().setDepth(DEPTH.FX);
        entry = { sprite, label, tank, armor, skull, gun, hpBar, bubble: null,
                  interp: new GridInterpolator(p.x, p.y), data: p, color: i, wasAlive: p.alive,
                  // Solo NUESTRO personaje se predice (los demás se interpolan)
                  predictor: isMe ? new LocalPredictor(p.x, p.y) : null };
        if (!p.alive) { sprite.setVisible(false); label.setVisible(false); }
        this.players.set(p.id, entry);
      }
      const stepMs = p.moveDelayTicks * this.msPerTick;
      if (entry.predictor && p.alive) {
        // Nuestro personaje: comparamos lo que predijimos con lo que dice el servidor
        entry.predictor.reconcile(p.x, p.y, now, entry.interp, stepMs * 2 + net.latency + 150);
      } else {
        // La duración del deslizamiento = lo que tarda el servidor en dejarle dar otro paso
        entry.interp.setTarget(p.x, p.y, stepMs, now);
      }
      if (entry.wasAlive && !p.alive) this.playerDies(entry);
      entry.wasAlive = p.alive;
      entry.data = p;
    });
  }

  playerDies(entry) {
    entry.label.setVisible(false);
    entry.gun.setVisible(false);
    entry.hpBar.clear();
    entry.tank.setVisible(false);
    entry.armor.setVisible(false);
    entry.skull.setVisible(false);
    entry.sprite.setVisible(true).stop();
    this.tweens.add({
      targets: entry.sprite, angle: 720, scale: 0, alpha: 0, duration: 700, ease: 'Quad.in',
      onComplete: () => entry.sprite.setVisible(false),
    });
  }

  // ---------------------------------------------------- eventos (sonido/FX) ---

  playEvents(events) {
    let exploded = false;
    events.forEach((ev) => {
      const w = ev.position ? tileToWorld(ev.position.x, ev.position.y) : null;
      switch (ev.type) {
        case 'BOMB_PLACED':
          sfx.placeBomb();
          break;
        case 'EXPLOSION':
          if (!exploded) { // en una reacción en cadena, un solo sonido fuerte
            exploded = true;
            sfx.explosion();
            this.cameras.main.shake(180, 0.006);
          }
          this.burst(w.x, w.y, [0xffffff, 0xffb000, 0xff5a00], 16, 300);
          break;
        case 'POWERUP_TAKEN':
          sfx.powerUp();
          this.burst(w.x, w.y, [0xfde68a, 0xffffff], 12, 200);
          if (ev.playerId === net.playerId) this.floatText(w.x, w.y - 20, '¡Mejora!', '#fde68a');
          break;
        case 'PLAYER_HIT': {
          sfx.hit();
          if (ev.playerId === net.playerId) this.cameras.main.flash(250, 255, 60, 60);
          this.burst(w.x, w.y, [0xffffff, 0xef4444], 12, 200);
          break;
        }
        case 'ENEMY_KILLED':
          sfx.enemyKilled();
          break;
        case 'BOMB_KICKED':
          sfx.kick();
          this.burst(w.x, w.y, [0xe2e8f0], 6, 120);
          break;
        case 'CURSED': {
          sfx.curse();
          this.burst(w.x, w.y, [0xa855f7, 0x3b0764], 14, 160);
          const cursed = net.lastState.players.find((p) => p.id === ev.playerId);
          if (ev.playerId === net.playerId && cursed && cursed.curse) {
            const texts = this.mode === 'GUNS' ? { ...CURSE_TEXT, ...CURSE_TEXT_GUNS } : CURSE_TEXT;
            this.floatText(w.x, w.y - 30, texts[cursed.curse], '#d8b4fe');
          }
          break;
        }
        case 'TANK_ON':
          sfx.tankOn();
          this.burst(w.x, w.y, [0xfbbf24, 0xffffff, 0x22c55e], 20, 250);
          this.floatText(w.x, w.y - 30, '¡TANQUE!', '#fbbf24');
          break;
        case 'TANK_HIT':
          sfx.metalHit();
          this.burst(w.x, w.y, [0x94a3b8, 0xffffff], 12, 220);
          if (ev.playerId === net.playerId) this.cameras.main.shake(150, 0.008);
          break;
        case 'TANK_DESTROYED':
          sfx.explosion();
          this.burst(w.x, w.y, [0x475569, 0x94a3b8, 0xff5a00], 26, 320);
          break;
        case 'SHOT_FIRED': {
          const shooter = this.players.get(ev.playerId);
          if (!shooter || shooter.data.tankArmor > 0) {
            sfx.shoot(); // cañón del tanque
          } else {
            sfx.gunshot(shooter.data.weapon);
            // Fogonazo en la boca del arma
            this.burst(shooter.gun.x, shooter.gun.y, [0xfff3b0, 0xffb000], 4, 80);
          }
          break;
        }
        case 'PICKAXE_SWING':
          this.swingPickaxe(ev.playerId, w);
          break;
        case 'BLOCK_CRACKED':
          sfx.pickaxe();
          this.burst(w.x, w.y, [0xc8743c, 0x6b3a1f], 6, 140);
          break;
        case 'ENEMY_DAMAGED': {
          const hit = [...this.enemies.values()].find((e) => e.data.x === ev.position.x && e.data.y === ev.position.y);
          if (hit) {
            hit.sprite.setTintFill(0xffffff);
            this.time.delayedCall(70, () => hit.sprite.clearTint());
          }
          sfx.hitSoft();
          break;
        }
        case 'PLAYER_DAMAGED': {
          sfx.hitSoft();
          this.burst(w.x, w.y, [0xef4444, 0xffffff], 6, 140);
          if (ev.playerId === net.playerId) this.cameras.main.shake(90, 0.004);
          break;
        }
        case 'HEALED':
          sfx.heal();
          this.burst(w.x, w.y, [0x22c55e, 0xffffff], 14, 160);
          this.floatText(w.x, w.y - 30, '+vida', '#86efac');
          break;
        case 'WEAPON_UP':
          sfx.powerUp();
          if (ev.playerId === net.playerId) this.floatText(w.x, w.y - 30, '+cadencia', '#fde68a');
          break;
        case 'WEAPON_EVOLVED': {
          sfx.evolve();
          this.burst(w.x, w.y, [0xfbbf24, 0xffffff, 0x38bdf8], 28, 300);
          const who = net.lastState.players.find((p) => p.id === ev.playerId);
          if (who) this.floatText(w.x, w.y - 34, `¡${WEAPON_NAMES[who.weapon]}!`, '#fbbf24', 22);
          break;
        }
        // SUDDEN_DEATH y GAME_STARTED los anuncia el HUD (textos fijos en pantalla)
      }
    });
  }

  /** Errores no graves del servidor: por ahora solo avisamos si los trucos están apagados. */
  onServerError(err) {
    if (this.closing || err.code !== 'CHEATS_DISABLED') return;
    const me = this.players.get(net.playerId);
    if (me) this.floatText(me.sprite.x, me.sprite.y - 30, 'Trucos desactivados', '#fca5a5');
  }

  /** Globito con el emote encima del jugador durante 1,6 s. */
  onEmote({ playerId, emote }) {
    if (this.closing) return;
    const entry = this.players.get(playerId);
    if (!entry || !entry.data.alive) return;
    if (entry.bubble) entry.bubble.destroy();

    const bg = this.add.graphics();
    bg.fillStyle(0xffffff, 1).fillRoundedRect(-22, -22, 44, 38, 10);
    bg.fillTriangle(-6, 15, 6, 15, 0, 24); // piquito del globo
    bg.lineStyle(3, 0x0b1020, 1).strokeRoundedRect(-22, -22, 44, 38, 10);
    const icon = this.add.text(0, -3, EMOTES[emote], { fontSize: '24px' }).setOrigin(0.5);
    const bubble = this.add.container(entry.sprite.x, Math.max(HUD_HEIGHT + 24, entry.sprite.y - 60), [bg, icon])
      .setDepth(DEPTH.FX + 1).setScale(0);
    entry.bubble = bubble;
    sfx.pop();
    this.tweens.add({ targets: bubble, scale: 1, duration: 180, ease: 'Back.out' });
    this.tweens.add({
      targets: bubble, alpha: 0, delay: 1400, duration: 250,
      onComplete: () => { bubble.destroy(); if (entry.bubble === bubble) entry.bubble = null; },
    });
  }

  /** Explosión de partículas de colores. */
  burst(x, y, tints, quantity, speed) {
    const emitter = this.add.particles(x, y, 'pixel', {
      speed: { min: speed * 0.3, max: speed },
      angle: { min: 0, max: 360 },
      scale: { start: 3, end: 0 },
      lifespan: { min: 250, max: 600 },
      gravityY: 300,
      tint: tints,
      emitting: false,
    }).setDepth(DEPTH.FX);
    emitter.explode(quantity);
    this.time.delayedCall(800, () => emitter.destroy());
  }

  floatText(x, y, text, color, size = 16) {
    const t = this.add.text(x, y, text, textStyle(size, color)).setOrigin(0.5).setDepth(DEPTH.FX);
    this.tweens.add({ targets: t, y: y - 30, alpha: 0, duration: size > 16 ? 1400 : 900, onComplete: () => t.destroy() });
  }

  /** Animación del pico: aparece entre el jugador y la casilla golpeada y da un "zas". */
  swingPickaxe(playerId, target) {
    const entry = this.players.get(playerId);
    if (!entry) return;
    const x = (entry.sprite.x + target.x) / 2;
    const y = (entry.sprite.y + target.y) / 2;
    const pick = this.add.image(x, y, 'pickaxe').setScale(SCALE * 0.8).setDepth(DEPTH.FX).setAngle(-70);
    this.tweens.add({
      targets: pick, angle: 40, duration: 120, ease: 'Quad.in',
      onComplete: () => this.tweens.add({ targets: pick, alpha: 0, duration: 120, onComplete: () => pick.destroy() }),
    });
    sfx.swing();
  }

  /**
   * ¿Se puede pisar esta casilla? Las MISMAS reglas que el servidor (Game.canPlayerEnter):
   * solo suelo, y sin bomba encima. La usa la predicción.
   */
  canEnter(x, y) {
    const row = this.mapRows && this.mapRows[y];
    if (!row || row[x] !== '.') return false;
    for (const b of this.bombs.values()) {
      if (b.interp.toX === x && b.interp.toY === y) return false;
    }
    return true;
  }

  /** Coloca el arma en la mano del jugador, girada hacia donde mira. */
  placeGun(entry, p, facing, w, alpha) {
    const [ox, oy] = GUN_OFFSET[facing];
    entry.gun.setTexture(`gun-${p.weapon}`).setVisible(true).setAlpha(alpha)
      .setPosition(w.x + ox, w.y + oy)
      .setDepth(DEPTH.ENTITY + w.y + (facing === 'UP' ? -1 : 1)) // mirando arriba, el arma va detrás
      .setFlipX(facing === 'LEFT')
      .setAngle(facing === 'UP' ? -90 : facing === 'DOWN' ? 90 : 0);
  }

  /** Grietas en los bloques que llevan golpes de pico. */
  syncCracks(list) {
    const seen = new Set();
    list.forEach((c) => {
      const key = `${c.x},${c.y}`;
      seen.add(key);
      const texture = c.hits >= 2 ? 'crack2' : 'crack1';
      let img = this.cracks.get(key);
      if (!img) {
        const w = tileToWorld(c.x, c.y);
        img = this.add.image(w.x, w.y, texture).setScale(SCALE).setDepth(DEPTH.SOLID + 1);
        this.cracks.set(key, img);
      }
      img.setTexture(texture);
    });
    for (const [key, img] of this.cracks) {
      if (!seen.has(key)) {
        img.destroy();
        this.cracks.delete(key);
      }
    }
  }

  // ================================================= CADA FOTOGRAMA (60 FPS) ===

  update(time, delta) {
    // Cámara: sigue a mi jugador; si estoy eliminado, al primero que siga vivo (modo espectador)
    const me = this.players.get(net.playerId);
    const target = me && me.data.alive ? me : [...this.players.values()].find((e) => e.data.alive);
    if (target) {
      const pos = target.interp.position(time);
      const w = tileToWorld(pos.x, pos.y);
      this.focus.setPosition(w.x, w.y);
    }

    // Jugadores: posición interpolada + animación según la dirección
    for (const entry of this.players.values()) {
      const p = entry.data;
      if (!p.alive) continue;

      // PREDICCIÓN: nuestro personaje avanza en cuanto pulsamos, sin esperar al servidor
      let facing = p.facing;
      let wantsToWalk = p.moving;
      if (entry.predictor) {
        const held = this.held[this.held.length - 1];
        const dir = held && p.curse === 'REVERSED' ? OPPOSITE[held] : held; // misma regla que el servidor
        if (!this.finished) {
          entry.predictor.update(time, dir, (x, y) => this.canEnter(x, y), p.moveDelayTicks * this.msPerTick, entry.interp);
        }
        if (dir) facing = dir;
        wantsToWalk = !!dir;
      }

      const pos = entry.interp.position(time);
      const w = tileToWorld(pos.x, pos.y);
      const walking = pos.moving || wantsToWalk;
      const inTank = p.tankArmor > 0;
      entry.sprite.setPosition(w.x, w.y - 4).setDepth(DEPTH.ENTITY + w.y);
      entry.label.setPosition(w.x, w.y - 34);

      // Invulnerable tras un golpe: parpadea. Desconectado: semitransparente.
      // Al tanque le quedan ≤3 s: también parpadea para avisar de que se acaba.
      const blink = (p.invulnerable || (inTank && p.tankSeconds <= 3)) && Math.floor(time / 90) % 2 === 0;
      const alpha = !p.connected ? 0.45 : blink ? 0.3 : 1;

      if (inTank) {
        // Montado en el tanque: se ve el tanque girado hacia donde mira
        entry.sprite.setVisible(false);
        entry.tank.setVisible(true).setPosition(w.x, w.y).setDepth(DEPTH.ENTITY + w.y)
          .setAngle(DIR_ANGLE[facing]).setAlpha(alpha);
        if (walking) entry.tank.play(`tank${entry.color}-roll`, true);
        else entry.tank.stop();
        entry.armor.setVisible(true).setPosition(w.x, w.y + 30).setText('■'.repeat(p.tankArmor));
        entry.gun.setVisible(false);
      } else {
        entry.tank.setVisible(false);
        entry.armor.setVisible(false);
        entry.sprite.setVisible(true).setAlpha(alpha).setFlipX(facing === 'LEFT');
        const key = `player${entry.color}`;
        if (walking) {
          const anim = facing === 'UP' ? 'up' : facing === 'DOWN' ? 'down' : 'side';
          entry.sprite.play(`${key}-walk-${anim}`, true); // true = no reiniciar si ya está sonando
        } else {
          entry.sprite.stop();
          entry.sprite.setFrame(STAND_FRAME[facing]);
        }
        // Modo pistolas: el arma en la mano, apuntando hacia donde mira
        if (this.mode === 'GUNS') this.placeGun(entry, p, facing, w, alpha);
        else entry.gun.setVisible(false);
      }

      // Modo pistolas: barra de vida encima de la cabeza
      entry.hpBar.clear();
      if (this.mode === 'GUNS' && !inTank) {
        drawBar(entry.hpBar, w.x - 18, w.y - 26, 36, 5, p.hp / p.maxHp);
      }

      // Maldito: calavera flotando sobre la cabeza y el personaje parpadea en morado
      if (p.curse) {
        entry.skull.setVisible(true).setPosition(w.x, w.y - 52 + Math.sin(time / 150) * 3);
        const purple = Math.floor(time / 150) % 2 === 0;
        (inTank ? entry.tank : entry.sprite).setTint(purple ? 0xc084fc : 0xffffff);
      } else {
        entry.skull.setVisible(false);
        entry.sprite.clearTint();
        entry.tank.clearTint();
      }

      // El globo no puede subir por encima del mapa (lo taparía el HUD)
      if (entry.bubble) entry.bubble.setPosition(w.x, Math.max(HUD_HEIGHT + 24, w.y - (p.curse ? 82 : 64)));
    }

    // Bombas pateadas y balas: posición interpolada
    for (const entry of this.bombs.values()) {
      const pos = entry.interp.position(time);
      const w = tileToWorld(pos.x, pos.y);
      entry.sprite.setPosition(w.x, w.y);
    }
    for (const entry of this.shells.values()) {
      const pos = entry.interp.position(time);
      const w = tileToWorld(pos.x, pos.y);
      entry.sprite.setPosition(w.x, w.y);
    }

    // Enemigos (con barra de vida si están heridos, en el modo pistolas)
    for (const entry of this.enemies.values()) {
      const pos = entry.interp.position(time);
      const w = tileToWorld(pos.x, pos.y);
      entry.sprite.setPosition(w.x, w.y - 2).setDepth(DEPTH.ENTITY + w.y).setFlipX(entry.data.dir === 'LEFT');
      entry.hpBar.clear();
      const e = entry.data;
      if (e.maxHp > 1 && e.hp < e.maxHp) drawBar(entry.hpBar, w.x - 16, w.y - 26, 32, 4, e.hp / e.maxHp);
    }

    // Bombas: "laten" cada vez más rápido y se ponen rojas antes de explotar
    for (const entry of this.bombs.values()) {
      const progress = 1 - entry.ticksLeft / entry.total; // 0 = recién puesta, 1 = a punto
      entry.phase += delta * (0.006 + 0.03 * progress * progress);
      const pulse = 1 + 0.1 * Math.sin(entry.phase);
      entry.sprite.setScale(SCALE * entry.pop * pulse);
      if (progress > 0.65 && Math.sin(entry.phase) > 0) entry.sprite.setTint(0xff7070);
      else entry.sprite.clearTint();
    }
  }
}
