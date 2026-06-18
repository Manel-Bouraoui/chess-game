// frontend/src/hooks/useChessGame.ts
// Central game state management with chess.js validation

import { useReducer, useCallback, useRef, useMemo } from 'react';
import { Chess, Move as ChessJsMove, Square } from 'chess.js';
import {
  GameState,
  GameMode,
  PlayerColor,
  GameStatus,
  Move,
  GameUIState,
} from '@/types/game';

interface GameContextState {
  gameState: GameState | null;
  chess: Chess;
  uiState: GameUIState;
  isLocalPlayerTurn: boolean;
  validMoves: Square[];
  capturedByWhite: string[]; // black pieces captured by white
  capturedByBlack: string[]; // white pieces captured by black
}

type GameAction =
  | { type: 'INIT_GAME'; payload: GameState; localColor: PlayerColor }
  | { type: 'MAKE_MOVE'; payload: Move }
  | { type: 'SELECT_SQUARE'; payload: Square }
  | { type: 'DESELECT_SQUARE' }
  | { type: 'SET_HIGHLIGHTED_SQUARES'; payload: Set<Square> }
  | { type: 'SET_THINKING'; payload: boolean }
  | { type: 'SYNC_GAME_STATE'; payload: GameState }
  | { type: 'RESET_GAME' };

interface GameContextConfig {
  mode: GameMode;
  localPlayerId: string;
  gameId: string;
}

const initialUIState: GameUIState = {
  selectedSquare: null,
  highlightedSquares: new Set(),
  isThinking: false,
  lastMoveHighlight: null,
  draggedPiece: null,
};

/**
 * Chess game state reducer
 */
