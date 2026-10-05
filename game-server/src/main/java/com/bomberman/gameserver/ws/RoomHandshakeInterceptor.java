package com.bomberman.gameserver.ws;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Se ejecuta ANTES de abrir el WebSocket (durante el "handshake" HTTP).
 *
 * Lee el código de sala de la URL: /ws/game?room=ABCD
 * El código va en la URL (y no en un mensaje posterior) a propósito: así Nginx
 * puede leerlo y mandar a todos los jugadores de la sala al MISMO servidor
 * (hash $arg_room). Es nuestra versión de las "sticky sessions" del ALB.
 */
public class RoomHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ROOM_ATTRIBUTE = "room";
    /** De 4 a 8 letras mayúsculas o números. */
    private static final Pattern ROOM_CODE = Pattern.compile("^[A-Z0-9]{4,8}$");

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String room = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("room");
        if (room == null || !ROOM_CODE.matcher(room.toUpperCase()).matches()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false; // no se abre el WebSocket
        }
        attributes.put(ROOM_ATTRIBUTE, room.toUpperCase());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nada que hacer
    }
}
