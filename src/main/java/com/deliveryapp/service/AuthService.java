package com.deliveryapp.service;

import com.deliveryapp.dto.auth.AuthResponse;
import com.deliveryapp.dto.auth.LoginRequest;
import com.deliveryapp.dto.auth.SignupRequest;
import com.deliveryapp.dto.user.UserResponse;
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
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.CUSTOMER); // Default role
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Auto-login after registration
        return login(new LoginRequest(request.getPhoneNumber(), request.getPassword()));
    }

    public AuthResponse login(LoginRequest request) {
        // 1. Authenticate
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getPhoneNumber(),
                        request.getPassword()
                )
        );

        // 2. Fetch User Entity
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber()).orElseThrow();

        // 3. Update Last Login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // 4. Generate Token
        String token = tokenService.generateToken(authentication, user.getUserId());

        // 5. Map Entity to UserResponse DTO
        UserResponse userResponse = mapToUserResponse(user);

        // 6. Return combined response
        return new AuthResponse(token, userResponse);
    }

    // Helper Method to Map Entity -> DTO
    private UserResponse mapToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setName(user.getName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setEmail(user.getEmail());
        response.setProfileImage(user.getProfileImage());
        response.setUserType(user.getUserType());
        response.setIsActive(user.getIsActive());

        // Address
        response.setAddress(user.getAddress());
        response.setLatitude(user.getLatitude());
        response.setLongitude(user.getLongitude());

        // Driver Info (only if applicable, otherwise null)
        if (user.getUserType() == UserType.DRIVER) {
            response.setVehicleType(user.getVehicleType());
            response.setVehicleNumber(user.getVehicleNumber());
            response.setIsAvailable(user.getIsAvailable());
            response.setRating(user.getRating());
            response.setTotalDeliveries(user.getTotalDeliveries());
        }

        return response;
    }
}