package com.deliveryapp.controller;

import com.deliveryapp.dto.coupon.CouponRequest;
import com.deliveryapp.entity.Coupon;
import com.deliveryapp.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Secures all endpoints to Admin only
public class AdminCouponController {

    private final CouponService couponService;

    // 1. Get All Coupons
    @GetMapping
    public ResponseEntity<List<Coupon>> getAllCoupons() {
        return ResponseEntity.ok(couponService.getAllCoupons());
    }

    // 2. Get Coupon by ID
    @GetMapping("/{id}")
    public ResponseEntity<Coupon> getCouponById(@PathVariable Long id) {
        return ResponseEntity.ok(couponService.getCouponById(id));
    }

    // 3. Create Coupon
    @PostMapping
    public ResponseEntity<Coupon> createCoupon(@RequestBody CouponRequest request) {
        return ResponseEntity.ok(couponService.createCoupon(request));
    }

    // 4. Update Coupon
    @PutMapping("/{id}")
    public ResponseEntity<Coupon> updateCoupon(@PathVariable Long id, @RequestBody CouponRequest request) {
        return ResponseEntity.ok(couponService.updateCoupon(id, request));
    }

    // 5. Toggle Active Status (Enable/Disable)
    @PatchMapping("/{id}/status")
    public ResponseEntity<Coupon> toggleCouponStatus(@PathVariable Long id) {
        return ResponseEntity.ok(couponService.toggleCouponStatus(id));
    }

    // 6. Delete Coupon
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.ok("Coupon deleted successfully");
    }
}