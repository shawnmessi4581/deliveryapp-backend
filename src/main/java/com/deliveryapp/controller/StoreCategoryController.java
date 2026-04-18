package com.deliveryapp.controller;

import com.deliveryapp.dto.catalog.StoreCategoryRequest;
import com.deliveryapp.dto.catalog.StoreCategoryResponse;
import com.deliveryapp.entity.StoreCategory;
import com.deliveryapp.service.StoreCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/store-categories")
@RequiredArgsConstructor
public class StoreCategoryController {

    private final StoreCategoryService storeCategoryService;

    // =================================================================================
    // PUBLIC (User App)
    // =================================================================================

    // Usage: GET /api/store-categories/store/5/active
    @GetMapping("/store/{storeId}/active")
    public ResponseEntity<List<StoreCategoryResponse>> getActiveCategories(@PathVariable Long storeId) {
        List<StoreCategory> categories = storeCategoryService.getActiveCategoriesForStore(storeId);
        return ResponseEntity.ok(categories.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    // =================================================================================
    // ADMIN DASHBOARD
    // =================================================================================

    // Usage: GET /api/store-categories/store/5/all
    @GetMapping("/store/{storeId}/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<StoreCategoryResponse>> getAllCategories(@PathVariable Long storeId) {
        List<StoreCategory> categories = storeCategoryService.getAllCategoriesForStore(storeId);
        return ResponseEntity.ok(categories.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<StoreCategoryResponse> createCategory(@RequestBody StoreCategoryRequest request) {
        StoreCategory saved = storeCategoryService.createStoreCategory(request);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<StoreCategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestBody StoreCategoryRequest request) {
        StoreCategory updated = storeCategoryService.updateStoreCategory(id, request);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        storeCategoryService.deleteStoreCategory(id);
        return ResponseEntity.ok("Store Category deleted successfully");
    }

    // =================================================================================
    // MAPPER
    // =================================================================================

    private StoreCategoryResponse mapToResponse(StoreCategory sc) {
        StoreCategoryResponse dto = new StoreCategoryResponse();
        dto.setStoreCategoryId(sc.getStoreCategoryId());
        dto.setName(sc.getName());
        dto.setIsActive(sc.getIsActive());
        dto.setDisplayOrder(sc.getDisplayOrder());

        if (sc.getStore() != null) {
            dto.setStoreId(sc.getStore().getStoreId());
        }
        return dto;
    }
}