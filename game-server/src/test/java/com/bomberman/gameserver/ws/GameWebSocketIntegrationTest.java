package com.bomberman.gameserver.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Test de integración: arranca el servidor de verdad en un puerto aleatorio y
 * conecta varios clientes WebSocket, como harían los navegadores.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Cliente de prueba: guarda todo lo que recibe en una cola. */
    static class TestClient extends TextWebSocketHandler {
        final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage m) {
            inbox.add(m.getPayload());
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }
    }

    private TestClient connect(String room) throws Exception {
        TestClient client = new TestClient();
        client.session = new StandardWebSocketClient()
                .execute(client, "ws://localhost:" + port + "/ws/game?room=" + room)
                .get(5, TimeUnit.SECONDS);
        return client;
    }

    /** Espera (máx. 5 s) a recibir un mensaje del tipo indicado que cumpla la condición. */
    private JsonNode await(TestClient client, String type, Predicate<JsonNode> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            String raw = left > 0 ? client.inbox.poll(left, TimeUnit.MILLISECONDS) : null;
            if (raw == null) {
                fail("No llegó ningún mensaje " + type);
            }
            JsonNode node = mapper.readTree(raw);
            if (type.equals(node.path("type").asText()) && condition.test(node)) {
                return node;
            }
        }
    }

    private JsonNode await(TestClient client, String type) throws Exception {
        return await(client, type, n -> true);
    }

    @Test
    void dosJugadoresSeUnenEmpiezanYRecibenElMismoEstado() throws Exception {
        TestClient ana = connect("SALA1");
        ana.send("{\"type\":\"JOIN\",\"name\":\"Ana\"}");
        JsonNode welcomeAna = await(ana, "WELCOME");
        String anaId = welcomeAna.get("playerId").asText();

        TestClient beto = connect("SALA1");
        beto.send("{\"type\":\"JOIN\",\"name\":\"Beto\"}");
        await(beto, "WELCOME");

        // Ana ve en la sala de espera que son 2 y que ella es la anfitriona
        JsonNode lobby = await(ana, "LOBBY", n -> n.get("players").size() == 2);
        assertThat(lobby.get("hostId").asText()).isEqualTo(anaId);

        ana.send("{\"type\":\"START\"}");
        JsonNode stateAna = await(ana, "STATE");
        JsonNode stateBeto = await(beto, "STATE");
        assertThat(stateAna.get("status").asText()).isEqualTo("RUNNING");
        assertThat(stateAna.get("players")).hasSize(2);
        assertThat(stateBeto.get("map")).hasSize(new com.bomberman.gameserver.game.GameConfig().height);

        // Ana pone una bomba y ambos la ven
        ana.send("{\"type\":\"PLACE_BOMB\"}");
        await(ana, "STATE", n -> n.get("bombs").size() == 1);
        await(beto, "STATE", n -> n.get("bombs").size() == 1);
    }

    @Test
    void soloElAnfitrionPuedeEmpezar() throws Exception {
        TestClient ana = connect("SALA2");
        ana.send("{\"type\":\"JOIN\",\"name\":\"Ana\"}");
        await(ana, "WELCOME");
        TestClient beto = connect("SALA2");
        beto.send("{\"type\":\"JOIN\",\"name\":\"Beto\"}");
        await(beto, "WELCOME");

        beto.send("{\"type\":\"START\"}");
        JsonNode error = await(beto, "ERROR");
        assertThat(error.get("code").asText()).isEqualTo("NOT_HOST");
    }

    @Test
    void elQuintoJugadorRecibeSalaLlena() throws Exception {
        for (int i = 1; i <= 4; i++) {
            TestClient c = connect("SALA3");
            c.send("{\"type\":\"JOIN\",\"name\":\"J" + i + "\"}");
            await(c, "WELCOME");
        }
        TestClient quinto = connect("SALA3");
        quinto.send("{\"type\":\"JOIN\",\"name\":\"Quinto\"}");
        assertThat(await(quinto, "ERROR").get("code").asText()).isEqualTo("ROOM_FULL");
    }

    @Test
    void unJugadorPuedeReconectarseConSuToken() throws Exception {
        TestClient ana = connect("SALA4");
        ana.send("{\"type\":\"JOIN\",\"name\":\"Ana\"}");
        await(ana, "WELCOME");
        TestClient beto = connect("SALA4");
        beto.send("{\"type\":\"JOIN\",\"name\":\"Beto\"}");
        JsonNode welcome = await(beto, "WELCOME");
        String betoId = welcome.get("playerId").asText();
        String token = welcome.get("reconnectToken").asText();

        ana.send("{\"type\":\"START\"}");
        await(beto, "STATE");

        // Beto pierde la conexión...
        beto.session.close();
        await(ana, "STATE", n -> !playerConnected(n, betoId));

        // ...y vuelve con su token: recupera el mismo jugador
        TestClient betoOtraVez = connect("SALA4");
        betoOtraVez.send("{\"type\":\"JOIN\",\"name\":\"Beto\",\"reconnectToken\":\"" + token + "\"}");
        assertThat(await(betoOtraVez, "WELCOME").get("playerId").asText()).isEqualTo(betoId);
        await(ana, "STATE", n -> playerConnected(n, betoId));
    }

    @Test
    void unMensajeMalFormadoDevuelveError() throws Exception {
        TestClient c = connect("SALA5");
        c.send("esto no es json");
        assertThat(await(c, "ERROR").get("code").asText()).isEqualTo("BAD_MESSAGE");
        c.send("{\"type\":\"MOVE\",\"dir\":\"DIAGONAL\"}");
        assertThat(await(c, "ERROR").get("code").asText()).isEqualTo("BAD_MESSAGE");
    }

    @Test
    void unCodigoDeSalaInvalidoSeRechazaAlConectar() {
        assertThrows(ExecutionException.class, () -> connect("x!"));
    }

    @Test
    void losEmotesLleganATodaLaSala() throws Exception {
        TestClient ana = connect("SALA7");
        ana.send("{\"type\":\"JOIN\",\"name\":\"Ana\"}");
        String anaId = await(ana, "WELCOME").get("playerId").asText();
        TestClient beto = connect("SALA7");
        beto.send("{\"type\":\"JOIN\",\"name\":\"Beto\"}");
        await(beto, "WELCOME");

        ana.send("{\"type\":\"EMOTE\",\"emote\":2}");
        JsonNode emote = await(beto, "EMOTE");
        assertThat(emote.get("playerId").asText()).isEqualTo(anaId);
        assertThat(emote.get("emote").asInt()).isEqualTo(2);

        ana.send("{\"type\":\"EMOTE\",\"emote\":9}");
        assertThat(await(ana, "ERROR").get("code").asText()).isEqualTo("BAD_MESSAGE");
    }

    @Test
    void losTrucosEstanDesactivadosPorDefecto() throws Exception {
        TestClient c = connect("SALA8");
        c.send("{\"type\":\"JOIN\",\"name\":\"Tramposo\"}");
        await(c, "WELCOME");
        c.send("{\"type\":\"CHEAT\",\"powerUp\":\"TANK\"}");
        assertThat(await(c, "ERROR").get("code").asText()).isEqualTo("CHEATS_DISABLED");
    }

    @Test
    void elAnfitrionEligeElModoPistolas() throws Exception {
        TestClient ana = connect("SALA9");
        ana.send("{\"type\":\"JOIN\",\"name\":\"Ana\"}");
        await(ana, "WELCOME");
        TestClient beto = connect("SALA9");
        beto.send("{\"type\":\"JOIN\",\"name\":\"Beto\"}");
        await(beto, "WELCOME");

        beto.send("{\"type\":\"SET_MODE\",\"mode\":\"GUNS\"}");
        assertThat(await(beto, "ERROR").get("code").asText()).isEqualTo("NOT_HOST");

        ana.send("{\"type\":\"SET_MODE\",\"mode\":\"GUNS\"}");
        await(beto, "LOBBY", n -> "GUNS".equals(n.get("mode").asText()));
        ana.send("{\"type\":\"START\"}");
        JsonNode state = await(beto, "STATE");
        assertThat(state.get("mode").asText()).isEqualTo("GUNS");
        assertThat(state.get("players").get(0).get("hp").asInt()).isEqualTo(100);
    }

    @Test
    void respondeAlPing() throws Exception {
        TestClient c = connect("SALA6");
        c.send("{\"type\":\"PING\",\"t\":123}");
        assertThat(await(c, "PONG").get("t").asLong()).isEqualTo(123);
    }

    private static boolean playerConnected(JsonNode state, String playerId) {
        for (JsonNode p : state.get("players")) {
            if (p.get("id").asText().equals(playerId)) {
                return p.get("connected").asBoolean();
            }
        }
        return false;
    }
}
