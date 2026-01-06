package com.deliveryapp.controller;

import com.deliveryapp.dto.catalog.*;
import com.deliveryapp.entity.*;
import com.deliveryapp.mapper.catalog.CatalogMapper; // Import the Mapper
import com.deliveryapp.service.CatalogService;
import com.deliveryapp.util.DistanceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final DistanceUtil distanceUtil;
    private final CatalogMapper catalogMapper; // Inject Mapper

    // ==================== CATEGORIES ====================

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        List<Category> categories = catalogService.getAllActiveCategories();
        List<CategoryResponse> response = categories.stream()
                .map(catalogMapper::toCategoryResponse) // Use Mapper
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long categoryId) {
        Category category = catalogService.getCategoryById(categoryId);
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(category)); // Use Mapper
    }

    // ==================== SUBCATEGORIES ====================

    @GetMapping("/categories/{categoryId}/subcategories")
    public ResponseEntity<List<SubCategoryResponse>> getSubCategories(@PathVariable Long categoryId) {
        List<SubCategory> subCategories = catalogService.getSubCategoriesByCategoryId(categoryId);
        List<SubCategoryResponse> response = subCategories.stream()
                .map(catalogMapper::toSubCategoryResponse) // Use Mapper
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // ==================== STORES ====================

    @GetMapping("/stores")
    public ResponseEntity<List<StoreResponse>> getAllActiveStores() {
        List<Store> stores = catalogService.getAllActiveStores();
        List<StoreResponse> response = stores.stream()
                .map(catalogMapper::toStoreResponse) // Use Mapper
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // UPDATED: Get Store By ID with User Location for Fee Prediction
    @GetMapping("/stores/{storeId}")
    public ResponseEntity<StoreResponse> getStoreById(
            @PathVariable Long storeId,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng
    ) {
        Store store = catalogService.getStoreById(storeId);

        // Map using CatalogMapper
        StoreResponse response = catalogMapper.toStoreResponse(store);

        // Calculate Fee if user location is provided
        if (userLat != null && userLng != null && store.getLatitude() != null && store.getLongitude() != null) {
            double distance = distanceUtil.calculateDistance(userLat, userLng, store.getLatitude(), store.getLongitude());

            // Fee = Distance (km) * Store Fee Per KM
            double feePerKm = store.getDeliveryFeeKM() != null ? store.getDeliveryFeeKM() : 0.0;
            double predictedFee = distance * feePerKm;

            // Optional: Round to 2 decimal places
            predictedFee = Math.round(predictedFee * 100.0) / 100.0;

            response.setPredictedDeliveryFee(predictedFee);
        } else {
            response.setPredictedDeliveryFee(0.0);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stores/category/{categoryId}")
    public ResponseEntity<List<StoreResponse>> getStoresByCategory(@PathVariable Long categoryId) {
        List<Store> stores = catalogService.getStoresByCategory(categoryId);
        return ResponseEntity.ok(stores.stream()
                .map(catalogMapper::toStoreResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    @GetMapping("/stores/subcategory/{subCategoryId}")
    public ResponseEntity<List<StoreResponse>> getStoresBySubCategory(@PathVariable Long subCategoryId) {
        List<Store> stores = catalogService.getStoresBySubCategory(subCategoryId);
        return ResponseEntity.ok(stores.stream()
                .map(catalogMapper::toStoreResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    // ==================== PRODUCTS ====================

    // GET All Products (Random Order)
    @GetMapping("/products/all")
    public ResponseEntity<List<ProductResponse>> getAllProductsRandom() {
        List<Product> products = catalogService.getAllProductsRandomly();
        return ResponseEntity.ok(products.stream()
                .map(catalogMapper::toProductResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long productId) {
        Product product = catalogService.getProductById(productId);
        return ResponseEntity.ok(catalogMapper.toProductResponse(product)); // Use Mapper
    }

    @GetMapping("/products/store/{storeId}")
    public ResponseEntity<List<ProductResponse>> getProductsByStore(@PathVariable Long storeId) {
        List<Product> products = catalogService.getProductsByStore(storeId);
        return ResponseEntity.ok(products.stream()
                .map(catalogMapper::toProductResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    // GET Products by Category (All stores)
    @GetMapping("/products/category/{categoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId) {
        List<Product> products = catalogService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(products.stream()
                .map(catalogMapper::toProductResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    // GET Products by SubCategory (All stores)
    @GetMapping("/products/subcategory/{subCategoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsBySubCategory(@PathVariable Long subCategoryId) {
        List<Product> products = catalogService.getProductsBySubCategory(subCategoryId);
        return ResponseEntity.ok(products.stream()
                .map(catalogMapper::toProductResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    @GetMapping("/products/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(
            @RequestParam("q") String keyword,
            @RequestParam("categoryId") long categoryId) {
        List<Product> products = catalogService.searchProducts(keyword, categoryId);
        return ResponseEntity.ok(products.stream()
                .map(catalogMapper::toProductResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    @GetMapping("/products/store/search")
    public ResponseEntity<List<ProductResponse>> searchProductsInStore(
            @RequestParam("q") String keyword,
            @RequestParam("storeId") long storeId) {
        List<Product> products = catalogService.searchProductsInStore(keyword, storeId);
        return ResponseEntity.ok(products.stream()
                .map(catalogMapper::toProductResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    @GetMapping("/products/store/{storeId}/category/{categoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsByStoreAndCategory(
            @PathVariable Long storeId,
            @PathVariable Long categoryId) {
        List<Product> products = catalogService.getProductsByStoreAndCategory(storeId, categoryId);
        return ResponseEntity.ok(products.stream()
                .map(catalogMapper::toProductResponse) // Use Mapper
                .collect(Collectors.toList()));
    }

    @GetMapping("/products/store/{storeId}/subcategory/{subCategoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsByStoreAndSubCategory(
            @PathVariable Long storeId,
            @PathVariable Long subCategoryId) {
        List<Product> products = catalogService.getProductsByStoreAndSubCategory(storeId, subCategoryId);
        return ResponseEntity.ok(products.stream()
                .map(catalogMapper::toProductResponse) // Use Mapper
                .collect(Collectors.toList()));
    }
}