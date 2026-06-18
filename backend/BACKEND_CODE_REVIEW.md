# Chess Backend - Comprehensive Code Review

**Review Date:** April 30, 2026  
**Reviewer:** Kiro AI  
**Project:** Chess Application Backend (Spring Boot 3.2.4, Java 21)

---

## Executive Summary

This review identifies **CRITICAL** issues that will prevent your chess application from functioning correctly. The backend has a solid architectural foundation but lacks the core chess logic implementation. The most severe problem is that **moves are not validated at all** - the system accepts any move without checking chess rules.

### Severity Levels
- 🔴 **CRITICAL**: Will cause game-breaking bugs or security vulnerabilities
- 🟠 **HIGH**: Significant functionality gaps or potential crashes
- 🟡 **MEDIUM**: Code quality issues that should be addressed
- 🔵 **LOW**: Minor improvements and optimizations

---

## 🔴 CRITICAL ISSUES

### 1. **NO CHESS MOVE VALIDATION** 🔴
**Location:** `GameService.processMove()`, `GameValidator.validateMove()`

**Problem:**
The `processMove()` method accepts moves without validating chess rules. It only checks square format, not whether the move is legal according to chess rules.

```java
// Current code in GameService.processMove()
public boolean processMove(String gameId, MoveRequestDTO moveRequest) {
    // ... gets game
    MoveEntity move = new MoveEntity();
    move.setFromSquare(moveRequest.getFrom());
    move.setToSquare(moveRequest.getTo());
    game.getMoves().add(move);  // ❌ NO VALIDATION!
    // ...
}
```

