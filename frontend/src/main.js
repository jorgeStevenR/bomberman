import { GAME_HEIGHT, GAME_WIDTH } from './config.js';
import { net } from './net.js';
import { BootScene } from './scenes/BootScene.js';
import { GameOverScene } from './scenes/GameOverScene.js';
import { GameScene } from './scenes/GameScene.js';
import { HudScene } from './scenes/HudScene.js';
import { LobbyScene } from './scenes/LobbyScene.js';
import { MenuScene } from './scenes/MenuScene.js';

/**
 * Punto de entrada del frontend.
 *
 * Escenas:  Boot → Menu → Lobby → Game (+ Hud encima) → GameOver (encima) → Game...
 */
const game = new Phaser.Game({
  type: Phaser.AUTO,         // WebGL si está disponible, si no Canvas
  parent: 'game',
  width: GAME_WIDTH,
  height: GAME_HEIGHT,
  backgroundColor: '#0b1020',
  pixelArt: true,            // sin suavizado: los píxeles se ven nítidos al escalar
  dom: { createContainer: true }, // permite poner <input> HTML sobre el canvas
  scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
  scene: [BootScene, MenuScene, LobbyScene, GameScene, HudScene, GameOverScene],
});

// ---------------------------------------------------------------------------
// "Director": decide qué escena se ve según lo que dice el SERVIDOR.
// Las escenas no se cambian solas entre sí; así el cliente siempre muestra
// la pantalla que corresponde al estado real de la sala (también al reconectar).
// ---------------------------------------------------------------------------

const isActive = (key) => game.scene.isActive(key);

/** Detiene todas las escenas y arranca solo la indicada. */
function showOnly(key, data) {
  game.scene.getScenes(false).forEach((s) => {
    const k = s.scene.key;
    if (k !== key && k !== 'Boot' && (s.scene.isActive() || s.scene.isPaused())) game.scene.stop(k);
  });
  game.scene.start(key, data);
}

net.on('lobby', (lobby) => {
  if (lobby.status === 'RUNNING') {
    // Empieza una partida (o "volver a jugar" desde la pantalla de fin)
    if (!isActive('Game') || isActive('GameOver')) {
      // Olvidamos la partida anterior: si no, la escena nueva dibujaría el mapa viejo
      net.lastState = null;
      net.lastGameOver = null;
      showOnly('Game');
    }
  } else if (!isActive('Lobby') && !isActive('Game')) {
    showOnly('Lobby');
  }
});

net.on('state', (state) => {
  // Reconexión a una partida en curso
  if (state.status === 'RUNNING' && !isActive('Game')) showOnly('Game');
});

net.on('gameover', () => {
  if (isActive('Game')) game.scene.run('GameOver');
});

net.on('fatal', (message) => showOnly('Menu', { message }));
net.on('lost', (message) => showOnly('Menu', { message }));

// Para depurar desde la consola del navegador (F12): window.bm.net.lastState
window.bm = { game, net };
