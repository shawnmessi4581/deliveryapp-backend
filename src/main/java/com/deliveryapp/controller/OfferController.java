package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.service.PromotionalOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public (unauthenticated) read-only endpoints for promotional offers.
 * Customers use these to display active offer badges on product cards.
 */
@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
@Tag(name = "Public Offers", description = "Public endpoints for browsing active promotional offers")
public class OfferController {

    private final PromotionalOfferService offerService;

    /**
     * GET /api/offers?page=0&size=10
     * Returns the currently active and valid promotional offers, newest first.
     */
    @GetMapping
    @Operation(summary = "Get all active offers (paged)", description = "Returns currently active and valid promotional offers across all stores, newest first.")
    public ResponseEntity<PagedResponse<OfferResponse>> getAllActiveOffers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(toPagedResponse(
                offerService.getPublicActiveOffers(PageRequest.of(page, size))));
    }

    /**
     * GET /api/offers/store/{storeId}?page=0&size=10
     * Returns the active offers for products belonging to a specific store, newest first.
     */
    @GetMapping("/store/{storeId}")
    @Operation(summary = "Get active offers for a store (paged)", description = "Returns active promotional offers for products in the specified store, newest first.")
    public ResponseEntity<PagedResponse<OfferResponse>> getActiveOffersForStore(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(toPagedResponse(
                offerService.getPublicActiveOffersForStore(storeId, PageRequest.of(page, size))));
    }

    private PagedResponse<OfferResponse> toPagedResponse(Page<OfferResponse> result) {
        return new PagedResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }
}
