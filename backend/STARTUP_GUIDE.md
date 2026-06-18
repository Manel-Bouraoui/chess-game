# Chess Backend - Startup Guide

## Current Status

✅ **Code Compilation:** SUCCESS  
⚠️ **Stockfish:** Not installed (AI moves disabled)  
❌ **MySQL Database:** Not running or database doesn't exist

---

## Prerequisites Setup

### 1. Start MySQL (XAMPP)

Since you're using XAMPP, start MySQL:

```bash
# Open XAMPP Control Panel
# Click "Start" next to MySQL
```

Or via command line:
```bash
cd C:\xampp
mysql_start.bat
```

### 2. Create Database

Open MySQL command line or phpMyAdmin:

```sql
CREATE DATABASE IF NOT EXISTS chess_db;
USE chess_db;

-- Tables will be created automatically by Hibernate
```

**Or use phpMyAdmin:**
1. Open http://localhost/phpmyadmin
2. Click "New" to create database
3. Name it: `chess_db`
4. Click "Create"

### 3. Install Stockfish (Optional - for AI moves)

**Windows:**
1. Download from: https://stockfishchess.org/download/windows/
2. Extract to: `C:\Program Files\Stockfish\`
3. Set environment variable:
   ```powershell
   $env:STOCKFISH_PATH="C:\Program Files\Stockfish\stockfish-windows-x86-64-avx2.exe"
   ```

**Or skip AI support:**
- Application will run without Stockfish
- PVP (Player vs Player) games will work
- PVE (Player vs AI) games will not be available

---

## Start Application

### Option 1: With Maven
```bash
mvn spring-boot:run
```

### Option 2: Package and Run JAR
```bash
mvn clean package
java -jar target/chess-backend-1.0.0.jar
```

---

## Verify Application Started

### Check Logs
Look for:
```
Started ChessBackendApplication in X.XXX seconds
Tomcat started on port(s): 8080 (http)
```

### Test Endpoints

**Health Check:**
```bash
curl http://localhost:8080/api/game/active
```

**Expected Response:**
```json
{
  "success": true,
  "games": [],
  "count": 0
}
```

---

## Quick Test

### 1. Create a Game
```bash
curl -X POST http://localhost:8080/api/game/create \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "PVP",
    "whitePlayerName": "Alice",
    "blackPlayerName": "Bob"
  }'
```

### 2. Make a Move
```bash
# Use gameId from create response
curl -X POST http://localhost:8080/api/game/{gameId}/move \
  -H "Content-Type: application/json" \
  -d '{
    "playerId": "{whitePlayerId}",
    "from": "e2",
    "to": "e4",
    "timestamp": 1714502400000
  }'
```

---

## Troubleshooting

### MySQL Connection Error
```
Communications link failure
```

**Solution:**
1. Start MySQL in XAMPP Control Panel
2. Verify MySQL is running on port 3306
3. Check credentials in `application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/chess_db
   spring.datasource.username=root
   spring.datasource.password=
   ```

### Database Doesn't Exist
```
Unknown database 'chess_db'
```

**Solution:**
```sql
CREATE DATABASE chess_db;
```

### Stockfish Not Found
```
⚠️ Stockfish executable not found
⚠️ AI moves will not be available
```

**Solution:**
- This is just a warning
- Application will work for PVP games
- Install Stockfish if you need AI opponents

### Port 8080 Already in Use
```
Port 8080 was already in use
```

**Solution:**
Change port in `application.properties`:
```properties
server.port=8081
```

---

## Configuration

### Environment Variables (Production)

```bash
# Database
export DB_URL="jdbc:mysql://localhost:3306/chess_db"
export DB_USERNAME="root"
export DB_PASSWORD="your_password"

# Security
export JWT_SECRET="your-secure-secret-key"

# Stockfish (optional)
export STOCKFISH_PATH="C:\\path\\to\\stockfish.exe"
```

### Application Properties

Edit `src/main/resources/application.properties`:

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/chess_db
spring.datasource.username=root
spring.datasource.password=

# Stockfish
stockfish.path=C:\\Program Files\\Stockfish\\stockfish.exe
```

---

## API Endpoints

### REST API
- `POST /api/game/create` - Create new game
- `GET /api/game/{gameId}` - Get game state
- `POST /api/game/{gameId}/move` - Make a move
- `GET /api/game/{gameId}/ai-move` - Get AI move (requires Stockfish)
- `POST /api/game/{gameId}/forfeit` - Forfeit game
- `GET /api/game/active` - List active games

### WebSocket
- Connect: `ws://localhost:8080/ws`
- Send move: `/app/game/{gameId}/move`
- Subscribe: `/topic/game/{gameId}`

---

## Next Steps

1. ✅ Fix MySQL connection (start MySQL, create database)
2. ✅ Start application
3. ⚠️ (Optional) Install Stockfish for AI support
4. ✅ Test with curl or Postman
5. ✅ Connect your frontend

---

## Success Indicators

When everything is working, you'll see:

```
✅ Stockfish engine initialized successfully
   (or ⚠️ warning if not installed - that's OK)

✅ HikariPool-1 - Start completed

✅ Started ChessBackendApplication in X.XXX seconds

✅ Tomcat started on port(s): 8080 (http)
```

---

## Need Help?

### Check Application Logs
```bash
tail -f logs/chess-backend.log
```

### Check MySQL Status
```bash
# In XAMPP Control Panel
# MySQL should show "Running" in green
```

### Test MySQL Connection
```bash
mysql -u root -p
# Enter password (empty for XAMPP default)
SHOW DATABASES;
```

---

**Your chess backend is ready once MySQL is running and the database is created!** 🚀
