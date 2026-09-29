package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.offer.OfferRequest;
import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.service.PromotionalOfferService;
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
 * Vendor-scoped CRUD endpoints for promotional offers.
 *
 * <p>All operations are automatically constrained to the logged-in vendor's store —
 * a vendor can only manage offers for their own products.
 */
@RestController
@RequestMapping("/api/vendor/offers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
@Tag(name = "Vendor – Offers", description = "Vendor CRUD for their store's promotional offers")
public class VendorOfferController {

    private final PromotionalOfferService offerService;
    private final UserService userService;

    // ── Helper: resolve vendor's store ──────────────────────────────────────

    private Long getVendorStoreId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = jwt.getClaim("userId");
        User vendor = userService.getUserById(userId);

        if (vendor.getManagedStore() == null) {
            throw new InvalidDataException("حسابك غير مرتبط بمتجر. تواصل مع الإدارة.");
        }
        return vendor.getManagedStore().getStoreId();
    }

    private Long getVendorUserId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("userId");
    }

    // ── GET my offers (paged) ────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List my store's offers (paged)")
    public ResponseEntity<PagedResponse<OfferResponse>> getMyOffers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<OfferResponse> result = offerService.vendorGetMyOffers(getVendorStoreId(), pageable);

        return ResponseEntity.ok(new PagedResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()));
    }

    // ── GET by ID ────────────────────────────────────────────────────────────

    @GetMapping("/{offerId}")
    @Operation(summary = "Get one of my offers by ID")
    public ResponseEntity<OfferResponse> getMyOfferById(@PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.vendorGetOfferById(offerId, getVendorStoreId()));
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a new offer for one of my products")
    public ResponseEntity<OfferResponse> createOffer(@Valid @RequestBody OfferRequest request) {
        return ResponseEntity.ok(
                offerService.vendorCreateOffer(request, getVendorStoreId(), getVendorUserId()));
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @PutMapping("/{offerId}")
    @Operation(summary = "Update one of my existing offers")
    public ResponseEntity<OfferResponse> updateOffer(
            @PathVariable Long offerId,
            @Valid @RequestBody OfferRequest request) {
        return ResponseEntity.ok(
                offerService.vendorUpdateOffer(offerId, request, getVendorStoreId(), getVendorUserId()));
    }

    // ── TOGGLE STATUS ────────────────────────────────────────────────────────

    @PatchMapping("/{offerId}/status")
    @Operation(summary = "Toggle the active/inactive status of one of my offers")
    public ResponseEntity<OfferResponse> toggleStatus(@PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.vendorToggleStatus(offerId, getVendorStoreId()));
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{offerId}")
    @Operation(summary = "Delete one of my offers")
    public ResponseEntity<String> deleteOffer(@PathVariable Long offerId) {
        offerService.vendorDeleteOffer(offerId, getVendorStoreId());
        return ResponseEntity.ok("تم حذف العرض الترويجي بنجاح");
    }
}
