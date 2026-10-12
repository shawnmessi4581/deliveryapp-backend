package com.deliveryapp.controller.catalog;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.OrderCalculationService;
import com.deliveryapp.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    // PAGED ROUTES — same stores and order as the routes above, one page at a time.
    // Separate paths so app versions still calling the list routes keep working.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/catalog/stores/paged?page=&size=&userLat=&userLng=
     */
    @GetMapping("/paged")
    public ResponseEntity<PagedResponse<StoreResponse>> getAllActiveStoresPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        return ResponseEntity.ok(toPagedResponse(storeService.getAllActiveStores(), page, size, userLat, userLng));
    }

    /**
     * GET /api/catalog/stores/paged/category/{categoryId}?page=&size=&userLat=&userLng=
     */
    @GetMapping("/paged/category/{categoryId}")
    public ResponseEntity<PagedResponse<StoreResponse>> getStoresByCategoryPaged(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        return ResponseEntity.ok(toPagedResponse(
                storeService.getStoresByCategory(categoryId), page, size, userLat, userLng));
    }

    /**
     * GET /api/catalog/stores/paged/subcategory/{subCategoryId}?page=&size=&userLat=&userLng=
     */
    @GetMapping("/paged/subcategory/{subCategoryId}")
    public ResponseEntity<PagedResponse<StoreResponse>> getStoresBySubCategoryPaged(
            @PathVariable Long subCategoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        return ResponseEntity.ok(toPagedResponse(
                storeService.getStoresBySubCategory(subCategoryId), page, size, userLat, userLng));
    }

    /**
     * 🆓 GET /api/catalog/stores/paged/free-delivery?page=&size=&userLat=&userLng=
     */
    @GetMapping("/paged/free-delivery")
    public ResponseEntity<PagedResponse<StoreResponse>> getFreeDeliveryStoresPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        return ResponseEntity.ok(toPagedResponse(
                storeService.getFreeDeliveryStores(), page, size, userLat, userLng));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Open-first depends on the current time, so the order can't come from the query: sort the
     * stores here, then map and price only the requested page.
     */
    private PagedResponse<StoreResponse> toPagedResponse(List<Store> stores, int page, int size,
            Double userLat, Double userLng) {
        List<Store> sorted = new ArrayList<>(stores);
        sorted.sort(Comparator
                .comparing((Store s) -> !catalogMapper.isStoreOpen(s))
                .thenComparing(s -> s.getDisplayOrder() != null ? s.getDisplayOrder() : Integer.MAX_VALUE)
                .thenComparing(Store::getStoreId)); // stable across pages when displayOrder ties

        Pageable pageable = PageRequest.of(page, size);
        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = Math.min(from + size, sorted.size());
        Page<StoreResponse> result = new PageImpl<>(
                toResponses(sorted.subList(from, to), userLat, userLng), pageable, sorted.size());

        return new PagedResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

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