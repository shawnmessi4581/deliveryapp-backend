package com.deliveryapp.service;

import com.deliveryapp.dto.user.CreateDriverRequest;
import com.deliveryapp.dto.user.CreateUserRequest;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود برقم: " + userId));

        if (user.getProfileImage() != null) {
            fileStorageService.deleteFile(user.getProfileImage());
        }
        userRepository.deleteById(userId);
    }

    @Transactional
    public void updateUserStatus(Long userId, Boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود برقم: " + userId));
        user.setIsActive(isActive);
        userRepository.save(user);
    }

    public List<User> getAllDrivers() {
        return userRepository.findByUserType(UserType.DRIVER);
    }

    @Transactional
    public User createDriver(CreateDriverRequest request, MultipartFile image) {
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new DuplicateResourceException("رقم الهاتف مسجل بالفعل");
        }

        User driver = new User();
        driver.setName(request.getName());
        driver.setPhoneNumber(request.getPhoneNumber());
        driver.setEmail(request.getEmail());
        driver.setPassword(passwordEncoder.encode(request.getPassword()));
        driver.setUserType(UserType.DRIVER);
        driver.setIsActive(true);

        driver.setVehicleType(request.getVehicleType());
        driver.setVehicleNumber(request.getVehicleNumber());
        driver.setIsAvailable(true);
        driver.setRating(5.0);
        driver.setTotalDeliveries(0);
        driver.setCreatedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "profiles");
            driver.setProfileImage(imageUrl);
        }

        return userRepository.save(driver);
    }

    @Transactional
    public User createDashboardUser(CreateUserRequest request) {
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new DuplicateResourceException("رقم الهاتف موجود بالفعل");
        }

        User user = new User();
        user.setName(request.getName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        if (request.getRole() == UserType.ADMIN || request.getRole() == UserType.EMPLOYEE) {
            user.setUserType(request.getRole());
        } else {
            throw new InvalidDataException("دور غير صالح. استخدم ADMIN أو EMPLOYEE.");
        }

        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
}