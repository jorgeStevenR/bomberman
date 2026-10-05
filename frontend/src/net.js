import { wsUrl } from './config.js';

/**
 * Conexión con el game-server. Es un "emisor de eventos": las escenas se
 * suscriben con net.on('state', fn) y reciben los mensajes ya convertidos de JSON.
 *
 * Eventos que emite:
 *   welcome, lobby, state, gameover, emote, error  → mensajes del servidor
 *   fatal (mensaje)       → error que impide seguir (sala llena, partida empezada...)
 *   reconnecting          → se cortó la conexión y estamos reintentando
 *   lost (mensaje)        → no se pudo reconectar
 *   latency (ms)
 *
 * También guarda el último mensaje de cada tipo (lastLobby, lastState...) para
 * que una escena que se abre tarde pueda pintarse de inmediato.
 */
const FATAL_ERRORS = ['ROOM_FULL', 'GAME_IN_PROGRESS'];
const RECONNECT_WINDOW_MS = 15000; // igual que la gracia del servidor

class Network extends Phaser.Events.EventEmitter {
  constructor() {
    super();
    this.ws = null;
    this.room = null;
    this.name = null;
    this.playerId = null;
    this.tickRate = 20;
    this.latency = 0;
    this.lastLobby = null;
    this.lastState = null;
    this.lastGameOver = null;
    this.intentionalClose = false;
    this.reconnectUntil = 0;
    setInterval(() => this.send({ type: 'PING', t: Date.now() }), 3000);
  }

  /** Entra en una sala. Si ya estuvimos en ella (recargar la página), se reconecta con el token. */
  connect(room, name) {
    this.room = room.toUpperCase();
    this.name = name;
    this.playerId = null;
    this.lastLobby = this.lastState = this.lastGameOver = null;
    this.intentionalClose = false;
    this.reconnectUntil = 0;
    sessionStorage.setItem('bm-last-room', this.room);
    sessionStorage.setItem('bm-last-name', this.name);
    this._open();
  }

  _open() {
    const ws = new WebSocket(wsUrl(this.room));
    this.ws = ws;

    ws.onopen = () => {
      this.reconnectUntil = 0;
      this.send({ type: 'JOIN', name: this.name, reconnectToken: sessionStorage.getItem(this._tokenKey()) });
    };

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      switch (msg.type) {
        case 'WELCOME':
          this.playerId = msg.playerId;
          this.tickRate = msg.tickRate;
          sessionStorage.setItem(this._tokenKey(), msg.reconnectToken);
          this.emit('welcome', msg);
          break;
        case 'LOBBY':
          this.lastLobby = msg;
          this.emit('lobby', msg);
          break;
        case 'STATE':
          this.lastState = msg;
          this.emit('state', msg);
          break;
        case 'GAME_OVER':
          this.lastGameOver = msg;
          this.emit('gameover', msg);
          break;
        case 'EMOTE':
          this.emit('emote', msg);
          break;
        case 'PONG':
          this.latency = Date.now() - msg.t;
          this.emit('latency', this.latency);
          break;
        case 'ERROR':
          if (FATAL_ERRORS.includes(msg.code)) {
            this.intentionalClose = true;
            sessionStorage.removeItem(this._tokenKey());
            this.emit('fatal', msg.message);
          } else {
            console.warn('Error del servidor:', msg.code, msg.message);
            this.emit('error', msg);
          }
          break;
      }
    };

    ws.onclose = () => {
      if (this.ws !== ws || this.intentionalClose) return;
      // Nunca llegamos a entrar: el servidor no está o la sala no es válida
      if (!this.playerId) {
        this.emit('lost', 'No se pudo conectar con el servidor');
        return;
      }
      // Conexión perdida a mitad: reintentamos cada segundo durante 15 s
      if (!this.reconnectUntil) this.reconnectUntil = Date.now() + RECONNECT_WINDOW_MS;
      if (Date.now() < this.reconnectUntil) {
        this.emit('reconnecting');
        setTimeout(() => this._open(), 1000);
      } else {
        this.emit('lost', 'Se perdió la conexión con el servidor');
      }
    };
  }

  /** Salir de la sala a propósito: olvidamos el token para no volver a entrar solos. */
  leave() {
    this.intentionalClose = true;
    if (this.room) sessionStorage.removeItem(this._tokenKey());
    sessionStorage.removeItem('bm-last-room');
    if (this.ws) this.ws.close();
    this.ws = null;
    this.playerId = null;
  }

  send(obj) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(obj));
    }
  }

  isHost() {
    return this.lastLobby && this.lastLobby.hostId === this.playerId;
  }

  /** Índice de color de un jugador: su posición en la lista que manda el servidor. */
  colorIndex(playerId) {
    const list = (this.lastState && this.lastState.players) || (this.lastLobby && this.lastLobby.players) || [];
    const i = list.findIndex((p) => p.id === playerId);
    return i < 0 ? 0 : i;
  }

  _tokenKey() {
    return 'bm-token-' + this.room;
  }
}

/** Una única conexión para todo el juego. */
export const net = new Network();
