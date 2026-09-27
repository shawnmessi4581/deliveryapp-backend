package com.deliveryapp.controller.catalog;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.StoreService;
import com.deliveryapp.util.DistanceUtil;
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
    private final DistanceUtil distanceUtil;

    @GetMapping
    public ResponseEntity<List<StoreResponse>> getAllActiveStores() {
        return ResponseEntity.ok(sortOpenFirst(
                storeService.getAllActiveStores().stream()
                        .map(catalogMapper::toStoreResponse)
                        .collect(Collectors.toList())));
    }

    @GetMapping("/{storeId}")
    public ResponseEntity<StoreResponse> getStoreById(
            @PathVariable Long storeId,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        Store store = storeService.getStoreById(storeId);
        StoreResponse response = catalogMapper.toStoreResponse(store);

        if (userLat != null && userLng != null && store.getLatitude() != null && store.getLongitude() != null) {
            double distance = distanceUtil.calculateDistance(userLat, userLng, store.getLatitude(),
                    store.getLongitude());
            double feePerKm = store.getDeliveryFeeKM() != null ? store.getDeliveryFeeKM() : 0.0;
            response.setPredictedDeliveryFee(Math.ceil(distance * feePerKm));
        } else {
            response.setPredictedDeliveryFee(0.0);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<StoreResponse>> getStoresByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(sortOpenFirst(
                storeService.getStoresByCategory(categoryId).stream()
                        .map(catalogMapper::toStoreResponse)
                        .collect(Collectors.toList())));
    }

    @GetMapping("/subcategory/{subCategoryId}")
    public ResponseEntity<List<StoreResponse>> getStoresBySubCategory(@PathVariable Long subCategoryId) {
        return ResponseEntity.ok(sortOpenFirst(
                storeService.getStoresBySubCategory(subCategoryId).stream()
                        .map(catalogMapper::toStoreResponse)
                        .collect(Collectors.toList())));
    }

    /**
     * 🆓 Public: Get all active stores offering free delivery.
     * GET /api/catalog/stores/free-delivery
     */
    @GetMapping("/free-delivery")
    public ResponseEntity<List<StoreResponse>> getFreeDeliveryStores() {
        return ResponseEntity.ok(sortOpenFirst(
                storeService.getFreeDeliveryStores().stream()
                        .map(catalogMapper::toStoreResponse)
                        .collect(Collectors.toList())));
    }

    /**
     * 🕐 Sorts stores: open first, then closed. Preserves displayOrder within each
     * group.
     */
    private List<StoreResponse> sortOpenFirst(List<StoreResponse> stores) {
        stores.sort(Comparator
                .comparing((StoreResponse s) -> !Boolean.TRUE.equals(s.getIsOpenNow())) // open (true) first
                .thenComparing(s -> s.getDisplayOrder() != null ? s.getDisplayOrder() : Integer.MAX_VALUE));
        return stores;
    }
}