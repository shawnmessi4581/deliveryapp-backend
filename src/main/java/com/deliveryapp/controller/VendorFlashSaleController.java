package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.flashsale.FlashSaleRequest;
import com.deliveryapp.dto.flashsale.FlashSaleResponse;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.service.FlashSaleService;
import com.deliveryapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Vendor-scoped CRUD for flash sales.
 *
 * <p>All operations are automatically constrained to the logged-in vendor's store.
 * The {@code applicableTo} is always forced to {@code STORE} server-side — vendors
 * cannot create flash sales scoped to categories or products outside their store.
 */
@RestController
@RequestMapping("/api/vendor/flash-sales")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
@Tag(name = "Vendor – Flash Sales", description = "Vendor CRUD for their store's flash sales")
public class VendorFlashSaleController {

    private final FlashSaleService flashSaleService;
    private final UserService userService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Long vendorStoreId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = jwt.getClaim("userId");
        User vendor = userService.getUserById(userId);
        if (vendor.getManagedStore() == null) {
            throw new InvalidDataException("حسابك غير مرتبط بمتجر. تواصل مع الإدارة.");
        }
        return vendor.getManagedStore().getStoreId();
    }

    private Long vendorUserId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("userId");
    }

    // ── GET all (paged) ──────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List my store's flash sales (paged)")
    public ResponseEntity<PagedResponse<FlashSaleResponse>> getMyFlashSales(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<FlashSaleResponse> result = flashSaleService.vendorGetAll(vendorStoreId(), pageable);

        return ResponseEntity.ok(new PagedResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast()));
    }

    // ── GET by ID ────────────────────────────────────────────────────────────

    @GetMapping("/{flashSaleId}")
    @Operation(summary = "Get one of my flash sales by ID")
    public ResponseEntity<FlashSaleResponse> getById(@PathVariable Long flashSaleId) {
        return ResponseEntity.ok(flashSaleService.vendorGetById(flashSaleId, vendorStoreId()));
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(
        summary = "Create a flash sale for my store",
        description = "The flash sale is automatically scoped to your store. " +
                      "A unique coupon code is auto-generated unless you provide one. " +
                      "The backing Coupon is created and managed automatically."
    )
    public ResponseEntity<FlashSaleResponse> create(@Valid @RequestBody FlashSaleRequest request) {
        return ResponseEntity.ok(flashSaleService.vendorCreate(request, vendorStoreId(), vendorUserId()));
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @PutMapping("/{flashSaleId}")
    @Operation(summary = "Update one of my flash sales")
    public ResponseEntity<FlashSaleResponse> update(
            @PathVariable Long flashSaleId,
            @Valid @RequestBody FlashSaleRequest request) {
        return ResponseEntity.ok(
                flashSaleService.vendorUpdate(flashSaleId, request, vendorStoreId(), vendorUserId()));
    }

    // ── TOGGLE STATUS ────────────────────────────────────────────────────────

    @PatchMapping("/{flashSaleId}/status")
    @Operation(summary = "Toggle active/inactive status of one of my flash sales")
    public ResponseEntity<FlashSaleResponse> toggleStatus(@PathVariable Long flashSaleId) {
        return ResponseEntity.ok(flashSaleService.vendorToggleStatus(flashSaleId, vendorStoreId()));
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{flashSaleId}")
    @Operation(summary = "Delete one of my flash sales and its backing coupon")
    public ResponseEntity<String> delete(@PathVariable Long flashSaleId) {
        flashSaleService.vendorDelete(flashSaleId, vendorStoreId());
        return ResponseEntity.ok("تم حذف الفلاش سيل والكوبون المرتبط به بنجاح");
    }
}
