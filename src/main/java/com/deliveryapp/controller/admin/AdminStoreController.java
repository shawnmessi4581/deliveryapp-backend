package com.deliveryapp.controller.admin;

import com.deliveryapp.dto.catalog.StoreRequest;
import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/stores")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class AdminStoreController {

    private final StoreService storeService;
    private final CatalogMapper catalogMapper;

    @GetMapping
    public ResponseEntity<List<StoreResponse>> getAllStores() {
        return ResponseEntity.ok(storeService.getAllStores().stream()
                .map(catalogMapper::toAdminStoreResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> createStore(
            @ModelAttribute StoreRequest request,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        Store store = storeService.createStore(request, logo, cover);
        return ResponseEntity.ok(catalogMapper.toAdminStoreResponse(store));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> updateStore(
            @PathVariable Long id,
            @ModelAttribute StoreRequest request,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        Store store = storeService.updateStore(id, request, isActive, logo, cover);
        return ResponseEntity.ok(catalogMapper.toAdminStoreResponse(store));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteStore(@PathVariable Long id) {
        storeService.deleteStore(id);
        return ResponseEntity.ok("تم حذف المتجر");
    }

    /**
     * 🆓 Toggle free delivery for a store (Admin only).
     * PATCH /api/admin/stores/{id}/free-delivery?enabled=true|false
     */
    @PatchMapping("/{id}/free-delivery")
    public ResponseEntity<StoreResponse> toggleFreeDelivery(
            @PathVariable Long id,
            @RequestParam Boolean enabled) {
        Store store = storeService.toggleFreeDelivery(id, enabled);
        return ResponseEntity.ok(catalogMapper.toAdminStoreResponse(store));
    }

    /**
     * 💹 Set the price markup customers pay on top of this store's prices (0–100, 0 = none).
     * PATCH /api/admin/stores/{id}/price-markup?percentage=10
     */
    @PatchMapping("/{id}/price-markup")
    public ResponseEntity<StoreResponse> setPriceMarkup(
            @PathVariable Long id,
            @RequestParam Double percentage) {
        Store store = storeService.setPriceMarkup(id, percentage);
        return ResponseEntity.ok(catalogMapper.toAdminStoreResponse(store));
    }
}
