package com.chess.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for game state
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameStateDTO {

    private String gameId;
    private String mode;
    private String status;
    private PlayerDTO whitePlayer;
    private PlayerDTO blackPlayer;
    private String fen;
    private List<MoveDTO> moves;
    private MoveDTO lastMove;
    private String currentTurnColor;
    private int moveCount;
    private boolean inCheck;
    private boolean inCheckmate;
    private boolean inStalemate;
    private List<String> legalMoves;
    private long createdAt;
    private long updatedAt;

    // Getters and Setters
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public PlayerDTO getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(PlayerDTO whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public PlayerDTO getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(PlayerDTO blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public String getFen() {
        return fen;
    }

    public void setFen(String fen) {
        this.fen = fen;
    }

    public List<MoveDTO> getMoves() {
        return moves;
    }

    public void setMoves(List<MoveDTO> moves) {
        this.moves = moves;
    }

    public MoveDTO getLastMove() {
        return lastMove;
    }

    public void setLastMove(MoveDTO lastMove) {
        this.lastMove = lastMove;
    }

    public String getCurrentTurnColor() {
        return currentTurnColor;
    }

    public void setCurrentTurnColor(String currentTurnColor) {
        this.currentTurnColor = currentTurnColor;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public void setMoveCount(int moveCount) {
        this.moveCount = moveCount;
    }

    public boolean isInCheck() {
        return inCheck;
    }

    public void setInCheck(boolean inCheck) {
        this.inCheck = inCheck;
    }

    public boolean isInCheckmate() {
        return inCheckmate;
    }

    public void setInCheckmate(boolean inCheckmate) {
        this.inCheckmate = inCheckmate;
    }

    public boolean isInStalemate() {
        return inStalemate;
    }

    public void setInStalemate(boolean inStalemate) {
        this.inStalemate = inStalemate;
    }

    public List<String> getLegalMoves() {
        return legalMoves;
    }

    public void setLegalMoves(List<String> legalMoves) {
        this.legalMoves = legalMoves;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

}
