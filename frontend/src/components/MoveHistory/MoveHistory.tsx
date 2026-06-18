import React from 'react';
import { Move } from '@/types/game';
import styles from './MoveHistory.module.css';

interface MoveHistoryProps {
    moves: Move[];
    maxHeight?: number;
}

export const MoveHistory: React.FC<MoveHistoryProps> = ({ moves, maxHeight = 300 }) => {
    const groupedMoves = [];
    for (let i = 0; i < moves.length; i += 2) {
        groupedMoves.push({
            number: i / 2 + 1,
            whiteMove: moves[i],
            blackMove: moves[i + 1],
        });
    }

    return (
        <div className={styles.moveHistory} style={{ maxHeight: `${maxHeight}px` }}>
            <div className={styles.header}>
                <span className={styles.moveNum}>#</span>
                <span className={styles.whiteMove}>White</span>
                <span className={styles.blackMove}>Black</span>
            </div>

            <div className={styles.moves}>
                {moves.length === 0 ? (
                    <div className={styles.empty}>No moves yet</div>
                ) : (
                    groupedMoves.map((pair) => (
                        <div key={pair.number} className={styles.movePair}>
                            <span className={styles.moveNum}>{pair.number}.</span>
                            <span className={styles.moveText}>
                                {pair.whiteMove.from}-{pair.whiteMove.to}
                                {pair.whiteMove.promotion && `=${pair.whiteMove.promotion.toUpperCase()}`}
                            </span>
                            <span className={styles.moveText}>
                                {pair.blackMove
                                    ? `${pair.blackMove.from}-${pair.blackMove.to}${pair.blackMove.promotion ? `=${pair.blackMove.promotion.toUpperCase()}` : ''
                                    }`
                                    : '...'}
                            </span>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
};

export default MoveHistory;