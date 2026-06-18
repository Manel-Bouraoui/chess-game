// Utility class for game state validation

package com.chess.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chess.backend.dto.MoveRequestDTO;
import com.chess.backend.entity.GameStateEntity;

public class GameValidator {
    private static final Logger logger = LoggerFactory.getLogger(GameValidator.class);
    private static final long FIVE_MINUTES_MS = 5 * 60 * 1000;
    private static final long ONE_MINUTE_MS = 60 * 1000;

    /**
     * Validate a move request with comprehensive checks
     */
    public static boolean validateMove(MoveRequestDTO move) {
        if (move == null) {
            logger.warn("Move request is null");
            return false;
        }

        if (move.getGameId() == null || move.getGameId().trim().isEmpty()) {
            logger.warn("Game ID is null or empty");
            return false;
        }

        if (move.getPlayerId() == null || move.getPlayerId().trim().isEmpty()) {
            logger.warn("Player ID is null or empty");
            return false;
        }

        if (!ChessUtil.isValidSquare(move.getFrom())) {
            logger.warn("Invalid 'from' square: {}", move.getFrom());
            return false;
        }

        if (!ChessUtil.isValidSquare(move.getTo())) {
            logger.warn("Invalid 'to' square: {}", move.getTo());
            return false;
        }

        if (move.getFrom().equals(move.getTo())) {
            logger.warn("From and to squares are the same: {}", move.getFrom());
            return false;
        }

        if (move.getPromotion() != null && !ChessUtil.isValidPromotion(move.getPromotion())) {
            logger.warn("Invalid promotion piece: {}", move.getPromotion());
            return false;
        }

        // Validate timestamp is within reasonable range
        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - FIVE_MINUTES_MS;
        long oneMinuteInFuture = now + ONE_MINUTE_MS;

        if (move.getTimestamp() < fiveMinutesAgo || move.getTimestamp() > oneMinuteInFuture) {
            logger.warn("Timestamp out of acceptable range: {} (now: {})", move.getTimestamp(), now);
            return false;
        }

        return true;
    }

    /**
     * Validate game state entity
     */
    public static boolean validateGameState(GameStateEntity gameState) {
        if (gameState == null) {
            logger.warn("GameState is null");
            return false;
        }

        if (gameState.getGameId() == null || gameState.getGameId().isEmpty()) {
            logger.warn("GameState has invalid gameId");
            return false;
        }

        if (gameState.getFen() == null || !ChessUtil.isValidFen(gameState.getFen())) {
            logger.warn("GameState has invalid FEN: {}", gameState.getFen());
            return false;
        }

        if (gameState.getWhitePlayer() == null || gameState.getBlackPlayer() == null) {
            logger.warn("GameState missing players");
            return false;
        }

        if (gameState.getStatus() == null || gameState.getStatus().isEmpty()) {
            logger.warn("GameState has invalid status");
            return false;
        }

        return true;
    }

    /**
     * Validate move sequence (from != to)
     */
    public static boolean validateMoveSequence(String from, String to) {
        if (from == null || to == null) {
            logger.warn("From or to square is null");
            return false;
        }
        
        if (from.equals(to)) {
            logger.warn("Invalid move sequence: from and to squares are the same");
            return false;
        }
        return true;
    }

    /**
     * Validate player turn
     */
    public static boolean validatePlayerTurn(GameStateEntity gameState, String playerId) {
        if (gameState == null) {
            logger.warn("GameState is null");
            return false;
        }

        if (playerId == null || playerId.trim().isEmpty()) {
            logger.warn("Player ID is null or empty");
            return false;
        }

        String currentTurn = gameState.getCurrentTurnColor();
        String playerColor = getPlayerColor(gameState, playerId);

        if (playerColor == null) {
            logger.warn("Player {} not found in game {}", playerId, gameState.getGameId());
            return false;
        }

        boolean isPlayerTurn = currentTurn.equals(playerColor);
        if (!isPlayerTurn) {
            logger.warn("Not player's turn. Current: {}, Player: {} (color: {})", 
                currentTurn, playerId, playerColor);
        }

        return isPlayerTurn;
    }

    /**
     * Get player color in game
     */
    private static String getPlayerColor(GameStateEntity gameState, String playerId) {
        if (gameState.getWhitePlayer() != null && 
            gameState.getWhitePlayer().getPlayerId().equals(playerId)) {
            return "w";
        }
        if (gameState.getBlackPlayer() != null && 
            gameState.getBlackPlayer().getPlayerId().equals(playerId)) {
            return "b";
        }
        return null;
    }

    /**
     * Validate game is in playable state
     */
    public static boolean isGameActive(GameStateEntity gameState) {
        if (gameState == null) {
            return false;
        }
        
        String status = gameState.getStatus();
        return "IN_PROGRESS".equals(status) || "WAITING".equals(status);
    }
}