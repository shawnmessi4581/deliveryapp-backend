package com.deliveryapp.controller;

import com.deliveryapp.dto.catalog.ProductRequest;
import com.deliveryapp.dto.catalog.StoreRequest;
import com.deliveryapp.dto.coupon.CouponRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.*;
import com.deliveryapp.repository.DeliveryInstructionRepository;
import com.deliveryapp.service.AdminService;
import com.deliveryapp.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Secures the entire controller
public class AdminController {

    private final AdminService adminService;
    private final CouponService couponService;
    private final DeliveryInstructionRepository instructionRepository;

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }
    // DELETE User
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

    // TOGGLE Status (Ban/Activate)
    // Usage: PATCH /api/admin/users/123/status?active=false
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<String> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Boolean active) {

        adminService.updateUserStatus(userId, active);
        String status = active ? "activated" : "deactivated";
        return ResponseEntity.ok("User has been " + status);
    }
    // ==================== CATEGORIES ====================

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Category> createCategory(
            @RequestParam("name") String name,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(adminService.createCategory(name, image));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return ResponseEntity.ok("Category deleted");
    }
    @PutMapping(value = "/categories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Category> updateCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        return ResponseEntity.ok(adminService.updateCategory(id, name, isActive, image));
    }

    // ==================== SUBCATEGORIES ====================

    @PostMapping(value = "/subcategories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubCategory> createSubCategory(
            @RequestParam("name") String name,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(adminService.createSubCategory(name, categoryId, image));
    }

    // ==================== STORES ====================

    @PostMapping(value = "/stores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Store> createStore(
            @ModelAttribute StoreRequest request, // Binds form fields to DTO
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        return ResponseEntity.ok(adminService.createStore(request, logo, cover));
    }

    @DeleteMapping("/stores/{id}")
    public ResponseEntity<String> deleteStore(@PathVariable Long id) {
        adminService.deleteStore(id);
        return ResponseEntity.ok("Store deleted");
    }

    // ==================== PRODUCTS ====================

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Product> createProduct(
            @ModelAttribute ProductRequest request, // Binds form fields to DTO
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(adminService.createProduct(request, image));
    }

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<?> addVariant(
            @PathVariable Long productId,
            @RequestParam("name") String name,
            @RequestParam("price") Double priceAdjustment) {
        return ResponseEntity.ok(adminService.addProductVariant(productId, name, priceAdjustment));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable Long id) {
        adminService.deleteProduct(id);
        return ResponseEntity.ok("Product deleted");
    }
    // --- COUPONS ---
    @PostMapping("/coupons")
    public ResponseEntity<Coupon> createCoupon(@RequestBody CouponRequest request) {
        return ResponseEntity.ok(couponService.createCoupon(request));
    }

    // --- DELIVERY INSTRUCTIONS ---
    @PostMapping("/instructions")
    public ResponseEntity<DeliveryInstruction> createInstruction(@RequestParam String text) {
        DeliveryInstruction instruction = new DeliveryInstruction();
        instruction.setInstruction(text);
        instruction.setIsActive(true);
        return ResponseEntity.ok(instructionRepository.save(instruction));
    }

    @DeleteMapping("/instructions/{id}")
    public ResponseEntity<String> deleteInstruction(@PathVariable Long id) {
        instructionRepository.deleteById(id);
        return ResponseEntity.ok("Instruction deleted");
    }

    @GetMapping("/instructions")
    public ResponseEntity<List<DeliveryInstruction>> getAllInstructions() {
        return ResponseEntity.ok(instructionRepository.findAll());
    }
}