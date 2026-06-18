// frontend/src/types/game.ts
// TypeScript interfaces for chess game state and moves

import { Square as ChessJsSquare } from 'chess.js';

/**
 * Core game mode enum
 */
export enum GameMode {
  PVP = 'pvp',      // Player vs Player
  PVE = 'pve',      // Player vs Engine (AI)
  SPECTATE = 'spectate'
}

/**
 * Game status
 */
export enum GameStatus {
  WAITING = 'waiting',      // Waiting for opponent/start
  IN_PROGRESS = 'in_progress',
  CHECKMATE = 'checkmate',
  STALEMATE = 'stalemate',
  DRAW = 'draw',
  ABANDONED = 'abandoned'
}

/**
 * Player color
 */
export enum PlayerColor {
  WHITE = 'w',
  BLACK = 'b'
}

/**
 * Represents a single move
 */
export interface Move {
  from: ChessJsSquare;
  to: ChessJsSquare;
  promotion?: 'q' | 'r' | 'b' | 'n';
  timestamp: number;
}

/**
 * Represents a player in the game
 */
export interface Player {
  id: string;
  name: string;
  color: PlayerColor;
  isAi: boolean;
  difficulty?: number; // 1-10 for AI difficulty
  connectionStatus: 'connected' | 'disconnected' | 'idle';
}

/**
 * Game session metadata
 */
export interface GameSession {
  id: string;
  mode: GameMode;
  status: GameStatus;
  players: [Player, Player];
  currentTurn: PlayerColor;
  moveCount: number;
  createdAt: number;
  updatedAt: number;
}

/**
 * Current board and game state
 */
export interface GameState {
  session: GameSession;
  fen: string;                    // FEN notation for board position
  moves: Move[];                  // Complete move history
  lastMove?: Move;
  inCheck: boolean;
  inCheckmate: boolean;
  inStalemate: boolean;
  legalMoves: string[];          // legal moves in UCI format
}

/**
 * WebSocket message types
 */
export interface WebSocketMessage<T = unknown> {
  type: 'move' | 'game-state' | 'chat' | 'error' | 'ping' | 'auth';
  gameId: string;
  payload: T;
  timestamp: number;
}

/**
 * Move request sent to server
 */
export interface MoveRequest {
  from: string;
  to: string;
  promotion?: string;
}

/**
 * AI move response
 */
export interface AiMoveResponse {
  move: Move;
  evaluation: number;  // Centipawns
  depth: number;
}

/**
 * UI-specific game view state
 */
export interface GameUIState {
  selectedSquare: ChessJsSquare | null;
  highlightedSquares: Set<ChessJsSquare>;
  isThinking: boolean;          // True when AI is computing
  lastMoveHighlight: Move | null;
  draggedPiece: { from: ChessJsSquare; x: number; y: number } | null;
}

/**
 * Game statistics for a player
 */
export interface PlayerStats {
  playerId: string;
  totalGames: number;
  wins: number;
  losses: number;
  draws: number;
  averageTimePerMove: number;
  favoriteOpening: string;
}