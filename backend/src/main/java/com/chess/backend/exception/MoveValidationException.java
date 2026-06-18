package com.chess.backend.exception;

public class MoveValidationException extends GameException {
    public MoveValidationException(String message) {
        super(message, "INVALID_MOVE");
    }

    public MoveValidationException(String message, String details) {
        super(message, "INVALID_MOVE", details);
    }

    public MoveValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static MoveValidationException invalidSquare(String square) {
        return new MoveValidationException(
                "Invalid square notation: " + square,
                "Squares must be in format like 'e2', 'e4', etc.");
    }

    public static MoveValidationException illegalMove(String from, String to) {
        return new MoveValidationException(
                "Illegal move: " + from + " to " + to,
                "This move is not allowed in the current position");
    }

    public static MoveValidationException invalidPromotion(String promotion) {
        return new MoveValidationException(
                "Invalid promotion piece: " + promotion,
                "Promotion must be one of: q (queen), r (rook), b (bishop), n (knight)");
    }

    public static MoveValidationException notPlayersTurn(String playerId) {
        return new MoveValidationException(
                "Not player's turn: " + playerId,
                "Player cannot move when it's the opponent's turn");
    }

}
