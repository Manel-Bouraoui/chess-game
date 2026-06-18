// frontend/src/components/ChessBoard/ChessBoard.tsx
// Chess board with WORKING click-to-move and move highlighting

import React, { useCallback, useState, useEffect } from 'react';
import { Chessboard } from 'react-chessboard';
import { Square } from 'chess.js';
import { Move } from '@/types/game';
import { UseChessGameReturn } from '@/hooks/useChessGame';
import styles from './ChessBoard.module.css';

interface ChessBoardProps {
    game: UseChessGameReturn;
    onMoveAttempt: (move: Move) => Promise<boolean>;
    disabled?: boolean;
    isThinking?: boolean;
    gameMode?: string | null;
    localPlayerId?: string;
}

export const ChessBoard: React.FC<ChessBoardProps> = ({
    game,
    onMoveAttempt,
    disabled = false,
    isThinking = false,
    gameMode = 'pvp',
    localPlayerId = '',
}) => {
    const [selectedSquare, setSelectedSquare] = useState<Square | null>(null);
    const [possibleMoves, setPossibleMoves] = useState<Square[]>([]);

    // Use game's chess instance directly - no local backup
    const chess = game.chess;
    const localPlayer = game.gameState?.session.players.find(p => p.id === localPlayerId);
    const boardOrientation: 'white' | 'black' = localPlayer?.color === 'b' ? 'black' : 'white';

    // Update position when game state changes
    useEffect(() => {
        // Clear selection when position changes (after a move)
        setSelectedSquare(null);
        setPossibleMoves([]);
    }, [game.gameState?.fen]);

    // Handle square clicks - CORRECT API for react-chessboard
    const onSquareClick = useCallback(async (square: Square) => {
        if (isThinking || disabled) return;
        
        const currentTurn = chess.turn();
        
        // PvE: block AI's turn entirely
        if (gameMode === 'pve' && currentTurn === 'b') return;

        // PvP: each player can only move their own color
        if (gameMode === 'pvp') {
            const localPlayer = game.gameState?.session.players.find(p => p.id === localPlayerId);
            if (!localPlayer || currentTurn !== localPlayer.color) return;
        }
        
        // If no square is selected yet — select a piece of the right color
        if (!selectedSquare) {
            const piece = chess.get(square);
            if (!piece) return;
            
            // Only allow selecting pieces of current turn's color
            if (piece.color !== currentTurn) return;
            
            const moves = chess.moves({ square, verbose: true });
            if (moves.length > 0) {
                setSelectedSquare(square);
                setPossibleMoves(moves.map((m: any) => m.to));
            }
            return;
        }

        // A piece is already selected — handle the destination click
        if (selectedSquare === square) {
            setSelectedSquare(null);
            setPossibleMoves([]);
            return;
        }

        if (possibleMoves.includes(square)) {
            const move: Move = {
                from: selectedSquare,
                to: square,
                timestamp: Date.now(),
            };

            const success = game.makeMove(move);
            setSelectedSquare(null);
            setPossibleMoves([]);
            
            if (success) {
                void onMoveAttempt(move);
            }
            return;
        }

        // Switch selection to another piece of the right color
        const piece = chess.get(square);
        if (piece && piece.color === currentTurn) {
            const moves = chess.moves({ square, verbose: true });
            if (moves.length > 0) {
                setSelectedSquare(square);
                setPossibleMoves(moves.map((m: any) => m.to));
                return;
            }
        }

        setSelectedSquare(null);
        setPossibleMoves([]);
    }, [selectedSquare, possibleMoves, chess, game, onMoveAttempt, isThinking, disabled, gameMode, localPlayerId]);

    // Drag and drop handler - must be synchronous (react-chessboard requirement)
    const onPieceDrop = useCallback((sourceSquare: string, targetSquare: string): boolean => {
        if (isThinking || disabled) return false;
        
        const currentTurn = chess.turn();
        const piece = chess.get(sourceSquare as Square);
        if (!piece) return false;

        // PvE: block AI's turn
        if (gameMode === 'pve' && currentTurn === 'b') return false;
        if (gameMode === 'pve' && piece.color === 'b') return false;

        // PvP: only allow moving your own color
        if (gameMode === 'pvp') {
            const localPlayer = game.gameState?.session.players.find(p => p.id === localPlayerId);
            if (!localPlayer || piece.color !== localPlayer.color) return false;
            if (currentTurn !== localPlayer.color) return false;
        }
        
        const move: Move = {
            from: sourceSquare as Square,
            to: targetSquare as Square,
            timestamp: Date.now(),
        };

        const success = game.makeMove(move);
        if (success) {
            void onMoveAttempt(move);
            setSelectedSquare(null);
            setPossibleMoves([]);
            return true;
        }
        return false;
    }, [chess, game, onMoveAttempt, isThinking, disabled, gameMode, localPlayerId]);

    // Create square styles for highlighting
    const customSquareStyles = React.useMemo(() => {
        const styles: Record<string, React.CSSProperties> = {};
        
        // Highlight selected square in yellow
        if (selectedSquare) {
            styles[selectedSquare] = {
                backgroundColor: 'rgba(255, 255, 0, 0.4)',
            };
        }
        
        // Highlight possible moves with green circles
        possibleMoves.forEach(square => {
            styles[square] = {
                background: 'radial-gradient(circle, rgba(0, 255, 0, 0.5) 36%, transparent 40%)',
                borderRadius: '50%',
            };
        });
        
        return styles;
    }, [selectedSquare, possibleMoves]);

    const currentPosition = chess.fen();

    return (
        <div className={styles.chessboardContainer}>
            <div className={styles.boardWrapper}>
                <Chessboard
                    options={{
                        position: currentPosition,
                        onSquareClick: ({ square }) => void onSquareClick(square as Square),
                        onPieceDrop: ({ sourceSquare, targetSquare }) => onPieceDrop(sourceSquare, targetSquare),
                        boardOrientation,
                        allowDragging: !isThinking,
                        squareStyles: customSquareStyles,
                        animationDurationInMs: 200,
                    }}
                />
            </div>
        </div>
    );
};

export default ChessBoard;
