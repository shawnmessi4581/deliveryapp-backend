package com.deliveryapp.controller.catalog;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.StoreService;
import com.deliveryapp.util.DistanceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(storeService.getAllActiveStores().stream()
                .map(catalogMapper::toStoreResponse)
                .collect(Collectors.toList()));
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
        return ResponseEntity.ok(storeService.getStoresByCategory(categoryId).stream()
                .map(catalogMapper::toStoreResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/subcategory/{subCategoryId}")
    public ResponseEntity<List<StoreResponse>> getStoresBySubCategory(@PathVariable Long subCategoryId) {
        return ResponseEntity.ok(storeService.getStoresBySubCategory(subCategoryId).stream()
                .map(catalogMapper::toStoreResponse)
                .collect(Collectors.toList()));
    }
}