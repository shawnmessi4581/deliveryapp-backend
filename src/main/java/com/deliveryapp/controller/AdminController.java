package com.deliveryapp.controller;

import com.deliveryapp.dto.catalog.*;
import com.deliveryapp.dto.coupon.CouponRequest;
import com.deliveryapp.dto.order.OrderCustomerResponse;
import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.user.CreateDriverRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.*;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.repository.DeliveryInstructionRepository;
import com.deliveryapp.service.AdminService;
import com.deliveryapp.service.CouponService;
import com.deliveryapp.service.OrderService;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Secures the entire controller
public class AdminController {

    private final AdminService adminService;
    private final CouponService couponService;
    private final OrderService orderService;
    private final DeliveryInstructionRepository instructionRepository;
    private final UrlUtil urlUtil; // 2. Inject

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }
    // DELETE User
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

    // TOGGLE Status (Ban/Activate)
    // Usage: PATCH /api/admin/users/123/status?active=false
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<String> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Boolean active) {

        adminService.updateUserStatus(userId, active);
        String status = active ? "activated" : "deactivated";
        return ResponseEntity.ok("User has been " + status);
    }

    // 1. GET List of Drivers
    @GetMapping("/drivers")
    public ResponseEntity<List<UserResponse>> getAllDrivers() {
        // We reuse the UserResponse DTO to hide passwords/tokens
        List<UserResponse> drivers = adminService.getAllDrivers().stream()
                .map(this::mapToUserResponse) // Define helper below or reuse from AdminController
                .collect(Collectors.toList());
        return ResponseEntity.ok(drivers);
    }

    // 2. ASSIGN DRIVER
    @PatchMapping("/{orderId}/assign/{driverId}")
    public ResponseEntity<OrderResponse> assignDriver(
            @PathVariable Long orderId,
            @PathVariable Long driverId) {

        Order order = orderService.assignDriver(orderId, driverId);
        return ResponseEntity.ok(mapToOrderResponse(order));
    }
    // CREATE DRIVER
    @PostMapping(value = "/drivers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> createDriver(
            @ModelAttribute CreateDriverRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        User driver = adminService.createDriver(request, image);

        // Reuse your existing UserResponse Mapper or create a new one
        return ResponseEntity.ok(mapToUserResponse(driver));
    }



    // ==================== CATEGORIES ====================
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        List<Category> categories = adminService.getAllCategories();

        List<CategoryResponse> response = categories.stream()
                .map(this::mapToCategoryResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Category> createCategory(
            @RequestParam("name") String name,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(adminService.createCategory(name, image));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return ResponseEntity.ok("Category deleted");
    }
    @PutMapping(value = "/categories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Category> updateCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        return ResponseEntity.ok(adminService.updateCategory(id, name, isActive, image));
    }

    // ==================== SUBCATEGORIES ====================

     // 1. CREATE (Returns DTO)
    @PostMapping(value = "/subcategories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubCategoryResponse> createSubCategory(
            @RequestParam("name") String name,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        // 1. Call Service to save
        SubCategory savedSubCategory = adminService.createSubCategory(name, categoryId, image);

        // 2. Map Entity -> DTO
        return ResponseEntity.ok(mapToSubCategoryResponse(savedSubCategory));
    }
    @GetMapping("/subcategories")
    public ResponseEntity<List<SubCategoryResponse>> getAllSubCategories() {
        List<SubCategory> subCategories = adminService.getAllSubCategories();

        // Convert to DTOs
        List<SubCategoryResponse> response = subCategories.stream()
                .map(this::mapToSubCategoryResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }


    // 2. UPDATE (Returns DTO)
    @PutMapping(value = "/subcategories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubCategoryResponse> updateSubCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        // 1. Call Service to update
        SubCategory updatedSubCategory = adminService.updateSubCategory(id, name, categoryId, isActive, image);

        // 2. Map Entity -> DTO
        return ResponseEntity.ok(mapToSubCategoryResponse(updatedSubCategory));
    }

    // DELETE
    @DeleteMapping("/subcategories/{id}")
    public ResponseEntity<String> deleteSubCategory(@PathVariable Long id) {
        adminService.deleteSubCategory(id);
        return ResponseEntity.ok("SubCategory deleted successfully");
    }

    // ==================== STORES ====================

    // 1. GET ALL STORES
    @GetMapping("/stores")
    public ResponseEntity<List<StoreResponse>> getAllStores() {
        List<Store> stores = adminService.getAllStores();
        return ResponseEntity.ok(stores.stream()
                .map(this::mapToStoreResponse) // Use the mapper below
                .collect(Collectors.toList()));
    }

    // 2. CREATE STORE (Updated to return DTO)
    @PostMapping(value = "/stores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> createStore(
            @ModelAttribute StoreRequest request,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {

        Store store = adminService.createStore(request, logo, cover);
        return ResponseEntity.ok(mapToStoreResponse(store));
    }

    // 3. UPDATE STORE
    @PutMapping(value = "/stores/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> updateStore(
            @PathVariable Long id,
            @ModelAttribute StoreRequest request,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {

        Store updatedStore = adminService.updateStore(id, request, isActive, logo, cover);
        return ResponseEntity.ok(mapToStoreResponse(updatedStore));
    }

    @DeleteMapping("/stores/{id}")
    public ResponseEntity<String> deleteStore(@PathVariable Long id) {
        adminService.deleteStore(id);
        return ResponseEntity.ok("Store deleted");
    }

    // ==================== PRODUCTS ====================
    // 1. GET ALL PRODUCTS
    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<Product> products = adminService.getAllProducts();
        return ResponseEntity.ok(products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList()));
    }
    // 2. CREATE PRODUCT (Updated to return DTO)
    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        Product product = adminService.createProduct(request, image);
        return ResponseEntity.ok(mapToProductResponse(product));
    }
    // 3. UPDATE PRODUCT
    @PutMapping(value = "/products/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        Product updatedProduct = adminService.updateProduct(id, request, image);
        return ResponseEntity.ok(mapToProductResponse(updatedProduct));
    }

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<?> addVariant(
            @PathVariable Long productId,
            @RequestParam("name") String name,
            @RequestParam("price") Double priceAdjustment) {
        return ResponseEntity.ok(adminService.addProductVariant(productId, name, priceAdjustment));
    }
    @DeleteMapping("/variants/{variantId}")
    public ResponseEntity<String> deleteProductVariant(@PathVariable Long variantId) {
        adminService.deleteProductVariant(variantId);
        return ResponseEntity.ok("Variant deleted successfully");
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable Long id) {
        adminService.deleteProduct(id);
        return ResponseEntity.ok("Product deleted");
    }

    // --- DELIVERY INSTRUCTIONS ---
    @PostMapping("/instructions")
    public ResponseEntity<DeliveryInstruction> createInstruction(@RequestParam String text) {
        DeliveryInstruction instruction = new DeliveryInstruction();
        instruction.setInstruction(text);
        instruction.setIsActive(true);
        return ResponseEntity.ok(instructionRepository.save(instruction));
    }

    @DeleteMapping("/instructions/{id}")
    public ResponseEntity<String> deleteInstruction(@PathVariable Long id) {
        instructionRepository.deleteById(id);
        return ResponseEntity.ok("Instruction deleted");
    }

    @GetMapping("/instructions")
    public ResponseEntity<List<DeliveryInstruction>> getAllInstructions() {
        return ResponseEntity.ok(instructionRepository.findAll());
    }
    // ==================== MANUAL MAPPERS ====================


    // --- Helper: Map User to UserResponse (If not reusing from another service) ---
    private UserResponse mapToUserResponse(User user) {
        UserResponse dto = new UserResponse();

        // --- Basic User Info ---
        dto.setUserId(user.getUserId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setUserType(user.getUserType());
        dto.setIsActive(user.getIsActive());

        // Convert Image Path to Full URL
        if (urlUtil != null) {
            dto.setProfileImage(urlUtil.getFullUrl(user.getProfileImage()));
        } else {
            dto.setProfileImage(user.getProfileImage());
        }

        // --- Driver Specific Info ---
        // Only populate these if the user is actually a driver
        if (user.getUserType() == UserType.DRIVER) {
            dto.setVehicleType(user.getVehicleType());
            dto.setVehicleNumber(user.getVehicleNumber());
            dto.setIsAvailable(user.getIsAvailable());

            // The specific fields you requested:
            dto.setRating(user.getRating() != null ? user.getRating() : 5.0); // Default to 5.0 if null
            dto.setTotalDeliveries(user.getTotalDeliveries() != null ? user.getTotalDeliveries() : 0);

            // Location (Useful for map view)
            dto.setCurrentLocationLat(user.getCurrentLocationLat());
            dto.setCurrentLocationLng(user.getCurrentLocationLng());
        }

        return dto;
    }
    private CategoryResponse mapToCategoryResponse(Category category) {
        CategoryResponse dto = new CategoryResponse();
        dto.setCategoryId(category.getCategoryId());
        dto.setName(category.getName());
        dto.setImageUrl(urlUtil.getFullUrl(category.getIcon()));
        dto.setActive(category.getIsActive());
        return dto;
    }
    private SubCategoryResponse mapToSubCategoryResponse(SubCategory subCategory) {
        SubCategoryResponse dto = new SubCategoryResponse();
        dto.setSubCategoryId(subCategory.getSubcategoryId());
        dto.setName(subCategory.getName());
        dto.setImageUrl(urlUtil.getFullUrl(subCategory.getIcon()));
        dto.setIsActive(subCategory.getIsActive());

        if (subCategory.getCategory() != null) {
            dto.setParentCategoryId(subCategory.getCategory().getCategoryId());
            dto.setParentCategoryName(subCategory.getCategory().getName());
        }
        return dto;
    }
    // ==================== MAPPER ====================
    private StoreResponse mapToStoreResponse(Store store) {
        StoreResponse dto = new StoreResponse();
        dto.setStoreId(store.getStoreId());
        dto.setName(store.getName());
        dto.setDescription(store.getDescription());
        dto.setPhone(store.getPhone()); // Added
        dto.setLogo(urlUtil.getFullUrl(store.getLogo()));
        dto.setCoverImage(urlUtil.getFullUrl(store.getCoverImage()));
        dto.setAddress(store.getAddress());
        dto.setLatitude(store.getLatitude());
        dto.setLongitude(store.getLongitude());
        dto.setIsActive(store.getIsActive()); // Added
        dto.setRating(store.getRating());
        dto.setTotalOrders(store.getTotalOrders());
        dto.setEstimatedDeliveryTime(store.getEstimatedDeliveryTime());
        dto.setDeliveryFeeKM(store.getDeliveryFeeKM());
        dto.setMinimumOrder(store.getMinimumOrder());

        if (store.getCategory() != null) {
            dto.setCategoryId(store.getCategory().getCategoryId());
            dto.setCategoryName(store.getCategory().getName());
        }
        if (store.getSubCategory() != null) {
            dto.setSubCategoryId(store.getSubCategory().getSubcategoryId());
            dto.setSubCategoryName(store.getSubCategory().getName());
        }
        return dto;
    }
    // ==================== MAPPER ====================
    private ProductResponse mapToProductResponse(Product product) {
        ProductResponse dto = new ProductResponse();
        dto.setProductId(product.getProductId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setBasePrice(product.getBasePrice());
        dto.setImageUrl(urlUtil.getFullUrl(product.getImage()));
        dto.setAvailable(product.getIsAvailable());
        if (product.getStore() != null) {
            // You need to duplicate/access a mapToStoreResponse logic here
            // Or manually map it if you don't want to duplicate the whole method
            StoreResponse storeDto = new StoreResponse();
            storeDto.setStoreId(product.getStore().getStoreId());
            storeDto.setName(product.getStore().getName());
            storeDto.setLogo(urlUtil.getFullUrl(product.getStore().getLogo())); // Ensure UrlUtil is injected
            storeDto.setDeliveryFeeKM(product.getStore().getDeliveryFeeKM());
            // Map other fields as needed...

            dto.setStore(storeDto);
        }
        if (product.getStore() != null) {
            dto.setStoreId(product.getStore().getStoreId());
            dto.setStoreName(product.getStore().getName()); // Added
        }
        if (product.getCategory() != null) {
            dto.setCategoryId(product.getCategory().getCategoryId());
            dto.setCategoryName(product.getCategory().getName()); // Added
        }
        if (product.getSubCategory() != null) {
            dto.setSubCategoryId(product.getSubCategory().getSubcategoryId());
            dto.setSubCategoryName(product.getSubCategory().getName()); // Added
        }

        // Map Variants
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            List<ProductVariantResponse> variantDtos = product.getVariants().stream().map(v -> {
                ProductVariantResponse vDto = new ProductVariantResponse();
                vDto.setVariantId(v.getVariantId());
                vDto.setVariantName(v.getVariantValue());
                vDto.setPriceAdjustment(v.getPriceAdjustment());
                return vDto;
            }).collect(Collectors.toList());
            dto.setVariants(variantDtos);
        } else {
            dto.setVariants(Collections.emptyList());
        }

        return dto;
    }
    // ==================== MAPPER ====================
    private OrderResponse mapToOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());

        // --- Store Info ---
        if (order.getStore() != null) {
            response.setStoreId(order.getStore().getStoreId());
            response.setStoreName(order.getStore().getName());
        }

        // --- Driver Info ---
        if (order.getDriver() != null) {
            response.setDriverId(order.getDriver().getUserId());
            response.setDriverName(order.getDriver().getName());
            response.setDriverPhone(order.getDriver().getPhoneNumber());
        }

        // --- Customer Info ---
        if (order.getUser() != null) {
            User customer = order.getUser();
            // Ensure you have imported OrderCustomerResponse
            OrderCustomerResponse customerDto = new OrderCustomerResponse();
            customerDto.setUserId(customer.getUserId());
            customerDto.setName(customer.getName());
            customerDto.setPhoneNumber(customer.getPhoneNumber());
            customerDto.setProfileAddress(customer.getAddress());

            response.setCustomerDetails(customerDto);
        }

        // --- Location & Status ---
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());

        // --- FINANCIALS ---
        response.setSubtotal(order.getSubtotal());
        response.setDeliveryFee(order.getDeliveryFee());

        // Map Discount & Coupon
        response.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : 0.0);
        response.setCouponId(order.getCouponId());

        response.setTotalAmount(order.getTotalAmount());

        // --- Order Items ---
        if (order.getOrderItems() != null) {
            response.setItems(order.getOrderItems().stream().map(item -> {
                OrderItemResponse r = new OrderItemResponse();
                r.setProductName(item.getProductName());
                r.setVariantDetails(item.getVariantDetails());
                r.setQuantity(item.getQuantity());
                r.setUnitPrice(item.getUnitPrice());
                r.setTotalPrice(item.getTotalPrice());
                r.setNotes(item.getNotes());
                return r;
            }).collect(Collectors.toList()));
        } else {
            response.setItems(Collections.emptyList());
        }

        return response;
    }
}