package com.deliveryapp.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;

    public TokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * Standard token generation — used by login().
     * Pulls scope from the Authentication object's granted authorities.
     */
    public String generateToken(Authentication authentication, Long userId) {
        Instant now = Instant.now();

        String scope = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(now.plus(3650, ChronoUnit.DAYS)) // ← fixed
                .subject(authentication.getName())
                .claim("scope", scope)
                .claim("userId", userId)
                .build();

        return this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Token generation after OTP verification — used by verifyAccount().
     *
     * We don't have an Authentication object here because the user just verified
     * their OTP and we can't re-authenticate without the raw password.
     * We build the claims manually using the same structure as generateToken()
     * so the token is identical in format and the rest of the app works normally.
     *
     * scope is set to "ROLE_CUSTOMER" to match what Spring Security
     * would assign via the normal login path.
     */
    public String generateTokenForUserId(Long userId, String phoneNumber) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(now.plus(3650, ChronoUnit.DAYS)) // ← fixed
                .subject(phoneNumber) // matches authentication.getName() from login
                .claim("scope", "ROLE_CUSTOMER")
                .claim("userId", userId)
                .build();

        return this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}