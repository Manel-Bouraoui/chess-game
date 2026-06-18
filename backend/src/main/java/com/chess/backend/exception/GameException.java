package com.chess.backend.exception;

public class GameException extends RuntimeException {
    private String errorCode;
    private String details;

    public GameException(String message) {
        super(message);
        this.errorCode = "GAME_ERROR";
    }

    public GameException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public GameException(String message, String errorCode, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public GameException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "GAME_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

}
