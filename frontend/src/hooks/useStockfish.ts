// frontend/src/hooks/useStockfish.ts
// Disabled Stockfish hook to prevent SharedArrayBuffer errors

import { useCallback, useState } from 'react';

interface UseStockfishOptions {
  difficulty: number;
  onReady?: () => void;
}

/**
 * Disabled Stockfish hook - returns dummy implementation
 * This prevents SharedArrayBuffer errors that crash the app
 */
export function useStockfish(options: UseStockfishOptions) {
  const [isThinking, setIsThinking] = useState(false);

  const getBestMove = useCallback(async (fen: string): Promise<string | null> => {
    // Return null - no AI moves for now
    return null;
  }, []);

  const stop = useCallback(() => {
    setIsThinking(false);
  }, []);

  return {
    isReady: true,
    isThinking,
    getBestMove,
    stop,
  };
}

export type UseStockfishReturn = ReturnType<typeof useStockfish>;