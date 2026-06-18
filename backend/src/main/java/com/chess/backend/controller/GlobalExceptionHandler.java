package com.chess.backend.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.chess.backend.exception.GameException;
import com.chess.backend.exception.MoveValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MoveValidationException.class)
    public ResponseEntity<?> handleMoveValidationException(
            MoveValidationException ex,
            WebRequest request) {
        logger.warn("Move validation error: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", Map.of(
                "code", ex.getErrorCode(),
                "message", ex.getMessage(),
                "details", ex.getDetails()));
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(GameException.class)
    public ResponseEntity<?> handleGameException(
            GameException ex,
            WebRequest request) {
        logger.warn("Game error: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", Map.of(
                "code", ex.getErrorCode(),
                "message", ex.getMessage(),
                "details", ex.getDetails() != null ? ex.getDetails() : ""));
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));

        logger.warn("Validation error: {}", message);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", Map.of(
                "code", "VALIDATION_ERROR",
                "message", message));
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {
        logger.warn("Illegal argument: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", Map.of(
                "code", "INVALID_ARGUMENT",
                "message", ex.getMessage()));
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(
            Exception ex,
            WebRequest request) {
        logger.error("Unexpected error", ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", Map.of(
                "code", "INTERNAL_ERROR",
                "message", "An unexpected error occurred"));
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

}
