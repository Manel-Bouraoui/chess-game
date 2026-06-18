# System Architecture

## Overview
The Local Office Chess application uses a decoupled client-server architecture, enabling high-performance gameplay while maintaining real-time communication.

## Frontend (Client)
- **Framework**: React 19 + Vite + TypeScript.
- **Styling**: Tailwind CSS v4 (Dark mode, responsive design).
- **Game Logic (`chess.js`)**: Handles all complex chess rules locally (en passant, castling, stalemates) to provide immediate feedback without waiting for the server.
- **UI Component (`react-chessboard`)**: Renders the board visually.
- **AI Engine (`Stockfish.js`)**: Runs Stockfish entirely in the browser using Web Workers. This offloads CPU-intensive calculations from the backend server to the client's device, ensuring the server remains performant even with multiple concurrent users.

## Backend (Server)
- **Framework**: Spring Boot 3 (Java 21).
- **Communication Layer**: WebSockets with STOMP protocol. WebSockets are essential for low-latency PvP gameplay, avoiding the overhead of continuous REST polling.
- **Security**: Spring Security configured to allow CORS for local development and open access to WebSocket endpoints.
- **State Management**: The backend routes `MoveMessage` DTOs between players using Spring's `SimpMessagingTemplate` and `@MessageMapping`.

## Data Flow
### Player vs Player (PvP)
1. **Move Made**: Player A makes a move on the frontend.
2. **Local Validation**: `chess.js` validates the move immediately.
3. **Transmission**: If valid, the move (and new FEN state) is sent to the backend via WebSocket (`/app/game/{gameId}/move`).
4. **Broadcast**: The `GameController` receives the move and broadcasts it to all subscribers of the topic (`/topic/game/{gameId}`).
5. **Opponent Update**: Player B's client receives the broadcast, validates the FEN, and updates their UI.

### Player vs AI (PvE)
1. **Move Made**: Player makes a move.
2. **Local Validation**: `chess.js` validates the move.
3. **Engine Trigger**: The client sends the current FEN state to the local Stockfish Web Worker along with the chosen difficulty level.
4. **Engine Response**: Stockfish computes the best move and posts it back to the React component.
5. **UI Update**: The frontend applies the AI's move using `chess.js` and updates the board. The backend is completely bypassed in PvE.
