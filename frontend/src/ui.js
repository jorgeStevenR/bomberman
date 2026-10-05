import { FONT } from './config.js';
import { sfx } from './sound.js';

/** Estilo de texto con borde oscuro (se lee bien sobre cualquier fondo). */
export function textStyle(size, color = '#ffffff', extra = {}) {
  return {
    fontFamily: FONT, fontSize: `${size}px`, fontStyle: 'bold', color,
    stroke: '#0b1020', strokeThickness: Math.max(3, Math.round(size / 6)), ...extra,
  };
}

/**
 * Botón sencillo: rectángulo redondeado + texto, con efecto al pasar el ratón.
 * Devuelve un objeto con setEnabled(bool) y setLabel(texto).
 */
export function makeButton(scene, x, y, label, onClick, { width = 220, color = 0xf59e0b, textColor = '#1f2937' } = {}) {
  const height = 52;
  const bg = scene.add.graphics();
  const text = scene.add.text(x, y, label, textStyle(22, textColor, { strokeThickness: 0 })).setOrigin(0.5);
  const zone = scene.add.zone(x, y, width, height).setInteractive({ useHandCursor: true });
  let enabled = true;
  let hover = false;

  const draw = () => {
    bg.clear();
    const fill = !enabled ? 0x475569 : hover ? Phaser.Display.Color.ValueToColor(color).lighten(15).color : color;
    bg.fillStyle(0x0b1020, 1).fillRoundedRect(x - width / 2, y - height / 2 + 4, width, height, 10); // sombra
    bg.fillStyle(fill, 1).fillRoundedRect(x - width / 2, y - height / 2, width, height, 10);
    bg.lineStyle(3, 0x0b1020, 1).strokeRoundedRect(x - width / 2, y - height / 2, width, height, 10);
  };
  draw();

  zone.on('pointerover', () => { hover = true; draw(); });
  zone.on('pointerout', () => { hover = false; draw(); });
  zone.on('pointerdown', () => {
    if (!enabled) return;
    sfx.click();
    onClick();
  });

  return {
    setEnabled(value) { enabled = value; text.setAlpha(value ? 1 : 0.6); draw(); return this; },
    setLabel(value) { text.setText(value); return this; },
    setVisible(value) { bg.setVisible(value); text.setVisible(value); zone.setVisible(value); zone.input.enabled = value; return this; },
  };
}

/** Fondo decorativo: suelo de hierba con muros, igual que el mapa. */
export function drawBackdrop(scene) {
  const { width, height } = scene.scale;
  for (let y = 0; y < height; y += 48) {
    for (let x = 0; x < width; x += 48) {
      const border = x === 0 || y === 0 || x + 48 >= width || y + 48 >= height;
      scene.add.image(x, y, border ? 'wall' : 'floor').setOrigin(0).setScale(3);
    }
  }
  scene.add.rectangle(0, 0, width, height, 0x0b1020, 0.55).setOrigin(0);
}
