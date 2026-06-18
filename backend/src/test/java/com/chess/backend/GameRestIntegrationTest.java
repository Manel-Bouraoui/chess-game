package com.chess.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.chess.backend.dto.AiMoveDTO;
import com.chess.backend.service.ChessEngineService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GameRestIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private ChessEngineService chessEngineService;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        Mockito.when(chessEngineService.isEngineAvailable()).thenReturn(false);
        Mockito.when(chessEngineService.generateMove(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(new AiMoveDTO());
    }

    @Test
    void createJoinWhiteMove_e2e4_updatesFen() {
        Map<String, Object> createBody = Map.of(
                "mode", "pvp",
                "playerName", "Alice");

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
                baseUrl + "/api/games/create",
                createBody,
                Map.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        String gameId = (String) createResp.getBody().get("gameId");
        assertThat(gameId).isNotBlank();

        @SuppressWarnings("unchecked")
        Map<String, Object> initial = (Map<String, Object>) createResp.getBody().get("initialGameState");
        assertThat(initial).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> white = (Map<String, Object>) initial.get("whitePlayer");
        String whiteId = (String) white.get("id");

        Map<String, Object> joinBody = Map.of("playerName", "Bob");
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> joinResp = restTemplate.postForEntity(
                baseUrl + "/api/games/" + gameId + "/join",
                joinBody,
                Map.class);

        assertThat(joinResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(joinResp.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        String blackId = (String) joinResp.getBody().get("playerId");
        assertThat(blackId).isNotBlank();

        Map<String, Object> moveBody = new HashMap<>();
        moveBody.put("playerId", whiteId);
        moveBody.put("from", "e2");
        moveBody.put("to", "e4");
        moveBody.put("timestamp", System.currentTimeMillis());

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> moveResp = restTemplate.postForEntity(
                baseUrl + "/api/games/" + gameId + "/move",
                moveBody,
                Map.class);

        assertThat(moveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(moveResp.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> game = (Map<String, Object>) moveResp.getBody().get("game");
        assertThat(game).isNotNull();
        // FEN board ranks use piece placement codes; e2-e4 appears as "4P3" on rank 4, not literal "e4".
        assertThat(game.get("fen").toString()).contains("4P3");

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> activeResp = restTemplate.exchange(
                baseUrl + "/api/games/active",
                HttpMethod.GET,
                null,
                Map.class);

        assertThat(activeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeResp.getBody()).isNotNull();
    }

    @Test
    void healthEndpointsReachable() {
        ResponseEntity<Map> health = restTemplate.getForEntity(baseUrl + "/api/health", Map.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> gamesHealth = restTemplate.getForEntity(baseUrl + "/api/games/health", Map.class);
        assertThat(gamesHealth.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
