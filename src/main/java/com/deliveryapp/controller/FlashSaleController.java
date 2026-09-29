package com.deliveryapp.controller;

import com.deliveryapp.dto.flashsale.FlashSaleResponse;
import com.deliveryapp.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public (unauthenticated) read-only endpoints for flash sales.
 *
 * <p>Customers browse active flash sales, see the countdown timer,
 * copy the coupon code, and apply it at checkout.
 * No authentication required.
 */
@RestController
@RequestMapping("/api/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Public Flash Sales", description = "Public endpoints for browsing active flash sales with countdown timers")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    /**
     * GET /api/flash-sales
     * Returns all currently active and live flash sales across all stores,
     * ordered by soonest-ending first (most urgent at the top).
     */
    @GetMapping
    @Operation(
        summary = "Get all live flash sales",
        description = "Returns all active flash sales within their countdown window, sorted by soonest expiry. " +
                      "Each item includes the coupon code and remainingSeconds for the countdown timer."
    )
    public ResponseEntity<List<FlashSaleResponse>> getAllActiveFlashSales() {
        return ResponseEntity.ok(flashSaleService.getPublicActive());
    }

    /**
     * GET /api/flash-sales/store/{storeId}
     * Returns all active flash sales for a specific store.
     */
    @GetMapping("/store/{storeId}")
    @Operation(
        summary = "Get live flash sales for a specific store",
        description = "Returns all active flash sales associated with the given store, sorted by soonest expiry."
    )
    public ResponseEntity<List<FlashSaleResponse>> getActiveFlashSalesForStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(flashSaleService.getPublicActiveForStore(storeId));
    }
}
