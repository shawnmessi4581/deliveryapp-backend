package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.offer.OfferRequest;
import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.service.PromotionalOfferService;
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
 * Admin CRUD endpoints for promotional offers.
 * Admin can manage offers for ANY product across ANY store.
 */
@RestController
@RequestMapping("/api/admin/offers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin – Offers", description = "Admin CRUD for promotional offers")
public class AdminOfferController {

    private final PromotionalOfferService offerService;

    private Long getCurrentAdminId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("userId");
    }

    // ── GET all (paged) ──────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all offers (paged)")
    public ResponseEntity<PagedResponse<OfferResponse>> getAllOffers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<OfferResponse> result = offerService.adminGetAllOffers(pageable);

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
    @Operation(summary = "Get a single offer by ID")
    public ResponseEntity<OfferResponse> getOfferById(@PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.adminGetOfferById(offerId));
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a new promotional offer")
    public ResponseEntity<OfferResponse> createOffer(@Valid @RequestBody OfferRequest request) {
        return ResponseEntity.ok(offerService.adminCreateOffer(request, getCurrentAdminId()));
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @PutMapping("/{offerId}")
    @Operation(summary = "Update an existing promotional offer")
    public ResponseEntity<OfferResponse> updateOffer(
            @PathVariable Long offerId,
            @Valid @RequestBody OfferRequest request) {
        return ResponseEntity.ok(offerService.adminUpdateOffer(offerId, request, getCurrentAdminId()));
    }

    // ── TOGGLE STATUS ────────────────────────────────────────────────────────

    @PatchMapping("/{offerId}/status")
    @Operation(summary = "Toggle the active/inactive status of an offer")
    public ResponseEntity<OfferResponse> toggleStatus(@PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.adminToggleStatus(offerId));
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{offerId}")
    @Operation(summary = "Delete a promotional offer")
    public ResponseEntity<String> deleteOffer(@PathVariable Long offerId) {
        offerService.adminDeleteOffer(offerId);
        return ResponseEntity.ok("تم حذف العرض الترويجي بنجاح");
    }
}
