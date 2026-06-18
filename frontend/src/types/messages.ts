// frontend/src/types/messages.ts
// WebSocket and HTTP message contracts

import { Move, MoveRequest, GameState, GameStatus } from './game';

/**
 * STOMP (WebSocket subprotocol) message topics
 */
export const STOMP_TOPICS = {
  GAME_STATE: '/topic/game/{gameId}/state',
  PLAYER_MOVES: '/topic/game/{gameId}/moves',
  PLAYER_CONNECTED: '/topic/game/{gameId}/connected',
  CHAT: '/topic/game/{gameId}/chat',
} as const;

export const STOMP_ENDPOINTS = {
  SEND_MOVE: '/app/game.move',
  REQUEST_GAME_STATE: '/app/game.request-state',
  FORFEIT: '/app/game.forfeit',
  CHAT_MESSAGE: '/app/chat.send',
} as const;

/**
 * Incoming WebSocket message from server
 */
export interface ServerMessage<T = unknown> {
  type?: 'move' | 'game-state' | 'error' | 'connected' | 'disconnected';
  gameId?: string;
  payload?: T;
  timestamp?: number;
  source?: 'server' | 'broadcast';
}

/**
 * Move notification from server
 */
export interface MoveNotification {
  move: Move;
  gameState: GameState;
  movedBy: string;  // playerId
  isLegal: boolean;
}

/**
 * Game state synchronization message
 */
export interface GameStateSyncMessage {
  gameState: GameState;
  reason: 'initial' | 'move' | 'rejoin' | 'refresh';
}

/**
 * Error message from server
 */
export interface ErrorMessage {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

/**
 * Player connection status update
 */
export interface ConnectionStatusMessage {
  playerId: string;
  status: 'connected' | 'disconnected' | 'reconnecting';
  playerName: string;
}

/**
 * Client request to send a move
 */
export interface ClientMoveMessage extends MoveRequest {
  gameId: string;
}

/**
 * Chat message in game
 */
export interface ChatMessage {
  gameId: string;
  playerId: string;
  playerName: string;
  message: string;
  timestamp: number;
}

/**
 * Heartbeat/ping message for connection health
 */
export interface PingMessage {
  clientTime: number;
  sequence: number;
}

export interface PongMessage {
  serverTime: number;
  clientTime: number;
  latency: number;
  sequence: number;
}

/**
 * HTTP Response DTOs
 */

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
  timestamp: number;
}

export interface CreateGameRequest {
  mode: 'pvp' | 'pve';
  aiDifficulty?: number;
  playerName: string;
}

export interface CreateGameResponse {
  gameId: string;
  sessionId: string;
  players: Array<{ id: string; name: string; color: string }>;
  initialGameState: GameState;
}

export interface JoinGameRequest {
  gameId: string;
  playerName: string;
}

export interface JoinGameResponse {
  sessionId: string;
  player: { id: string; name: string; color: string };
  gameState: GameState;
  opponent: { id: string; name: string; isAi: boolean; difficulty?: number };
}