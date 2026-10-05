package com.deliveryapp.controller;

import com.deliveryapp.config.SecurityConfig;
import com.deliveryapp.dto.auth.AuthResponse;
import com.deliveryapp.exception.InvalidRefreshTokenException;
import com.deliveryapp.service.AuthService;
import com.deliveryapp.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, AuthControllerSecurityTest.TestKeys.class })
class AuthControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private AuthService authService;
    @MockitoBean private UserDetailsService userDetailsService;

    @TestConfiguration
    static class TestKeys {
        @Bean
        KeyUtils keyUtils() throws NoSuchAlgorithmException {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new KeyUtils() {
                @Override
                public KeyPair getRsaKeyPair() {
                    return keyPair;
                }
            };
        }
    }

    @Test
    void refresh_stillWorksWhenTheAppSendsItsExpiredAccessTokenAlong() throws Exception {
        when(authService.refresh("refresh-1")).thenReturn(new AuthResponse("access-2", "refresh-2", null));

        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(Instant.now().minus(2, ChronoUnit.HOURS)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-2"));
    }

    @Test
    void endedSession_isReportedWithTheSessionExpiredCode() throws Exception {
        when(authService.refresh("refresh-1")).thenThrow(new InvalidRefreshTokenException("انتهت الجلسة."));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(InvalidRefreshTokenException.CODE));
    }

    @Test
    void logout_endsTheSessionTheAccessTokenBelongsTo() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(Instant.now())))
                .andExpect(status().isOk());

        verify(authService).logout(7L, "session-1");
    }

    @Test
    void logout_withAnExpiredAccessToken_is401SoTheAppRefreshesFirst() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(Instant.now().minus(2, ChronoUnit.HOURS))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutAccessToken_is401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    /** An access token for user 7 in session "session-1", issued at {@code issuedAt}, valid for one hour. */
    private String accessToken(Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("allin-shops")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(1, ChronoUnit.HOURS))
                .subject("0999000111")
                .claim("scope", "ROLE_CUSTOMER")
                .claim("userId", 7L)
                .claim("sid", "session-1")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
