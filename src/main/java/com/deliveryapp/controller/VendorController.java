package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.catalog.ProductRequest;
import com.deliveryapp.dto.catalog.ProductResponse;
import com.deliveryapp.dto.catalog.StoreRequest; // 🟢 Import this
import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.order.VendorOrderResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.mapper.order.OrderMapper;
import com.deliveryapp.service.OrderService;
import com.deliveryapp.service.ProductService;
import com.deliveryapp.service.StoreService;
import com.deliveryapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vendor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')") // 🔒 Only Vendors
public class VendorController {

    private final UserService userService;
    private final OrderService orderService;
    private final ProductService productService;
    private final StoreService storeService;
    private final OrderMapper orderMapper;
    private final CatalogMapper catalogMapper;

    // --- HELPER: Get Logged In Vendor's Store ID ---
    private Long getVendorStoreId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = jwt.getClaim("userId");
        User vendor = userService.getUserById(userId);

        if (vendor.getManagedStore() == null) {
            throw new InvalidDataException("Your account is not linked to a store. Contact Admin.");
        }
        return vendor.getManagedStore().getStoreId();
    }

    // ==========================================
    // 1. ORDERS (Split into Active / History)
    // ==========================================
    // Usage: /api/vendor/orders?activeOnly=true <-- For the main kitchen screen
    // Usage: /api/vendor/orders?activeOnly=false <-- For the history/accounting
    // screen

    @GetMapping("/orders")
    public ResponseEntity<PagedResponse<VendorOrderResponse>> getMyOrders(
            @RequestParam(defaultValue = "true") Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long storeId = getVendorStoreId();
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orderPage = orderService.getVendorOrders(storeId, activeOnly, pageable);

        // 🟢 FIX: Map to VendorOrderResponse, passing the storeId to filter items
        List<VendorOrderResponse> content = orderPage.getContent().stream()
                .map(order -> orderMapper.toVendorOrderResponse(order, storeId))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(
                content, orderPage.getNumber(), orderPage.getSize(),
                orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast()));
    }

    // ==========================================
    // 2. STORE MANAGEMENT
    // ==========================================

    @PatchMapping("/store/busy")
    public ResponseEntity<StoreResponse> toggleStoreBusy(@RequestParam Boolean isBusy) {
        Long storeId = getVendorStoreId();
        Store updatedStore = storeService.toggleStoreBusyStatus(storeId, isBusy);
        return ResponseEntity.ok(catalogMapper.toStoreResponse(updatedStore));
    }

    // 🟢 NEW: Update Vendor's Store Info
    @PutMapping(value = "/store/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> updateMyStore(
            @ModelAttribute StoreRequest request,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {

        Long storeId = getVendorStoreId();

        // Security: Ensure the vendor cannot change which category/subcategory they
        // belong to,
        // or their commission rate. Only Super Admins can do that.
        // We override the request fields to null to protect them.
        request.setCategoryId(null);
        request.setSubCategoryIds(null);
        request.setCommissionPercentage(null);
        request.setDeliveryFeeKM(null); // Optional: Do you want vendors setting their own delivery fee? Usually no.

        // Call the same update method used by Admin
        Store updatedStore = storeService.updateStore(storeId, request, isActive, logo, cover);

        return ResponseEntity.ok(catalogMapper.toStoreResponse(updatedStore));
    }

    // ==========================================
    // 3. PRODUCT MANAGEMENT
    // ==========================================
    @GetMapping("/products")
    public ResponseEntity<PagedResponse<ProductResponse>> getMyProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long storeId = getVendorStoreId();
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage = productService.getProductsByStore(storeId, pageable);

        List<ProductResponse> content = productPage.getContent().stream()
                .map(catalogMapper::toProductResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(
                content, productPage.getNumber(), productPage.getSize(),
                productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast()));
    }

    @PutMapping(value = "/products/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateMyProduct(
            @PathVariable Long productId,
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile mainImage,
            @RequestParam(value = "gallery", required = false) List<MultipartFile> galleryImages) {

        Long vendorStoreId = getVendorStoreId();
        Product existingProduct = productService.getProductById(productId);

        if (!existingProduct.getStore().getStoreId().equals(vendorStoreId)) {
            throw new InvalidDataException("Access Denied: Product belongs to another store.");
        }

        request.setStoreId(vendorStoreId); // Force store ID

        Product updatedProduct = productService.updateProduct(productId, request, mainImage, galleryImages);
        return ResponseEntity.ok(catalogMapper.toProductResponse(updatedProduct));
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createMyProduct(
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile mainImage,
            @RequestParam(value = "gallery", required = false) List<MultipartFile> galleryImages) {

        request.setStoreId(getVendorStoreId()); // Force store ID

        Product newProduct = productService.createProduct(request, mainImage, galleryImages);
        return ResponseEntity.ok(catalogMapper.toProductResponse(newProduct));
    }
}