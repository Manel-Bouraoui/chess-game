# Chess Application - Production Boilerplate

A full-stack, real-time chess application for local office play. Employees can compete in **Player vs Player (PvP)** or **Player vs AI (PvE)** modes with live WebSocket synchronization and a sophisticated frontend built on React 18+.

---

## 📋 Quick Start

### Prerequisites
- **Java 21+** (Spring Boot 3.x)
- **Node.js 18+** (React 18+)
- **Docker** (optional, for Stockfish engine container)
- **pnpm** or **npm** (recommended: pnpm for monorepo)

### Installation & Running

#### 1. Backend Setup
```bash
cd backend
./mvnw clean install
./mvnw spring-boot:run
```
Backend runs on `http://localhost:8080`

#### 2. Frontend Setup
```bash
cd frontend
pnpm install
pnpm dev
```
Frontend runs on `http://localhost:5173`

#### 3. WebSocket Connection
- The React frontend automatically connects to `ws://localhost:8080/ws` on mount.
- Messages are routed via STOMP protocol.

---

## 🏗️ Project Structure

```
chess-app-boilerplate/
├── backend/                              # Spring Boot 3.x application
│   ├── src/main/java/com/chess/
│   │   ├── config/                       # Spring configurations
│   │   │   ├── WebSocketConfig.java      # STOMP + WebSocket setup
│   │   │   ├── SecurityConfig.java       # Spring Security (optional auth)
│   │   │   └── CorsConfig.java           # CORS for local dev
│   │   ├── controller/
│   │   │   ├── GameController.java       # WebSocket message handlers
│   │   │   └── AiController.java         # AI engine REST endpoints
│   │   ├── service/
│   │   │   ├── GameService.java          # Game state & move logic
│   │   │   ├── ChessEngineService.java   # AI move generation
│   │   │   └── PlayerService.java        # Player session management
│   │   ├── model/
│   │   │   ├── GameState.java            # Core game model
│   │   │   ├── Player.java               # Player entity
│   │   │   └── Move.java                 # Move DTO/record
│   │   ├── dto/
│   │   │   ├── MoveRequestDTO.java       # Incoming move requests
│   │   │   ├── GameStateDTO.java         # Outgoing game state
│   │   │   └── AiMoveDTO.java            # AI response payload
│   │   └── ChessBackendApplication.java
│   ├── pom.xml                           # Maven dependencies
│   └── docker/                           # Optional: Stockfish container
│       └── Dockerfile
│
├── frontend/                             # React 18+ + TypeScript application
│   ├── src/
│   │   ├── components/
│   │   │   ├── ChessBoard/
│   │   │   │   ├── ChessBoard.tsx        # Main board component
│   │   │   │   ├── Square.tsx            # Individual square
│   │   │   │   └── ChessBoard.module.css
│   │   │   ├── GamePanel/
│   │   │   │   ├── GamePanel.tsx         # Game controls & info
│   │   │   │   ├── AiSelector.tsx        # AI difficulty chooser
│   │   │   │   └── MoveHistory.tsx       # Move log
│   │   │   └── Layout/
│   │   │       ├── Header.tsx
│   │   │       └── Footer.tsx
│   │   ├── hooks/
│   │   │   ├── useWebSocket.ts           # WebSocket connection hook
│   │   │   ├── useChessGame.ts           # Game state management
│   │   │   └── useStockfish.ts           # Stockfish.js WASM wrapper
│   │   ├── services/
│   │   │   ├── gameService.ts            # API calls to backend
│   │   │   ├── stockfishService.ts       # Stockfish integration
│   │   │   └── wsClient.ts               # WebSocket client wrapper
│   │   ├── types/
│   │   │   ├── game.ts                   # TypeScript game interfaces
│   │   │   └── messages.ts               # WebSocket message types
│   │   ├── styles/
│   │   │   ├── globals.css               # Global styling
│   │   │   └── tailwind.css              # Tailwind config
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   └── tailwind.config.ts
│
├── docs/                                 # Architecture & guides
│   ├── ARCHITECTURE.md                   # System design documentation
│   ├── API_CONTRACTS.md                  # WebSocket & REST API specs
│   ├── DEPLOYMENT.md                     # Docker & production setup
│   └── DEVELOPMENT.md                    # Contributing guidelines
│
├── docker-compose.yml                    # Local orchestration (optional)
└── .github/
    └── workflows/
        └── ci.yml                        # GitHub Actions (optional)
```

