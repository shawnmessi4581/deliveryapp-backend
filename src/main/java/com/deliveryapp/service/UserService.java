package com.deliveryapp.service;

import com.deliveryapp.dto.user.UserUpdateRequest;
import com.deliveryapp.entity.OtpVerification;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.OtpVerificationRepository;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpRepository;
    private final FileStorageService fileStorageService;

    public User registerUser(User user) {
        if (userRepository.findByPhoneNumber(user.getPhoneNumber()).isPresent()) {
            throw new DuplicateResourceException("Phone number " + user.getPhoneNumber() + " is already registered.");
        }
        return userRepository.save(user);
    }

    public User getUserByPhone(String phone) {
        return userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with phone: " + phone));
    }

    @Transactional
    public String generateAndSendOtp(String phoneNumber) {
        String otpCode = String.valueOf(new Random().nextInt(9000) + 1000);

        OtpVerification otp = new OtpVerification();
        otp.setPhoneNumber(phoneNumber);
        otp.setOtpCode(otpCode);
        otp.setCreatedAt(LocalDateTime.now());
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setIsVerified(false);

        userRepository.findByPhoneNumber(phoneNumber).ifPresent(otp::setUser);

        otpRepository.save(otp);
// reals sms otp
        System.out.println("SENDING OTP TO " + phoneNumber + ": " + otpCode);
        return otpCode;
    }

    @Transactional
    public boolean verifyOtp(String phoneNumber, String code) {
        OtpVerification otp = otpRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No OTP request found for this number"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidDataException("OTP has expired. Please request a new one.");
        }

        if (otp.getOtpCode().equals(code)) {
            otp.setIsVerified(true);
            otpRepository.save(otp);
            return true;
        }

        throw new InvalidDataException("Invalid OTP code");
    }

    public User updateDriverLocation(Long driverId, Double lat, Double lng) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        if (driver.getUserType() != UserType.DRIVER) {
            throw new InvalidDataException("User is not a driver");
        }

        driver.setCurrentLocationLat(lat);
        driver.setCurrentLocationLng(lng);
        return userRepository.save(driver);
    }
    // ... imports ...

    @Transactional
    public User updateUserProfile(Long userId, UserUpdateRequest request, MultipartFile profileImage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 1. Update Basic Info
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            user.setName(request.getName());
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            user.setEmail(request.getEmail());
        }

        // 2. Update Primary Address
        if (request.getAddress() != null && !request.getAddress().trim().isEmpty()) {
            user.setAddress(request.getAddress());
            if (request.getLatitude() != null) user.setLatitude(request.getLatitude());
            if (request.getLongitude() != null) user.setLongitude(request.getLongitude());
        }

        // 3. Update Driver Specifics (Only if user is a DRIVER)
        if (user.getUserType() == com.deliveryapp.enums.UserType.DRIVER) {
            if (request.getVehicleType() != null && !request.getVehicleType().isEmpty()) {
                user.setVehicleType(request.getVehicleType());
            }
            if (request.getVehicleNumber() != null && !request.getVehicleNumber().isEmpty()) {
                user.setVehicleNumber(request.getVehicleNumber());
            }
            if (request.getIsAvailable() != null) {
                user.setIsAvailable(request.getIsAvailable());
            }
        }

        // 4. Update Profile Image
        if (profileImage != null && !profileImage.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(profileImage, "profiles");
            user.setProfileImage(imageUrl);
        }

        return userRepository.save(user);
    }
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }
}