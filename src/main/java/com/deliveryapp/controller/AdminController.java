package com.deliveryapp.controller;

import com.deliveryapp.dto.catalog.*;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.user.CreateDriverRequest;
import com.deliveryapp.dto.user.CreateUserRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.*;
import com.deliveryapp.mapper.catalog.CatalogMapper; // Inject
import com.deliveryapp.mapper.order.OrderMapper; // Inject (Create this one based on previous chat if missing)
import com.deliveryapp.mapper.user.UserMapper; // Inject
import com.deliveryapp.repository.DeliveryInstructionRepository;
import com.deliveryapp.service.AdminService;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;
    private final DeliveryInstructionRepository instructionRepository;

    // Mappers
    private final CatalogMapper catalogMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    // --- USERS ---
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers().stream() // Returns Stream<User>
                .map(userMapper::toUserResponse) // Maps User -> UserResponse
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<String> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Boolean active) {
        adminService.updateUserStatus(userId, active);
        String status = active ? "activated" : "deactivated";
        return ResponseEntity.ok("User has been " + status);
    }

    // CREATE DASHBOARD USER (Admin/Employee)
    @PostMapping("/users/create")
    public ResponseEntity<UserResponse> createDashboardUser(@RequestBody CreateUserRequest request) {
        User user = adminService.createDashboardUser(request);
        return ResponseEntity.ok(userMapper.toUserResponse(user)); // Reuse your existing mapper logic (via UserMapper)
    }

    // --- DRIVERS ---
    @GetMapping("/drivers")
    public ResponseEntity<List<UserResponse>> getAllDrivers() {
        return ResponseEntity.ok(adminService.getAllDrivers().stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList()));
    }

    @PatchMapping("/{orderId}/assign/{driverId}")
    public ResponseEntity<OrderResponse> assignDriver(
            @PathVariable Long orderId,
            @PathVariable Long driverId) {
        Order order = orderService.assignDriver(orderId, driverId);
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }

    @PostMapping(value = "/drivers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> createDriver(
            @ModelAttribute CreateDriverRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        User driver = adminService.createDriver(request, image);
        return ResponseEntity.ok(userMapper.toUserResponse(driver));
    }

    // --- CATEGORIES ---
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(adminService.getAllCategories().stream()
                .map(catalogMapper::toCategoryResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CategoryResponse> createCategory(
            @RequestParam("name") String name,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        Category cat = adminService.createCategory(name, image);
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(cat));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return ResponseEntity.ok("Category deleted");
    }

    @PutMapping(value = "/categories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        Category cat = adminService.updateCategory(id, name, isActive, image);
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(cat));
    }

    // --- SUBCATEGORIES ---
    @PostMapping(value = "/subcategories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubCategoryResponse> createSubCategory(
            @RequestParam("name") String name,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        SubCategory sub = adminService.createSubCategory(name, categoryId, image);
        return ResponseEntity.ok(catalogMapper.toSubCategoryResponse(sub));
    }

    @GetMapping("/subcategories")
    public ResponseEntity<List<SubCategoryResponse>> getAllSubCategories() {
        return ResponseEntity.ok(adminService.getAllSubCategories().stream()
                .map(catalogMapper::toSubCategoryResponse)
                .collect(Collectors.toList()));
    }

    @PutMapping(value = "/subcategories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubCategoryResponse> updateSubCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        SubCategory sub = adminService.updateSubCategory(id, name, categoryId, isActive, image);
        return ResponseEntity.ok(catalogMapper.toSubCategoryResponse(sub));
    }

    @DeleteMapping("/subcategories/{id}")
    public ResponseEntity<String> deleteSubCategory(@PathVariable Long id) {
        adminService.deleteSubCategory(id);
        return ResponseEntity.ok("SubCategory deleted successfully");
    }

    // --- STORES ---
    @GetMapping("/stores")
    public ResponseEntity<List<StoreResponse>> getAllStores() {
        return ResponseEntity.ok(adminService.getAllStores().stream()
                .map(catalogMapper::toStoreResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/stores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> createStore(
            @ModelAttribute StoreRequest request,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        Store store = adminService.createStore(request, logo, cover);
        return ResponseEntity.ok(catalogMapper.toStoreResponse(store));
    }

    @PutMapping(value = "/stores/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> updateStore(
            @PathVariable Long id,
            @ModelAttribute StoreRequest request,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        Store store = adminService.updateStore(id, request, isActive, logo, cover);
        return ResponseEntity.ok(catalogMapper.toStoreResponse(store));
    }

    @DeleteMapping("/stores/{id}")
    public ResponseEntity<String> deleteStore(@PathVariable Long id) {
        adminService.deleteStore(id);
        return ResponseEntity.ok("Store deleted");
    }

    // --- PRODUCTS ---
    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(adminService.getAllProducts().stream()
                .map(catalogMapper::toProductResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            // NEW: Accept List of Files
            @RequestParam(value = "gallery", required = false) List<MultipartFile> gallery) {

        Product product = adminService.createProduct(request, image, gallery);
        return ResponseEntity.ok(catalogMapper.toProductResponse(product));
    }

    @PutMapping(value = "/products/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "gallery", required = false) List<MultipartFile> gallery) {
        Product product = adminService.updateProduct(id, request, image, gallery);
        return ResponseEntity.ok(catalogMapper.toProductResponse(product));
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

    // --- INSTRUCTIONS ---
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
}