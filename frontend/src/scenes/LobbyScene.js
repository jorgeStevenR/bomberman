import { PLAYER_COLORS, SCALE } from '../config.js';
import { net } from '../net.js';
import { drawBackdrop, makeButton, textStyle } from '../ui.js';

/** Sala de espera: código para compartir, jugadores conectados y botón de empezar. */
export class LobbyScene extends Phaser.Scene {
  constructor() {
    super('Lobby');
  }

  create() {
    const { width } = this.scale;
    drawBackdrop(this);

    this.add.text(width / 2, 70, 'SALA DE ESPERA', textStyle(40, '#fbbf24')).setOrigin(0.5);
    this.add.text(width / 2, 125, 'Código de la sala:', textStyle(20, '#e2e8f0')).setOrigin(0.5);
    this.codeText = this.add.text(width / 2, 175, net.room, textStyle(56, '#ffffff', { strokeThickness: 8 }))
      .setOrigin(0.5);
    this.add.text(width / 2, 222, 'Compártelo con tus amigos para que se unan', textStyle(16, '#cbd5e1'))
      .setOrigin(0.5);

    // 4 huecos de jugador
    this.slots = [];
    for (let i = 0; i < 4; i++) {
      const x = width / 2 - 255 + i * 170;
      const y = 340;
      const panel = this.add.rectangle(x, y, 150, 170, 0x0f172a, 0.85).setStrokeStyle(3, PLAYER_COLORS[i].hex);
      const sprite = this.add.sprite(x, y - 25, `player${i}`, 0).setScale(SCALE);
      const name = this.add.text(x, y + 45, '', textStyle(18)).setOrigin(0.5);
      const tag = this.add.text(x, y + 70, '', textStyle(14, '#fbbf24')).setOrigin(0.5);
      this.slots.push({ panel, sprite, name, tag, index: i });
    }

    this.info = this.add.text(width / 2, 480, '', textStyle(18, '#e2e8f0')).setOrigin(0.5);
    this.startButton = makeButton(this, width / 2, 545, '¡Empezar!', () => net.send({ type: 'START' }), { width: 260 });
    makeButton(this, width / 2, 620, 'Salir', () => {
      net.leave();
      this.scene.start('Menu');
    }, { width: 160, color: 0x94a3b8 });

    net.on('lobby', this.render, this);
    this.closing = false;
    this.events.once('shutdown', () => {
      this.closing = true;
      net.off('lobby', this.render, this);
    });
    if (net.lastLobby) this.render(net.lastLobby);
  }

  render(lobby) {
    // El mismo mensaje que cierra esta escena (partida empezada) puede llegar aquí
    // justo después: si la escena ya no está activa, no tocamos sus sprites.
    if (this.closing) return;
    this.codeText.setText(lobby.room);
    this.slots.forEach((slot) => {
      const p = lobby.players[slot.index];
      if (p) {
        slot.name.setText(p.name + (p.id === net.playerId ? ' (tú)' : ''));
        slot.tag.setText(p.host ? '★ anfitrión' : p.connected ? '' : 'desconectado');
        slot.sprite.setAlpha(p.connected ? 1 : 0.4).play(`player${slot.index}-walk-down`, true);
        slot.panel.setFillStyle(0x0f172a, 0.85);
      } else {
        slot.name.setText('libre');
        slot.tag.setText('');
        slot.sprite.stop().setFrame(0).setAlpha(0.15);
        slot.panel.setFillStyle(0x0f172a, 0.5);
      }
    });

    const n = lobby.players.length;
    if (net.isHost()) {
      this.info.setText(n === 1 ? 'Puedes empezar solo contra los enemigos o esperar a más jugadores'
        : `${n} jugadores listos. ¡Cuando quieras!`);
      this.startButton.setVisible(true).setEnabled(true);
    } else {
      this.info.setText('Esperando a que el anfitrión empiece la partida...');
      this.startButton.setVisible(false);
    }
  }
}
