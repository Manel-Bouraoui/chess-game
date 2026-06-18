import { GameState, GameSession, GameMode, GameStatus, PlayerColor, Player } from '@/types/game';
import { ServerMessage } from '@/types/messages';

export function mapBackendStatus(backendStatus: string): GameStatus {
  const statusMap: Record<string, GameStatus> = {
    WAITING: GameStatus.WAITING,
    IN_PROGRESS: GameStatus.IN_PROGRESS,
    CHECKMATE: GameStatus.CHECKMATE,
    STALEMATE: GameStatus.STALEMATE,
    DRAW: GameStatus.DRAW,
    ABANDONED: GameStatus.ABANDONED,
  };

  return statusMap[backendStatus] || GameStatus.IN_PROGRESS;
}

/** Maps backend GameStateDTO JSON to frontend GameState. */
export function adaptBackendGameState(backendGameState: Record<string, unknown>): GameState {
  if (
    !backendGameState?.whitePlayer ||
    typeof backendGameState.whitePlayer !== 'object' ||
    !backendGameState?.blackPlayer ||
    typeof backendGameState.blackPlayer !== 'object'
  ) {
    throw new Error('Invalid game state received from server');
  }

  const wp = backendGameState.whitePlayer as Record<string, unknown>;
  const bp = backendGameState.blackPlayer as Record<string, unknown>;

  const players: [Player, Player] = [
    {
      id: String(wp.id),
      name: String(wp.name ?? ''),
      color: PlayerColor.WHITE,
      isAi: Boolean(wp.isAi ?? wp.ai ?? false),
      difficulty: typeof wp.difficulty === 'number' ? wp.difficulty : undefined,
      connectionStatus: (wp.connectionStatus as Player['connectionStatus']) || 'connected',
    },
    {
      id: String(bp.id),
      name: String(bp.name ?? ''),
      color: PlayerColor.BLACK,
      isAi: Boolean(bp.isAi ?? bp.ai ?? false),
      difficulty: typeof bp.difficulty === 'number' ? bp.difficulty : undefined,
      connectionStatus: (bp.connectionStatus as Player['connectionStatus']) || 'connected',
    },
  ];

  const modeRaw = String(backendGameState.mode ?? '').toLowerCase();
  const session: GameSession = {
    id: String(backendGameState.gameId ?? ''),
    mode: modeRaw === 'pve' ? GameMode.PVE : GameMode.PVP,
    status: mapBackendStatus(String(backendGameState.status ?? '')),
    players,
    currentTurn:
      backendGameState.currentTurnColor === 'w' ? PlayerColor.WHITE : PlayerColor.BLACK,
    moveCount: Number(backendGameState.moveCount ?? 0),
    createdAt: Number(backendGameState.createdAt ?? Date.now()),
    updatedAt: Number(backendGameState.updatedAt ?? Date.now()),
  };

  return {
    session,
    fen:
      String(backendGameState.fen) ||
      'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
    moves: (backendGameState.moves as GameState['moves']) || [],
    lastMove: backendGameState.lastMove as GameState['lastMove'],
    inCheck: Boolean(backendGameState.inCheck),
    inCheckmate: Boolean(backendGameState.inCheckmate),
    inStalemate: Boolean(backendGameState.inStalemate),
    legalMoves: (backendGameState.legalMoves as string[]) || [],
  };
}

/** Accepts wrapped ServerMessage or raw DTO from WebSocket. */
export function extractGameStateFromMessage(message: ServerMessage | Record<string, unknown>): Record<
  string,
  unknown
> | null {
  const rawPayload =
    message && typeof message === 'object' && 'payload' in message
      ? (message as ServerMessage).payload
      : message;

  if (!rawPayload || typeof rawPayload !== 'object') return null;
  const obj = rawPayload as Record<string, unknown>;

  if (obj.gameState && typeof obj.gameState === 'object') {
    return obj.gameState as Record<string, unknown>;
  }
  if (typeof obj.gameId === 'string' && typeof obj.fen === 'string') {
    return obj;
  }
  return null;
}
