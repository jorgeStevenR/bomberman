package com.bomberman.gameserver.protocol;

/**
 * Cualquier mensaje que envía el navegador. Todos tienen "type"; el resto de
 * campos solo se usan en algunos tipos (los que no vienen quedan en null).
 *
 * Ejemplos:
 *   {"type":"JOIN","name":"Ana"}
 *   {"type":"JOIN","name":"Ana","reconnectToken":"4f1c..."}   (reconexión)
 *   {"type":"START"}
 *   {"type":"MOVE","dir":"UP"}
 *   {"type":"STOP"}
 *   {"type":"PLACE_BOMB"}
 *   {"type":"PING","t":1712345678901}
 *   {"type":"EMOTE","emote":2}
 *   {"type":"CHEAT","powerUp":"TANK"}   (solo si el servidor tiene los trucos activados)
 *   {"type":"SET_MODE","mode":"GUNS"}   (anfitrión, en la sala de espera)
 *   {"type":"FIRE","on":true}           (modo pistolas: mantener / soltar el gatillo)
 *   {"type":"MINE"}                     (modo pistolas: golpe de pico)
 *
 * Fíjate en que el cliente NUNCA envía su posición: solo intenciones.
 */
public record ClientMessage(
        String type,
        String name,
        String dir,
        String reconnectToken,
        Long t,
        Integer emote,
        String powerUp,
        String mode,
        Boolean on) {
}
