/**
 * PREDICCIÓN EN EL CLIENTE (client-side prediction) para TU propio personaje.
 *
 * El problema: con un servidor autoritativo, al pulsar una tecla el mensaje viaja
 * al servidor, este te mueve, y la respuesta vuelve. Hasta entonces tu muñeco no
 * se mueve. Con 150 ms de ida y vuelta (jugando por internet) se nota MUCHO.
 *
 * La solución que usan los juegos online: el cliente NO espera. Cuando pulsas una
 * tecla, tu muñeco se mueve al instante en tu pantalla aplicando las MISMAS reglas
 * que el servidor (no atravesar muros, bloques ni bombas, misma velocidad). Mientras,
 * el servidor sigue siendo quien manda:
 *
 *   - Guardamos el historial de casillas que hemos "predicho".
 *   - Cuando llega la posición real del servidor, la buscamos en ese historial:
 *       · si está → el servidor va por detrás de nosotros pero coincide: todo bien.
 *       · si NO está → nos equivocamos (nos empujó algo que no sabíamos, nos
 *         golpearon y reaparecimos...) → nos colocamos donde dice el servidor.
 *   - Si el servidor se queda quieto mucho rato en una casilla anterior, es que
 *     rechazó un paso que nosotros dimos → también corregimos.
 *
 * Así sigue siendo imposible hacer trampa (el servidor decide), pero el juego
 * responde al instante. Solo se predice TU personaje; los demás se interpolan.
 */
const DELTA = { UP: [0, -1], DOWN: [0, 1], LEFT: [-1, 0], RIGHT: [1, 0] };

export class LocalPredictor {
  constructor(x, y) {
    this.x = x;
    this.y = y;
    this.history = [{ x, y }];
    this.nextStepAt = 0;
    this.lastServer = { x, y };
    this.lastServerChangeAt = 0;
  }

  /**
   * Cada fotograma: si hay una dirección pulsada y ya toca dar otro paso, lo damos YA.
   * @param canEnter función (x, y) → ¿se puede pisar esa casilla? (mismas reglas que el servidor)
   */
  update(now, dir, canEnter, stepMs, interp) {
    if (!dir || now < this.nextStepAt) return;
    const [dx, dy] = DELTA[dir];
    const tx = this.x + dx;
    const ty = this.y + dy;
    if (!canEnter(tx, ty)) return;
    this.x = tx;
    this.y = ty;
    this.history.push({ x: tx, y: ty });
    if (this.history.length > 30) this.history.shift();
    interp.setTarget(tx, ty, stepMs, now);
    this.nextStepAt = now + stepMs;
  }

  /** Llega la posición real del servidor: comprobamos si nuestra predicción era buena. */
  reconcile(sx, sy, now, interp, maxLagMs) {
    if (sx !== this.lastServer.x || sy !== this.lastServer.y) {
      this.lastServer = { x: sx, y: sy };
      this.lastServerChangeAt = now;
    }
    const index = this.history.findIndex((h) => h.x === sx && h.y === sy);
    if (index === -1) {
      this.snap(sx, sy, now, interp); // nos equivocamos: manda el servidor
      return;
    }
    this.history = this.history.slice(index); // pasos ya confirmados por el servidor
    const behind = this.history.length > 1;
    if (behind && now - this.lastServerChangeAt > maxLagMs) {
      this.snap(sx, sy, now, interp); // el servidor rechazó un paso que dimos
    }
  }

  snap(sx, sy, now, interp) {
    this.x = sx;
    this.y = sy;
    this.history = [{ x: sx, y: sy }];
    this.lastServerChangeAt = now;
    interp.setTarget(sx, sy, 120, now); // corrección rápida (o teletransporte si está lejos)
  }
}
