import { net } from '../net.js';
import { createAnimations, createTextures } from '../textures.js';

/** Primera escena: genera todos los sprites y animaciones, y pasa al menú. */
export class BootScene extends Phaser.Scene {
  constructor() {
    super('Boot');
  }

  create() {
    createTextures(this);
    createAnimations(this);
    this.scene.start('Menu');

    // ¿Se recargó la página estando en una sala? Volvemos a entrar con el token
    // guardado: el servidor nos devuelve el mismo jugador (reconexión).
    const room = sessionStorage.getItem('bm-last-room');
    const name = sessionStorage.getItem('bm-last-name');
    if (room && name && sessionStorage.getItem('bm-token-' + room)) {
      net.connect(room, name);
    }
  }
}
