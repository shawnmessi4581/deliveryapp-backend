package com.deliveryapp.controller;

import com.deliveryapp.dto.user.FcmTokenRequest;
import com.deliveryapp.dto.user.UserProfileResponse;
import com.deliveryapp.dto.user.UserUpdateRequest;
import com.deliveryapp.entity.User;
import com.deliveryapp.entity.UserAddress;
import com.deliveryapp.mapper.user.UserMapper; // 1. Import Mapper
import com.deliveryapp.service.AddressService;
import com.deliveryapp.service.UserService;
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
    private final AddressService addressService;
    private final UserMapper userMapper; // 2. Inject Mapper

    // GET PROFILE (Secure & Complete)
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable Long userId) {
        // 1. Fetch User & Addresses
        User user = userService.getUserById(userId);
        List<UserAddress> addresses = addressService.getUserAddresses(userId);

        // 2. Map & Return
        return ResponseEntity.ok(userMapper.toUserProfileResponse(user, addresses));
    }

    // UPDATE PROFILE
    @PutMapping(value = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> updateUserProfile(
            @PathVariable Long userId,
            @ModelAttribute UserUpdateRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        // 1. Update User
        User updatedUser = userService.updateUserProfile(userId, request, image);

        // 2. Fetch Addresses to build full response
        List<UserAddress> addresses = addressService.getUserAddresses(userId);

        // 3. Map & Return
        return ResponseEntity.ok(userMapper.toUserProfileResponse(updatedUser, addresses));
    }

    @PatchMapping("/{userId}/fcm-token")
    public ResponseEntity<String> updateFcmToken(
            @PathVariable Long userId,
            @RequestBody FcmTokenRequest request) {

        userService.updateFcmToken(userId, request.getFcmToken());
        return ResponseEntity.ok("تم تحديث رمز FCM بنجاح");
    }
}