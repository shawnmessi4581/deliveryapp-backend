package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.ProductRequest;
import com.deliveryapp.dto.catalog.StoreRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.*;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    // ==================== CATEGORY CRUD ====================

    // ... imports
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(java.util.stream.Collectors.toList());
    }
    // 1. Permanently Delete User
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        userRepository.deleteById(userId);
    }

    // 2. Toggle Active Status (Ban/Unban)
    @Transactional
    public void updateUserStatus(Long userId, Boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setIsActive(isActive);
        userRepository.save(user);
    }

    public Category createCategory(String name, MultipartFile image) {
        System.out.println("Attempting to create category: " + name);

        try {
            Category category = new Category();
            category.setName(name);
            category.setIsActive(true);
            category.setDisplayOrder(0);

            // Debug: Check if image is received
            if (image != null && !image.isEmpty()) {
                System.out.println("Image received. Name: " + image.getOriginalFilename());

                // This is likely where it fails (File System)
                String imageUrl = fileStorageService.storeFile(image, "categories");
                System.out.println("Image saved at: " + imageUrl);

                category.setIcon(imageUrl);
            } else {
                System.out.println("No image provided. If DB column is NOT NULL, this will crash.");
            }

            // This is the other place it might fail (Database)
            return categoryRepository.save(category);

        } catch (Exception e) {
            // THIS WILL PRINT THE ERROR TO YOUR CONSOLE
            System.err.println("CRASHED IN CREATE CATEGORY:");
            e.printStackTrace();
            throw new RuntimeException("Error creating category: " + e.getMessage());
        }
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        // Optional: Delete image file here using fileStorageService.deleteFile(category.getImageUrl());
        categoryRepository.delete(category);
    }

    // ==================== SUBCATEGORY CRUD ====================

    public SubCategory createSubCategory(String name, Long categoryId, MultipartFile image) {
        Category parent = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent Category not found"));

        SubCategory sub = new SubCategory();
        sub.setName(name);
        sub.setCategory(parent);
        sub.setIsActive(true);

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "subcategories");
            sub.setIcon(imageUrl);
        }

        return subCategoryRepository.save(sub);
    }

    // ==================== STORE CRUD ====================

    @Transactional
    public Store createStore(StoreRequest request, MultipartFile logo, MultipartFile cover) {
        Store store = new Store();
        store.setName(request.getName());
        store.setDescription(request.getDescription());
        store.setPhone(request.getPhone());
        store.setAddress(request.getAddress());
        store.setLatitude(request.getLatitude());
        store.setLongitude(request.getLongitude());
        store.setDeliveryFeeKM(request.getDeliveryFeeKM());
        store.setMinimumOrder(request.getMinimumOrder());
        store.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime());
        store.setIsActive(true);
        store.setCreatedAt(LocalDateTime.now());
        store.setRating(5.0); // Default start rating
        store.setTotalOrders(0);

        // Relationships
        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId()).orElseThrow();
            store.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId()).orElseThrow();
            store.setSubCategory(sub);
        }

        // Files
        if (logo != null && !logo.isEmpty()) {
            store.setLogo(fileStorageService.storeFile(logo, "stores"));
        }
        if (cover != null && !cover.isEmpty()) {
            store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
        }

        return storeRepository.save(store);
    }

    public void deleteStore(Long id) {
        storeRepository.deleteById(id);
    }

    // ==================== PRODUCT CRUD ====================

    @Transactional
    public Product createProduct(ProductRequest request, MultipartFile image) {
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setIsAvailable(true);
        product.setStore(store);

        // Optional Relationships
        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId()).orElseThrow();
            product.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId()).orElseThrow();
            product.setSubCategory(sub);
        }

        // File
        if (image != null && !image.isEmpty()) {
            product.setImage(fileStorageService.storeFile(image, "products"));
        }

        return productRepository.save(product);
    }

    public ProductVariant addProductVariant(Long productId, String name, Double priceAdjustment) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setVariantValue(name);
        variant.setPriceAdjustment(priceAdjustment);
        variant.setIsAvailable(true);
        return variantRepository.save(variant);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
    private UserResponse mapToUserResponse(User user) {
       UserResponse dto = new UserResponse();
        dto.setUserId(user.getUserId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setUserType(user.getUserType());
        dto.setProfileImage(user.getProfileImage()); // Ensure User entity has getProfileImage() or getImageUrl()
        dto.setIsActive(user.getIsActive());

        dto.setAddress(user.getAddress());
        dto.setLatitude(user.getLatitude());
        dto.setLongitude(user.getLongitude());

        // Driver details
        dto.setVehicleType(user.getVehicleType());
        dto.setVehicleNumber(user.getVehicleNumber());
        dto.setIsAvailable(user.getIsAvailable());
        dto.setRating(user.getRating());
        dto.setTotalDeliveries(user.getTotalDeliveries());

        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLogin(user.getLastLogin());

        return dto;
    }
}