---

## 🔑 Key Technologies

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Frontend** | React 18 + TypeScript | Dynamic UI with type safety |
| **Frontend Logic** | `chess.js` | Move validation & game rules |
| **Frontend UI** | `react-chessboard` | Professional board rendering |
| **Frontend AI** | `stockfish.js` (WASM) | Client-side AI moves |
| **Real-time** | WebSocket (STOMP) | Live move synchronization |
| **Styling** | Tailwind CSS | Utility-first dark theme |
| **Backend** | Spring Boot 3.x | REST API + WebSocket server |
| **Backend Async** | Spring WebFlux | Reactive stream handling |
| **AI Engine** | Stockfish (docker) | Optional: Heavy AI compute |

---

## 🎮 Feature Set

### Implemented
- ✅ Real-time PvP chess with WebSocket sync
- ✅ Player vs AI (Stockfish.js) with difficulty selector (1–10)
- ✅ Full move validation (en passant, castling, stalemate, checkmate)
- ✅ Dark-themed, responsive React UI
- ✅ Move history & game state persistence (LocalStorage)
- ✅ Session management via Spring Security tokens

### Future Extensions
- 🔜 Elo rating system & leaderboards
- 🔜 Game replay & PGN export
- 🔜 Challenge/invitation system
- 🔜 Spectator mode
- 🔜 Mobile app (React Native)

---

## 📡 Data Flow

### PvP Move Flow
```
1. Player A clicks board → ChessBoard.tsx validates with chess.js
2. Valid move emitted to WebSocket via STOMP
3. Backend GameController receives move message
4. GameService updates GameState & validates
5. Backend broadcasts updated GameState to both players
6. Frontend receives, chess.js.move() updates board
7. Player B's board updates in real-time
```

### PvE Move Flow
```
1. Player clicks board → chess.js validates
2. Move sent to GameController (Spring WebSocket)
3. GameService updates state, sends confirmation
4. Frontend detects it's AI's turn
5. useStockfish.ts queries stockfish.js in worker thread
6. AI move generated locally (no server compute)
7. Move validated on backend and broadcast to all listeners
```

---

## 🔐 Security Considerations

- **WebSocket Authentication**: JWT tokens passed in WebSocket handshake headers.
- **Move Validation**: All moves re-validated server-side (never trust client).
- **CORS**: Configured for `localhost:5173` in dev; lock down in production.
- **Rate Limiting**: Optional: Spring Security rate limiter for API endpoints.

---

## 📚 Documentation Files

| File | Purpose |
|------|---------|
| `docs/ARCHITECTURE.md` | Detailed system design, component interactions, and scalability notes |
| `docs/API_CONTRACTS.md` | WebSocket message schemas, REST endpoints, and error codes |
| `docs/DEPLOYMENT.md` | Docker setup, environment variables, and cloud deployment |
| `docs/DEVELOPMENT.md` | Local dev setup, testing strategies, and code style guidelines |

---

## 🛠️ Development Workflow

### Running Tests
```bash
# Backend
cd backend
./mvnw test

# Frontend
cd frontend
pnpm test
```

### Building for Production
```bash
# Backend JAR
cd backend
./mvnw clean package -DskipTests

# Frontend bundle
cd frontend
pnpm build
```

### Docker Deployment
```bash
docker-compose up -d
# Builds and runs backend (port 8080) + frontend (port 5173) + optional Stockfish container
```

---

## 🎯 Next Steps

1. **Clone this boilerplate** into your repository.
2. **Install dependencies** for both backend and frontend.
3. **Read `docs/ARCHITECTURE.md`** for deep-dive on component design.
4. **Customize** Spring Security config and WebSocket topics as needed.
5. **Deploy locally** with `docker-compose up` and test PvP + PvE flows.

---

## 📝 License

MIT License. Use freely for internal office applications.

---

## 🤝 Contributing

Follow the guidelines in `docs/DEVELOPMENT.md` for code style, testing, and PR submissions.
