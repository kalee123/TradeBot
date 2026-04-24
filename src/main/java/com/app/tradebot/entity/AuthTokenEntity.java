package com.app.tradebot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_tokens", schema = "tradedb")
public class AuthTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "access_token", nullable = false, length = 500)
    private String accessToken;

    @Column(name = "api_key", nullable = false, length = 100)
    private String apiKey;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public void setAccessToken(String accessToken) {
    }

    public void setApiKey(String yourApiKey) {
    }

    public void setExpiresAt(LocalDateTime localDateTime) {
    }
}
