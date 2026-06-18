package com.chess.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for making a move
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoveRequestDTO {

    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid game ID format")
    private String gameId;

    @NotNull(message = "Player ID is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid player ID format")
    private String playerId;

    @NotNull(message = "From square is required")
    @Pattern(regexp = "^[a-h][1-8]$", message = "Invalid from square format (must be like 'e2')")
    private String from;

    @NotNull(message = "To square is required")
    @Pattern(regexp = "^[a-h][1-8]$", message = "Invalid to square format (must be like 'e4')")
    private String to;

    @Pattern(regexp = "^[qrbnQRBN]?$", message = "Invalid promotion piece (must be q, r, b, or n)")
    private String promotion;

    @Min(value = 0, message = "Timestamp must be positive")
    private long timestamp;

    public MoveRequestDTO() {
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getPromotion() {
        return promotion;
    }

    public void setPromotion(String promotion) {
        this.promotion = promotion;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
