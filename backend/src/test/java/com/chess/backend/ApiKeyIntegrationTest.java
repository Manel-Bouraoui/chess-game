package com.chess.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.chess.backend.service.ChessEngineService;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.api-key=integration-test-api-key")
@ActiveProfiles("test")
class ApiKeyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private ChessEngineService chessEngineService;

    @Test
    void apiRoutesRequireKeyWhenConfigured() {
        Mockito.when(chessEngineService.isEngineAvailable()).thenReturn(false);

        String base = "http://localhost:" + port;

        ResponseEntity<Map> denied = restTemplate.getForEntity(base + "/api/games/active", Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "integration-test-api-key");
        ResponseEntity<Map> ok = restTemplate.exchange(
                base + "/api/games/active",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> healthOk = restTemplate.getForEntity(base + "/api/health", Map.class);
        assertThat(healthOk.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
