// package com.deliveryapp.controller;

// import com.deliveryapp.dto.PagedResponse;
// import com.deliveryapp.dto.catalog.*;
// import com.deliveryapp.entity.*;
// import com.deliveryapp.mapper.catalog.CatalogMapper; // Import the Mapper
// import com.deliveryapp.service.CatalogService;
// import com.deliveryapp.util.DistanceUtil;
// import lombok.RequiredArgsConstructor;

// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.PageRequest;
// import org.springframework.data.domain.Pageable;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;
// import org.springframework.data.domain.Sort; // Add this import

// import java.util.List;
// import java.util.stream.Collectors;

// @RestController
// @RequestMapping("/api/catalog")
// @RequiredArgsConstructor
// public class CatalogController {

// private final CatalogService catalogService;
// private final DistanceUtil distanceUtil;
// private final CatalogMapper catalogMapper; // Inject Mapper

// // ==================== CATEGORIES ====================

// @GetMapping("/categories")
// public ResponseEntity<List<CategoryResponse>> getAllCategories() {
// List<Category> categories = catalogService.getAllActiveCategories();
// List<CategoryResponse> response = categories.stream()
// .map(catalogMapper::toCategoryResponse) // Use Mapper
// .collect(Collectors.toList());
// return ResponseEntity.ok(response);
// }

// @GetMapping("/categories/{categoryId}")
// public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long
// categoryId) {
// Category category = catalogService.getCategoryById(categoryId);
// return ResponseEntity.ok(catalogMapper.toCategoryResponse(category)); // Use
// Mapper
// }

// // ==================== SUBCATEGORIES ====================

// @GetMapping("/categories/{categoryId}/subcategories")
// public ResponseEntity<List<SubCategoryResponse>>
// getSubCategories(@PathVariable Long categoryId) {
// List<SubCategory> subCategories =
// catalogService.getSubCategoriesByCategoryId(categoryId);
// List<SubCategoryResponse> response = subCategories.stream()
// .map(catalogMapper::toSubCategoryResponse) // Use Mapper
// .collect(Collectors.toList());
// return ResponseEntity.ok(response);
// }

// // ==================== STORES ====================

// @GetMapping("/stores")
// public ResponseEntity<List<StoreResponse>> getAllActiveStores() {
// List<Store> stores = catalogService.getAllActiveStores();
// List<StoreResponse> response = stores.stream()
// .map(catalogMapper::toStoreResponse) // Use Mapper
// .collect(Collectors.toList());
// return ResponseEntity.ok(response);
// }

// // UPDATED: Get Store By ID with User Location for Fee Prediction
// @GetMapping("/stores/{storeId}")
// public ResponseEntity<StoreResponse> getStoreById(
// @PathVariable Long storeId,
// @RequestParam(required = false) Double userLat,
// @RequestParam(required = false) Double userLng) {
// Store store = catalogService.getStoreById(storeId);

// // Map using CatalogMapper
// StoreResponse response = catalogMapper.toStoreResponse(store);

// // Calculate Fee if user location is provided
// if (userLat != null && userLng != null && store.getLatitude() != null &&
// store.getLongitude() != null) {
// double distance = distanceUtil.calculateDistance(userLat, userLng,
// store.getLatitude(),
// store.getLongitude());

// // Fee = Distance (km) * Store Fee Per KM
// double feePerKm = store.getDeliveryFeeKM() != null ? store.getDeliveryFeeKM()
// : 0.0;
// double predictedFee = distance * feePerKm;

// // Optional: Round to 2 decimal places
// predictedFee = Math.round(predictedFee * 100.0) / 100.0;

// response.setPredictedDeliveryFee(predictedFee);
// } else {
// response.setPredictedDeliveryFee(0.0);
// }

// return ResponseEntity.ok(response);
// }

// @GetMapping("/stores/category/{categoryId}")
// public ResponseEntity<List<StoreResponse>> getStoresByCategory(@PathVariable
// Long categoryId) {
// List<Store> stores = catalogService.getStoresByCategory(categoryId);
// return ResponseEntity.ok(stores.stream()
// .map(catalogMapper::toStoreResponse) // Use Mapper
// .collect(Collectors.toList()));
// }

// @GetMapping("/stores/subcategory/{subCategoryId}")
// public ResponseEntity<List<StoreResponse>>
// getStoresBySubCategory(@PathVariable Long subCategoryId) {
// List<Store> stores = catalogService.getStoresBySubCategory(subCategoryId);
// return ResponseEntity.ok(stores.stream()
// .map(catalogMapper::toStoreResponse) // Use Mapper
// .collect(Collectors.toList()));
// }

// // ==================== PRODUCTS ====================

