package com.bomberman.gameserver.ws;

/**
 * Limitador de mensajes por "ventana fija": como máximo N mensajes por segundo.
 * Si un cliente (o un tramposo con un script) envía más, los sobrantes se descartan.
 *
 * Un jugador normal envía muy pocos mensajes: MOVE al pulsar una tecla, STOP al
 * soltarla, PLACE_BOMB y un PING cada pocos segundos. 30/s es muchísimo margen.
 *
 * Hay algoritmos más finos (token bucket), pero la ventana fija es fácil de
 * entender y suficiente aquí. Nginx añadirá otro límite delante (fase 5, "WAF").
 */
public class RateLimiter {

    private final int maxPerWindow;
    private final long windowMillis;
    private long windowStart = 0;
    private int count = 0;
    private long totalRejected = 0;

    public RateLimiter(int maxPerWindow, long windowMillis) {
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = windowMillis;
    }

    /** @return true si el mensaje se acepta; false si hay que descartarlo. */
    public synchronized boolean tryAcquire(long nowMillis) {
        if (nowMillis - windowStart >= windowMillis) {
            windowStart = nowMillis; // empieza un segundo nuevo
            count = 0;
        }
        if (count < maxPerWindow) {
            count++;
            return true;
        }
        totalRejected++;
        return false;
    }

    public synchronized long getTotalRejected() {
        return totalRejected;
    }
}
