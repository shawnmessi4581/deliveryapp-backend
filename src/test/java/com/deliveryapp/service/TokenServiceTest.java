package com.deliveryapp.service;

import com.deliveryapp.entity.RefreshToken;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidRefreshTokenException;
import com.deliveryapp.repository.RefreshTokenRepository;
import com.deliveryapp.service.TokenService.SessionTokens;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static KeyPair keyPair;

    private final SecretKey refreshTokenKey =
            new SecretKeySpec("test-key-for-refresh-tokens-0001".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @Mock private RefreshTokenRepository repository;

    /** Stand-in for the refresh_tokens table, keyed by token hash. */
    private final Map<String, RefreshToken> rows = new HashMap<>();

    private TokenService tokenService;
    private JwtDecoder jwtDecoder;
    private User customer;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(keyPair.getPrivate()).build();
        jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        tokenService = new TokenService(
                new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk))),
                repository,
                refreshTokenKey,
                Duration.ofHours(24),
                Duration.ofDays(365));

        lenient().when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getCreatedAt() == null) {
                token.setCreatedAt(LocalDateTime.now());
            }
            rows.put(token.getTokenHash(), token);
            return token;
        });
        lenient().when(repository.findByTokenHash(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.<String>getArgument(0))));
        lenient().when(repository.findByTokenHashForUpdate(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.<String>getArgument(0))));
        lenient().doAnswer(invocation -> {
            String familyId = invocation.getArgument(0);
            rows.values().stream().filter(t -> t.getFamilyId().equals(familyId)).forEach(t -> t.setRevoked(true));
            return null;
        }).when(repository).revokeFamily(anyString());
        lenient().doAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            rows.values().stream().filter(t -> t.getUser().getUserId().equals(userId)).forEach(t -> t.setRevoked(true));
            return null;
        }).when(repository).revokeAllForUser(anyLong());

        customer = new User();
        customer.setUserId(7L);
        customer.setPhoneNumber("0999000111");
        customer.setUserType(UserType.CUSTOMER);
        customer.setIsActive(true);
    }

    @Test
    void refresh_issuesANewAccessToken_andKeepsTheSameRefreshToken() {
        SessionTokens login = tokenService.startSession(customer, "Flutter/Android");

        SessionTokens refreshed = tokenService.refresh(login.refreshToken());

        assertEquals(login.refreshToken(), refreshed.refreshToken());
        assertSame(customer, refreshed.user());
        assertDoesNotThrow(() -> jwtDecoder.decode(refreshed.accessToken()));
    }

    @Test
    void retriesAndParallelRefreshes_allKeepWorking_whateverResponseTheAppKeeps() {
        String token = tokenService.startSession(customer, null).refreshToken();

        Set<String> issued = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            issued.add(tokenService.refresh(token).refreshToken());
        }

        assertEquals(Set.of(token), issued);
        assertEquals(1, rows.size());
    }

    @Test
    void refresh_pushesTheSessionExpiryAFullTtlForward() {
        String token = tokenService.startSession(customer, null).refreshToken();
        RefreshToken row = rows.values().iterator().next();
        row.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        tokenService.refresh(token);

        assertTrue(row.getExpiresAt().isAfter(LocalDateTime.now().plusDays(364)));
    }

    @Test
    void appStillHoldingTheTokenFromBeforeItsLastRotation_getsTheCurrentOne() {
        // Refreshed on a backend that rotated tokens, but the app never saved the response
        String original = tokenService.startSession(customer, null).refreshToken();
        String current = rotateTheOldWay(original);

        assertEquals(current, tokenService.refresh(original).refreshToken());
        assertEquals(current, tokenService.refresh(current).refreshToken());
    }

    @Test
    void tokenFromBeforeTheLastRotation_worksEvenAfterTheCleanupJobDeletedItsRow() {
        String original = tokenService.startSession(customer, null).refreshToken();
        String current = rotateTheOldWay(original);
        rows.values().removeIf(RefreshToken::isRevoked);

        assertEquals(current, tokenService.refresh(original).refreshToken());
    }

    @Test
    void tokenFromTwoRotationsAgo_isRejected_withoutEndingTheSession() {
        String first = tokenService.startSession(customer, null).refreshToken();
        String third = rotateTheOldWay(rotateTheOldWay(first));

        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(first));
        assertDoesNotThrow(() -> tokenService.refresh(third));
    }

    @Test
    void logout_endsOnlyThatDevicesSession() {
        SessionTokens phone = tokenService.startSession(customer, "phone");
        SessionTokens tablet = tokenService.startSession(customer, "tablet");

        tokenService.revokeSession(sessionId(phone));

        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(phone.refreshToken()));
        assertDoesNotThrow(() -> tokenService.refresh(tablet.refreshToken()));
    }

    @Test
    void logout_alsoEndsTheTokenFromBeforeTheLastRotation() {
        SessionTokens login = tokenService.startSession(customer, null);
        String current = rotateTheOldWay(login.refreshToken());

        tokenService.revokeSession(sessionId(login));

        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(login.refreshToken()));
        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(current));
    }

    @Test
    void deactivatedAccount_cannotRefresh() {
        String token = tokenService.startSession(customer, null).refreshToken();
        customer.setIsActive(false);

        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(token));
    }

    @Test
    void accountWithoutActiveFlag_canStillRefresh() {
        // Same rule as login: only an explicit false blocks
        String token = tokenService.startSession(customer, null).refreshToken();
        customer.setIsActive(null);

        assertDoesNotThrow(() -> tokenService.refresh(token));
    }

    @Test
    void expiredSession_isRejected() {
        String token = tokenService.startSession(customer, null).refreshToken();
        rows.values().forEach(t -> t.setExpiresAt(LocalDateTime.now().minusMinutes(1)));

        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(token));
    }

    @Test
    void missingOrUnknownToken_isRejectedAsAnEndedSession() {
        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh(null));
        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh("  "));
        assertThrows(InvalidRefreshTokenException.class, () -> tokenService.refresh("not-a-real-token"));
    }

    @Test
    void accessToken_lastsADayAndKeepsItsSessionAcrossRefreshes() {
        SessionTokens login = tokenService.startSession(customer, null);
        Jwt jwt = jwtDecoder.decode(login.accessToken());

        assertEquals(7L, jwt.<Long>getClaim("userId"));
        assertEquals("ROLE_CUSTOMER", jwt.getClaimAsString("scope"));
        assertEquals(Duration.ofHours(24), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
        assertNotNull(jwt.getClaimAsString("sid"));

        Jwt refreshed = jwtDecoder.decode(tokenService.refresh(login.refreshToken()).accessToken());
        assertEquals(jwt.getClaimAsString("sid"), refreshed.getClaimAsString("sid"));
    }

    @Test
    void longDeviceInfo_isCutToFitTheColumn() {
        tokenService.startSession(customer, "x".repeat(500));

        assertEquals(100, rows.values().iterator().next().getDeviceInfo().length());
    }

    private String sessionId(SessionTokens tokens) {
        return jwtDecoder.decode(tokens.accessToken()).getClaimAsString("sid");
    }

    /** Does what /refresh did while refresh tokens rotated, and returns the token it handed out. */
    private String rotateTheOldWay(String token) {
        RefreshToken previous = rows.get(sha256(token));
        previous.setRevoked(true);
        previous.setRotatedAt(LocalDateTime.now());

        String next;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(refreshTokenKey);
            next = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }

        RefreshToken current = new RefreshToken();
        current.setTokenHash(sha256(next));
        current.setFamilyId(previous.getFamilyId());
        current.setUser(previous.getUser());
        current.setExpiresAt(LocalDateTime.now().plusDays(365));
        current.setCreatedAt(LocalDateTime.now());
        rows.put(current.getTokenHash(), current);
        return next;
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
