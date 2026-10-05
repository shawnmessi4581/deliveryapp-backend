package com.deliveryapp.service;

import com.deliveryapp.entity.User;
import com.deliveryapp.mapper.user.UserMapper;
import com.deliveryapp.repository.OtpVerificationRepository;
import com.deliveryapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenService tokenService;
    @Mock private OtpVerificationRepository otpVerificationRepository;
    @Mock private UserMapper userMapper;
    @Mock private SmsService smsService;

    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, authenticationManager, tokenService,
                otpVerificationRepository, userMapper, smsService);

        user = new User();
        user.setUserId(7L);
        user.setFcmToken("fcm-token");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void logout_endsOnlyTheCallersSession() {
        service.logout(7L, "session-1");

        verify(tokenService).revokeSession("session-1");
        verify(tokenService, never()).revokeAllTokensForUser(any());
        assertNull(user.getFcmToken());
    }

    @Test
    void logout_withAnAccessTokenFromBeforeSessionsExisted_endsAllSessions() {
        service.logout(7L, null);

        verify(tokenService).revokeAllTokensForUser(7L);
        assertNull(user.getFcmToken());
    }
}
