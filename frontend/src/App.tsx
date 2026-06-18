import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { ChessBoard } from '@/components/ChessBoard/ChessBoard';
import { GameMode, GameStatus, PlayerColor, Move } from '@/types/game';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useChessGame } from '@/hooks/useChessGame';
import { useStockfish } from '@/hooks/useStockfish';
import { STOMP_ENDPOINTS, STOMP_TOPICS } from '@/types/messages';
import { adaptBackendGameState, extractGameStateFromMessage } from '@/utils/backendGameAdapter';
import { parseUciMove } from '@/utils/uci';
import { Square } from 'chess.js';
import axios from 'axios';
import './App.css';

const API_BASE_URL = import.meta.env.VITE_API_URL || window.location.origin;
const http = axios.create({ baseURL: API_BASE_URL, timeout: 10000 });

function App() {
  const [gameMode, setGameMode] = useState<GameMode | null>(null);
  const [gameId, setGameId] = useState<string | null>(null);
  const [joinGameId, setJoinGameId] = useState('');
  const [aiDifficulty, setAiDifficulty] = useState(5);
  const [playerName, setPlayerName] = useState('');
  const [screen, setScreen] = useState<'menu' | 'setup' | 'lobby' | 'join' | 'game'>('menu');
  const [forfeitedBy, setForfeitedBy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [localPlayerId, setLocalPlayerId] = useState<string>(() => localStorage.getItem('playerId') || '');

  const ws = useWebSocket({
    autoConnect: true,
    debug: false,
    onConnect: () => {
      console.log('Connected to WebSocket');
    },
    onError: (message) => setError(message),
  });

  const game = useChessGame({
    mode: gameMode || GameMode.PVP,
    localPlayerId,
    gameId: gameId || '',
  });

  const stockfish = useStockfish({
    difficulty: aiDifficulty,
    onReady: () => undefined,
  });

  const gameRef = useRef(game);
  gameRef.current = game;
  const screenRef = useRef(screen);
  screenRef.current = screen;

  const forfeitedByRef = useRef<string | null>(null);
  forfeitedByRef.current = forfeitedBy;

  const subscribeToGameUpdates = useCallback((gId: string) => {
    console.log('Subscribing to game updates for', gId);

    try {
      ws.subscribe(STOMP_TOPICS.PLAYER_MOVES.replace('{gameId}', gId), (message) => {
        const nextState = extractGameStateFromMessage(message);
        if (nextState) {
          const adapted = adaptBackendGameState(nextState);
          gameRef.current.syncGameState(adapted);
        }
      });

      ws.subscribe(STOMP_TOPICS.GAME_STATE.replace('{gameId}', gId), (message) => {
        const nextState = extractGameStateFromMessage(message);
        if (nextState) {
          const adapted = adaptBackendGameState(nextState);
          gameRef.current.syncGameState(adapted);

          if (adapted.session.status === GameStatus.IN_PROGRESS && screenRef.current === 'lobby') {
            setScreen('game');
          }

          // Forfeit received from server — show game-over modal on BOTH sides
          const statusStr = String((nextState as Record<string, unknown>).status ?? '');
          if ((statusStr === 'FORFEIT' || statusStr === 'ABANDONED') && forfeitedByRef.current === null) {
            // Determine who forfeited: it's the player who is NOT the local player
            // (the local forfeiter already set forfeitedBy via the button click)
            const localId = localStorage.getItem('playerId') ?? '';
            const forfeiter = adapted.session.players.find(p => p.id !== localId);
            setForfeitedBy(forfeiter?.name ?? 'Opponent');
          }
        }
      });

      ws.subscribe(STOMP_TOPICS.PLAYER_CONNECTED.replace('{gameId}', gId), (msg) => {
        console.log('Player status:', msg.payload);
      });
    } catch (error) {
      console.error('Failed to subscribe to game updates:', error);
      throw error;
    }
  }, [ws]);

  const subscribeWithRetry = useCallback((targetGameId: string) => {
    // The WebSocket hook queues operations automatically when not connected.
    // Just call subscribeToGameUpdates directly — it will queue if not connected
    // and flush automatically once the connection is established.
    subscribeToGameUpdates(targetGameId);
  }, [subscribeToGameUpdates]);

  const createGame = async (mode: GameMode) => {
    setError(null);
    if (!playerName.trim()) {
      setError('Please enter your name.');
      return;
    }

    try {
      const response = await http.post('/api/games/create', {
        mode: mode === GameMode.PVE ? 'pve' : 'pvp',
        playerName,
        aiDifficulty: mode === GameMode.PVE ? aiDifficulty : undefined,
      });

      const { gameId: newGameId, initialGameState } = response.data;
      if (!newGameId || !initialGameState) {
        throw new Error('Invalid response format: missing gameId or initialGameState');
      }
      
      setGameId(newGameId);
      setGameMode(mode);
      const adaptedGameState = adaptBackendGameState(initialGameState);

      const playerId = adaptedGameState.session.players[0].id; // White player
      localStorage.setItem('playerId', playerId);
      localStorage.setItem('gameId', newGameId);
      setLocalPlayerId(playerId);

      game.initGame(adaptedGameState, PlayerColor.WHITE);
      subscribeWithRetry(newGameId);

      setScreen(mode === GameMode.PVE ? 'game' : 'lobby');
    } catch (error: any) {
      const message = error?.response?.data?.error || error?.message || 'Failed to create game';
      setError(message);
    }
  };

  const joinGame = async (targetGameId: string) => {
    setError(null);
    if (!playerName.trim()) {
      setError('Please enter your name.');
      return;
    }

    try {
      const response = await http.post(
        `/api/games/${targetGameId}/join`,
        { playerName }
      );

      const { gameId: newGameId, gameState } = response.data;
      setGameId(newGameId);
      setGameMode(GameMode.PVP);

      // Convert backend format to frontend format
      const adaptedGameState = adaptBackendGameState(gameState);

      const playerId = adaptedGameState.session.players[1].id; // Black player
      localStorage.setItem('playerId', playerId);
      localStorage.setItem('gameId', newGameId);
      setLocalPlayerId(playerId);

      game.initGame(adaptedGameState, PlayerColor.BLACK);
      subscribeWithRetry(newGameId);

      setScreen('game');
    } catch (error: any) {
      const message = error?.response?.data?.error || error?.message || 'Failed to join game';
      setError(message);
    }
  };

  const requestStateSync = useCallback((targetGameId: string) => {
    ws.send(STOMP_ENDPOINTS.REQUEST_GAME_STATE, {
      gameId: targetGameId,
      timestamp: Date.now(),
    });
  }, [ws]);

  const makeAiMove = useCallback(async () => {
    if (!gameId || gameMode !== GameMode.PVE || !game.gameState) return;

    try {
      game.setThinking(true);
      const fen = game.chess.fen();

      try {
        const res = await http.get(`/api/games/${gameId}/ai-move`, {
          params: { difficulty: aiDifficulty, fen },
        });
        const uci =
          res.data?.success && res.data?.move?.move ? String(res.data.move.move) : '';
        if (uci && uci !== '(none)') {
          const parsed = parseUciMove(uci);
          if (parsed) {
            const move: Move = {
              from: parsed.from as Square,
              to: parsed.to as Square,
              promotion: parsed.promotion as Move['promotion'],
              timestamp: Date.now(),
            };
            if (game.makeMove(move)) {
              ws.send(STOMP_ENDPOINTS.SEND_MOVE, {
                gameId,
                playerId: game.gameState.session.players[1].id,
                from: move.from,
                to: move.to,
                promotion: move.promotion,
                timestamp: Date.now(),
              });
            }
            game.setThinking(false);
            return;
          }
        }
      } catch {
        // Fall back to local random move when engine is unavailable or request fails.
      }

      const legalMoves = game.chess.moves({ verbose: true });
      if (legalMoves.length === 0) {
        game.setThinking(false);
        return;
      }

      const randomMove = legalMoves[Math.floor(Math.random() * legalMoves.length)];
      const move: Move = {
        from: randomMove.from as Square,
        to: randomMove.to as Square,
        promotion: randomMove.promotion as Move['promotion'],
        timestamp: Date.now(),
      };

      game.makeMove(move);
      ws.send(STOMP_ENDPOINTS.SEND_MOVE, {
        gameId,
        playerId: game.gameState.session.players[1].id,
        from: move.from,
        to: move.to,
        promotion: move.promotion,
        timestamp: Date.now(),
      });

      game.setThinking(false);
    } catch {
      game.setThinking(false);
      requestStateSync(gameId);
    }
  }, [gameId, gameMode, aiDifficulty, game, ws, requestStateSync]);

  const handleMoveAttempt = useCallback(async (move: any): Promise<boolean> => {
    if (!gameId) {
      setError('No game ID available.');
      return false;
    }

    try {
      ws.send(STOMP_ENDPOINTS.SEND_MOVE, {
        gameId,
        playerId: localPlayerId,
        from: move.from,
        to: move.to,
        promotion: move.promotion,
        timestamp: Date.now(),
      });

      // Only trigger AI in PvE mode
      if (gameMode === GameMode.PVE) {
        setTimeout(() => {
          void makeAiMove();
        }, 700);
      }
      
      return true;
    } catch (error) {
      requestStateSync(gameId);
      setError('Failed to send move. Resyncing game state...');
      return false;
    }
  }, [gameId, gameMode, localPlayerId, ws, makeAiMove, requestStateSync]);

  const forfeitGame = () => {
    if (!gameId || !localPlayerId) return;

    if (confirm('Are you sure you want to forfeit?')) {
      ws.send(STOMP_ENDPOINTS.FORFEIT, { gameId, playerId: localPlayerId });
      // Show modal immediately for the forfeiting player
      const localPlayer = game.gameState?.session.players.find(p => p.id === localPlayerId);
      setForfeitedBy(localPlayer?.name ?? 'You');
    }
  };

  const resetGame = () => {
    game.reset();
    setGameId(null);
    setGameMode(null);
    setScreen('menu');
    setError(null);
    setForfeitedBy(null);
    localStorage.removeItem('gameId');
  };

  useEffect(() => {
    if (!gameId || screen !== 'lobby') return;
    const poll = setInterval(async () => {
      try {
        const response = await http.get(`/api/games/${gameId}`);
        const next = adaptBackendGameState(response.data.game);
        game.syncGameState(next);
        if (next.session.status === GameStatus.IN_PROGRESS) {
          setScreen('game');
        }
      } catch (err) {
        console.error('Lobby poll failed', err);
      }
    }, 2000);
    return () => clearInterval(poll);
  }, [gameId, screen, game]);

  // Captured pieces come directly from the reducer (proper React state)
  const capturedByWhite = game.capturedByWhite;
  const capturedByBlack = game.capturedByBlack;

  const statusText = useMemo(() => {
    if (!game.gameState) return '';
    if (game.chess.isCheckmate()) return game.chess.turn() === 'w' ? '⚫ Black wins by checkmate!' : '⚪ White wins by checkmate!';
    if (game.chess.isStalemate()) return '🤝 Stalemate — Draw!';
    if (game.chess.isDraw()) return '🤝 Draw!';
    if (game.chess.inCheck()) return game.chess.turn() === 'w' ? '⚠️ White is in check!' : '⚠️ Black is in check!';
    if (gameMode === GameMode.PVE) {
      return game.isMyTurn ? '⚪ Your turn (White)' : '⚫ AI is thinking... (Black)';
    }
    return game.isMyTurn ? '🟢 Your turn' : '⏳ Opponent\'s turn';
  }, [game.gameState, game.isMyTurn, game.chess, gameMode]);

  return (
    <div className="app-container">
      {/* Main Content */}
      {screen === 'menu' && (
        <div className="menu-screen">
          <div className="menu-card">
            <h1 className="menu-title">♟️ Chess Arena</h1>
            <p className="menu-subtitle">Play against friends or challenge the AI</p>

            <input
              type="text"
              placeholder="Enter your name"
              value={playerName}
              onChange={(e) => setPlayerName(e.target.value)}
              className="menu-input"
            />

            <div className="menu-buttons">
              <button
                onClick={() => createGame(GameMode.PVP)}
                className="btn btn-primary"
              >
                Create Game
              </button>

              <button
                onClick={() => setScreen('join')}
                className="btn btn-secondary"
              >
                Join Game
              </button>

              <button
                onClick={() => {
                  setGameMode(GameMode.PVE);
                  setError(null);
                  setScreen('setup');
                }}
                className="btn btn-secondary"
              >
                Play vs AI
              </button>
            </div>
          </div>
        </div>
      )}

      {/* AI Setup Screen */}
      {screen === 'setup' && (
        <div className="menu-screen">
          <div className="menu-card">
            <h2>Select Difficulty</h2>
            <p>Choose how challenging you want the AI to be</p>

            <div className="difficulty-slider">
              <input
                type="range"
                min="1"
                max="10"
                value={aiDifficulty}
                onChange={(e) => setAiDifficulty(parseInt(e.target.value))}
                className="slider"
              />
              <div className="difficulty-labels">
                <span>Easy (1)</span>
                <span className="difficulty-value">{aiDifficulty}</span>
                <span>Hard (10)</span>
              </div>
            </div>

            <div className="menu-buttons">
              <button
                onClick={() => createGame(GameMode.PVE)}
                className="btn btn-primary"
              >
                Start Game
              </button>
              <button
                onClick={() => setScreen('menu')}
                className="btn btn-outline"
              >
                Back
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Join Game Screen */}
      {screen === 'join' && (
        <div className="menu-screen">
          <div className="menu-card">
            <h2>Join Game</h2>
            <p>Enter the game ID shared by your friend</p>

            <input
              type="text"
              placeholder="Enter game ID"
              value={joinGameId}
              onChange={(e) => setJoinGameId(e.target.value)}
              className="menu-input"
            />

            <div className="menu-buttons">
              <button
                onClick={() => joinGame(joinGameId)}
                className="btn btn-primary"
                disabled={!joinGameId.trim()}
              >
                Join Game
              </button>
              <button
                onClick={() => setScreen('menu')}
                className="btn btn-outline"
              >
                Back
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Lobby Screen (PvP waiting) */}
      {screen === 'lobby' && (
        <div className="menu-screen">
          <div className="menu-card">
            <h2>Waiting for opponent...</h2>
            <p className="game-id">Share this ID: <code>{gameId}</code></p>

            <div className="menu-buttons">
              <button
                onClick={() => resetGame()}
                className="btn btn-outline"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Game Screen */}
      {screen === 'game' && game.gameState && (
        <div className="game-screen">
          <div className="game-header">
            <h1>Chess Arena</h1>
            <p className="game-status">{statusText}</p>
          </div>

          <div className="game-content">
            <div style={{ position: 'relative' }}>
              <ChessBoard
                game={game}
                onMoveAttempt={handleMoveAttempt}
                disabled={game.chess.isGameOver() || (gameMode === GameMode.PVP && !game.isMyTurn)}
                isThinking={stockfish.isThinking || game.uiState.isThinking}
                gameMode={gameMode ? gameMode.toLowerCase() : 'pvp'}
                localPlayerId={localPlayerId}
              />

              {/* Check warning banner */}
              {game.chess.inCheck() && !game.chess.isCheckmate() && (
                <div style={{
                  position: 'absolute', top: '8px', left: '50%',
                  transform: 'translateX(-50%)',
                  background: '#dc2626', color: '#fff',
                  padding: '6px 20px', borderRadius: '20px',
                  fontWeight: 'bold', fontSize: '14px', zIndex: 10,
                  whiteSpace: 'nowrap',
                  boxShadow: '0 2px 12px rgba(220,38,38,0.5)',
                  animation: 'pulse 1s infinite',
                }}>
                  ⚠️ {game.chess.turn() === 'w' ? 'White' : 'Black'} is in Check!
                </div>
              )}
            </div>

            <div className="game-panel">
              <h3>Game Info</h3>
              <div className="game-info">
                <p><strong>Turn:</strong> {game.chess.turn() === 'w' ? '⚪ White' : '⚫ Black'}</p>
                <p><strong>Moves:</strong> {game.gameState.moves.length}</p>
                <p><strong>Status:</strong> {
                  game.chess.isCheckmate() ? '🏁 Checkmate' :
                  game.chess.isStalemate() ? '🤝 Stalemate' :
                  game.chess.isDraw() ? '🤝 Draw' :
                  game.chess.inCheck() ? '⚠️ Check' :
                  'In Progress'
                }</p>
              </div>

              {/* Captured pieces */}
              <div style={{ marginTop: '12px' }}>
                <h4 style={{ fontSize: '0.75rem', color: '#888', textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '8px' }}>Captured Pieces</h4>
                <div style={{ marginBottom: '6px' }}>
                  <span style={{ fontSize: '11px', color: '#666', marginRight: '6px' }}>⚪ White took:</span>
                  <span style={{ fontSize: '16px', letterSpacing: '2px' }}>
                    {capturedByWhite.length > 0 ? capturedByWhite.join('') : <span style={{ color: '#444', fontSize: '11px' }}>none</span>}
                  </span>
                </div>
                <div>
                  <span style={{ fontSize: '11px', color: '#666', marginRight: '6px' }}>⚫ Black took:</span>
                  <span style={{ fontSize: '16px', letterSpacing: '2px' }}>
                    {capturedByBlack.length > 0 ? capturedByBlack.join('') : <span style={{ color: '#444', fontSize: '11px' }}>none</span>}
                  </span>
                </div>
              </div>

              <div className="move-history">
                <h4>Move History</h4>
                <div className="moves-list">
                  {(() => {
                    const history = game.chess.history({ verbose: true });
                    if (history.length === 0) {
                      return <span style={{ color: '#666', fontSize: '12px' }}>No moves yet</span>;
                    }
                    const pairs: React.ReactElement[] = [];
                    for (let i = 0; i < history.length; i += 2) {
                      const white = history[i];
                      const black = history[i + 1];
                      pairs.push(
                        <div key={i} className="move" style={{ display: 'flex', gap: '8px', padding: '3px 0', borderBottom: '1px solid #333' }}>
                          <span style={{ color: '#666', minWidth: '24px' }}>{Math.floor(i / 2) + 1}.</span>
                          <span style={{ color: '#fff', minWidth: '60px' }}>{white?.san ?? ''}</span>
                          <span style={{ color: '#aaa', minWidth: '60px' }}>{black?.san ?? ''}</span>
                        </div>
                      );
                    }
                    return pairs;
                  })()}
                </div>
              </div>

              <div className="game-actions">
                <button onClick={forfeitGame} className="btn btn-danger">Forfeit</button>
                <button onClick={resetGame} className="btn btn-outline">Main Menu</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Game Over Modal (chess.com style) ── */}
      {screen === 'game' && game.gameState && (game.chess.isGameOver() || forfeitedBy !== null) && (
        <div style={{
          position: 'fixed', inset: 0,
          background: 'rgba(0,0,0,0.85)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000, backdropFilter: 'blur(4px)',
        }}>
          <div style={{
            background: '#1e1e1e', border: '1px solid #444',
            borderRadius: '16px', padding: '40px 48px',
            maxWidth: '420px', width: '90%', textAlign: 'center',
            boxShadow: '0 24px 80px rgba(0,0,0,0.8)',
          }}>
            <div style={{ fontSize: '4rem', marginBottom: '12px' }}>
              {forfeitedBy ? '🏳️' : game.chess.isCheckmate() ? '🏆' : '🤝'}
            </div>

            <h2 style={{ color: '#fff', fontSize: '2rem', fontWeight: 700, marginBottom: '6px' }}>
              {forfeitedBy
                ? `${forfeitedBy} Forfeits!`
                : game.chess.isCheckmate()
                  ? (game.chess.turn() === 'w' ? 'Black Wins!' : 'White Wins!')
                  : game.chess.isStalemate() ? 'Stalemate!'
                  : 'Draw!'}
            </h2>

            <p style={{ color: '#888', fontSize: '1rem', marginBottom: '28px' }}>
              {forfeitedBy ? 'by Forfeit'
                : game.chess.isCheckmate() ? 'by Checkmate'
                : game.chess.isStalemate() ? 'No legal moves available'
                : game.chess.isInsufficientMaterial() ? 'Insufficient material'
                : game.chess.isThreefoldRepetition() ? 'Threefold repetition'
                : 'by Draw'}
            </p>

            {/* Score card */}
            <div style={{
              display: 'flex', justifyContent: 'center', gap: '24px',
              background: '#2a2a2a', borderRadius: '12px',
              padding: '16px 24px', marginBottom: '28px',
            }}>
              {[
                { icon: '⚪', name: game.gameState.session.players[0]?.name ?? 'White', color: 'w' },
                { icon: '⚫', name: game.gameState.session.players[1]?.name ?? 'Black', color: 'b' },
              ].map(({ icon, name, color }) => {
                // Simple, correct score logic
                const thisPlayer = game.gameState!.session.players.find(p => p.color === color);
                let won = false;
                let lost = false;

                if (forfeitedBy !== null) {
                  // Forfeiter loses (0), other player wins (1)
                  lost = thisPlayer?.name === forfeitedBy;
                  won = !lost;
                } else if (game.chess.isCheckmate()) {
                  // Checkmated player (whose turn it is) loses
                  won = game.chess.turn() !== color;
                  lost = game.chess.turn() === color;
                }
                // Draw: both stay false → ½

                const score = won ? '1' : lost ? '0' : '½';
                return (
                  <div key={color} style={{ textAlign: 'center', flex: 1 }}>
                    <div style={{ fontSize: '1.8rem' }}>{icon}</div>
                    <div style={{ color: '#fff', fontWeight: 600, marginTop: '4px', fontSize: '0.9rem' }}>{name}</div>
                    <div style={{
                      color: won ? '#4ade80' : lost ? '#f87171' : '#facc15',
                      fontSize: '1.5rem', fontWeight: 700, marginTop: '4px',
                    }}>{score}</div>
                  </div>
                );
              })}
            </div>

            <p style={{ color: '#555', fontSize: '0.85rem', marginBottom: '28px' }}>
              Game ended after {game.chess.history().length} moves
            </p>

            <div style={{ display: 'flex', gap: '12px' }}>
              <button onClick={resetGame} className="btn btn-primary" style={{ flex: 1 }}>
                🔄 Play Again
              </button>
              <button onClick={resetGame} className="btn btn-outline" style={{ flex: 1 }}>
                🏠 Main Menu
              </button>
            </div>
          </div>
        </div>
      )}
      {error && (
        <div style={{ position: 'fixed', bottom: 12, right: 12, background: '#7f1d1d', color: '#fff', padding: '10px 14px', borderRadius: 8 }}>
          {error}
        </div>
      )}
    </div>
  );
}

export default App;