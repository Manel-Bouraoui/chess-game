package com.chess.backend.service;

import com.chess.backend.entity.PlayerEntity;
import com.chess.backend.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PlayerService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Get or create a player
     */
    @Transactional
    public PlayerEntity getOrCreatePlayer(String playerId, String name, String color, Boolean isAi, Integer difficulty) {
        Optional<PlayerEntity> existingPlayer = playerRepository.findByPlayerId(playerId);
        
        if (existingPlayer.isPresent()) {
            PlayerEntity player = existingPlayer.get();
            // Update connection status
            player.setConnectionStatus("CONNECTED");
            player.setLastSeen(System.currentTimeMillis());
            playerRepository.save(player);
            logger.debug("Player found: {}", playerId);
            return player;
        }

        // Create new player
        PlayerEntity player = new PlayerEntity();
        player.setPlayerId(playerId);
        player.setName(name);
        player.setColor(color);
        player.setIsAi(isAi != null ? isAi : false);
        player.setDifficulty(difficulty);
        player.setConnectionStatus("CONNECTED");
        player.setLastSeen(System.currentTimeMillis());
        
        playerRepository.save(player);
        logger.info("Player created: {} ({})", name, playerId);
        
        return player;
    }

    /**
     * Get player by ID
     */
    public PlayerEntity getPlayer(String playerId) {
        return playerRepository.findByPlayerId(playerId).orElse(null);
    }

    /**
     * Update player connection status
     */
    @Transactional
    public void updateConnectionStatus(String playerId, String status) {
        Optional<PlayerEntity> playerOpt = playerRepository.findByPlayerId(playerId);
        if (playerOpt.isPresent()) {
            PlayerEntity player = playerOpt.get();
            player.setConnectionStatus(status);
            player.setLastSeen(System.currentTimeMillis());
            playerRepository.save(player);
            logger.debug("Player {} connection status updated to {}", playerId, status);
        }
    }

    /**
     * Update player last seen timestamp
     */
    @Transactional
    public void updateLastSeen(String playerId) {
        Optional<PlayerEntity> playerOpt = playerRepository.findByPlayerId(playerId);
        if (playerOpt.isPresent()) {
            PlayerEntity player = playerOpt.get();
            player.setLastSeen(System.currentTimeMillis());
            playerRepository.save(player);
        }
    }
}
