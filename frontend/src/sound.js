/**
 * Efectos de sonido SINTETIZADOS con la Web Audio API del navegador.
 * No usamos archivos de audio: así no hay problemas de licencias y no hay
 * nada que descargar. Cada sonido es un oscilador (tono) o ruido con una
 * envolvente de volumen, como en las consolas antiguas.
 *
 * Los navegadores no dejan sonar audio hasta que el usuario hace clic o pulsa
 * una tecla; por eso unlock() se llama en el primer clic/tecla.
 */
class Sfx {
  constructor() {
    this.ctx = null;
    this.muted = localStorage.getItem('bm-muted') === '1';
  }

  unlock() {
    if (!this.ctx) this.ctx = new AudioContext();
    if (this.ctx.state === 'suspended') this.ctx.resume();
  }

  toggleMute() {
    this.muted = !this.muted;
    localStorage.setItem('bm-muted', this.muted ? '1' : '0');
    return this.muted;
  }

  _ready() {
    return this.ctx && !this.muted;
  }

  /** Un tono: frecuencia inicial → final (slideTo), con caída de volumen. */
  tone(freq, duration, { type = 'square', volume = 0.12, slideTo = null, delay = 0 } = {}) {
    if (!this._ready()) return;
    const t0 = this.ctx.currentTime + delay;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t0);
    if (slideTo) osc.frequency.exponentialRampToValueAtTime(slideTo, t0 + duration);
    gain.gain.setValueAtTime(volume, t0);
    gain.gain.exponentialRampToValueAtTime(0.001, t0 + duration);
    osc.connect(gain).connect(this.ctx.destination);
    osc.start(t0);
    osc.stop(t0 + duration);
  }

  /** Ruido blanco filtrado: la base de una explosión. */
  noise(duration, { volume = 0.4, filter = 1000 } = {}) {
    if (!this._ready()) return;
    const t0 = this.ctx.currentTime;
    const length = Math.floor(this.ctx.sampleRate * duration);
    const buffer = this.ctx.createBuffer(1, length, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < length; i++) data[i] = Math.random() * 2 - 1;
    const src = this.ctx.createBufferSource();
    src.buffer = buffer;
    const lowpass = this.ctx.createBiquadFilter();
    lowpass.type = 'lowpass';
    lowpass.frequency.setValueAtTime(filter, t0);
    lowpass.frequency.exponentialRampToValueAtTime(80, t0 + duration);
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(volume, t0);
    gain.gain.exponentialRampToValueAtTime(0.001, t0 + duration);
    src.connect(lowpass).connect(gain).connect(this.ctx.destination);
    src.start(t0);
  }

  placeBomb() { this.tone(520, 0.09, { slideTo: 300, volume: 0.1 }); }
  explosion() { this.noise(0.7, { volume: 0.5, filter: 1200 }); this.tone(110, 0.45, { type: 'sine', volume: 0.35, slideTo: 35 }); }
  powerUp() { [523, 659, 784, 1047].forEach((f, i) => this.tone(f, 0.09, { delay: i * 0.06, volume: 0.09 })); }
  hit() { this.tone(700, 0.35, { type: 'sawtooth', slideTo: 90, volume: 0.12 }); }
  enemyKilled() { this.tone(260, 0.18, { type: 'triangle', slideTo: 900, volume: 0.15 }); }
  click() { this.tone(880, 0.05, { volume: 0.07 }); }
  kick() { this.tone(180, 0.08, { type: 'square', slideTo: 90, volume: 0.15 }); this.noise(0.08, { volume: 0.15, filter: 600 }); }
  curse() { [300, 280, 300, 260, 220].forEach((f, i) => this.tone(f, 0.12, { type: 'sawtooth', delay: i * 0.08, volume: 0.07 })); }
  tankOn() { [262, 330, 392, 523, 659].forEach((f, i) => this.tone(f, 0.1, { type: 'square', delay: i * 0.05, volume: 0.09 })); }
  shoot() { this.noise(0.15, { volume: 0.3, filter: 3000 }); this.tone(400, 0.12, { type: 'square', slideTo: 120, volume: 0.1 }); }
  metalHit() { this.tone(1200, 0.15, { type: 'square', slideTo: 500, volume: 0.1 }); this.tone(90, 0.2, { type: 'sine', volume: 0.25 }); }
  wallDrop() { this.tone(70, 0.12, { type: 'sine', volume: 0.3, slideTo: 40 }); }
  alarm() { [880, 660, 880, 660, 880, 660].forEach((f, i) => this.tone(f, 0.18, { type: 'square', delay: i * 0.2, volume: 0.08 })); }
  pop() { this.tone(660, 0.07, { type: 'sine', slideTo: 990, volume: 0.1 }); }

  // ---- Modo pistolas ----
  /** Un sonido distinto por arma. */
  gunshot(weapon) {
    switch (weapon) {
      case 'SMG': this.noise(0.05, { volume: 0.12, filter: 4000 }); this.tone(900, 0.04, { slideTo: 400, volume: 0.04 }); break;
      case 'SHOTGUN': this.noise(0.25, { volume: 0.35, filter: 1500 }); this.tone(140, 0.2, { type: 'sine', slideTo: 60, volume: 0.2 }); break;
      case 'RIFLE': this.tone(1600, 0.18, { type: 'sawtooth', slideTo: 200, volume: 0.08 }); this.noise(0.08, { volume: 0.15, filter: 5000 }); break;
      default: this.noise(0.08, { volume: 0.18, filter: 2500 }); this.tone(500, 0.06, { slideTo: 200, volume: 0.06 });
    }
  }
  swing() { this.noise(0.06, { volume: 0.06, filter: 3000 }); }
  pickaxe() { this.tone(1300, 0.06, { type: 'square', slideTo: 900, volume: 0.07 }); this.noise(0.07, { volume: 0.12, filter: 2000 }); }
  hitSoft() { this.tone(300, 0.07, { type: 'square', slideTo: 150, volume: 0.06 }); }
  heal() { [440, 554, 659].forEach((f, i) => this.tone(f, 0.12, { type: 'sine', delay: i * 0.07, volume: 0.1 })); }
  evolve() { [392, 523, 659, 784, 1047].forEach((f, i) => this.tone(f, 0.14, { type: 'square', delay: i * 0.08, volume: 0.09 })); }
  win() { [523, 659, 784, 659, 784, 1047].forEach((f, i) => this.tone(f, 0.16, { delay: i * 0.13, volume: 0.1 })); }
  lose() { [392, 349, 311, 262].forEach((f, i) => this.tone(f, 0.28, { type: 'triangle', delay: i * 0.22, volume: 0.15 })); }
}

export const sfx = new Sfx();

// Desbloquear el audio con la primera interacción del usuario
['pointerdown', 'keydown'].forEach((ev) => window.addEventListener(ev, () => sfx.unlock()));
