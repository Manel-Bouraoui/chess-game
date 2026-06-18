// Utility class for chess operations

package com.chess.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.bhlangonijr.chesslib.Board;

public class ChessUtil {

    private static final Logger logger = LoggerFactory.getLogger(ChessUtil.class);

    private static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Validate if a square notation is valid (e.g., "e2", "e4")
     */
    public static boolean isValidSquare(String square) {
        if (square == null || square.length() != 2) {
            return false;
        }
        return isValidFile(square.charAt(0)) && isValidRank(square.charAt(1));
    }

    /**
     * Validate file (column a-h)
     */
    private static boolean isValidFile(char file) {
        return file >= 'a' && file <= 'h';
    }

    /**
     * Validate rank (row 1-8)
     */
    private static boolean isValidRank(char rank) {
        return rank >= '1' && rank <= '8';
    }

    /**
     * Validate FEN notation with comprehensive checks
     */
    public static boolean isValidFen(String fen) {
        if (fen == null || fen.trim().isEmpty()) {
            return false;
        }

        String[] parts = fen.split(" ");
        if (parts.length != 6) {
            return false;
        }

        // Validate board position (part 0)
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            return false;
        }

        for (String rank : ranks) {
            int fileCount = 0;
            for (char c : rank.toCharArray()) {
                if (Character.isDigit(c)) {
                    int emptySquares = Character.getNumericValue(c);
                    if (emptySquares < 1 || emptySquares > 8) {
                        return false;
                    }
                    fileCount += emptySquares;
                } else if ("prnbqkPRNBQK".indexOf(c) >= 0) {
                    fileCount++;
                } else {
                    return false; // Invalid character
                }
            }
            if (fileCount != 8) {
                return false; // Each rank must have exactly 8 squares
            }
        }

        // Validate active color (part 1)
        if (!parts[1].matches("[wb]")) {
            return false;
        }

        // Validate castling rights (part 2)
        if (!parts[2].matches("^(-|K?Q?k?q?)$")) {
            return false;
        }

        // Validate en passant target square (part 3)
        if (!parts[3].matches("^(-|[a-h][36])$")) {
            return false;
        }

        // Validate halfmove clock (part 4)
        try {
            int halfmove = Integer.parseInt(parts[4]);
            if (halfmove < 0) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // Validate fullmove number (part 5)
        try {
            int fullmove = Integer.parseInt(parts[5]);
            if (fullmove < 1) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // Final validation: try to load in chess library
        try {
            Board testBoard = new Board();
            testBoard.loadFromFen(fen);
            return true;
        } catch (Exception e) {
            logger.warn("FEN failed chess library validation: {}", fen);
            return false;
        }
    }

    /**
     * Get starting FEN position
     */
    public static String getStartingFen() {
        return STARTING_FEN;
    }

    /**
     * Validate promotion piece
     */
    public static boolean isValidPromotion(String promotion) {
        if (promotion == null) {
            return true; // Promotion is optional in the DTO
        }
        return promotion.toLowerCase().matches("[qrbn]");
    }

    /**
     * Check if a move requires promotion (pawn reaching last rank)
     */
    public static boolean requiresPromotion(String from, String to, String fen) {
        if (from == null || to == null || from.length() != 2 || to.length() != 2) {
            return false;
        }

        // Check if destination is rank 1 or 8
        char toRank = to.charAt(1);
        if (toRank != '1' && toRank != '8') {
            return false;
        }

        // Check if moving piece is a pawn
        try {
            Board board = new Board();
            board.loadFromFen(fen);
            com.github.bhlangonijr.chesslib.Square fromSquare = 
                com.github.bhlangonijr.chesslib.Square.fromValue(from.toUpperCase());
            com.github.bhlangonijr.chesslib.Piece piece = board.getPiece(fromSquare);
            
            return piece.getPieceType() == com.github.bhlangonijr.chesslib.PieceType.PAWN;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get white's turn from FEN
     */
    public static boolean isWhiteTurn(String fen) {
        String[] parts = fen.split(" ");
        return parts.length > 1 && "w".equals(parts[1]);
    }

    /**
     * Get black's turn from FEN
     */
    public static boolean isBlackTurn(String fen) {
        return !isWhiteTurn(fen);
    }

    /**
     * Get current turn color ("w" or "b")
     */
    public static String getCurrentTurnColor(String fen) {
        String[] parts = fen.split(" ");
        return parts.length > 1 ? parts[1] : "w";
    }

    /**
     * Convert side to move to color string
     */
    public static String sideToColorString(com.github.bhlangonijr.chesslib.Side side) {
        return side == com.github.bhlangonijr.chesslib.Side.WHITE ? "w" : "b";
    }
}
