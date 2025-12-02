package com.deliveryapp.service;

import com.deliveryapp.dto.auth.AuthResponse;
import com.deliveryapp.dto.auth.LoginRequest;
import com.deliveryapp.dto.auth.SignupRequest;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthResponse register(SignupRequest request) {
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new DuplicateResourceException("Phone number already exists");
        }

        User user = new User();
        user.setName(request.getName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail()); // Optional
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.CUSTOMER);
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Auto-login logic
        return login(new LoginRequest(request.getPhoneNumber(), request.getPassword()));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getPhoneNumber(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByPhoneNumber(request.getPhoneNumber()).orElseThrow();

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = tokenService.generateToken(authentication, user.getUserId());

        return new AuthResponse(token, user.getUserId(), user.getPhoneNumber(), user.getUserType().name());
    }
}