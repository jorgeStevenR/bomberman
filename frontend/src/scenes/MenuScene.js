import { SCALE } from '../config.js';
import { net } from '../net.js';
import { drawBackdrop, textStyle } from '../ui.js';

/**
 * Pantalla de inicio: nombre + crear sala o unirse con un código.
 * Los campos de texto son HTML normal colocado encima del canvas (this.add.dom),
 * porque Phaser no trae cajas de texto propias.
 *
 * (En la fase 4 aquí añadiremos login y la API de matchmaking.)
 */
export class MenuScene extends Phaser.Scene {
  constructor() {
    super('Menu');
  }

  /** data.message: texto a mostrar (p. ej. "La sala está llena"). */
  create(data) {
    const { width } = this.scale;
    drawBackdrop(this);

    // Título con un personaje y una bomba animados
    this.add.text(width / 2, 110, 'BOMBERMAN', textStyle(64, '#fbbf24', { strokeThickness: 10 })).setOrigin(0.5);
    this.add.text(width / 2, 165, 'multijugador en red local', textStyle(20, '#e2e8f0')).setOrigin(0.5);
    const hero = this.add.sprite(width / 2 - 60, 235, 'player0', 0).setScale(SCALE).play('player0-walk-down');
    const bomb = this.add.sprite(width / 2 + 60, 235, 'bomb', 0).setScale(SCALE).play('bomb-fuse');
    this.tweens.add({ targets: [hero, bomb], y: 225, duration: 600, yoyo: true, repeat: -1, ease: 'Sine.inOut' });

    const lastName = sessionStorage.getItem('bm-last-name') || localStorage.getItem('bm-name') || '';
    const form = this.add.dom(width / 2, 420).createFromHTML(`
      <div class="menu-form">
        <input id="name" maxlength="16" placeholder="Tu nombre" value="${escapeHtml(lastName)}">
        <button id="create">Crear sala nueva</button>
        <div class="sep">— o únete con un código —</div>
        <div class="row">
          <input id="room" maxlength="8" placeholder="CÓDIGO">
          <button id="join" class="secondary">Unirse</button>
        </div>
        <p class="msg" id="msg"></p>
      </div>`);

    const $ = (id) => form.node.querySelector('#' + id);
    const msg = $('msg');
    if (data && data.message) msg.textContent = data.message;

    const getName = () => {
      const name = $('name').value.trim();
      if (!name) {
        msg.textContent = 'Escribe tu nombre';
        $('name').focus();
        return null;
      }
      localStorage.setItem('bm-name', name);
      return name;
    };

    $('create').addEventListener('click', () => {
      const name = getName();
      if (name) this.join(randomRoomCode(), name, msg);
    });
    const joinWithCode = () => {
      const name = getName();
      const room = $('room').value.trim().toUpperCase();
      if (!name) return;
      if (!/^[A-Z0-9]{4,8}$/.test(room)) {
        msg.textContent = 'El código tiene de 4 a 8 letras o números';
        return;
      }
      this.join(room, name, msg);
    };
    $('join').addEventListener('click', joinWithCode);
    $('room').addEventListener('keydown', (e) => { if (e.key === 'Enter') joinWithCode(); });

    this.add.text(width / 2, 650, 'WASD: mover · Espacio: bomba/disparar · E: pico · 1-4: emotes · M: mudo',
      textStyle(16, '#cbd5e1')).setOrigin(0.5);
  }

  join(room, name, msgEl) {
    msgEl.style.color = '#a7f3d0';
    msgEl.textContent = `Conectando a la sala ${room}...`;
    net.connect(room, name);
    // main.js cambia a la sala de espera cuando llega el LOBBY
  }
}

/** Código de sala aleatorio de 4 caracteres (sin letras confusas como O/0 o I/1). */
function randomRoomCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 4; i++) code += chars[Math.floor(Math.random() * chars.length)];
  return code;
}

function escapeHtml(text) {
  return text.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
