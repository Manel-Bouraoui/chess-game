package com.chess.backend.controller;

import com.chess.backend.dto.GameStateDTO;
import com.chess.backend.dto.MoveRequestDTO;
import com.chess.backend.entity.GameStateEntity;
import com.chess.backend.service.GameService;
import com.chess.backend.exception.MoveValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class GameController {

    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameController(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Receives moves from /app/game.move
     * Validates and processes the move, then broadcasts to /topic/game/{gameId}/moves
     */
    @MessageMapping("/game.move")
    public GameStateDTO processMove(
            @Payload MoveRequestDTO moveRequest,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String gameId = moveRequest.getGameId();
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("GameId missing in move request");
            throw new MoveValidationException("Game ID is required");
        }
        
        try {
            // Set timestamp if not provided
            if (moveRequest.getTimestamp() == 0) {
                moveRequest.setTimestamp(System.currentTimeMillis());
            }

            // Process move through service (includes all validation)
            GameStateDTO result = gameService.processMove(gameId, moveRequest);
            
            logger.info("WebSocket move processed: game={}, from={}, to={}", 
                gameId, moveRequest.getFrom(), moveRequest.getTo());
            
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/moves", result);
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/state", result);
            
            return result;

        } catch (MoveValidationException e) {
            logger.warn("Invalid move via WebSocket: game={}, error={}", gameId, e.getMessage());
            // Return error state
            GameStateEntity game = gameService.getGame(gameId);
            GameStateDTO errorDto = gameService.toDTO(game);
            // Could add error field to DTO if needed
            return errorDto;
        } catch (Exception e) {
            logger.error("Error processing WebSocket move: game={}", gameId, e);
            // Return current game state
            GameStateEntity game = gameService.getGame(gameId);
            return gameService.toDTO(game);
        }
    }

    /**
     * Receives game state updates (e.g. restarts, forfeit)
     * Broadcasts to /topic/game/{gameId}/state
     */
    @MessageMapping("/game/{gameId}/state")
    public GameStateDTO processState(
            @DestinationVariable String gameId, 
            @Payload GameStateDTO state) {
        
        try {
            logger.info("WebSocket state update: game={}, status={}", gameId, state.getStatus());
            
            // Get current game state
            GameStateEntity game = gameService.getGame(gameId);
            if (game == null) {
                logger.warn("Game not found for state update: {}", gameId);
                return state;
            }

            // Update game status if changed
            if (state.getStatus() != null && !state.getStatus().equals(game.getStatus())) {
                gameService.endGame(gameId, state.getStatus());
                game = gameService.getGame(gameId);
            }

            GameStateDTO result = gameService.toDTO(game);
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/state", result);
            return result;

        } catch (Exception e) {
            logger.error("Error processing WebSocket state update: game={}", gameId, e);
            return state;
        }
    }

    /**
     * Handle game state requests from frontend
     */
    @MessageMapping("/game.request-state")
    public GameStateDTO requestGameState(@Payload Map<String, String> request) {
        try {
            String gameId = request.get("gameId");
            if (gameId == null || gameId.isEmpty()) {
                logger.warn("GameId missing in state request");
                return null;
            }

            GameStateEntity game = gameService.getGame(gameId);
            if (game == null) {
                logger.warn("Game not found for state request: {}", gameId);
                return null;
            }

            logger.info("WebSocket state requested: game={}", gameId);
            GameStateDTO result = gameService.toDTO(game);
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/state", result);
            return result;

        } catch (Exception e) {
            logger.error("Error processing WebSocket state request", e);
            return null;
        }
    }

    /**
     * Handle forfeit requests from frontend
     */
    @MessageMapping("/game.forfeit")
    public GameStateDTO forfeitGame(@Payload Map<String, String> request) {
        try {
            String gameId = request.get("gameId");
            String playerId = request.get("playerId");
            
            if (gameId == null || gameId.isEmpty()) {
                logger.warn("GameId missing in forfeit request");
                return null;
            }

            logger.info("WebSocket forfeit: game={}, player={}", gameId, playerId);
            
            gameService.forfeitGame(gameId, playerId);
            GameStateEntity game = gameService.getGame(gameId);
            GameStateDTO result = gameService.toDTO(game);
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/state", result);
            return result;

        } catch (Exception e) {
            logger.error("Error processing WebSocket forfeit: game={}", request.get("gameId"), e);
            return null;
        }
    }
}
