package com.deliveryapp.scheduled;

import com.deliveryapp.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Cleans up expired and revoked refresh tokens from the database.
 *
 * Enable scheduling in your main class or config:
 * 
 * @EnableScheduling on your @SpringBootApplication class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Runs every day at 03:00 AM server time.
     * Deletes all tokens that are either revoked or expired more than 7 days ago.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        refreshTokenRepository.deleteExpiredOrRevoked(cutoff);
        log.info("Refresh token cleanup completed. Cutoff: {}", cutoff);
    }
}