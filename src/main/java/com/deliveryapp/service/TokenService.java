package com.deliveryapp.service;

import com.deliveryapp.entity.RefreshToken;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    // Access token is short-lived — if stolen, it expires quickly.
    private static final long ACCESS_TOKEN_MINUTES = 15;

    // Refresh token is long-lived — stored hashed in DB, rotated on every use.
    private static final long REFRESH_TOKEN_DAYS = 30;

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    // ─── Access Token ──────────────────────────────────────────────────────────

    /**
     * Generates a short-lived JWT access token from a Spring Security
     * Authentication.
     * Used by login().
     */
    public String generateAccessToken(Authentication authentication, Long userId) {
        String scope = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));
        return buildAccessToken(authentication.getName(), scope, userId);
    }

    /**
     * Generates a short-lived JWT access token directly from user fields.
     * Used after OTP verification where we don't have an Authentication object.
     */
    public String generateAccessTokenForUser(User user) {
        String scope = "ROLE_" + user.getUserType().name();
        return buildAccessToken(user.getPhoneNumber(), scope, user.getUserId());
    }

    private String buildAccessToken(String subject, String scope, Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("allin-shops")
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES))
                .subject(subject)
                .claim("scope", scope)
                .claim("userId", userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    // ─── Refresh Token ─────────────────────────────────────────────────────────

    /**
     * Creates a new refresh token for a user, starting a fresh token family.
     * Call this on first login or after logout+login.
     *
     * @param user       the authenticated user
     * @param deviceInfo optional device identifier (e.g. "Flutter/Android")
     * @return the raw token to send to the client (only time it's available in
     *         plain text)
     */
    @Transactional
    public String createRefreshToken(User user, String deviceInfo) {
        String familyId = UUID.randomUUID().toString();
        return issueRefreshToken(user, familyId, deviceInfo);
    }

    /**
     * Rotates a refresh token:
     * 1. Validates the incoming raw token
     * 2. Detects reuse (theft) and nukes the entire family if found
     * 3. Revokes the old token
     * 4. Issues a new token in the same family
     *
     * @param rawToken the raw refresh token sent by the client
     * @return the new raw refresh token
     */
    @Transactional
    public RotationResult rotateRefreshToken(String rawToken) {
        String hash = sha256(rawToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidDataException("رمز التحديث غير صالح."));

        // ── Theft detection: token was already rotated/revoked ──
        if (stored.isRevoked()) {
            log.warn("SECURITY: Reuse of revoked refresh token detected. Family: {} | User: {}",
                    stored.getFamilyId(), stored.getUser().getUserId());
            // Nuke the entire family — all sessions from this login are now invalid.
            refreshTokenRepository.revokeFamily(stored.getFamilyId());
            throw new InvalidDataException(
                    "تم اكتشاف إعادة استخدام رمز منتهي. تم إلغاء جميع الجلسات لأسباب أمنية. يرجى تسجيل الدخول مرة أخرى.");
        }

        // ── Expiry check ──
        if (stored.isExpired()) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new InvalidDataException("انتهت صلاحية رمز التحديث. يرجى تسجيل الدخول مرة أخرى.");
        }

        // ── Revoke old token ──
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        // ── Issue new token in the same family ──
        User user = stored.getUser();
        String newRawToken = issueRefreshToken(user, stored.getFamilyId(), stored.getDeviceInfo());

        log.debug("Refresh token rotated for user: {}", user.getUserId());
        return new RotationResult(user, newRawToken);
    }

    /**
     * Revokes all refresh tokens for a user.
     * Call on logout, password change, or account compromise.
     */
    @Transactional
    public void revokeAllTokensForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
        log.info("All refresh tokens revoked for user: {}", userId);
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────

    private String issueRefreshToken(User user, String familyId, String deviceInfo) {
        // Generate a cryptographically random 256-bit token
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setTokenHash(sha256(rawToken));
        token.setFamilyId(familyId);
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));
        token.setDeviceInfo(deviceInfo);
        token.setRevoked(false);
        refreshTokenRepository.save(token);

        return rawToken;
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

    /**
     * Carries both the user and the new raw refresh token out of
     * rotateRefreshToken().
     */
    public record RotationResult(User user, String newRawRefreshToken) {
    }

    // ─── Backward-compat stubs (remove after migrating all callers) ───────────

    /**
     * @deprecated Use generateAccessToken(Authentication, Long) instead.
     */
    @Deprecated
    public String generateToken(Authentication authentication, Long userId) {
        return generateAccessToken(authentication, userId);
    }

    /**
     * @deprecated Use generateAccessTokenForUser(User) instead.
     */
    @Deprecated
    public String generateTokenForUserId(Long userId, String phoneNumber) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("allin-shops")
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_MINUTES, ChronoUnit.MINUTES))
                .subject(phoneNumber)
                .claim("scope", "ROLE_CUSTOMER")
                .claim("userId", userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}