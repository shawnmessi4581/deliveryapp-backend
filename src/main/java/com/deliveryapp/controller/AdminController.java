package com.deliveryapp.controller;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.catalog.*;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.user.CreateDriverRequest;
import com.deliveryapp.dto.user.CreateUserRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.*;
import com.deliveryapp.mapper.catalog.AdminCatalogMapper;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.mapper.order.OrderMapper;
import com.deliveryapp.mapper.user.UserMapper;
import com.deliveryapp.repository.DeliveryInstructionRepository;
import com.deliveryapp.service.AdminService;
import com.deliveryapp.service.OrderService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
// NOTE: We removed the class-level @PreAuthorize so we can control each method
// individually
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;
    private final DeliveryInstructionRepository instructionRepository;

    // Mappers
    private final CatalogMapper catalogMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final AdminCatalogMapper adminCatalogMapper;

    // ==================== USERS (Strict Security) ====================

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')") // Employees can view users
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers().stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')") // ONLY Admins can delete users
    public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok("تم حذف المستخدم بنجاح");
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')") // ONLY Admins can ban/unban users
    public ResponseEntity<String> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Boolean active) {
        adminService.updateUserStatus(userId, active);
        String status = active ? "تم تفعيل المستخدم" : "تم تعطيل المستخدم";
        return ResponseEntity.ok(status);
    }

    @PostMapping("/users/create")
    @PreAuthorize("hasRole('ADMIN')") // ONLY Admins can create other Admins/Employees
    public ResponseEntity<UserResponse> createDashboardUser(@RequestBody CreateUserRequest request) {
        User user = adminService.createDashboardUser(request);
        return ResponseEntity.ok(userMapper.toUserResponse(user));
    }

    // ==================== DRIVERS ====================

    @GetMapping("/drivers")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<UserResponse>> getAllDrivers() {
        return ResponseEntity.ok(adminService.getAllDrivers().stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList()));
    }

    @PatchMapping("/{orderId}/assign/{driverId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')") // Both can assign drivers
    public ResponseEntity<OrderResponse> assignDriver(
            @PathVariable Long orderId,
            @PathVariable Long driverId) {
        Order order = orderService.assignDriver(orderId, driverId);
        return ResponseEntity.ok(orderMapper.toOrderResponse(order));
    }

    @PostMapping(value = "/drivers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')") // Both can register new drivers
    public ResponseEntity<UserResponse> createDriver(
            @ModelAttribute CreateDriverRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        User driver = adminService.createDriver(request, image);
        return ResponseEntity.ok(userMapper.toUserResponse(driver));
    }

    // ==================== CATALOG (Admin + Employee access) ====================

    // --- CATEGORIES ---
    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(adminService.getAllCategories().stream()
                .map(catalogMapper::toCategoryResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CategoryResponse> createCategory(
            @ModelAttribute CategoryRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        Category cat = adminService.createCategory(request, image);
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(cat));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return ResponseEntity.ok("تم حذف الفئة");
    }

    @PutMapping(value = "/categories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @ModelAttribute CategoryRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        Category cat = adminService.updateCategory(id, request, image);
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(cat));
    }

    // --- SUBCATEGORIES ---
    @PostMapping(value = "/subcategories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SubCategoryResponse> createSubCategory(
            @ModelAttribute SubCategoryRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        SubCategory sub = adminService.createSubCategory(request, image);
        return ResponseEntity.ok(catalogMapper.toSubCategoryResponse(sub));
    }

    @GetMapping("/subcategories")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<SubCategoryResponse>> getAllSubCategories() {
        return ResponseEntity.ok(adminService.getAllSubCategories().stream()
                .map(catalogMapper::toSubCategoryResponse)
                .collect(Collectors.toList()));
    }

    @PutMapping(value = "/subcategories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SubCategoryResponse> updateSubCategory(
            @PathVariable Long id,
            @ModelAttribute SubCategoryRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        SubCategory sub = adminService.updateSubCategory(id, request, image);
        return ResponseEntity.ok(catalogMapper.toSubCategoryResponse(sub));
    }

    @DeleteMapping("/subcategories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteSubCategory(@PathVariable Long id) {
        adminService.deleteSubCategory(id);
        return ResponseEntity.ok("تم حذف الفئة الفرعية بنجاح");
    }

    // --- STORES ---
    @GetMapping("/stores")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<StoreResponse>> getAllStores() {
        return ResponseEntity.ok(adminService.getAllStores().stream()
                .map(catalogMapper::toStoreResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/stores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<StoreResponse> createStore(
            @ModelAttribute StoreRequest request,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        Store store = adminService.createStore(request, logo, cover);
        return ResponseEntity.ok(catalogMapper.toStoreResponse(store));
    }

    @PutMapping(value = "/stores/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteStore(@PathVariable Long id) {
        adminService.deleteStore(id);
        return ResponseEntity.ok("تم حذف المتجر");
    }

    // 1. GET ALL PRODUCTS (Admin View)
    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<PagedResponse<AdminProductResponse>> getAllProducts(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());

        // Pass the filters to the service
        Page<Product> productPage = adminService.getAllProducts(storeId, categoryId, subCategoryId, pageable);

        List<AdminProductResponse> content = productPage.getContent().stream()
                .map(adminCatalogMapper::toAdminProductResponse)
                .collect(Collectors.toList());

        PagedResponse<AdminProductResponse> response = new PagedResponse<>(
                content, productPage.getNumber(), productPage.getSize(),
                productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast());

        return ResponseEntity.ok(response);
    }

    // 2. CREATE PRODUCT (Admin View)
    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<AdminProductResponse> createProduct(
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "gallery", required = false) List<MultipartFile> gallery) {

        Product product = adminService.createProduct(request, image, gallery);
        return ResponseEntity.ok(adminCatalogMapper.toAdminProductResponse(product)); // <--- USE ADMIN MAPPER
    }

    // 3. UPDATE PRODUCT (Admin View)
    @PutMapping(value = "/products/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<AdminProductResponse> updateProduct(
            @PathVariable Long id,
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "gallery", required = false) List<MultipartFile> gallery) {

        Product product = adminService.updateProduct(id, request, image, gallery);
        return ResponseEntity.ok(adminCatalogMapper.toAdminProductResponse(product)); // <--- USE ADMIN MAPPER
    }

    @PostMapping("/products/{productId}/variants")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<?> addVariant(
            @PathVariable Long productId,
            @RequestParam("name") String name,
            @RequestParam("price") Double priceAdjustment) {
        return ResponseEntity.ok(adminService.addProductVariant(productId, name, priceAdjustment));
    }

    @DeleteMapping("/variants/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteProductVariant(@PathVariable Long variantId) {
        adminService.deleteProductVariant(variantId);
        return ResponseEntity.ok("تم حذف النوع بنجاح");
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteProduct(@PathVariable Long id) {
        adminService.deleteProduct(id);
        return ResponseEntity.ok("تم حذف المنتج");
    }

    // --- INSTRUCTIONS ---
    @PostMapping("/instructions")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<DeliveryInstruction> createInstruction(@RequestParam String text) {
        DeliveryInstruction instruction = new DeliveryInstruction();
        instruction.setInstruction(text);
        instruction.setIsActive(true);
        return ResponseEntity.ok(instructionRepository.save(instruction));
    }

    @DeleteMapping("/instructions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteInstruction(@PathVariable Long id) {
        instructionRepository.deleteById(id);
        return ResponseEntity.ok("تم حذف التعليمات");
    }

    @GetMapping("/instructions")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<DeliveryInstruction>> getAllInstructions() {
        return ResponseEntity.ok(instructionRepository.findAll());
    }

    // --- COLORS ---
    @GetMapping("/colors")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<ColorResponse>> getAllColors() {
        List<ColorResponse> response = adminService.getAllColors().stream().map(c -> {
            ColorResponse dto = new ColorResponse();
            dto.setColorId(c.getColorId());
            dto.setName(c.getName());
            dto.setHexCode(c.getHexCode());
            return dto;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/colors")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> createColor(@RequestBody ColorRequest request) {
        adminService.createColor(request.getName(), request.getHexCode());
        return ResponseEntity.ok("تمت إضافة اللون");
    }

    @PutMapping("/colors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<ColorResponse> updateColor(
            @PathVariable Long id,
            @RequestBody ColorRequest request) {

        Color updatedColor = adminService.updateColor(id, request.getName(), request.getHexCode());

        // Map to DTO
        ColorResponse dto = new ColorResponse();
        dto.setColorId(updatedColor.getColorId());
        dto.setName(updatedColor.getName());
        dto.setHexCode(updatedColor.getHexCode());

        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/colors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteColor(@PathVariable Long id) {
        adminService.deleteColor(id);
        return ResponseEntity.ok("تم حذف اللون");
    }
}