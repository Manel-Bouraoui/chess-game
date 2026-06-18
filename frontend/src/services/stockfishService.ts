
/**
 * Initialize Stockfish AI engine
 */
export async function initializeStockfish(): Promise<any> {
    return new Promise((resolve, reject) => {
        try {
            // @ts-ignore - Stockfish loaded globally from CDN
            if (typeof window.Stockfish === 'function') {
                // @ts-ignore
                window.Stockfish().then((engine: any) => {
                    console.log('✅ Stockfish initialized');
                    resolve(engine);
                }).catch((error: any) => {
                    console.error('❌ Stockfish initialization error:', error);
                    reject(error);
                });
            } else {
                reject(new Error('Stockfish not loaded'));
            }
        } catch (error) {
            console.error('❌ Stockfish initialization error:', error);
            reject(error);
        }
    });
}

/**
 * Get best move from Stockfish
 */
export async function getBestMove(
    engine: any,
    fen: string,
    difficulty: number
): Promise<{ move: string; evaluation: number; depth: number }> {
    return new Promise((resolve, reject) => {
        try {
            const depth = mapDifficultyToDepth(difficulty);

            // Build command sequence
            const commands = [
                'uci',
                'isready',
                `setoption name Skill Level value ${Math.min(20, (difficulty - 1) * 2)}`,
                `position fen ${fen}`,
                `go depth ${depth}`,
            ];

            let bestMove = '';
            let evaluation = 0;
            let searchDepth = depth;

            // Send commands
            commands.forEach(cmd => {
                if (engine.postMessage) {
                    engine.postMessage(cmd);
                }
            });

            // Handle response
            const handleMessage = (event: any) => {
                const output = event.data || '';

                if (output.includes('bestmove')) {
                    const match = output.match(/bestmove (\S+)/);
                    if (match) {
                        bestMove = match[1];
                    }

                    if (engine.removeEventListener) {
                        engine.removeEventListener('message', handleMessage);
                    }

                    resolve({
                        move: bestMove,
                        evaluation,
                        depth: searchDepth,
                    });
                }

                // Parse evaluation
                if (output.includes('score cp')) {
                    const evalMatch = output.match(/score cp (-?\d+)/);
                    if (evalMatch) {
                        evaluation = parseInt(evalMatch[1], 10);
                    }
                }
            };

            if (engine.addEventListener) {
                engine.addEventListener('message', handleMessage);
            } else if (engine.onmessage) {
                engine.onmessage = handleMessage;
            }

            // Timeout after 30 seconds
            setTimeout(() => {
                resolve({
                    move: bestMove || 'e2e4',
                    evaluation,
                    depth: searchDepth,
                });
            }, 30000);
        } catch (error) {
            console.error('❌ Error getting best move:', error);
            reject(error);
        }
    });
}

/**
 * Map difficulty level (1-10) to search depth
 */
function mapDifficultyToDepth(difficulty: number): number {
    const depthMap: Record<number, number> = {
        1: 4,
        2: 6,
        3: 8,
        4: 10,
        5: 12,
        6: 14,
        7: 16,
        8: 18,
        9: 20,
        10: 22,
    };
    return depthMap[Math.max(1, Math.min(10, difficulty))] || 12;
}