**Missing Validations:**
- ✗ Piece movement rules (knight L-shape, bishop diagonal, etc.)
- ✗ Path obstruction (pieces blocking the way)
- ✗ Capturing own pieces
- ✗ Moving into check
- ✗ Castling legality (king/rook haven't moved, no pieces between, not castling through check)
- ✗ En passant validation
- ✗ Pawn promotion enforcement
- ✗ Check detection
- ✗ Checkmate detection
- ✗ Stalemate detection

**Impact:** Players can make any move, including illegal ones. The game will be unplayable.

**Recommendation:**
```java
// Option 1: Use a chess library (RECOMMENDED)
// Uncomment in pom.xml:
// <dependency>
//     <groupId>com.github.bhlangonijr</groupId>
//     <artifactId>chesslib</artifactId>
//     <version>1.3.2</version>
// </dependency>

// Then implement proper validation:
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;

public boolean processMove(String gameId, MoveRequestDTO moveRequest) {
    GameStateEntity game = getGame(gameId);
    if (game == null) {
        throw new GameException("Game not found", "GAME_NOT_FOUND");
    }
    
    // Validate using chess library
    Board board = new Board();
    board.loadFromFen(game.getFen());
    
    Move move = new Move(moveRequest.getFrom(), moveRequest.getTo());
    if (!board.legalMoves().contains(move)) {
        throw MoveValidationException.illegalMove(
            moveRequest.getFrom(), 
            moveRequest.getTo()
        );
    }
    
    // Apply move and update FEN
    board.doMove(move);
    game.setFen(board.getFen());
    
    // Detect game state
    game.setInCheck(board.isKingAttacked());
    game.setInCheckmate(board.isMated());
    game.setInStalemate(board.isStaleMate());
    
    // ... rest of logic
}
```

---

### 2. **FEN STRING NEVER UPDATED** 🔴
**Location:** `GameService.processMove()`

**Problem:**
The FEN string (board position) is never updated after moves. The game state becomes desynchronized from the actual board position.

```java
// Current code - FEN is NEVER updated!
public boolean processMove(String gameId, MoveRequestDTO moveRequest) {
    // ... 
    game.getMoves().add(move);
    game.setMoveCount(game.getMoveCount() + 1);
    // ❌ game.setFen() is NEVER called!
}
```

**Impact:** 
- AI moves will be based on the starting position, not current position
- Game state (check, checkmate) cannot be determined
- Reconnecting players see wrong board state

**Fix:** Update FEN after each validated move (see recommendation in Issue #1).

---

### 3. **RACE CONDITIONS IN MULTIPLAYER** 🔴
**Location:** `GameService.processMove()`, `GameController`

**Problem:**
No synchronization mechanism prevents simultaneous moves from both players.

**Scenario:**
1. Player 1 sends move at timestamp T
2. Player 2 sends move at timestamp T+5ms
3. Both moves get processed, violating turn order

**Current Code:**
```java
@Transactional
public boolean processMove(String gameId, MoveRequestDTO moveRequest) {
    // ❌ No locking mechanism
    // ❌ No turn validation before processing
    game.getMoves().add(move);
    // ...
}
```

**Recommendation:**
```java
// Add pessimistic locking
@Transactional
public boolean processMove(String gameId, MoveRequestDTO moveRequest) {
    GameStateEntity game = gameStateRepository
        .findByGameIdWithLock(gameId)  // Use @Lock(LockModeType.PESSIMISTIC_WRITE)
        .orElseThrow(() -> new GameException("Game not found"));
    
    // Validate turn BEFORE processing
    if (!GameValidator.validatePlayerTurn(game, moveRequest.getPlayerId())) {
        throw MoveValidationException.notPlayersTurn(moveRequest.getPlayerId());
    }
    
    // ... process move
}

// In repository:
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT g FROM GameStateEntity g WHERE g.gameId = :gameId")
Optional<GameStateEntity> findByGameIdWithLock(@Param("gameId") String gameId);
```

---

### 4. **STOCKFISH PROCESS LEAK** 🔴
**Location:** `ChessEngineService`

**Problem:**
Stockfish process is never properly managed. Each game could spawn a new process, leading to resource exhaustion.

```java
public void initializeEngine() {
    engineProcess = new ProcessBuilder(STOCKFISH_PATH).start();
    // ❌ No lifecycle management
    // ❌ Process never reused
    // ❌ No cleanup on errors
}

public AiMoveDTO generateMove(String fen, int difficulty) {
    if (engineProcess == null) {
        initializeEngine();  // ❌ Creates new process every time!
    }
}
```

**Impact:**
- Memory leaks
- File descriptor exhaustion
- Server crashes under load

**Recommendation:**
```java
@Service
public class ChessEngineService {
    private Process engineProcess;
    private final Object engineLock = new Object();
    
    @PostConstruct
    public void initializeEngine() {
        synchronized (engineLock) {
            if (engineProcess == null || !engineProcess.isAlive()) {
                // Initialize once
            }
        }
    }
    
    @PreDestroy
    public void shutdown() {
        // Ensure cleanup
        synchronized (engineLock) {
            if (engineProcess != null && engineProcess.isAlive()) {
                sendCommand("quit");
                engineProcess.destroy();
            }
        }
    }
    
    public AiMoveDTO generateMove(String fen, int difficulty) {
        synchronized (engineLock) {
            if (engineProcess == null || !engineProcess.isAlive()) {
                initializeEngine();
            }
            // ... generate move
        }
    }
}
```

---

### 5. **MALFORMED FEN CRASHES SERVER** 🔴
**Location:** `ChessUtil.isValidFen()`, `ChessEngineService.generateMove()`

**Problem:**
FEN validation only checks part count, not content validity.

```java
public static boolean isValidFen(String fen) {
    if (fen == null || fen.trim().isEmpty()) {
        return false;
    }
    String[] parts = fen.split(" ");
    return parts.length == 6;  // ❌ Only checks count, not content!
}
```

**Attack Vector:**
```
POST /api/game/move
{
  "fen": "invalid invalid invalid invalid invalid invalid"
}
```

This will crash Stockfish or cause undefined behavior.

**Recommendation:**
```java
public static boolean isValidFen(String fen) {
    if (fen == null || fen.trim().isEmpty()) {
        return false;
    }
    
    String[] parts = fen.split(" ");
    if (parts.length != 6) return false;
    
    // Validate board position (part 0)
    String[] ranks = parts[0].split("/");
    if (ranks.length != 8) return false;
    
    for (String rank : ranks) {
        int fileCount = 0;
        for (char c : rank.toCharArray()) {
            if (Character.isDigit(c)) {
                fileCount += Character.getNumericValue(c);
            } else if ("prnbqkPRNBQK".indexOf(c) >= 0) {
                fileCount++;
            } else {
                return false;  // Invalid character
            }
        }
        if (fileCount != 8) return false;  // Each rank must have 8 squares
    }
    
    // Validate active color (part 1)
    if (!parts[1].matches("[wb]")) return false;
    
    // Validate castling rights (part 2)
    if (!parts[2].matches("^(-|K?Q?k?q?)$")) return false;
    
    // Validate en passant (part 3)
    if (!parts[3].matches("^(-|[a-h][36])$")) return false;
    
    // Validate halfmove clock (part 4)
    try {
        int halfmove = Integer.parseInt(parts[4]);
        if (halfmove < 0) return false;
    } catch (NumberFormatException e) {
        return false;
    }
    
    // Validate fullmove number (part 5)
    try {
        int fullmove = Integer.parseInt(parts[5]);
        if (fullmove < 1) return false;
    } catch (NumberFormatException e) {
        return false;
    }
    
    return true;
}
```

---

## 🟠 HIGH PRIORITY ISSUES

### 6. **NO CASTLING RIGHTS TRACKING** 🟠
**Location:** Game state management

**Problem:**
The system doesn't track whether kings or rooks have moved, making castling validation impossible.

**Missing:**
- King movement history
- Rook movement history
- Castling rights in FEN are never updated

**Fix:** Use a chess library that handles this automatically, or track in `GameStateEntity`:
```java
@Column
private String castlingRights = "KQkq";  // Track in FEN format
```

---

### 7. **NO EN PASSANT DETECTION** 🟠
**Location:** Move processing

**Problem:**
En passant is a special pawn capture that must be executed immediately after a two-square pawn advance. The system doesn't track this.

**Impact:** En passant moves will be rejected or incorrectly processed.

**Fix:** Use chess library or track last move in FEN (4th field).

---

### 8. **PAWN PROMOTION NOT ENFORCED** 🟠
**Location:** `GameValidator.validateMove()`

**Problem:**
When a pawn reaches the 8th rank, promotion is optional in the code but mandatory in chess.

```java
if (move.getPromotion() != null && !ChessUtil.isValidPromotion(move.getPromotion())) {
    // ❌ Only validates IF promotion is provided
    // ❌ Doesn't check if promotion is REQUIRED
}
```

**Fix:**
```java
// Check if move is a pawn reaching last rank
if (isPawnPromotion(move.getFrom(), move.getTo(), game.getFen())) {
    if (move.getPromotion() == null) {
        throw new MoveValidationException(
            "Pawn promotion required",
            "Must specify promotion piece: q, r, b, or n"
        );
    }
}
```

---

### 9. **WEBSOCKET MESSAGES NOT VALIDATED** 🟠
**Location:** `GameController`

**Problem:**
WebSocket messages bypass all validation.

```java
@MessageMapping("/game/{gameId}/move")
@SendTo("/topic/game/{gameId}")
public MoveDTO processMove(@DestinationVariable String gameId, @Payload MoveDTO move) {
    return move;  // ❌ Just echoes back without validation!
}
```

**Impact:**
- Clients can broadcast fake moves
- No authentication check
- No game state validation

**Fix:**
```java
@MessageMapping("/game/{gameId}/move")
@SendTo("/topic/game/{gameId}")
public GameStateDTO processMove(
    @DestinationVariable String gameId, 
    @Payload MoveRequestDTO moveRequest,
    Principal principal  // Get authenticated user
) {
    // Validate player identity
    if (!moveRequest.getPlayerId().equals(principal.getName())) {
        throw new SecurityException("Player ID mismatch");
    }
    
    // Process move through service (with validation)
    boolean success = gameService.processMove(gameId, moveRequest);
    
    if (!success) {
        throw new MoveValidationException("Invalid move");
    }
    
    // Return updated game state
    GameStateEntity game = gameService.getGame(gameId);
    return gameService.toDTO(game);
}
```

---

### 10. **NO STALEMATE VS CHECKMATE DISTINCTION** 🟠
**Location:** `GameService.updateGameStatus()`

**Problem:**
The method is empty!

```java
@Transactional
public void updateGameStatus(String gameId) {
    GameStateEntity game = getGame(gameId);
    if (game == null) return;
    // ❌ EMPTY METHOD - does nothing!
}
```

**Impact:**
- Games never end automatically
- Stalemate treated as checkmate or vice versa
- Draw conditions not detected

**Fix:** Implement using chess library:
```java
@Transactional
public void updateGameStatus(String gameId) {
    GameStateEntity game = getGame(gameId);
    if (game == null) return;
    
    Board board = new Board();
    board.loadFromFen(game.getFen());
    
    if (board.isMated()) {
        game.setInCheckmate(true);
        game.setStatus("CHECKMATE");
        // Winner is the player who just moved
        String winner = game.getCurrentTurnColor().equals("w") ? "BLACK" : "WHITE";
        game.setWinner(winner);
    } else if (board.isStaleMate()) {
        game.setInStalemate(true);
        game.setStatus("STALEMATE");
    } else if (board.isDraw()) {
        game.setStatus("DRAW");
    } else if (board.isKingAttacked()) {
        game.setInCheck(true);
    }
    
    gameStateRepository.save(game);
}
```

---

## 🟡 MEDIUM PRIORITY ISSUES

### 11. **INSUFFICIENT INPUT SANITIZATION** 🟡
**Location:** All DTOs

**Problem:**
No validation annotations on DTOs.

**Fix:**
```java
public class MoveRequestDTO {
    @NotNull(message = "Game ID is required")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Invalid game ID format")
    private String gameId;
    
    @NotNull(message = "Player ID is required")
    private String playerId;
    
    @NotNull(message = "From square is required")
    @Pattern(regexp = "^[a-h][1-8]$", message = "Invalid square format")
    private String from;
    
    @NotNull(message = "To square is required")
    @Pattern(regexp = "^[a-h][1-8]$", message = "Invalid square format")
    private String to;
    
    @Pattern(regexp = "^[qrbn]$", message = "Invalid promotion piece")
    private String promotion;
    
    @Min(value = 0, message = "Timestamp must be positive")
    private long timestamp;
}
```

Add to controller:
```java
public boolean processMove(@Valid @RequestBody MoveRequestDTO moveRequest) {
    // ...
}
```

---

### 12. **DATABASE CREDENTIALS IN PLAIN TEXT** 🟡
**Location:** `application.properties`

**Problem:**
```properties
spring.datasource.username=root
spring.datasource.password=
security.jwt.secret=your-256-bit-secret-key-change-this-in-production-12345
```

**Fix:**
```properties
# Use environment variables
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
security.jwt.secret=${JWT_SECRET:}

# Or use Spring Cloud Config / Vault for production
```

---

### 13. **NO DRAW DETECTION** 🟡
**Location:** Game logic

**Missing:**
- Threefold repetition
- Fifty-move rule
- Insufficient material (K vs K, K+B vs K, etc.)

**Fix:** Use chess library's built-in draw detection.

---

### 14. **TIMESTAMP VALIDATION INCORRECT** 🟡
**Location:** `GameValidator.validateMove()`

```java
if (move.getTimestamp() <= 0) {
    logger.warn("Invalid timestamp: {}", move.getTimestamp());
    return false;
}
```

**Problem:**
- Doesn't check if timestamp is in the future
- Doesn't check if timestamp is reasonable (not from 1970)
- Client can send any positive number

**Fix:**
```java
long now = System.currentTimeMillis();
long fiveMinutesAgo = now - (5 * 60 * 1000);
long oneMinuteInFuture = now + (60 * 1000);

if (move.getTimestamp() < fiveMinutesAgo || move.getTimestamp() > oneMinuteInFuture) {
    logger.warn("Timestamp out of acceptable range: {}", move.getTimestamp());
    return false;
}
```

---

### 15. **NO MOVE TIMEOUT HANDLING** 🟡
**Location:** Game management

**Problem:**
If a player disconnects, the game hangs forever.

**Recommendation:**
```java
@Scheduled(fixedRate = 60000)  // Every minute
public void checkGameTimeouts() {
    List<GameStateEntity> activeGames = gameStateRepository
        .findByStatus("IN_PROGRESS");
    
    long now = System.currentTimeMillis();
    long timeout = 5 * 60 * 1000;  // 5 minutes
    
    for (GameStateEntity game : activeGames) {
        if (game.getUpdatedAt() != null) {
            long lastUpdate = game.getUpdatedAt()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
            
            if (now - lastUpdate > timeout) {
                game.setStatus("TIMEOUT");
                gameStateRepository.save(game);
                // Notify players via WebSocket
            }
        }
    }
}
```

---

### 16. **STOCKFISH PATH HARDCODED** 🟡
**Location:** `ChessEngineService`

```java
private static final String STOCKFISH_PATH = "/usr/games/stockfish";
```

**Problem:**
- Won't work on Windows
- Won't work if Stockfish is installed elsewhere
- Application.properties has the path but it's not used!

**Fix:**
```java
@Value("${stockfish.path:/usr/games/stockfish}")
private String stockfishPath;

public void initializeEngine() {
    engineProcess = new ProcessBuilder(stockfishPath).start();
    // ...
}
```

---

## 🔵 LOW PRIORITY ISSUES

### 17. **MISSING LOGGING FOR CRITICAL OPERATIONS** 🔵

Add structured logging:
```java
logger.info("Move processed: gameId={}, from={}, to={}, player={}, fen={}", 
    gameId, move.getFrom(), move.getTo(), playerId, newFen);
```

---

### 18. **NO METRICS/MONITORING** 🔵

Add Actuator endpoints:
```properties
management.endpoints.web.exposure.include=health,metrics,info,prometheus
management.metrics.export.prometheus.enabled=true
```

---

### 19. **INEFFICIENT MOVE HISTORY STORAGE** 🔵

**Problem:**
Storing all moves as separate entities is inefficient.

**Alternative:**
```java
@Column(columnDefinition = "TEXT")
private String moveHistory;  // Store as PGN or move list string
```

---

### 20. **NO API RATE LIMITING** 🔵

Add rate limiting to prevent abuse:
```java
@Bean
public RateLimiter rateLimiter() {
    return RateLimiter.create(10.0);  // 10 requests per second
}
```

---

## Security Assessment

### Authentication & Authorization
- ⚠️ **No authentication implemented** - JWT dependencies present but unused
- ⚠️ **No session management** - Players can impersonate others
- ⚠️ **No WebSocket authentication** - Anyone can send moves for any player

### Input Validation
- ❌ **No DTO validation** - Missing @Valid annotations
- ❌ **Weak FEN validation** - Can crash server
- ❌ **No SQL injection protection** - Using JPA correctly, but no input sanitization

### Data Protection
- ⚠️ **Credentials in plain text** - Use environment variables
- ✅ **CSRF disabled** - Appropriate for stateless API
- ✅ **CORS configured** - But allows all localhost ports

---

## Architecture Assessment

### Strengths ✅
1. Clean separation of concerns (Controller → Service → Repository)
2. Proper use of DTOs for API contracts
3. Global exception handling implemented
4. Transaction management with @Transactional
5. WebSocket configuration for real-time updates
6. Logging framework properly configured

### Weaknesses ❌
1. **No chess logic implementation** - Core functionality missing
2. **No concurrency control** - Race conditions in multiplayer
3. **Incomplete service methods** - updateGameStatus() is empty
4. **No integration tests** - Can't verify game flow
5. **Tight coupling to Stockfish** - No abstraction for chess engine

---

## Recommended Action Plan

### Phase 1: Critical Fixes (Do First) 🔴
1. **Integrate chess library** (chesslib) for move validation
2. **Implement proper move processing** with FEN updates
3. **Add pessimistic locking** to prevent race conditions
4. **Fix Stockfish lifecycle management**
5. **Implement robust FEN validation**

### Phase 2: High Priority 🟠
6. Implement `updateGameStatus()` method
7. Add WebSocket message validation
8. Enforce pawn promotion
9. Add authentication to WebSocket endpoints
10. Implement draw detection

### Phase 3: Medium Priority 🟡
11. Add DTO validation annotations
12. Externalize configuration (credentials, paths)
13. Implement move timeout handling
14. Add comprehensive error messages
15. Improve logging

### Phase 4: Low Priority 🔵
16. Add metrics and monitoring
17. Implement rate limiting
18. Optimize move storage
19. Add integration tests
20. Performance tuning

---

## Code Examples for Critical Fixes

### Complete Move Processing Implementation

```java
@Service
public class GameService {
    
    private final GameStateRepository gameStateRepository;
    private final ChessEngineService chessEngineService;
    
    @Transactional
    public GameStateDTO processMove(String gameId, MoveRequestDTO moveRequest) {
        // 1. Get game with pessimistic lock
        GameStateEntity game = gameStateRepository
            .findByGameIdWithLock(gameId)
            .orElseThrow(() -> new GameException("Game not found", "GAME_NOT_FOUND"));
        
        // 2. Validate game state
        if (!"IN_PROGRESS".equals(game.getStatus())) {
            throw new GameException("Game is not in progress", "GAME_NOT_ACTIVE");
        }
        
        // 3. Validate player turn
        if (!GameValidator.validatePlayerTurn(game, moveRequest.getPlayerId())) {
            throw MoveValidationException.notPlayersTurn(moveRequest.getPlayerId());
        }
        
        // 4. Load board from FEN
        Board board = new Board();
        try {
            board.loadFromFen(game.getFen());
        } catch (Exception e) {
            logger.error("Invalid FEN in database: {}", game.getFen());
            throw new GameException("Corrupted game state", "INVALID_FEN");
        }
        
        // 5. Validate and apply move
        Square from = Square.fromValue(moveRequest.getFrom().toUpperCase());
        Square to = Square.fromValue(moveRequest.getTo().toUpperCase());
        
        Move move;
        if (moveRequest.getPromotion() != null) {
            Piece promotionPiece = Piece.fromValue(
                moveRequest.getPromotion().toUpperCase()
            );
            move = new Move(from, to, promotionPiece);
        } else {
            move = new Move(from, to);
        }
        
        // Check if move is legal
        if (!board.legalMoves().contains(move)) {
            throw MoveValidationException.illegalMove(
                moveRequest.getFrom(),
                moveRequest.getTo()
            );
        }
        
        // 6. Apply move
        board.doMove(move);
        
        // 7. Update game state
        game.setFen(board.getFen());
        game.setMoveCount(game.getMoveCount() + 1);
        game.setCurrentTurnColor(board.getSideToMove().value());
        
        // 8. Store move in history
        MoveEntity moveEntity = new MoveEntity();
        moveEntity.setFromSquare(moveRequest.getFrom());
        moveEntity.setToSquare(moveRequest.getTo());
        moveEntity.setPromotion(moveRequest.getPromotion());
        moveEntity.setMoveTimestamp(System.currentTimeMillis());
        game.getMoves().add(moveEntity);
        
        // 9. Check game end conditions
        game.setInCheck(board.isKingAttacked());
        game.setInCheckmate(board.isMated());
        game.setInStalemate(board.isStaleMate());
        
        if (board.isMated()) {
            game.setStatus("CHECKMATE");
            String winner = board.getSideToMove().value().equals("w") ? "BLACK" : "WHITE";
            logger.info("Game {} ended in checkmate. Winner: {}", gameId, winner);
        } else if (board.isStaleMate()) {
            game.setStatus("STALEMATE");
            logger.info("Game {} ended in stalemate", gameId);
        } else if (board.isDraw()) {
            game.setStatus("DRAW");
            logger.info("Game {} ended in draw", gameId);
        }
        
        game.setUpdatedAt(LocalDateTime.now());
        gameStateRepository.save(game);
        
        logger.info("Move processed: game={}, from={}, to={}, newFen={}", 
            gameId, moveRequest.getFrom(), moveRequest.getTo(), board.getFen());
        
        return toDTO(game);
    }
}
```

---

## Testing Recommendations

### Unit Tests Needed
```java
@Test
void testIllegalMoveRejected() {
    // Try to move pawn backwards
    MoveRequestDTO move = new MoveRequestDTO();
    move.setFrom("e4");
    move.setTo("e3");  // Illegal for white pawn
    
    assertThrows(MoveValidationException.class, () -> {
        gameService.processMove(gameId, move);
    });
}

@Test
void testCastlingValidation() {
    // Test castling when king has moved
    // Test castling through check
    // Test castling with pieces in between
}

@Test
void testEnPassantCapture() {
    // Set up position where en passant is legal
    // Verify capture works correctly
}

@Test
void testPawnPromotionEnforced() {
    // Move pawn to 8th rank without promotion
    // Should throw exception
}

@Test
void testConcurrentMoveHandling() {
    // Simulate two players moving simultaneously
    // Verify only one move is accepted
}
```

---

## Conclusion

Your chess backend has a solid architectural foundation but is **not production-ready**. The most critical issue is the complete absence of chess move validation, which makes the game unplayable. 

**Immediate Actions Required:**
1. Integrate a chess library (chesslib recommended)
2. Implement proper move validation and FEN updates
3. Add concurrency control for multiplayer games
4. Fix Stockfish process management
5. Implement game end detection

**Estimated Effort:**
- Critical fixes: 2-3 days
- High priority: 2-3 days  
- Medium priority: 3-4 days
- Low priority: 2-3 days

**Total: ~10-13 days of development work**

The good news is that the architecture supports these fixes well. Once the chess logic is properly implemented, you'll have a solid, scalable chess application.

---

## Additional Resources

- [Chesslib Documentation](https://github.com/bhlangonijr/chesslib)
- [UCI Protocol Specification](http://wbec-ridderkerk.nl/html/UCIProtocol.html)
- [FEN Notation](https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation)
- [Spring WebSocket Security](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html)

---

**Review Complete** ✅
