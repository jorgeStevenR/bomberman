import { HUD_HEIGHT, PLAYER_COLORS, TILE } from '../config.js';
import { net } from '../net.js';
import { sfx } from '../sound.js';
import { textStyle } from '../ui.js';

const CARD_W = 150;
const MAX_HEARTS = 5;
const MINIMAP_MAX_W = 168;
const MINIMAP_MAX_H = 136;

/**
 * Todo lo que va FIJO en la pantalla, encima del juego:
 *  - barra superior con una tarjeta por jugador (vidas, power-ups, puntos) y el reloj,
 *  - minimapa (el mapa es más grande que la pantalla),
 *  - avisos grandes (muerte súbita, tanque escondido, reconectando).
 * Es una escena aparte: así ni la cámara que sigue al jugador ni los temblores
 * de las explosiones mueven el HUD.
 */
export class HudScene extends Phaser.Scene {
  constructor() {
    super('Hud');
  }

  create() {
    const { width, height } = this.scale;
    this.add.rectangle(0, 0, width, HUD_HEIGHT, 0x0f172a).setOrigin(0);
    this.add.rectangle(0, HUD_HEIGHT - 3, width, 3, 0x334155).setOrigin(0);

    this.cards = PLAYER_COLORS.map((color, i) => this.createCard(8 + i * 156, 8, color, i));

    const tx = width - 46;
    this.timeLabel = this.add.text(tx, 18, 'TIEMPO', textStyle(12, '#94a3b8', { strokeThickness: 0 })).setOrigin(0.5);
    this.timeText = this.add.text(tx, 40, '4:00', textStyle(24)).setOrigin(0.5);
    this.latencyText = this.add.text(tx, 64, '', textStyle(11, '#94a3b8', { strokeThickness: 0 })).setOrigin(0.5);

    // Borde rojo que late durante la muerte súbita
    this.dangerFrame = this.add.rectangle(width / 2, HUD_HEIGHT + (height - HUD_HEIGHT) / 2, width - 6, height - HUD_HEIGHT - 6)
      .setStrokeStyle(6, 0xef4444).setFillStyle(0xef4444, 0.06).setVisible(false);
    this.suddenDeath = false;

    // Aviso de reconexión
    this.banner = this.add.text(width / 2, HUD_HEIGHT + 24, '', textStyle(20, '#fca5a5')).setOrigin(0.5);

    // Minimapa: capa fija (muros/bloques, solo se redibuja si cambia el mapa) + capa móvil
    this.miniStatic = this.add.graphics();
    this.miniDynamic = this.add.graphics();
    this.mapRows = null;
    this.mapKey = '';
    this.startAnnounced = false;

    net.on('state', this.render, this);
    net.on('reconnecting', this.onReconnecting, this);
    this.closing = false;
    this.events.once('shutdown', () => {
      this.closing = true;
      net.off('state', this.render, this);
      net.off('reconnecting', this.onReconnecting, this);
    });
    if (net.lastState) this.render(net.lastState);
  }

  onReconnecting() {
    if (!this.closing) this.banner.setText('Conexión perdida. Reconectando...');
  }

  /** Texto grande en el centro de la pantalla que aparece, late y se va. */
  announce(message, color, size = 46) {
    const { width, height } = this.scale;
    const y = HUD_HEIGHT + (height - HUD_HEIGHT) / 2 - 40;
    const text = this.add.text(width / 2, y, message, textStyle(size, color, { strokeThickness: 8, align: 'center' }))
      .setOrigin(0.5).setScale(0);
    this.tweens.chain({
      targets: text,
      tweens: [
        { scale: 1.1, duration: 300, ease: 'Back.out' },
        { scale: 1, duration: 200, yoyo: true, repeat: 3 },
        { alpha: 0, y: y - 40, duration: 500, delay: 600 },
      ],
      onComplete: () => text.destroy(),
    });
  }

  // ------------------------------------------------------------- minimapa ----

