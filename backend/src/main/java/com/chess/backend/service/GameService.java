package com.chess.backend.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chess.backend.dto.*;
import com.chess.backend.entity.*;
import com.chess.backend.exception.GameException;
import com.chess.backend.exception.MoveValidationException;
import com.chess.backend.repository.GameStateRepository;
import com.chess.backend.util.ChessUtil;
import com.chess.backend.util.GameValidator;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.move.Move;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private static final String DEFAULT_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final GameStateRepository gameStateRepository;
    private final ChessEngineService chessEngineService;

    public GameService(GameStateRepository gameStateRepository, ChessEngineService chessEngineService) {
        this.gameStateRepository = gameStateRepository;
        this.chessEngineService = chessEngineService;
    }

    @Transactional
    public GameStateEntity createGame(String gameId, String mode, PlayerEntity whitePlayer, PlayerEntity blackPlayer) {
        if (gameId == null || gameId.trim().isEmpty()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
        if (whitePlayer == null || blackPlayer == null) {
            throw new IllegalArgumentException("Both players must be provided");
        }

        GameStateEntity game = new GameStateEntity();
        game.setGameId(gameId);
        game.setMode(mode.toUpperCase());
        game.setStatus("IN_PROGRESS");
        game.setFen(DEFAULT_FEN);
        game.setCurrentTurnColor("w");
        game.setMoveCount(0);
        game.setInCheck(false);
        game.setInCheckmate(false);
        game.setInStalemate(false);
        game.setWhitePlayer(whitePlayer);
        game.setBlackPlayer(blackPlayer);
        
        gameStateRepository.save(game);
        
        logger.info("Game created: {} | Mode: {} | Players: {} vs {}", 
            gameId, mode, whitePlayer.getName(), blackPlayer.getName());
        
        return game;
    }

    public GameStateEntity getGame(String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
        return gameStateRepository.findByGameId(gameId).orElse(null);
    }

    @Transactional
    public GameStateDTO processMove(String gameId, MoveRequestDTO moveRequest) {
        if (!GameValidator.validateMove(moveRequest)) {
            throw new MoveValidationException("Invalid move request format");
        }

        GameStateEntity game = gameStateRepository.findByGameIdWithLock(gameId)
            .orElseThrow(() -> new GameException("Game not found: " + gameId, "GAME_NOT_FOUND"));

        if (!"IN_PROGRESS".equals(game.getStatus())) {
            throw new GameException(
                "Game is not in progress. Current status: " + game.getStatus(), 
                "GAME_NOT_ACTIVE"
            );
        }

        if (!GameValidator.validatePlayerTurn(game, moveRequest.getPlayerId())) {
            throw MoveValidationException.notPlayersTurn(moveRequest.getPlayerId());
        }

        Board board = new Board();
        try {
            board.loadFromFen(game.getFen());
        } catch (Exception e) {
            logger.error("Invalid FEN in database: {}", game.getFen(), e);
            throw new GameException("Corrupted game state", "INVALID_FEN");
        }

        Square fromSquare;
        Square toSquare;
        try {
            fromSquare = Square.fromValue(moveRequest.getFrom().toUpperCase());
            toSquare = Square.fromValue(moveRequest.getTo().toUpperCase());
        } catch (Exception e) {
            throw MoveValidationException.invalidSquare(moveRequest.getFrom() + " or " + moveRequest.getTo());
        }

        Piece movingPiece = board.getPiece(fromSquare);
        boolean isPawnMove = movingPiece.getPieceType() == com.github.bhlangonijr.chesslib.PieceType.PAWN;
        boolean isPromotionRank = (toSquare.getRank() == com.github.bhlangonijr.chesslib.Rank.RANK_8 && 
                                   board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE) ||
                                  (toSquare.getRank() == com.github.bhlangonijr.chesslib.Rank.RANK_1 && 
                                   board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.BLACK);

        if (isPawnMove && isPromotionRank && moveRequest.getPromotion() == null) {
            throw new MoveValidationException(
                "Pawn promotion required",
                "Must specify promotion piece: q, r, b, or n"
            );
        }

        Move move;
        if (moveRequest.getPromotion() != null && !moveRequest.getPromotion().isEmpty()) {
            String promotionUpper = moveRequest.getPromotion().toUpperCase();
            Piece promotionPiece;
            
            switch (promotionUpper) {
                case "Q":
                    promotionPiece = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE ? 
                        Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
                    break;
                case "R":
                    promotionPiece = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE ? 
                        Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                    break;
                case "B":
                    promotionPiece = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE ? 
                        Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
                    break;
                case "N":
                    promotionPiece = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE ? 
                        Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
                    break;
                default:
                    throw MoveValidationException.invalidPromotion(moveRequest.getPromotion());
            }
            
            move = new Move(fromSquare, toSquare, promotionPiece);
        } else {
            move = new Move(fromSquare, toSquare);
        }

        List<Move> legalMoves = board.legalMoves();
        if (!legalMoves.contains(move)) {
            logger.warn("Illegal move attempted: {} to {} in position {}", 
                moveRequest.getFrom(), moveRequest.getTo(), game.getFen());
            throw MoveValidationException.illegalMove(moveRequest.getFrom(), moveRequest.getTo());
        }

        board.doMove(move);

        String newFen = board.getFen();
        game.setFen(newFen);
        game.setMoveCount(game.getMoveCount() + 1);
        game.setCurrentTurnColor(ChessUtil.sideToColorString(board.getSideToMove()));

        MoveEntity moveEntity = new MoveEntity();
        moveEntity.setFromSquare(moveRequest.getFrom());
        moveEntity.setToSquare(moveRequest.getTo());
        moveEntity.setPromotion(moveRequest.getPromotion());
        moveEntity.setMoveTimestamp(System.currentTimeMillis());
        game.getMoves().add(moveEntity);

        game.setInCheck(board.isKingAttacked());
        game.setInCheckmate(board.isMated());
        game.setInStalemate(board.isStaleMate());

        if (board.isMated()) {
            game.setStatus("CHECKMATE");
            String winner = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE ? "BLACK" : "WHITE";
            logger.info("Game {} ended in checkmate. Winner: {}", gameId, winner);
        } else if (board.isStaleMate()) {
            game.setStatus("STALEMATE");
            logger.info("Game {} ended in stalemate", gameId);
        } else if (board.isDraw()) {
            game.setStatus("DRAW");
            logger.info("Game {} ended in draw", gameId);
        } else if (board.isInsufficientMaterial()) {
            game.setStatus("DRAW");
            logger.info("Game {} ended in draw (insufficient material)", gameId);
        } else if (board.isRepetition()) {
            game.setStatus("DRAW");
            logger.info("Game {} ended in draw (threefold repetition)", gameId);
        }

        game.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(game);

        logger.info("Move processed: game={}, player={}, from={}, to={}, newFen={}", 
            gameId, moveRequest.getPlayerId(), moveRequest.getFrom(), moveRequest.getTo(), newFen);

        return toDTO(game);
    }

    @Transactional
    public GameStateEntity updateGame(GameStateEntity game) {
        if (game == null) {
            throw new IllegalArgumentException("Game cannot be null");
        }
        return gameStateRepository.save(game);
    }

    @Transactional
    public void updateGameStatus(String gameId) {
        GameStateEntity game = getGame(gameId);
        if (game == null) {
            logger.warn("Cannot update status for non-existent game: {}", gameId);
            return;
        }

        try {
            Board board = new Board();
            board.loadFromFen(game.getFen());

            game.setInCheck(board.isKingAttacked());
            game.setInCheckmate(board.isMated());
            game.setInStalemate(board.isStaleMate());

            if (board.isMated()) {
                game.setStatus("CHECKMATE");
                String winner = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE ? "BLACK" : "WHITE";
                logger.info("Game {} status updated to CHECKMATE. Winner: {}", gameId, winner);
            } else if (board.isStaleMate()) {
                game.setStatus("STALEMATE");
                logger.info("Game {} status updated to STALEMATE", gameId);
            } else if (board.isDraw()) {
                game.setStatus("DRAW");
                logger.info("Game {} status updated to DRAW", gameId);
            } else if (board.isInsufficientMaterial()) {
                game.setStatus("DRAW");
                logger.info("Game {} status updated to DRAW (insufficient material)", gameId);
            } else if (board.isRepetition()) {
                game.setStatus("DRAW");
                logger.info("Game {} status updated to DRAW (threefold repetition)", gameId);
            }

            game.setUpdatedAt(LocalDateTime.now());
            gameStateRepository.save(game);

        } catch (Exception e) {
            logger.error("Error updating game status for game {}", gameId, e);
        }
    }

    public AiMoveDTO getAiMove(String gameId, int difficulty, String fen) {
        GameStateEntity game = getGame(gameId);
        if (game == null) {
            throw new GameException("Game not found: " + gameId, "GAME_NOT_FOUND");
        }
        
        if (!"PVE".equals(game.getMode())) {
            throw new GameException("AI moves only available in PVE mode", "INVALID_MODE");
        }

        if (!chessEngineService.isEngineAvailable()) {
            throw new GameException("Chess engine is not available", "ENGINE_UNAVAILABLE");
        }

        // Use the provided FEN if available, otherwise fall back to the game's stored FEN
        String positionFen = (fen != null && !fen.trim().isEmpty()) ? fen : game.getFen();
        return chessEngineService.generateMove(positionFen, difficulty);
    }

    @Transactional
    public void forfeitGame(String gameId, String playerId) {
        GameStateEntity game = getGame(gameId);
        if (game == null) {
            throw new GameException("Game not found: " + gameId, "GAME_NOT_FOUND");
        }
        
        game.setStatus("ABANDONED");
        game.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(game);
        
        logger.info("Game {} forfeited by player {}", gameId, playerId);
    }

    @Transactional
    public void endGame(String gameId, String status) {
        GameStateEntity game = getGame(gameId);
        if (game == null) {
            throw new GameException("Game not found: " + gameId, "GAME_NOT_FOUND");
        }
        
        game.setStatus(status);
        game.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(game);
        
        logger.info("Game {} ended with status: {}", gameId, status);
    }

    public List<GameStateEntity> getActiveGames() {
        return gameStateRepository.findByStatusIn(List.of("WAITING", "IN_PROGRESS"));
    }

    public List<GameStateEntity> getWaitingGames() {
        return gameStateRepository.findByStatus("WAITING");
    }

    public GameStateDTO toDTO(GameStateEntity game) {
        if (game == null) {
            return null;
        }

        GameStateDTO dto = new GameStateDTO();
        dto.setGameId(game.getGameId());
        dto.setMode(game.getMode());
        dto.setStatus(game.getStatus());
        dto.setFen(game.getFen());
        dto.setCurrentTurnColor(game.getCurrentTurnColor());
        dto.setMoveCount(game.getMoveCount() != null ? game.getMoveCount() : 0);
        dto.setInCheck(game.getInCheck() != null && game.getInCheck());
        dto.setInCheckmate(game.getInCheckmate() != null && game.getInCheckmate());
        dto.setInStalemate(game.getInStalemate() != null && game.getInStalemate());
        
        if (game.getCreatedAt() != null) {
            dto.setCreatedAt(game.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        }
        if (game.getUpdatedAt() != null) {
            dto.setUpdatedAt(game.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        }
        
        if (game.getWhitePlayer() != null) {
            dto.setWhitePlayer(playerToDTO(game.getWhitePlayer()));
        }
        if (game.getBlackPlayer() != null) {
            dto.setBlackPlayer(playerToDTO(game.getBlackPlayer()));
        }
        
        // Add legal moves (empty for now, can be populated by chess engine)
        dto.setLegalMoves(new ArrayList<>());
        
        if (game.getMoves() != null && !game.getMoves().isEmpty()) {
            List<MoveDTO> moveDTOs = new ArrayList<>();
            for (MoveEntity move : game.getMoves()) {
                MoveDTO moveDTO = new MoveDTO();
                moveDTO.setFrom(move.getFromSquare());
                moveDTO.setTo(move.getToSquare());
                moveDTO.setPromotion(move.getPromotion());
                moveDTO.setTimestamp(move.getMoveTimestamp() != null ? move.getMoveTimestamp() : 0L);
                moveDTOs.add(moveDTO);
            }
            dto.setMoves(moveDTOs);
            
            MoveEntity lastMove = game.getMoves().get(game.getMoves().size() - 1);
            MoveDTO lastMoveDTO = new MoveDTO();
            lastMoveDTO.setFrom(lastMove.getFromSquare());
            lastMoveDTO.setTo(lastMove.getToSquare());
            lastMoveDTO.setPromotion(lastMove.getPromotion());
            lastMoveDTO.setTimestamp(lastMove.getMoveTimestamp() != null ? lastMove.getMoveTimestamp() : 0L);
            dto.setLastMove(lastMoveDTO);
        }
        
        return dto;
    }

    private PlayerDTO playerToDTO(PlayerEntity player) {
        if (player == null) {
            return null;
        }

        PlayerDTO dto = new PlayerDTO();
        dto.setId(player.getPlayerId());
        dto.setName(player.getName());
        dto.setColor(player.getColor());
        dto.setAi(player.getIsAi() != null && player.getIsAi());
        
        if (player.getIsAi() != null && player.getIsAi()) {
            dto.setDifficulty(player.getDifficulty());
        }
        
        dto.setConnectionStatus(player.getConnectionStatus());
        return dto;
    }
}
