package com.deliveryapp.controller;

import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.service.PromotionalOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * GET /api/offers
     * Returns all currently active and valid promotional offers.
     */
    @GetMapping
    @Operation(summary = "Get all active offers", description = "Returns all currently active and valid promotional offers across all stores.")
    public ResponseEntity<List<OfferResponse>> getAllActiveOffers() {
        return ResponseEntity.ok(offerService.getPublicActiveOffers());
    }

    /**
     * GET /api/offers/store/{storeId}
     * Returns all active offers for products belonging to a specific store.
     */
    @GetMapping("/store/{storeId}")
    @Operation(summary = "Get active offers for a store", description = "Returns all active promotional offers for products in the specified store.")
    public ResponseEntity<List<OfferResponse>> getActiveOffersForStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(offerService.getPublicActiveOffersForStore(storeId));
    }
}