  /** Redibuja muros y bloques del minimapa (solo cuando el mapa ha cambiado). */
  drawMinimapStatic(rows) {
    const cols = rows[0].length;
    this.cell = Math.max(1, Math.floor(Math.min(MINIMAP_MAX_W / cols, MINIMAP_MAX_H / rows.length)));
    const w = cols * this.cell;
    const h = rows.length * this.cell;
    this.miniX = this.scale.width - w - 10;
    this.miniY = this.scale.height - h - 10;

    const g = this.miniStatic;
    g.clear();
    g.fillStyle(0x0b1020, 0.8).fillRect(this.miniX - 3, this.miniY - 3, w + 6, h + 6);
    g.fillStyle(0x2f5d2a, 0.9).fillRect(this.miniX, this.miniY, w, h);
    rows.forEach((row, y) => {
      for (let x = 0; x < row.length; x++) {
        if (row[x] === '.') continue;
        g.fillStyle(row[x] === '#' ? 0x64748b : 0xb45309, 1)
          .fillRect(this.miniX + x * this.cell, this.miniY + y * this.cell, this.cell, this.cell);
      }
    });
    g.lineStyle(2, 0x334155, 1).strokeRect(this.miniX - 3, this.miniY - 3, w + 6, h + 6);
  }

  /** Jugadores, enemigos, el tanque y el rectángulo de lo que estás viendo (cada fotograma). */
  drawMinimapDynamic(time) {
    const s = net.lastState;
    if (!s || !this.mapRows) return;
    const g = this.miniDynamic;
    const c = this.cell;
    const at = (x, y) => [this.miniX + x * c, this.miniY + y * c];
    g.clear();

    // El tanque en el suelo parpadea en dorado: ¡a por él!
    if (Math.floor(time / 250) % 2 === 0) {
      s.powerUps.filter((p) => p.type === 'TANK').forEach((p) => {
        const [x, y] = at(p.x, p.y);
        g.fillStyle(0xfbbf24, 1).fillRect(x - 2, y - 2, c + 4, c + 4);
      });
    }
    s.enemies.forEach((e) => {
      const [x, y] = at(e.x, e.y);
      g.fillStyle(0xa855f7, 1).fillRect(x, y, c, c);
    });
    s.players.forEach((p, i) => {
      if (!p.alive) return;
      const [x, y] = at(p.x, p.y);
      g.fillStyle(PLAYER_COLORS[i].hex, 1).fillRect(x - 2, y - 2, c + 4, c + 4);
      if (p.id === net.playerId) g.lineStyle(2, 0xffffff, 1).strokeRect(x - 3, y - 3, c + 6, c + 6);
    });

    // Rectángulo blanco: la parte del mapa que se ve en pantalla
    const game = this.scene.get('Game');
    if (game && game.cameras && game.cameras.main && game.focus) {
      const view = game.cameras.main.worldView;
      g.lineStyle(1, 0xffffff, 0.7).strokeRect(
        this.miniX + (view.x / TILE) * c, this.miniY + ((view.y - HUD_HEIGHT) / TILE) * c,
        (view.width / TILE) * c, (view.height / TILE) * c);

      // Si nuestro personaje pasa por debajo del minimapa, lo hacemos casi transparente
      // para que no lo tape (pasa sobre todo en la esquina de abajo a la derecha).
      const screenX = game.focus.x - view.x;
      const screenY = game.focus.y - view.y + HUD_HEIGHT;
      const margin = TILE;
      const under = screenX > this.miniX - margin && screenY > this.miniY - margin;
      const alpha = under ? 0.2 : 1;
      this.miniStatic.setAlpha(alpha);
      this.miniDynamic.setAlpha(alpha);
    }
  }

  createCard(x, y, color, i) {
    const bg = this.add.rectangle(x, y, CARD_W, 64, 0x1e293b).setOrigin(0).setStrokeStyle(2, color.hex);
    const head = this.add.image(x + 20, y + 32, `player${i}`, 0).setScale(2);
    const name = this.add.text(x + 40, y + 4, '', textStyle(13, color.main, { strokeThickness: 2 }));
    const score = this.add.text(x + CARD_W - 5, y + 5, '', textStyle(12, '#ffffff', { strokeThickness: 2 })).setOrigin(1, 0);
    const hearts = [];
    for (let k = 0; k < MAX_HEARTS; k++) {
      hearts.push(this.add.image(x + 48 + k * 18, y + 32, 'heart').setScale(2));
    }
    const icons = [];
    const stats = ['BOMB_RANGE', 'EXTRA_BOMB', 'SPEED'].map((type, k) => {
      icons.push(this.add.image(x + 46 + k * 30, y + 52, `pu-${type}`).setScale(1));
      return this.add.text(x + 56 + k * 30, y + 45, '', textStyle(12, '#ffffff', { strokeThickness: 2 }));
    });
    const kick = this.add.image(x + 138, y + 52, 'pu-KICK').setScale(1);   // ¿puede patear?
    const skull = this.add.image(x + 9, y + 10, 'skull').setScale(1.3);    // ¿está maldito? (esquina)
    const armor = this.add.text(x + 20, y + 54, '', textStyle(11, '#93c5fd', { strokeThickness: 2 })).setOrigin(0.5);
    const status = this.add.text(x + 95, y + 32, '', textStyle(13, '#fca5a5')).setOrigin(0.5);
    return { elements: [bg, head, name, score, ...hearts, ...stats, ...icons, armor], bg, head, name, score,
             hearts, stats, kick, skull, armor, status, x, index: i };
  }

