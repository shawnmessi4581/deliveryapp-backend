package com.deliveryapp.config;

import com.deliveryapp.util.KeyUtils;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    public SecurityConfig(KeyUtils keyUtils) {
        KeyPair keyPair = keyUtils.getRsaKeyPair();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Actuator
                        .requestMatchers("/actuator/**").permitAll()
                        // Swagger / OpenAPI
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**")
                        .permitAll()
                        // Logout ends the caller's own session, so it needs their access token
                        .requestMatchers("/api/auth/logout", "/api/auth/logout-all").authenticated()
                        // Auth endpoints — /refresh uses its own refresh token, not a JWT
                        .requestMatchers("/api/auth/**").permitAll()
                        // Static uploads
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/app/download", "/api/app/link", "/api/app/version").permitAll()
                        .requestMatchers("/api/telegram/**").permitAll()
                        // Public browsing
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/catalog/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/banners/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/store-categories/**")
                        .permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/offers/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/flash-sales/**").permitAll()
                        // WebSocket endpoint
                        .requestMatchers("/ws/**").permitAll()
                        // Everything else requires a valid access token
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Login, signup, OTP and refresh never need an access token, but apps often attach the stored one
     * (possibly expired) to every request. Spring would reject an expired token with 401 before the
     * endpoint runs, so a stale header could stop a user from refreshing or even logging in.
     * The header is ignored on those endpoints.
     */
    private BearerTokenResolver bearerTokenResolver() {
        RequestMatcher noAccessTokenNeeded = new AndRequestMatcher(
                AntPathRequestMatcher.antMatcher("/api/auth/**"),
                new NegatedRequestMatcher(new OrRequestMatcher(
                        AntPathRequestMatcher.antMatcher("/api/auth/logout"),
                        AntPathRequestMatcher.antMatcher("/api/auth/logout-all"))));
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> noAccessTokenNeeded.matches(request) ? null : defaultResolver.resolve(request);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "https://allin-shops.com",
                "http://allin-shops.com",
                "https://dashboard.allin-shops.com"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(this.publicKey).build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        JWK jwk = new RSAKey.Builder(this.publicKey).privateKey(this.privateKey).build();
        JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwks);
    }

    /**
     * Key that derives each new refresh token from the previous one (see TokenService).
     * Taken from the JWT signing key, so it needs no extra configuration and stays the same
     * across restarts as long as the RSA key files do.
     */
    @Bean
    public SecretKey refreshTokenKey() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("allin-shops:refresh-token:v1".getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest.digest(this.privateKey.getEncoded()), "HmacSHA256");
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}