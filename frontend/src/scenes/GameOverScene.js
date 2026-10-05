import { PLAYER_COLORS } from '../config.js';
import { net } from '../net.js';
import { sfx } from '../sound.js';
import { makeButton, textStyle } from '../ui.js';

/** Pantalla de victoria / derrota / empate, encima de la partida terminada. */
export class GameOverScene extends Phaser.Scene {
  constructor() {
    super('GameOver');
  }

  create() {
    const { width, height } = this.scale;
    const result = net.lastGameOver;
    const me = result.results.find((r) => r.id === net.playerId);

    // ¿Qué título mostrar?
    let title, color, subtitle;
    if (result.winnerId === net.playerId) {
      title = '¡VICTORIA!'; color = '#fbbf24'; subtitle = '¡Eres el mejor bombardero!';
      sfx.win();
      this.confetti();
    } else if (result.winnerId) {
      title = 'DERROTA'; color = '#f87171'; subtitle = `Gana ${result.winnerName}`;
      sfx.lose();
    } else if (result.results.length === 1 || (me && !me.alive)) {
      title = 'DERROTA'; color = '#f87171'; subtitle = 'Te has quedado sin vidas';
      sfx.lose();
    } else {
      title = 'EMPATE'; color = '#e2e8f0'; subtitle = 'Nadie gana esta vez';
      sfx.lose();
    }

    const overlay = this.add.rectangle(0, 0, width, height, 0x0b1020, 0).setOrigin(0);
    this.tweens.add({ targets: overlay, fillAlpha: 0.78, duration: 400 });

    const titleText = this.add.text(width / 2, 150, title, textStyle(72, color, { strokeThickness: 10 }))
      .setOrigin(0.5).setScale(0);
    this.tweens.add({ targets: titleText, scale: 1, duration: 500, ease: 'Back.out', delay: 150 });
    this.add.text(width / 2, 215, subtitle, textStyle(22)).setOrigin(0.5);

    // Clasificación
    this.add.text(width / 2, 270, 'CLASIFICACIÓN', textStyle(18, '#94a3b8')).setOrigin(0.5);
    result.results.forEach((r, i) => {
      const c = PLAYER_COLORS[net.colorIndex(r.id)];
      const line = `${i + 1}.  ${r.name}${r.id === net.playerId ? ' (tú)' : ''}   ${r.score} pts${r.alive ? '' : '  ✝'}`;
      const t = this.add.text(width / 2, 310 + i * 38, line, textStyle(24, c.main)).setOrigin(0.5).setAlpha(0);
      this.tweens.add({ targets: t, alpha: 1, x: { from: width / 2 - 40, to: width / 2 }, delay: 400 + i * 150, duration: 300 });
    });

    // Botones
    this.againButton = makeButton(this, width / 2, 520, 'Volver a jugar', () => net.send({ type: 'START' }), { width: 260 });
    this.waitText = this.add.text(width / 2, 520, 'Esperando a que el anfitrión empiece otra...', textStyle(18, '#cbd5e1'))
      .setOrigin(0.5);
    makeButton(this, width / 2, 600, 'Salir al menú', () => {
      net.leave();
      this.scene.stop('Game');
      this.scene.start('Menu');
    }, { width: 220, color: 0x94a3b8 });

    net.on('lobby', this.updateButtons, this);
    this.closing = false;
    this.events.once('shutdown', () => {
      this.closing = true;
      net.off('lobby', this.updateButtons, this);
    });
    this.updateButtons();
  }

  /** Solo el anfitrión puede pulsar "volver a jugar" (el anfitrión puede cambiar si alguien se va). */
  updateButtons() {
    if (this.closing) return; // la escena se está cerrando
    const host = net.isHost();
    this.againButton.setVisible(host);
    this.waitText.setVisible(!host);
  }

  confetti() {
    const emitter = this.add.particles(0, -10, 'pixel', {
      x: { min: 0, max: this.scale.width },
      speedY: { min: 80, max: 220 },
      speedX: { min: -40, max: 40 },
      scale: { min: 2, max: 4 },
      rotate: { min: 0, max: 360 },
      lifespan: 4000,
      quantity: 3,
      frequency: 40,
      tint: [0xfbbf24, 0x3b82f6, 0xef4444, 0x22c55e, 0xffffff],
    });
    this.time.delayedCall(2500, () => emitter.stop());
  }
}
