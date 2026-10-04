package com.deliveryapp.controller.catalog;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.OrderCalculationService;
import com.deliveryapp.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalog/stores")
@RequiredArgsConstructor
public class CatalogStoreController {

    private final StoreService storeService;
    private final CatalogMapper catalogMapper;
    private final OrderCalculationService orderCalculationService;

    /**
     * GET /api/catalog/stores?userLat=&userLng=
     */
    @GetMapping
    public ResponseEntity<List<StoreResponse>> getAllActiveStores(
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        List<Store> stores = storeService.getAllActiveStores();
        List<StoreResponse> responses = toResponses(stores, userLat, userLng);
        return ResponseEntity.ok(sortOpenFirst(responses));
    }

    /**
     * GET /api/catalog/stores/{storeId}?userLat=&userLng=
     */
    @GetMapping("/{storeId}")
    public ResponseEntity<StoreResponse> getStoreById(
            @PathVariable Long storeId,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        Store store = storeService.getStoreById(storeId);
        StoreResponse response = catalogMapper.toStoreResponse(store);
        // Same as the list endpoints: no location → leave the fee empty instead of showing a "free" 0
        if (userLat != null && userLng != null) {
            response.setPredictedDeliveryFee(orderCalculationService.computeFeeForStore(store, userLat, userLng));
        }
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/catalog/stores/category/{categoryId}?userLat=&userLng=
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<StoreResponse>> getStoresByCategory(
            @PathVariable Long categoryId,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        List<Store> stores = storeService.getStoresByCategory(categoryId);
        return ResponseEntity.ok(sortOpenFirst(toResponses(stores, userLat, userLng)));
    }

    /**
     * GET /api/catalog/stores/subcategory/{subCategoryId}?userLat=&userLng=
     */
    @GetMapping("/subcategory/{subCategoryId}")
    public ResponseEntity<List<StoreResponse>> getStoresBySubCategory(
            @PathVariable Long subCategoryId,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        List<Store> stores = storeService.getStoresBySubCategory(subCategoryId);
        return ResponseEntity.ok(sortOpenFirst(toResponses(stores, userLat, userLng)));
    }

    /**
     * 🆓 GET /api/catalog/stores/free-delivery?userLat=&userLng=
     */
    @GetMapping("/free-delivery")
    public ResponseEntity<List<StoreResponse>> getFreeDeliveryStores(
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        List<Store> stores = storeService.getFreeDeliveryStores();
        return ResponseEntity.ok(sortOpenFirst(toResponses(stores, userLat, userLng)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Maps stores to responses and enriches each with the estimated delivery fee
     * via OrderCalculationService — the single source of truth for fee rules.
     */
    private List<StoreResponse> toResponses(List<Store> stores, Double userLat, Double userLng) {
        List<StoreResponse> responses = stores.stream()
                .map(catalogMapper::toStoreResponse)
                .collect(Collectors.toList());
        if (userLat != null && userLng != null) {
            for (int i = 0; i < responses.size(); i++) {
                responses.get(i).setPredictedDeliveryFee(
                        orderCalculationService.computeFeeForStore(stores.get(i), userLat, userLng));
            }
        }
        return responses;
    }

    /**
     * 🕐 Sorts stores: open first, then closed. Preserves displayOrder within each
     * group.
     */
    private List<StoreResponse> sortOpenFirst(List<StoreResponse> stores) {
        stores.sort(Comparator
                .comparing((StoreResponse s) -> !Boolean.TRUE.equals(s.getIsOpenNow()))
                .thenComparing(s -> s.getDisplayOrder() != null ? s.getDisplayOrder() : Integer.MAX_VALUE));
        return stores;
    }
}