import { describe, it, expect } from 'vitest';
import { adaptBackendGameState, extractGameStateFromMessage, mapBackendStatus } from './backendGameAdapter';
import { GameStatus, PlayerColor } from '../types/game';

describe('mapBackendStatus', () => {
  it('maps known statuses', () => {
    expect(mapBackendStatus('WAITING')).toBe(GameStatus.WAITING);
    expect(mapBackendStatus('IN_PROGRESS')).toBe(GameStatus.IN_PROGRESS);
    expect(mapBackendStatus('UNKNOWN')).toBe(GameStatus.IN_PROGRESS);
  });
});

describe('adaptBackendGameState', () => {
  it('maps a minimal valid DTO', () => {
    const dto = {
      gameId: 'g1',
      mode: 'PVP',
      status: 'IN_PROGRESS',
      fen: 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
      currentTurnColor: 'w',
      moveCount: 0,
      whitePlayer: { id: 'w1', name: 'A', isAi: false },
      blackPlayer: { id: 'b1', name: 'B', isAi: false },
      moves: [],
      inCheck: false,
      inCheckmate: false,
      inStalemate: false,
      legalMoves: [],
    };

    const gs = adaptBackendGameState(dto);
    expect(gs.session.id).toBe('g1');
    expect(gs.session.players[0].color).toBe(PlayerColor.WHITE);
    expect(gs.session.players[1].color).toBe(PlayerColor.BLACK);
    expect(gs.session.currentTurn).toBe(PlayerColor.WHITE);
  });

  it('throws when players missing', () => {
    expect(() => adaptBackendGameState({ gameId: 'x', fen: 'startpos' } as any)).toThrow();
  });
});

describe('extractGameStateFromMessage', () => {
  it('unwraps payload.gameState', () => {
    const inner = { gameId: 'g', fen: 'f', whitePlayer: {}, blackPlayer: {} };
    const m = { payload: { gameState: inner } };
    expect(extractGameStateFromMessage(m as any)).toEqual(inner);
  });

  it('accepts raw DTO', () => {
    const dto = { gameId: 'g', fen: 'f', whitePlayer: {}, blackPlayer: {} };
    expect(extractGameStateFromMessage(dto as any)).toEqual(dto);
  });
});
