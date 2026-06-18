package com.chess.backend.controller;

import com.chess.backend.dto.AiMoveDTO;
import com.chess.backend.dto.GameStateDTO;
import com.chess.backend.dto.MoveRequestDTO;
import com.chess.backend.entity.GameStateEntity;
import com.chess.backend.entity.PlayerEntity;
import com.chess.backend.service.GameService;
import com.chess.backend.service.PlayerService;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
public class GameRestController {

    private static final Logger logger = LoggerFactory.getLogger(GameRestController.class);

    private static final int MAX_PLAYER_NAME_LENGTH = 64;

    private final GameService gameService;
    private final PlayerService playerService;

    public GameRestController(GameService gameService, PlayerService playerService) {
        this.gameService = gameService;
        this.playerService = playerService;
    }

    /**
     * Create a new game
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createGame(@RequestBody Map<String, Object> request) {
        try {
            String mode = (String) request.getOrDefault("mode", "PVP");
            String playerName = sanitizePlayerName((String) request.get("playerName"));
            Integer aiDifficulty = (Integer) request.get("aiDifficulty");
            
            String whitePlayerName = playerName != null ? playerName : "Player";
            String blackPlayerName = "PVE".equalsIgnoreCase(mode) ? "AI Player" : "Black Player";

            // Create or get players
            PlayerEntity whitePlayer = playerService.getOrCreatePlayer(
                UUID.randomUUID().toString(),
                whitePlayerName,
                "w",
                false,
                null
            );

            PlayerEntity blackPlayer;
            if ("PVE".equalsIgnoreCase(mode)) {
                int difficulty = aiDifficulty != null ? aiDifficulty : 5;
                blackPlayer = playerService.getOrCreatePlayer(
                    "AI_" + UUID.randomUUID().toString(),
                    "AI Player",
                    "b",
                    true,
                    difficulty
                );
            } else {
                // For PvP, create placeholder black player
                blackPlayer = playerService.getOrCreatePlayer(
                    UUID.randomUUID().toString(),
                    "Waiting for player...",
                    "b",
                    false,
                    null
                );
            }

            // Create game
            String gameId = UUID.randomUUID().toString();
            GameStateEntity game = gameService.createGame(gameId, mode, whitePlayer, blackPlayer);
            
            // Set correct initial status
            if ("PVP".equalsIgnoreCase(mode)) {
                game.setStatus("WAITING"); // Wait for second player
                gameService.updateGame(game);
            }
            
            GameStateDTO gameDTO = gameService.toDTO(game);

            // Return format expected by frontend
            Map<String, Object> response = new HashMap<>();
            response.put("gameId", gameId);
            response.put("initialGameState", gameDTO);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error creating game", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Join an existing game (PvP)
     */
    @PostMapping("/{gameId}/join")
    public ResponseEntity<Map<String, Object>> joinGame(
            @PathVariable String gameId,
            @RequestBody Map<String, Object> request) {
        try {
            String playerName = sanitizePlayerName((String) request.get("playerName"));
            if (playerName == null || playerName.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Player name is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Get the game
            GameStateEntity game = gameService.getGame(gameId);
            if (game == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Game not found");
                return ResponseEntity.notFound().build();
            }

            // Check if game is joinable (waiting for second player)
            if (!"WAITING".equals(game.getStatus())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Game is not available for joining");
                return ResponseEntity.badRequest().body(response);
            }

            // Update black player with actual player info
            PlayerEntity blackPlayer = playerService.getOrCreatePlayer(
                UUID.randomUUID().toString(),
                playerName,
                "b",
                false,
                null
            );

            // Update game with second player
            game.setBlackPlayer(blackPlayer);
            game.setStatus("IN_PROGRESS");
            
            // Save the updated game
            gameService.updateGame(game);

            GameStateDTO gameDTO = gameService.toDTO(game);

            // Return format expected by frontend
            Map<String, Object> response = new HashMap<>();
            response.put("gameId", gameId);
            response.put("gameState", gameDTO);
            response.put("playerId", blackPlayer.getPlayerId());
            response.put("playerColor", "b");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error joining game: {}", gameId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get game state
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<Map<String, Object>> getGame(@PathVariable String gameId) {
        try {
            GameStateEntity game = gameService.getGame(gameId);
            if (game == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Game not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("game", gameService.toDTO(game));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting game: {}", gameId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Make a move (REST endpoint)
     */
    @PostMapping("/{gameId}/move")
    public ResponseEntity<Map<String, Object>> makeMove(
            @PathVariable String gameId,
            @Valid @RequestBody MoveRequestDTO moveRequest) {
        try {
            // Set gameId from path
            moveRequest.setGameId(gameId);

            // Set timestamp if not provided
            if (moveRequest.getTimestamp() == 0) {
                moveRequest.setTimestamp(System.currentTimeMillis());
            }

            // Process move
            GameStateDTO gameState = gameService.processMove(gameId, moveRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("game", gameState);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing move: game={}", gameId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Get AI move
     */
    @GetMapping("/{gameId}/ai-move")
    public ResponseEntity<Map<String, Object>> getAiMove(
            @PathVariable String gameId,
            @RequestParam(defaultValue = "5") int difficulty,
            @RequestParam(required = false) String fen) {
        try {
            AiMoveDTO aiMove = gameService.getAiMove(gameId, difficulty, fen);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("move", aiMove);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting AI move: game={}", gameId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Forfeit game
     */
    @PostMapping("/{gameId}/forfeit")
    public ResponseEntity<Map<String, Object>> forfeitGame(
            @PathVariable String gameId,
            @RequestBody Map<String, String> request) {
        try {
            String playerId = request.get("playerId");
            gameService.forfeitGame(gameId, playerId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Game forfeited");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error forfeiting game: {}", gameId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get active games
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveGames() {
        try {
            List<GameStateEntity> games = gameService.getActiveGames();
            List<GameStateDTO> gameDTOs = games.stream()
                .map(gameService::toDTO)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("games", gameDTOs);
            response.put("count", gameDTOs.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting active games", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Compatibility alias for listing active games.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listGames() {
        return getActiveGames();
    }

    /**
     * Compatibility health route used by some clients.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "chess-backend"
        ));
    }

    private static String sanitizePlayerName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_PLAYER_NAME_LENGTH) {
            return trimmed.substring(0, MAX_PLAYER_NAME_LENGTH);
        }
        return trimmed;
    }
}
