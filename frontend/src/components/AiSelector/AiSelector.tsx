import React, { useState } from 'react';
import styles from './AiSelector.module.css';

interface AiSelectorProps {
    onSelect: (difficulty: number) => void;
    onBack: () => void;
}

export const AiSelector: React.FC<AiSelectorProps> = ({ onSelect, onBack }) => {
    const [difficulty, setDifficulty] = useState(5);

    const difficultyLabels: Record<number, string> = {
        1: 'Beginner',
        2: 'Novice',
        3: 'Easy',
        4: 'Intermediate',
        5: 'Intermediate+',
        6: 'Advanced',
        7: 'Expert',
        8: 'Master',
        9: 'Grandmaster',
        10: 'Supercomputer',
    };

    return (
        <div className={styles.aiSelector}>
            <div className={styles.card}>
                <h2>Select AI Difficulty</h2>

                <div className={styles.sliderContainer}>
                    <input
                        type="range"
                        min="1"
                        max="10"
                        value={difficulty}
                        onChange={(e) => setDifficulty(parseInt(e.target.value))}
                        className={styles.slider}
                    />

                    <div className={styles.difficultyDisplay}>
                        <div className={styles.difficultyValue}>{difficulty}</div>
                        <div className={styles.difficultyLabel}>{difficultyLabels[difficulty]}</div>
                    </div>
                </div>

                <div className={styles.description}>
                    {difficulty === 1 && '🟢 Perfect for beginners. AI makes obvious mistakes.'}
                    {difficulty === 2 && '🟢 Beginner level. AI plays weakly but not totally random.'}
                    {difficulty === 3 && '🟡 Easy level. Casual players can win.'}
                    {difficulty === 4 && '🟡 Intermediate. Fair challenge for casual players.'}
                    {difficulty === 5 && '🟡 Standard level. Good balanced challenge.'}
                    {difficulty === 6 && '🟠 Advanced. Strong opponent. Requires good strategy.'}
                    {difficulty === 7 && '🟠 Expert. Very strong. Few humans can beat this.'}
                    {difficulty === 8 && '🔴 Master level. Extremely difficult. Rare wins.'}
                    {difficulty === 9 && '🔴 Grandmaster. Nearly unbeatable.'}
                    {difficulty === 10 && '⚫ Supercomputer. Virtually impossible to beat.'}
                </div>

                <div className={styles.actions}>
                    <button className={`${styles.btn} ${styles.btnPrimary}`} onClick={() => onSelect(difficulty)}>
                        Start Game
                    </button>
                    <button className={`${styles.btn} ${styles.btnSecondary}`} onClick={onBack}>
                        Back
                    </button>
                </div>
            </div>
        </div>
    );
};

export default AiSelector;