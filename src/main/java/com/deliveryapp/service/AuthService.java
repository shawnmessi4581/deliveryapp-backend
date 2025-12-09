package com.deliveryapp.service;

import com.deliveryapp.dto.auth.AuthResponse;
import com.deliveryapp.dto.auth.LoginRequest;
import com.deliveryapp.dto.auth.SignupRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.OtpVerification;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.OtpVerificationRepository;
import com.deliveryapp.repository.UserRepository;
import jakarta.transaction.Transactional;
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
    private  final OtpVerificationRepository  otpVerificationRepository;
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

    @Transactional
    public String initiatePasswordReset (String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with this phone number"));
        String otpCode = String.valueOf((int) (Math.random() * 9000) + 1000);
        // Clear existing OTPs for this number to prevent clutter
        otpVerificationRepository.deleteByPhoneNumber(phoneNumber);
        // Save new OTP
       OtpVerification otp = new OtpVerification();
        otp.setPhoneNumber(phoneNumber);
        otp.setOtpCode(otpCode);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(10)); // Valid for 10 mins
        otp.setUser(user);
        otpVerificationRepository.save(otp);

        // MOCK SMS SENDING
        System.out.println("========================================");
        System.out.println("🔐 OTP FOR PASSWORD RESET (" + phoneNumber + "): " + otpCode);
        System.out.println("========================================");
        // In real life: smsService.send(phoneNumber, "Your code is " + otpCode);

        return "OTP sent successfully";
    }

    // 2. Verify OTP & Reset Password
    @Transactional
    public String resetPassword(String phoneNumber, String otpCode, String newPassword) {
        // Find the OTP
     OtpVerification dbOtp = otpVerificationRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new InvalidDataException("Invalid or expired OTP"));

        // Check Expiry
        if (dbOtp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidDataException("OTP has expired. Please request a new one.");
        }

        // Check Code Match
        if (!dbOtp.getOtpCode().equals(otpCode)) {
            throw new InvalidDataException("Incorrect OTP code");
        }

        // Retrieve User
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Update Password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Clean up used OTP
        otpVerificationRepository.delete(dbOtp);

        return "Password changed successfully. You can now login.";
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