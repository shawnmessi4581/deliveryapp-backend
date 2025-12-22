package com.deliveryapp.controller;

import com.deliveryapp.dto.user.FcmTokenRequest;
import com.deliveryapp.dto.user.UserProfileResponse;
import com.deliveryapp.dto.user.UserUpdateRequest;
import com.deliveryapp.entity.User;
import com.deliveryapp.entity.UserAddress;
import com.deliveryapp.service.AddressService;
import com.deliveryapp.service.UserService;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AddressService addressService; // Inject Address Service
    private final UrlUtil urlUtil;                   // Inject URL Utility

    // GET PROFILE (Secure & Complete)
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable Long userId) {
        // 1. Fetch User
        User user = userService.getUserById(userId);

        // 2. Fetch Addresses
        List<UserAddress> addresses = addressService.getUserAddresses(userId);

        // 3. Map to DTO
        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getUserId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setUserType(user.getUserType().name());

        // Full URL for image
        response.setProfileImage(urlUtil.getFullUrl(user.getProfileImage()));

        // Primary Address
        response.setPrimaryAddress(user.getAddress());

        // Address List
        response.setSavedAddresses(addresses);

        // Driver Fields
        if (user.getUserType().name().equals("DRIVER")) {
            response.setVehicleType(user.getVehicleType());
            response.setRating(user.getRating());
        }

        return ResponseEntity.ok(response);
    }

    // UPDATE PROFILE (Keep existing logic, just ensure return type is handled if needed)
    @PutMapping(value = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> updateUserProfile(
            @PathVariable Long userId,
            @ModelAttribute UserUpdateRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        User updatedUser = userService.updateUserProfile(userId, request, image);

        // Recursively call get logic to return the full fresh profile
        return getUserProfile(userId);
    }
    @PatchMapping("/{userId}/fcm-token")
    public ResponseEntity<String> updateFcmToken(
            @PathVariable Long userId,
            @RequestBody FcmTokenRequest request) {

        userService.updateFcmToken(userId, request.getFcmToken());
        return ResponseEntity.ok("FCM Token updated successfully");
    }
}