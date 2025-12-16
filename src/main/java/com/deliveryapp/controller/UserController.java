package com.deliveryapp.controller;

import com.deliveryapp.dto.user.UserUpdateRequest;
import com.deliveryapp.entity.User;
import com.deliveryapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // UPDATE PROFILE
    @PutMapping(value = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<User> updateUserProfile(
            @PathVariable Long userId,
            @ModelAttribute UserUpdateRequest request, // Binds name, email, vehicle info
            @RequestParam(value = "image", required = false) MultipartFile image) {

        // Security Note: In a real app, you should check if the logged-in user matches the userId
        User updatedUser = userService.updateUserProfile(userId, request, image);
        return ResponseEntity.ok(updatedUser);
    }

    // GET PROFILE
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserProfile(@PathVariable Long userId) {
        // You might want to map this to a DTO to hide password/internal fields
        return ResponseEntity.ok(userService.getUserByPhone(
                userService.getUserById(userId).getPhoneNumber() // Reusing existing method or add getById to service
        ));
    }
}