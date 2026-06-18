// frontend/src/components/GamePanel/GamePanel.tsx
// Game information sidebar component

import React from 'react';
import { GameState } from '@/types/game';
import styles from './GamePanel.module.css';

interface GamePanelProps {
    gameState: GameState | null;
    onForfeit: () => void;
    onMainMenu: () => void;
    latency: number;
}

export const GamePanel: React.FC<GamePanelProps> = ({
    gameState,
    onForfeit,
    onMainMenu,
    latency,
}) => {
    if (!gameState) return null;

    const currentPlayer = gameState.session.currentTurn === 'w' ? 'White' : 'Black';
    const moveCount = gameState.session.moveCount;

    return (
        <div className={styles.gamePanel}>
            {/* Game Info */}
            <div className={styles.section}>
                <h3>Game Info</h3>
                <div className={styles.info}>
                    <div className={styles.infoRow}>
                        <span className={styles.label}>Current Turn:</span>
                        <span className={styles.value}>{currentPlayer}</span>
                    </div>
                    <div className={styles.infoRow}>
                        <span className={styles.label}>Move Count:</span>
                        <span className={styles.value}>{moveCount}</span>
                    </div>
                    <div className={styles.infoRow}>
                        <span className={styles.label}>Status:</span>
                        <span className={styles.value}>{gameState.session.status}</span>
                    </div>
                    <div className={styles.infoRow}>
                        <span className={styles.label}>Latency:</span>
                        <span className={styles.value}>{latency}ms</span>
                    </div>
                </div>
            </div>

            {/* Players */}
            <div className={styles.section}>
                <h3>Players</h3>
                <div className={styles.player}>
                    <div className={styles.playerColor}>⚪</div>
                    <div className={styles.playerInfo}>
                        <div className={styles.playerName}>{gameState.session.players[0].name}</div>
                        <div className={styles.playerStatus}>
                            {gameState.session.players[0].connectionStatus}
                        </div>
                    </div>
                </div>
                <div className={styles.player}>
                    <div className={styles.playerColor}>⚫</div>
                    <div className={styles.playerInfo}>
                        <div className={styles.playerName}>{gameState.session.players[1].name}</div>
                        <div className={styles.playerStatus}>
                            {gameState.session.players[1].connectionStatus}
                        </div>
                    </div>
                </div>
            </div>

            {/* Move History */}
            <div className={styles.section}>
                <h3>Move History</h3>
                <div className={styles.moveHistory}>
                    {gameState.moves.length === 0 ? (
                        <p className={styles.noMoves}>No moves yet</p>
                    ) : (
                        gameState.moves.map((move, index) => (
                            <div key={index} className={styles.move}>
                                <span className={styles.moveNumber}>{index + 1}.</span>
                                <span className={styles.moveNotation}>
                                    {move.from}-{move.to}
                                </span>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Actions */}
            <div className={styles.section}>
                <h3>Actions</h3>
                <div className={styles.actions}>
                    <button className={`${styles.button} ${styles.buttonDanger}`} onClick={onForfeit}>
                        Forfeit Game
                    </button>
                    <button className={`${styles.button} ${styles.buttonSecondary}`} onClick={onMainMenu}>
                        Main Menu
                    </button>
                </div>
            </div>
        </div>
    );
};

export default GamePanel;