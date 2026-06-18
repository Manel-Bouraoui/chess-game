package com.chess.backend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chess.backend.dto.AiMoveDTO;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * Service to interact with the Stockfish chess engine via UCI protocol.
 * Properly manages engine lifecycle with thread-safe operations.
 */
@Service
public class ChessEngineService {

    private static final Logger logger = LoggerFactory.getLogger(ChessEngineService.class);

    @Value("${stockfish.path:/usr/games/stockfish}")
    private String stockfishPath;

    private Process engineProcess;
    private BufferedWriter engineInput;
    private BufferedReader engineOutput;
    private final Object engineLock = new Object();
    private volatile boolean isInitialized = false;

    /**
     * Initialize Stockfish engine process on application startup
     */
    @PostConstruct
    public void initializeEngine() {
        synchronized (engineLock) {
            if (isInitialized && engineProcess != null && engineProcess.isAlive()) {
                logger.info("Stockfish engine already initialized");
                return;
            }

            try {
                logger.info("Initializing Stockfish engine from path: {}", stockfishPath);
                engineProcess = new ProcessBuilder(stockfishPath).start();
                engineInput = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
                engineOutput = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));

                sendCommand("uci");
                String line;
                boolean uciOk = false;
                long startTime = System.currentTimeMillis();
                long timeout = 5000;

                while ((line = engineOutput.readLine()) != null) {
                    if (line.equals("uciok")) {
                        uciOk = true;
                        break;
                    }
                    if (System.currentTimeMillis() - startTime > timeout) {
                        throw new RuntimeException("Stockfish initialization timeout");
                    }
                }

                if (!uciOk) {
                    throw new RuntimeException("Stockfish did not respond with uciok");
                }

                sendCommand("setoption name Threads value 1");
                sendCommand("setoption name Hash value 128");
                sendCommand("isready");

                while ((line = engineOutput.readLine()) != null) {
                    if (line.equals("readyok")) {
                        break;
                    }
                    if (System.currentTimeMillis() - startTime > timeout) {
                        throw new RuntimeException("Stockfish readyok timeout");
                    }
                }

                isInitialized = true;
                logger.info("✅ Stockfish engine initialized successfully");

            } catch (FileNotFoundException e) {
                logger.warn("⚠️ Stockfish executable not found at: {}", stockfishPath);
                logger.warn("⚠️ AI moves will not be available. Install Stockfish or set STOCKFISH_PATH environment variable");
                logger.warn("⚠️ Application will continue without AI support");
                isInitialized = false;
            } catch (Exception e) {
                logger.warn("⚠️ Failed to initialize Stockfish engine: {}", e.getMessage());
                logger.warn("⚠️ AI moves will not be available. Application will continue without AI support");
                isInitialized = false;
            }
        }
    }

    /**
     * Generate best move for a given position
     * 
     * @param fen        Position in Forsyth-Edwards notation
     * @param difficulty Level 1-10 (mapped to engine settings)
     * @return Best move and evaluation
     */
    public AiMoveDTO generateMove(String fen, int difficulty) {
        synchronized (engineLock) {
            // Ensure engine is initialized
            if (!isInitialized || engineProcess == null || !engineProcess.isAlive()) {
                logger.warn("Engine not initialized, attempting to initialize...");
                initializeEngine();
            }

            if (!isInitialized) {
                throw new RuntimeException("Stockfish engine is not available");
            }

            try {
                // Map difficulty to search depth
                int depth = mapDifficultyToDepth(difficulty);

                // Send new game command to reset state
                sendCommand("ucinewgame");
                sendCommand("isready");
                
                // Wait for readyok
                String line;
                while ((line = engineOutput.readLine()) != null) {
                    if (line.equals("readyok")) {
                        break;
                    }
                }

                // Send position and search command
                sendCommand("position fen " + fen);
                sendCommand("go depth " + depth);

                // Parse engine response
                String bestMove = null;
                int evaluation = 0;
                int searchDepth = 0;

                while ((line = engineOutput.readLine()) != null) {
                    if (line.startsWith("info")) {
                        // Parse evaluation: "info depth 20 seldepth 25 multipv 1 score cp 45 ..."
                        if (line.contains("score cp")) {
                            String[] parts = line.split(" ");
                            for (int i = 0; i < parts.length - 1; i++) {
                                if (parts[i].equals("cp")) {
                                    try {
                                        evaluation = Integer.parseInt(parts[i + 1]);
                                    } catch (NumberFormatException e) {
                                        logger.warn("Failed to parse evaluation: {}", parts[i + 1]);
                                    }
                                }
                                if (parts[i].equals("depth")) {
                                    try {
                                        searchDepth = Integer.parseInt(parts[i + 1]);
                                    } catch (NumberFormatException e) {
                                        logger.warn("Failed to parse depth: {}", parts[i + 1]);
                                    }
                                }
                            }
                        }
                        // Parse mate score: "score mate 5"
                        if (line.contains("score mate")) {
                            String[] parts = line.split(" ");
                            for (int i = 0; i < parts.length - 1; i++) {
                                if (parts[i].equals("mate")) {
                                    try {
                                        int mateIn = Integer.parseInt(parts[i + 1]);
                                        evaluation = mateIn > 0 ? 30000 : -30000;
                                    } catch (NumberFormatException e) {
                                        logger.warn("Failed to parse mate score: {}", parts[i + 1]);
                                    }
                                }
                            }
                        }
                    } else if (line.startsWith("bestmove")) {
                        // Extract best move: "bestmove e2e4 ponder e7e5"
                        String[] parts = line.split(" ");
                        if (parts.length > 1) {
                            bestMove = parts[1];
                        }
                        break;
                    }
                }

                if (bestMove == null || bestMove.equals("(none)")) {
                    throw new RuntimeException("Engine failed to produce a valid move");
                }

                AiMoveDTO result = new AiMoveDTO();
                result.setMove(bestMove);
                result.setEvaluation(evaluation);
                result.setDepth(searchDepth);

                logger.debug("AI move generated: {} (eval: {}, depth: {})", bestMove, evaluation, searchDepth);
                return result;

            } catch (IOException e) {
                logger.error("IO error while generating AI move", e);
                // Try to reinitialize engine
                isInitialized = false;
                throw new RuntimeException("Failed to generate AI move due to IO error", e);
            } catch (Exception e) {
                logger.error("Error generating AI move", e);
                throw new RuntimeException("Failed to generate AI move", e);
            }
        }
    }

    /**
     * Map difficulty level (1-10) to search depth
     */
    private int mapDifficultyToDepth(int difficulty) {
        // Clamp difficulty to valid range
        difficulty = Math.max(1, Math.min(10, difficulty));
        int[] depthMap = { 4, 6, 8, 10, 12, 14, 16, 18, 20, 22 };
        return depthMap[difficulty - 1];
    }

    /**
     * Send a command to the Stockfish engine
     */
    private void sendCommand(String command) throws IOException {
        if (engineInput != null) {
            engineInput.write(command);
            engineInput.newLine();
            engineInput.flush();
            logger.trace("Sent to engine: {}", command);
        } else {
            throw new IOException("Engine input stream is null");
        }
    }

    /**
     * Shutdown the engine gracefully on application shutdown
     */
    @PreDestroy
    public void shutdown() {
        synchronized (engineLock) {
            try {
                if (engineProcess != null && engineProcess.isAlive()) {
                    logger.info("Shutting down Stockfish engine...");
                    sendCommand("quit");
                    boolean terminated = engineProcess.waitFor(5, TimeUnit.SECONDS);
                    
                    if (!terminated) {
                        logger.warn("Engine did not terminate gracefully, forcing shutdown");
                        engineProcess.destroyForcibly();
                        engineProcess.waitFor(2, TimeUnit.SECONDS);
                    }
                    
                    // Close streams
                    if (engineInput != null) {
                        engineInput.close();
                    }
                    if (engineOutput != null) {
                        engineOutput.close();
                    }
                    
                    logger.info("Stockfish engine shut down successfully");
                }
                isInitialized = false;
            } catch (Exception e) {
                logger.error("Error shutting down Stockfish engine", e);
                if (engineProcess != null && engineProcess.isAlive()) {
                    engineProcess.destroyForcibly();
                }
            }
        }
    }

    /**
     * Check if engine is available and initialized
     */
    public boolean isEngineAvailable() {
        synchronized (engineLock) {
            return isInitialized && engineProcess != null && engineProcess.isAlive();
        }
    }
}
