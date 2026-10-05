/**
 * INTERPOLACIÓN: cómo dibujar a 60 FPS con datos que llegan a 20 Hz.
 *
 * El servidor envía un STATE cada 50 ms, pero el navegador dibuja un fotograma
 * cada ~16 ms. Si pintáramos al personaje SOLO en la posición del último STATE,
 * se vería "a saltos": quieto, quieto, ¡salto de una casilla!, quieto...
 *
 * Solución: cada sprite recuerda DE DÓNDE viene y A DÓNDE va, y en cada fotograma
 * calculamos un punto intermedio según el tiempo transcurrido:
 *
 *     t = (ahora - inicio) / duración          (0 = al salir, 1 = al llegar)
 *     posición = desde + (hasta - desde) * t
 *
 *     STATE:   casilla 3 ─────────────────────────▶ casilla 4
 *     dibujo:  3.00  3.05  3.10  ...  3.95  4.00   (un valor por fotograma)
 *
 * ¿Cuánto dura el viaje? En nuestro juego el movimiento es por casillas y el
 * servidor nos dice cada cuántos ticks puede dar un paso cada jugador
 * (moveDelayTicks). Si el jugador da un paso cada 6 ticks (300 ms), hacemos que
 * el deslizamiento dure exactamente 300 ms: justo cuando el sprite llega a la
 * casilla, el servidor ya le deja dar el siguiente paso, así que mientras se
 * mantenga la tecla pulsada el movimiento es continuo y a velocidad constante.
 *
 * (En juegos de movimiento libre, como un shooter, se usa otra técnica llamada
 * "snapshot interpolation": se guardan varios STATE y se dibuja el mundo ~100 ms
 * en el pasado, interpolando entre los dos STATE que rodean ese instante. La idea
 * es la misma: dibujar puntos intermedios entre posiciones que da el servidor.)
 */
export class GridInterpolator {
  constructor(x, y) {
    this.fromX = this.toX = x;
    this.fromY = this.toY = y;
    this.start = 0;
    this.duration = 0;
  }

  /** Llega una posición nueva del servidor. */
  setTarget(x, y, durationMs, now) {
    if (x === this.toX && y === this.toY) return;

    const current = this.position(now);
    const distance = Math.abs(x - current.x) + Math.abs(y - current.y);

    if (distance > 1.5) {
      // Salto grande (reaparecer en la esquina tras morir): teletransporte, sin deslizar
      this.fromX = this.toX = x;
      this.fromY = this.toY = y;
      this.duration = 0;
      return;
    }
    // Salimos desde donde se está DIBUJANDO ahora (no desde la casilla anterior),
    // así nunca hay saltos aunque un mensaje llegue un poco antes o después.
    this.fromX = current.x;
    this.fromY = current.y;
    this.toX = x;
    this.toY = y;
    this.start = now;
    this.duration = durationMs;
  }

  /** Posición a dibujar en este fotograma (en casillas, con decimales). */
  position(now) {
    if (this.duration <= 0) {
      return { x: this.toX, y: this.toY, moving: false };
    }
    const t = Math.min(1, Math.max(0, (now - this.start) / this.duration));
    return {
      x: this.fromX + (this.toX - this.fromX) * t,
      y: this.fromY + (this.toY - this.fromY) * t,
      moving: t < 1,
    };
  }
}
