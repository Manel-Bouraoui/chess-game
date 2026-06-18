package com.chess.backend.repository;

import com.chess.backend.entity.PlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerEntity, Long> {

    Optional<PlayerEntity> findByPlayerId(String playerId);

    Optional<PlayerEntity> findByName(String name);

    List<PlayerEntity> findByConnectionStatus(String connectionStatus);

    List<PlayerEntity> findByIsAi(Boolean isAi);

    boolean existsByPlayerId(String playerId);

    void deleteByPlayerId(String playerId);
}
