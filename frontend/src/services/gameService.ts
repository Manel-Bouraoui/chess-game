import axios, { type AxiosInstance } from 'axios';
import { GameState, GameMode } from '@/types/game';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

class GameService {
  private api: AxiosInstance;

  constructor() {
    this.api = axios.create({
      baseURL: `${API_BASE_URL}/api`,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      timeout: 10000,
    });
  }

  /**
   * Create a new game (PvP or PvE)
   */
  async createGame(mode: GameMode, playerName: string, aiDifficulty?: number) {
    try {
      const response = await this.api.post('/games/create', {
        mode: mode === GameMode.PVP ? 'pvp' : 'pve',
        playerName,
        aiDifficulty: aiDifficulty || 5,
      });

      return {
        gameId: response.data.gameId,
        initialGameState: response.data.initialGameState,
      };
    } catch (error) {
      console.error('Error creating game:', error);
      throw error;
    }
  }

  /**
   * Join an existing game (PvP)
   */
  async joinGame(gameId: string, playerName: string) {
    try {
      const response = await this.api.post(`/games/${gameId}/join`, {
        playerName,
      });

      return {
        gameId: response.data.gameId,
        gameState: response.data.gameState,
      };
    } catch (error) {
      console.error('Error joining game:', error);
      throw error;
    }
  }

  /**
   * Get current game state
   */
  async getGameState(gameId: string): Promise<GameState> {
    try {
      const response = await this.api.get(`/games/${gameId}`);
      return response.data.game;
    } catch (error) {
      console.error('Error getting game state:', error);
      throw error;
    }
  }

  /**
   * List available games (lobby)
   */
  async listGames() {
    try {
      const response = await this.api.get('/games/active');
      return response.data.games || [];
    } catch (error) {
      console.error('Error listing games:', error);
      throw error;
    }
  }

  /**
   * Forfeit a game
   */
  async forfeitGame(gameId: string, playerId: string) {
    try {
      const response = await this.api.post(`/games/${gameId}/forfeit`, { playerId });
      return response.data;
    } catch (error) {
      console.error('Error forfeiting game:', error);
      throw error;
    }
  }

  /**
   * Health check
   */
  async healthCheck() {
    try {
      const response = await this.api.get('/health');
      return response.data;
    } catch (error) {
      console.error('Error health check:', error);
      throw error;
    }
  }
}

export const gameService = new GameService();