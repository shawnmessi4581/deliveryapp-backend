package com.deliveryapp.controller.admin;

import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.user.CreateDriverRequest;
import com.deliveryapp.dto.user.CreateUserRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.User;
import com.deliveryapp.mapper.order.OrderMapper;
import com.deliveryapp.mapper.user.UserMapper;
import com.deliveryapp.service.AdminUserService;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final OrderService orderService;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    // --- USERS ---
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminUserService.getAllUsers().stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
        adminUserService.deleteUser(userId);
        return ResponseEntity.ok("تم حذف المستخدم بنجاح");
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> updateUserStatus(@PathVariable Long userId, @RequestParam Boolean active) {
        adminUserService.updateUserStatus(userId, active);
        return ResponseEntity.ok(active ? "تم تفعيل المستخدم" : "تم تعطيل المستخدم");
    }

    @PostMapping("/users/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createDashboardUser(@RequestBody CreateUserRequest request) {
        User user = adminUserService.createDashboardUser(request);
        return ResponseEntity.ok(userMapper.toUserResponse(user));
    }

    // --- DRIVERS ---
    @GetMapping("/drivers")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<UserResponse>> getAllDrivers() {
        return ResponseEntity.ok(adminUserService.getAllDrivers().stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/drivers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<UserResponse> createDriver(
            @ModelAttribute CreateDriverRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        User driver = adminUserService.createDriver(request, image);
        return ResponseEntity.ok(userMapper.toUserResponse(driver));
    }

    @PatchMapping("/{orderId}/assign/{driverId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<OrderResponse> assignDriver(@PathVariable Long orderId, @PathVariable Long driverId) {
        Order order = orderService.assignDriver(orderId, driverId);
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }
}