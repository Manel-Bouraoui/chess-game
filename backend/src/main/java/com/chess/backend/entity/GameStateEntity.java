package com.chess.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity representing a chess game stored in MySQL.
 * Maps to the `games` table in the chess_db database.
 */
@Entity
@Table(name = "games")
public class GameStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String gameId;

    @Column(nullable = false)
    private String mode; // PVP, PVE, SPECTATE

    @Column(nullable = false)
    private String status; // WAITING, IN_PROGRESS, CHECKMATE, STALEMATE, DRAW, ABANDONED

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "white_player_id")
    private PlayerEntity whitePlayer;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "black_player_id")
    private PlayerEntity blackPlayer;

    @Column(columnDefinition = "LONGTEXT")
    private String fen;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_state_id")
    private List<MoveEntity> moves = new ArrayList<>();

    @Column(length = 5)
    private String currentTurnColor; // w, b

    @Column
    private Integer moveCount = 0;

    @Column
    private Boolean inCheck = false;

    @Column
    private Boolean inCheckmate = false;

    @Column
    private Boolean inStalemate = false;

    @Column(columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    // Constructors
    public GameStateEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public PlayerEntity getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(PlayerEntity whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public PlayerEntity getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(PlayerEntity blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public String getFen() {
        return fen;
    }

    public void setFen(String fen) {
        this.fen = fen;
    }

    public List<MoveEntity> getMoves() {
        return moves;
    }

    public void setMoves(List<MoveEntity> moves) {
        this.moves = moves;
    }

    public String getCurrentTurnColor() {
        return currentTurnColor;
    }

    public void setCurrentTurnColor(String currentTurnColor) {
        this.currentTurnColor = currentTurnColor;
    }

    public Integer getMoveCount() {
        return moveCount;
    }

    public void setMoveCount(Integer moveCount) {
        this.moveCount = moveCount;
    }

    public Boolean getInCheck() {
        return inCheck;
    }

    public void setInCheck(Boolean inCheck) {
        this.inCheck = inCheck;
    }

    public Boolean getInCheckmate() {
        return inCheckmate;
    }

    public void setInCheckmate(Boolean inCheckmate) {
        this.inCheckmate = inCheckmate;
    }

    public Boolean getInStalemate() {
        return inStalemate;
    }

    public void setInStalemate(Boolean inStalemate) {
        this.inStalemate = inStalemate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}