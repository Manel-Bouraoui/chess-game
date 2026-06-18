package com.chess.backend.repository;

import com.chess.backend.entity.GameStateEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameStateRepository extends JpaRepository<GameStateEntity, Long> {

    Optional<GameStateEntity> findByGameId(String gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GameStateEntity g WHERE g.gameId = :gameId")
    Optional<GameStateEntity> findByGameIdWithLock(@Param("gameId") String gameId);

    List<GameStateEntity> findByStatus(String status);

    List<GameStateEntity> findByMode(String mode);

    List<GameStateEntity> findByStatusIn(List<String> statuses);

    @Query("SELECT g FROM GameStateEntity g WHERE g.status = 'IN_PROGRESS'")
    List<GameStateEntity> findActiveGames();

    @Query("SELECT COUNT(g) FROM GameStateEntity g WHERE g.status = 'IN_PROGRESS'")
    Long countActiveGames();

    boolean existsByGameId(String gameId);

    void deleteByGameId(String gameId);
}