// // GET All Products (Random Order)
// @GetMapping("/products/all")
// public ResponseEntity<PagedResponse<ProductResponse>> getAllProductsRandom(
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size,
// Sort.by("displayOrder").ascending());
// Page<Product> productPage = catalogService.getAllProductsRandomly(pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// @GetMapping("/products/{productId}")
// public ResponseEntity<ProductResponse> getProductById(@PathVariable Long
// productId) {
// Product product = catalogService.getProductById(productId);
// return ResponseEntity.ok(catalogMapper.toProductResponse(product));
// }

// @GetMapping("/products/store/{storeId}")
// public ResponseEntity<PagedResponse<ProductResponse>> getProductsByStore(
// @PathVariable Long storeId,
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size,
// Sort.by("displayOrder").ascending());
// Page<Product> productPage = catalogService.getProductsByStore(storeId,
// pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// // GET Products by Category (All stores)
// @GetMapping("/products/category/{categoryId}")
// public ResponseEntity<PagedResponse<ProductResponse>> getProductsByCategory(
// @PathVariable Long categoryId,
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size,
// Sort.by("displayOrder").ascending());
// Page<Product> productPage = catalogService.getProductsByCategory(categoryId,
// pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// // GET Products by SubCategory (All stores)
// @GetMapping("/products/subcategory/{subCategoryId}")
// public ResponseEntity<PagedResponse<ProductResponse>>
// getProductsBySubCategory(
// @PathVariable Long subCategoryId,
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size,
// Sort.by("displayOrder").ascending());
// Page<Product> productPage =
// catalogService.getProductsBySubCategory(subCategoryId, pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// // GET /api/catalog/products/search?q=burger&categoryId=0
// @GetMapping("/products/search")
// public ResponseEntity<PagedResponse<ProductResponse>> searchProducts(
// @RequestParam("q") String keyword,
// @RequestParam(value = "categoryId", defaultValue = "0") Long categoryId,
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size);
// Page<Product> productPage = catalogService.searchProducts(keyword,
// categoryId, pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// @GetMapping("/products/store/search")
// public ResponseEntity<PagedResponse<ProductResponse>> searchProductsInStore(
// @RequestParam("q") String keyword,
// @RequestParam("storeId") long storeId,
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size);
// Page<Product> productPage = catalogService.searchProductsInStore(keyword,
// storeId, pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// @GetMapping("/products/store/{storeId}/category/{categoryId}")
// public ResponseEntity<PagedResponse<ProductResponse>>
// getProductsByStoreAndCategory(
// @PathVariable Long storeId,
// @PathVariable Long categoryId,
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size,
// Sort.by("displayOrder").ascending());
// Page<Product> productPage =
// catalogService.getProductsByStoreAndCategory(storeId, categoryId, pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// @GetMapping("/products/store/{storeId}/subcategory/{subCategoryId}")
// public ResponseEntity<PagedResponse<ProductResponse>>
// getProductsByStoreAndSubCategory(
// @PathVariable Long storeId,
// @PathVariable Long subCategoryId,
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size,
// Sort.by("displayOrder").ascending());
// Page<Product> productPage =
// catalogService.getProductsByStoreAndSubCategory(storeId, subCategoryId,
// pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// // Usage: /api/catalog/products/price?max=50
// @GetMapping("/products/price")
// public ResponseEntity<PagedResponse<ProductResponse>> getProductsByPrice(
// @RequestParam Double max,
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size,
// Sort.by("displayOrder").ascending());
// Page<Product> productPage = catalogService.getProductsUnderPrice(max,
// pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// // NEW ROUTE: /api/catalog/products/new
// @GetMapping("/products/new")
// public ResponseEntity<PagedResponse<ProductResponse>> getNewestProducts(
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size);
// Page<Product> productPage = catalogService.getNewestProducts(pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// // GET Trending Products
// @GetMapping("/products/trending")
// public ResponseEntity<PagedResponse<ProductResponse>> getTrendingProducts(
// @RequestParam(defaultValue = "0") int page,
// @RequestParam(defaultValue = "10") int size) {
// Pageable pageable = PageRequest.of(page, size);
// Page<Product> productPage = catalogService.getTrendingProducts(pageable);
// return ResponseEntity.ok(createPagedResponse(productPage));
// }

// // --- HELPER METHOD TO CREATE PAGED RESPONSE ---
// private PagedResponse<ProductResponse> createPagedResponse(Page<Product>
// productPage) {
// List<ProductResponse> content = productPage.getContent().stream()
// .map(catalogMapper::toProductResponse)
// .collect(Collectors.toList());

// return new PagedResponse<>(
// content,
// productPage.getNumber(),
// productPage.getSize(),
// productPage.getTotalElements(),
// productPage.getTotalPages(),
// productPage.isLast());
// }
// }