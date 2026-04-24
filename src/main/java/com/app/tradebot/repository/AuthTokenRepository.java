package com.app.tradebot.repository;

import com.app.tradebot.entity.AuthTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthTokenEntity, Long> {
    Optional<AuthTokenEntity> findTopByUser_UserIdAndActiveTrueOrderByIssuedAtDesc(Long userId);
}
