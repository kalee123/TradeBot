package com.app.algodesk.broker;

import java.time.LocalDateTime;

public record BrokerSession(
        String accessToken,
        String publicToken,
        String refreshToken,
        String userBrokerId,   // broker side user id (e.g. Kite "user_id" QEX463)
        String userName,
        String email,
        String broker,
        LocalDateTime expiresAt
) {
}