  render(s) {
    if (this.closing) return; // la escena se está cerrando
    this.banner.setText('');

    // Minimapa: solo redibujamos muros/bloques si el mapa cambió
    if (s.map) {
      const key = s.map.join('');
      if (key !== this.mapKey) {
        this.mapKey = key;
        this.mapRows = s.map;
        this.drawMinimapStatic(s.map);
      }
    }

    // Aviso de inicio: en el primer estado de la partida que veamos (no dependemos del
    // evento GAME_STARTED, que podría llegar justo antes de que esta escena exista)
    if (!this.startAnnounced && s.status === 'RUNNING') {
      this.startAnnounced = true;
      if (s.tick < 60) this.announce('¡Hay un TANQUE escondido\ncerca del centro!', '#fbbf24', 34);
    }
    (s.events || []).forEach((ev) => {
      if (ev.type === 'SUDDEN_DEATH') {
        sfx.alarm();
        this.announce('¡MUERTE SÚBITA!', '#ef4444', 54);
      }
    });
    if (s.suddenDeath && !this.suddenDeath) {
      this.suddenDeath = true;
      this.dangerFrame.setVisible(true);
      this.tweens.add({ targets: this.dangerFrame, alpha: 0.35, duration: 450, yoyo: true, repeat: -1 });
    }

    this.cards.forEach((card, i) => {
      const p = s.players[i];
      if (!p) {
        // hueco vacío
        card.elements.forEach((el) => el.setAlpha(0.15));
        card.name.setText('—');
        card.score.setText('');
        card.stats.forEach((t) => t.setText(''));
        card.status.setText('');
        card.kick.setAlpha(0.15);
        card.skull.setVisible(false);
        card.armor.setText('');
        return;
      }
      const dimmed = !p.alive || !p.connected;
      card.elements.forEach((el) => el.setAlpha(dimmed ? 0.35 : 1));
      card.name.setText(p.id === net.playerId ? `${p.name} (tú)` : p.name);
      card.score.setText(`${p.score}`);
      card.hearts.forEach((h, k) => {
        h.setVisible(p.alive && k < Math.max(3, p.lives));
        h.setTexture(k < p.lives ? 'heart' : 'heart-empty');
      });
      // Alcance, bombas simultáneas y nivel de velocidad
      card.stats[0].setText(`${p.bombRange}`);
      card.stats[1].setText(`${p.maxBombs}`);
      card.stats[2].setText(`${p.speedLevel}`);
      card.status.setText(!p.alive ? 'ELIMINADO' : !p.connected ? 'desconectado' : '');

      // Patada: icono encendido o apagado. Calavera si está maldito.
      card.kick.setAlpha(p.canKick ? (dimmed ? 0.35 : 1) : 0.15);
      card.skull.setVisible(!!p.curse && p.alive);
      // En tanque: la cabeza se cambia por el tanque y se ve el blindaje que le queda
      const inTank = p.tankArmor > 0;
      card.head.setTexture(inTank ? `tank${card.index}` : `player${card.index}`, 0);
      card.armor.setText(inTank ? `■${p.tankArmor} ${p.tankSeconds}s` : '');
    });

    const min = Math.floor(s.timeLeft / 60);
    const sec = String(s.timeLeft % 60).padStart(2, '0');
    this.timeText.setText(`${min}:${sec}`).setColor(s.timeLeft <= 30 ? '#f87171' : '#ffffff');
    this.timeLabel.setText(s.suddenDeath ? '¡SÚBITA!' : 'TIEMPO').setColor(s.suddenDeath ? '#f87171' : '#94a3b8');
  }

  update(time) {
    this.latencyText.setText(`${net.latency} ms${sfx.muted ? ' · mudo' : ''}`);
    this.drawMinimapDynamic(time);
  }
}