function gameReducer(state: GameContextState, action: GameAction): GameContextState {
  try {
    switch (action.type) {
      case 'INIT_GAME': {
        const chess = new Chess(action.payload.fen);
        return {
          ...state,
          gameState: action.payload,
          chess,
          validMoves: [],
        };
      }

      case 'MAKE_MOVE': {
        const move = action.payload;
        const chessMove = state.chess.move({
          from: move.from,
          to: move.to,
          promotion: move.promotion,
        });

        if (!chessMove) {
          console.warn('Invalid move attempted:', move);
          return state;
        }

        if (!state.gameState) return state;

        const newMoves = [...state.gameState.moves, move];
        const nextTurn = state.chess.turn() === 'w' ? PlayerColor.WHITE : PlayerColor.BLACK;

        // Track captured piece
        const whiteEmojis: Record<string, string> = { p: '♟', r: '♜', n: '♞', b: '♝', q: '♛', k: '♚' };
        const blackEmojis: Record<string, string> = { p: '♙', r: '♖', n: '♘', b: '♗', q: '♕', k: '♔' };
        let newCapturedByWhite = state.capturedByWhite;
        let newCapturedByBlack = state.capturedByBlack;

        if (chessMove.captured) {
          if (chessMove.color === 'w') {
            newCapturedByWhite = [...state.capturedByWhite, whiteEmojis[chessMove.captured] ?? '?'];
          } else {
            newCapturedByBlack = [...state.capturedByBlack, blackEmojis[chessMove.captured] ?? '?'];
          }
        }

        return {
          ...state,
          capturedByWhite: newCapturedByWhite,
          capturedByBlack: newCapturedByBlack,
          gameState: {
            ...state.gameState,
            fen: state.chess.fen(),
            moves: newMoves,
            lastMove: move,
            inCheck: state.chess.inCheck(),
            inCheckmate: state.chess.isCheckmate(),
            inStalemate: state.chess.isStalemate(),
            session: {
              ...state.gameState.session,
              currentTurn: nextTurn,
              moveCount: newMoves.length,
            },
          },
          uiState: {
            ...state.uiState,
            selectedSquare: null,
            highlightedSquares: new Set(),
            lastMoveHighlight: move,
          },
        };
      }

      case 'SELECT_SQUARE': {
        const square = action.payload;
        const moves = state.chess.moves({ square, verbose: true });
        const targetSquares = moves.map((m: ChessJsMove) => m.to);
        const highlights = new Set(targetSquares);

        return {
          ...state,
          validMoves: targetSquares,
          uiState: {
            ...state.uiState,
            selectedSquare: square,
            highlightedSquares: highlights,
          },
        };
      }

      case 'DESELECT_SQUARE':
        return {
          ...state,
          validMoves: [],
          uiState: {
            ...state.uiState,
            selectedSquare: null,
            highlightedSquares: new Set(),
          },
        };

      case 'SET_HIGHLIGHTED_SQUARES':
        return {
          ...state,
          uiState: {
            ...state.uiState,
            highlightedSquares: action.payload,
          },
        };

      case 'SET_THINKING':
        return {
          ...state,
          uiState: {
            ...state.uiState,
            isThinking: action.payload,
          },
        };

      case 'SYNC_GAME_STATE': {
        const whiteEmojis: Record<string, string> = { p: '♟', r: '♜', n: '♞', b: '♝', q: '♛', k: '♚' };
        const blackEmojis: Record<string, string> = { p: '♙', r: '♖', n: '♘', b: '♗', q: '♕', k: '♔' };

        // If FEN already matches, just update gameState metadata — preserve everything
        if (state.chess.fen() === action.payload.fen) {
          return {
            ...state,
            gameState: action.payload,
            uiState: { ...state.uiState, selectedSquare: null, highlightedSquares: new Set() },
          };
        }

        // Rebuild chess by replaying all moves from existing history
        const existingVerbose = state.chess.history({ verbose: true });
        const rebuilt = new Chess();
        const capturedByWhite: string[] = [];
        const capturedByBlack: string[] = [];

        for (const m of existingVerbose) {
          try {
            const r = rebuilt.move({ from: m.from, to: m.to, promotion: m.promotion });
            if (!r) break;
            if (r.captured) {
              if (r.color === 'w') capturedByWhite.push(whiteEmojis[r.captured] ?? '?');
              else capturedByBlack.push(blackEmojis[r.captured] ?? '?');
            }
          } catch { break; }
        }

        // At this point rebuilt.fen() should equal state.chess.fen()
        // Now try to apply the ONE new move from the opponent to reach target FEN
        if (rebuilt.fen() === state.chess.fen() || existingVerbose.length === 0) {
          // Find the move that goes from current position to target FEN
          const candidates = rebuilt.moves({ verbose: true });
          let applied = false;
          for (const lm of candidates) {
            const test = new Chess(rebuilt.fen());
            const r = test.move({ from: lm.from, to: lm.to, promotion: lm.promotion });
            if (r && test.fen() === action.payload.fen) {
              rebuilt.move({ from: lm.from, to: lm.to, promotion: lm.promotion });
              if (r.captured) {
                if (r.color === 'w') capturedByWhite.push(whiteEmojis[r.captured] ?? '?');
                else capturedByBlack.push(blackEmojis[r.captured] ?? '?');
              }
              applied = true;
              break;
            }
          }

          if (applied) {
            return {
              ...state,
              gameState: action.payload,
              chess: rebuilt,
              capturedByWhite,
              capturedByBlack,
              uiState: { ...state.uiState, selectedSquare: null, highlightedSquares: new Set() },
            };
          }
        }

        // Fallback: load FEN but KEEP existing captures and history
        // by creating a new Chess instance from current history then loading FEN
        const fallback = new Chess();
        fallback.load(action.payload.fen);
        return {
          ...state,
          gameState: action.payload,
          chess: fallback,
          // Preserve existing captures — don't reset them on fallback
          uiState: { ...state.uiState, selectedSquare: null, highlightedSquares: new Set() },
        };
      }

      case 'RESET_GAME':
        return {
          gameState: null,
          chess: new Chess(),
          uiState: initialUIState,
          isLocalPlayerTurn: false,
          validMoves: [],
          capturedByWhite: [],
          capturedByBlack: [],
        };

      default:
        return state;
    }
  } catch (error) {
    console.error('Error in gameReducer:', error);
    return state;
  }
}

