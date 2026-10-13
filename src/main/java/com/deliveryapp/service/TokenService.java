package com.deliveryapp.service;

import com.deliveryapp.entity.RefreshToken;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.InvalidRefreshTokenException;
import com.deliveryapp.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues the access token (JWT) and refresh token of each device session.
 *
 * <p>A refresh must never log a real user out, so the refresh token does not change during a
 * session: a refresh only issues a new access token and sends the same refresh token back. Lost
 * responses, retries and parallel refreshes therefore can't leave the app holding an outdated
 * token. Each refresh also pushes the session's expiry forward, so a session only ends after
 * {@code app.auth.refresh-token-ttl} without opening the app, or on logout, password reset or
 * account deactivation.
 *
 * <p>Refresh tokens used to rotate on every use, each new one derived from the previous one with a
 * server key. Apps that still hold the token from before their last rotation are given the current
 * one (see {@link #refresh}).
 */
@Slf4j
@Service
public class TokenService {

    /** refresh_tokens.device_info column length. */
    private static final int DEVICE_INFO_MAX_LENGTH = 100;

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecretKey refreshTokenKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder,
                        RefreshTokenRepository refreshTokenRepository,
                        SecretKey refreshTokenKey,
                        @Value("${app.auth.access-token-ttl:24h}") Duration accessTokenTtl,
                        @Value("${app.auth.refresh-token-ttl:365d}") Duration refreshTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenKey = refreshTokenKey;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    // ─── Sessions ──────────────────────────────────────────────────────────────

    /**
     * Starts a new device session (login or account verification) and returns its first tokens.
     *
     * @param deviceInfo optional device identifier (e.g. "Flutter/Android")
     */
    @Transactional
    public SessionTokens startSession(User user, String deviceInfo) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = randomToken();
        saveRefreshToken(user, sessionId, refreshToken, truncate(deviceInfo));
        return new SessionTokens(user, generateAccessToken(user, sessionId), refreshToken);
    }

    /**
     * Returns a new access token for the session, along with the same refresh token.
     *
     * @throws InvalidRefreshTokenException when the session is over and the user must log in again
     */
    @Transactional
    public SessionTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            log.warn("Refresh rejected (no refresh token sent)");
            throw new InvalidRefreshTokenException("رمز التحديث مفقود. يرجى تسجيل الدخول مرة أخرى.");
        }
        String presented = rawRefreshToken.trim();

        // Locked, so a logout running at the same time can't be undone by the expiry update below
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(sha256(presented)).orElse(null);
        if (stored != null && isUsable(stored)) {
            return renew(stored, presented);
        }

        // From when refresh tokens rotated: the app may still hold the token from before its last
        // rotation (it never received or saved the response). The current one was derived from it,
        // so hand that one over. This also works after the cleanup job deleted the old row.
        String successor = successorOf(presented);
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(sha256(successor)).orElse(null);
        if (current != null && isUsable(current)) {
            log.info("Refresh with the token from before the last rotation: user {} | session {}",
                    current.getUser().getUserId(), current.getFamilyId());
            return renew(current, successor);
        }

        throw rejection(current != null ? current : stored);
    }

    /** Ends one device session (logout on that device). */
    @Transactional
    public void revokeSession(String sessionId) {
        refreshTokenRepository.revokeFamily(sessionId);
        log.info("Session revoked: {}", sessionId);
    }

    /**
     * Revokes all refresh tokens for a user.
     * Call on logout from all devices, password change, or account deletion.
     */
    @Transactional
    public void revokeAllTokensForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
        log.info("All refresh tokens revoked for user: {}", userId);
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────

    /** New access token, same refresh token; the session now ends a full TTL from today. */
    private SessionTokens renew(RefreshToken token, String rawToken) {
        User user = token.getUser();
        ensureAccountActive(user);

        token.setExpiresAt(LocalDateTime.now().plus(refreshTokenTtl));
        refreshTokenRepository.save(token);

        log.debug("Session refreshed for user: {}", user.getUserId());
        return new SessionTokens(user, generateAccessToken(user, token.getFamilyId()), rawToken);
    }

    /** {@code latest} is the newest row we know of in the token's chain (null when the token is unknown). */
    private InvalidRefreshTokenException rejection(RefreshToken latest) {
        if (latest == null) {
            log.warn("Refresh rejected (unknown refresh token)");
            return new InvalidRefreshTokenException("رمز التحديث غير صالح. يرجى تسجيل الدخول مرة أخرى.");
        }
        Long userId = latest.getUser().getUserId();
        if (latest.getRotatedAt() != null) {
            // From when refresh tokens rotated: the token that replaced this one was used already.
            log.warn("Refresh rejected (old token reused after its replacement was used): user {} | session {}",
                    userId, latest.getFamilyId());
        } else if (latest.isRevoked()) {
            log.warn("Refresh rejected (session ended by logout or password change): user {} | session {}",
                    userId, latest.getFamilyId());
        } else {
            log.warn("Refresh rejected (session expired): user {} | session {}", userId, latest.getFamilyId());
            return new InvalidRefreshTokenException("انتهت صلاحية رمز التحديث. يرجى تسجيل الدخول مرة أخرى.");
        }
        return new InvalidRefreshTokenException("انتهت الجلسة. يرجى تسجيل الدخول مرة أخرى.");
    }

    private void ensureAccountActive(User user) {
        // Same rule as login: only an account that was explicitly deactivated (or deleted) is refused.
        if (Boolean.FALSE.equals(user.getIsActive())) {
            log.warn("Refresh rejected (account disabled): user {}", user.getUserId());
            throw new InvalidRefreshTokenException("هذا الحساب غير مفعل. يرجى التواصل مع الدعم.");
        }
    }

    private String generateAccessToken(User user, String sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("allin-shops")
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(user.getPhoneNumber())
                .claim("scope", "ROLE_" + user.getUserType().name())
                .claim("userId", user.getUserId())
                .claim("sid", sessionId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private void saveRefreshToken(User user, String sessionId, String rawToken, String deviceInfo) {
        RefreshToken token = new RefreshToken();
        token.setTokenHash(sha256(rawToken));
        token.setFamilyId(sessionId);
        token.setUser(user);
        // Pushed forward on every refresh (see renew), so a session only expires after this long without use.
        token.setExpiresAt(LocalDateTime.now().plus(refreshTokenTtl));
        token.setDeviceInfo(deviceInfo);
        token.setRevoked(false);
        refreshTokenRepository.save(token);
    }

    private static boolean isUsable(RefreshToken token) {
        return !token.isRevoked() && !token.isExpired();
    }

    /** A cryptographically random 256-bit token. */
    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The refresh token that replaced {@code refreshToken} back when refresh tokens rotated. */
    private String successorOf(String refreshToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(refreshTokenKey);
            byte[] next = mac.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(next);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static String truncate(String deviceInfo) {
        if (deviceInfo == null || deviceInfo.length() <= DEVICE_INFO_MAX_LENGTH) {
            return deviceInfo;
        }
        return deviceInfo.substring(0, DEVICE_INFO_MAX_LENGTH);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ─── Result wrapper ────────────────────────────────────────────────────────

    /** The user and the tokens to send back after login, account verification or refresh. */
    public record SessionTokens(User user, String accessToken, String refreshToken) {
    }
}
