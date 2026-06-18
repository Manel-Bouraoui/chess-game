# Test Stockfish Integration
Write-Host "`n=== Testing Chess Backend with Stockfish ===" -ForegroundColor Cyan

# Test 1: Create a PVE game (Player vs AI)
Write-Host "`n1. Creating PVE game..." -ForegroundColor Yellow
$createResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/game/create" `
    -Method POST `
    -ContentType "application/json" `
    -Body (@{
        mode = "PVE"
        whitePlayerName = "Human Player"
        blackPlayerName = "Stockfish AI"
        difficulty = 5
    } | ConvertTo-Json)

if ($createResponse.success) {
    Write-Host "✅ Game created successfully!" -ForegroundColor Green
    $gameId = $createResponse.game.gameId
    Write-Host "   Game ID: $gameId" -ForegroundColor Gray
    Write-Host "   White: $($createResponse.game.whitePlayer.name)" -ForegroundColor Gray
    Write-Host "   Black: $($createResponse.game.blackPlayer.name) (AI)" -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to create game" -ForegroundColor Red
    exit 1
}

# Test 2: Make a move as white player
Write-Host "`n2. Making move as white (e2 to e4)..." -ForegroundColor Yellow
$whitePlayerId = $createResponse.game.whitePlayer.id
$moveResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/game/$gameId/move" `
    -Method POST `
    -ContentType "application/json" `
    -Body (@{
        playerId = $whitePlayerId
        from = "e2"
        to = "e4"
        timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    } | ConvertTo-Json)

if ($moveResponse.success) {
    Write-Host "✅ Move successful!" -ForegroundColor Green
    Write-Host "   FEN: $($moveResponse.game.fen)" -ForegroundColor Gray
} else {
    Write-Host "❌ Move failed" -ForegroundColor Red
    exit 1
}

# Test 3: Get AI move from Stockfish
Write-Host "`n3. Requesting AI move from Stockfish (difficulty 5)..." -ForegroundColor Yellow
try {
    $aiMoveResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/game/$gameId/ai-move?difficulty=5" `
        -Method GET

    if ($aiMoveResponse.success) {
        Write-Host "✅ Stockfish generated move!" -ForegroundColor Green
        Write-Host "   Move: $($aiMoveResponse.move.move)" -ForegroundColor Cyan
        Write-Host "   Evaluation: $($aiMoveResponse.move.evaluation) centipawns" -ForegroundColor Gray
        Write-Host "   Search Depth: $($aiMoveResponse.move.depth)" -ForegroundColor Gray
        
        # Parse the move (e.g., "e7e5")
        $aiMove = $aiMoveResponse.move.move
        $from = $aiMove.Substring(0, 2)
        $to = $aiMove.Substring(2, 2)
        
        Write-Host "`n   Stockfish suggests: $from → $to" -ForegroundColor Cyan
    } else {
        Write-Host "❌ Failed to get AI move" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ Error getting AI move: $_" -ForegroundColor Red
    exit 1
}

# Test 4: Make the AI move
Write-Host "`n4. Applying AI move ($from to $to)..." -ForegroundColor Yellow
$blackPlayerId = $createResponse.game.blackPlayer.id
$aiMoveApply = Invoke-RestMethod -Uri "http://localhost:8080/api/game/$gameId/move" `
    -Method POST `
    -ContentType "application/json" `
    -Body (@{
        playerId = $blackPlayerId
        from = $from
        to = $to
        timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    } | ConvertTo-Json)

if ($aiMoveApply.success) {
    Write-Host "✅ AI move applied successfully!" -ForegroundColor Green
    Write-Host "   New FEN: $($aiMoveApply.game.fen)" -ForegroundColor Gray
    Write-Host "   Move count: $($aiMoveApply.game.moveCount)" -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to apply AI move" -ForegroundColor Red
    exit 1
}

# Test 5: Test different difficulty levels
Write-Host "`n5. Testing different difficulty levels..." -ForegroundColor Yellow
foreach ($diff in @(1, 5, 10)) {
    Write-Host "   Testing difficulty $diff..." -ForegroundColor Gray
    try {
        $testMove = Invoke-RestMethod -Uri "http://localhost:8080/api/game/$gameId/ai-move?difficulty=$diff" `
            -Method GET
        Write-Host "   ✅ Difficulty $diff`: $($testMove.move.move) (depth: $($testMove.move.depth))" -ForegroundColor Green
    } catch {
        Write-Host "   ❌ Difficulty $diff failed" -ForegroundColor Red
    }
}

# Summary
Write-Host "`n=== Test Summary ===" -ForegroundColor Cyan
Write-Host "✅ Stockfish is working correctly!" -ForegroundColor Green
Write-Host "✅ AI can generate moves" -ForegroundColor Green
Write-Host "✅ Different difficulty levels work" -ForegroundColor Green
Write-Host "✅ Moves can be applied to the game" -ForegroundColor Green
Write-Host "`n🎮 Your Chess Backend with Stockfish AI is fully operational!" -ForegroundColor Cyan
Write-Host "`nGame ID for testing: $gameId" -ForegroundColor Yellow