/**
 * Custom hook for managing chess game state with full validation
 * Integrates chess.js for move validation
 */
export function useChessGame(config: GameContextConfig) {
  const [state, dispatch] = useReducer(gameReducer, {
    gameState: null,
    chess: new Chess(),
    uiState: initialUIState,
    isLocalPlayerTurn: false,
    validMoves: [],
    capturedByWhite: [],
    capturedByBlack: [],
  });

  const configRef = useRef(config);
  configRef.current = config;

  // Initialize game
  const initGame = useCallback((initialGameState: GameState, localColor: PlayerColor) => {
    dispatch({ type: 'INIT_GAME', payload: initialGameState, localColor });
  }, []);

  // Make a move - let the reducer validate via chess.js
  const makeMove = useCallback((move: Move): boolean => {
    try {
      // Validate first using current chess state before dispatching
      const legalMoves = state.chess.moves({ verbose: true });
      const isLegal = legalMoves.some(
        (m: ChessJsMove) => m.from === move.from && m.to === move.to
      );

      if (!isLegal) {
        console.warn('❌ Illegal move:', move.from, '->', move.to);
        return false;
      }

      dispatch({ type: 'MAKE_MOVE', payload: move });
      return true;
    } catch (error) {
      console.error('❌ Error in makeMove:', error);
      return false;
    }
  }, [state.chess]);

  // Select a square and show legal moves
  const selectSquare = useCallback((square: Square) => {
    dispatch({ type: 'SELECT_SQUARE', payload: square });
  }, []);

  // Deselect current square
  const deselectSquare = useCallback(() => {
    dispatch({ type: 'DESELECT_SQUARE' });
  }, []);

  // Update game state from server
  const syncGameState = useCallback((gameState: GameState) => {
    dispatch({ type: 'SYNC_GAME_STATE', payload: gameState });
  }, []);

  // Set thinking state (for UI feedback during AI move)
  const setThinking = useCallback((thinking: boolean) => {
    dispatch({ type: 'SET_THINKING', payload: thinking });
  }, []);

  // Memoize isMyTurn to prevent infinite re-renders
  const isMyTurnValue = useMemo(() => {
    if (!state.gameState) return false;
    const localPlayer = state.gameState.session.players.find(
      p => p.id === config.localPlayerId
    );
    return state.gameState.session.currentTurn === localPlayer?.color;
  }, [state.gameState, config.localPlayerId]);

  // Get legal moves for a square
  const getLegalMovesForSquare = useCallback((square: Square): Square[] => {
    return state.chess
      .moves({ square, verbose: true })
      .map((m: ChessJsMove) => m.to as Square);
  }, [state.chess]);

  // Check if game is over
  const isGameOver = useCallback((): boolean => {
    if (!state.gameState) return false;
    return [
      GameStatus.CHECKMATE,
      GameStatus.STALEMATE,
      GameStatus.DRAW,
      GameStatus.ABANDONED,
    ].includes(state.gameState.session.status);
  }, [state.gameState]);

  // Reset game state
  const reset = useCallback(() => {
    dispatch({ type: 'RESET_GAME' });
  }, []);

  return {
    // State
    gameState: state.gameState,
    chess: state.chess,
    uiState: state.uiState,
    isMyTurn: isMyTurnValue,
    validMoves: state.validMoves,
    capturedByWhite: state.capturedByWhite,
    capturedByBlack: state.capturedByBlack,

    // Actions
    initGame,
    makeMove,
    selectSquare,
    deselectSquare,
    syncGameState,
    setThinking,
    getLegalMovesForSquare,
    isGameOver,
    reset,
  };
}

export type UseChessGameReturn = ReturnType<typeof useChessGame>;