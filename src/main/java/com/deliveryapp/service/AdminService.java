package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.ProductRequest;
import com.deliveryapp.dto.catalog.StoreRequest;
import com.deliveryapp.dto.user.CreateDriverRequest;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.*;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UrlUtil urlUtil; // 2. Inject
     private final PasswordEncoder passwordEncoder;

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
    @Transactional
    public User createDriver(CreateDriverRequest request, MultipartFile image) {
        // 1. Check for duplicates
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new DuplicateResourceException("Phone number already registered");
        }

        // 2. Create User Entity
        User driver = new User();
        driver.setName(request.getName());
        driver.setPhoneNumber(request.getPhoneNumber());
        driver.setEmail(request.getEmail());

        // 3. Security & Role
        driver.setPassword(passwordEncoder.encode(request.getPassword()));
        driver.setUserType(UserType.DRIVER);
        driver.setIsActive(true);

        // 4. Driver Specific Fields
        driver.setVehicleType(request.getVehicleType());
        driver.setVehicleNumber(request.getVehicleNumber());
        driver.setIsAvailable(true); // Ready to take orders
        driver.setRating(5.0);       // Default starting rating
        driver.setTotalDeliveries(0);

        driver.setCreatedAt(LocalDateTime.now());

        // 5. Upload Image (Optional)
        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "profiles");
            driver.setProfileImage(imageUrl);
        }

        return userRepository.save(driver);
    }

    public List<User> getAllDrivers() {
        return userRepository.findByUserType(UserType.DRIVER);
    }
//categories
public List<Category> getAllCategories() {
    return categoryRepository.findAll();

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
    public Category updateCategory(Long id, String name, Boolean isActive, MultipartFile image) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        // Update Name if provided
        if (name != null && !name.trim().isEmpty()) {
            category.setName(name);
        }

        // Update Status if provided
        if (isActive != null) {
            category.setIsActive(isActive);
        }

        // Update Image if provided
        if (image != null && !image.isEmpty()) {
            // Optional: You could delete the old file here using:
             fileStorageService.deleteFile(category.getIcon());

            String imageUrl = fileStorageService.storeFile(image, "categories");
            category.setIcon(imageUrl);
        }

        return categoryRepository.save(category);
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
    public List<SubCategory> getAllSubCategories() {
        return subCategoryRepository.findAll();
    }

    // 2. Update SubCategory (Name, Parent Category, Active Status, Image)
    @Transactional
    public SubCategory updateSubCategory(Long id, String name, Long categoryId, Boolean isActive, MultipartFile image) {
        SubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubCategory not found with id: " + id));

        // Update Name
        if (name != null && !name.trim().isEmpty()) {
            subCategory.setName(name);
        }

        // Update Parent Category
        if (categoryId != null) {
            Category newParent = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));
            subCategory.setCategory(newParent);
        }

        // Update Status (Toggle Active)
        if (isActive != null) {
            subCategory.setIsActive(isActive);
        }

        // Update Image
        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "subcategories");
            subCategory.setIcon(imageUrl);
        }

        return subCategoryRepository.save(subCategory);
    }

    // 3. Delete SubCategory
    public void deleteSubCategory(Long id) {
        if (!subCategoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("SubCategory not found with id: " + id);
        }
        // Note: If products are linked to this subcategory, database might throw an error
        // unless Cascade Delete is configured in SQL.
        subCategoryRepository.deleteById(id);
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
    // 1. Get ALL Stores
    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }

    // 2. Update Store
    @Transactional
    public Store updateStore(Long id, StoreRequest request, Boolean isActive, MultipartFile logo, MultipartFile cover) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + id));

        // Update Basic Fields
        if (request.getName() != null) store.setName(request.getName());
        if (request.getDescription() != null) store.setDescription(request.getDescription());
        if (request.getPhone() != null) store.setPhone(request.getPhone());
        if (request.getAddress() != null) store.setAddress(request.getAddress());
        if (request.getLatitude() != null) store.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) store.setLongitude(request.getLongitude());
        if (request.getDeliveryFeeKM() != null) store.setDeliveryFeeKM(request.getDeliveryFeeKM());
        if (request.getMinimumOrder() != null) store.setMinimumOrder(request.getMinimumOrder());
        if (request.getEstimatedDeliveryTime() != null) store.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime());

        // Update Status
        if (isActive != null) {
            store.setIsActive(isActive);
        }

        // Update Categories
        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            store.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("SubCategory not found"));
            store.setSubCategory(sub);
        }

        // Update Images
        if (logo != null && !logo.isEmpty()) {
            store.setLogo(fileStorageService.storeFile(logo, "stores"));
        }
        if (cover != null && !cover.isEmpty()) {
            store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
        }

        return storeRepository.save(store);
    }

    // ==================== PRODUCT CRUD ====================
// 1. Get ALL Products
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // 2. Update Product
    @Transactional
    public Product updateProduct(Long id, ProductRequest request, MultipartFile image) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // Update Fields
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getBasePrice() != null) product.setBasePrice(request.getBasePrice());
        if (request.getIsAvailable() != null) product.setIsAvailable(request.getIsAvailable());

        // Update Relationships
        if (request.getStoreId() != null) {
            Store store = storeRepository.findById(request.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
            product.setStore(store);
        }
        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            product.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("SubCategory not found"));
            product.setSubCategory(sub);
        }

        // Update Image
        if (image != null && !image.isEmpty()) {
            product.setImage(fileStorageService.storeFile(image, "products"));
        }

        return productRepository.save(product);
    }
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
    // DELETE Variant
    public void deleteProductVariant(Long variantId) {
        if (!variantRepository.existsById(variantId)) {
            throw new ResourceNotFoundException("Variant not found with id: " + variantId);
        }
        variantRepository.deleteById(variantId);
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
        dto.setProfileImage(urlUtil.getFullUrl(user.getProfileImage())); // Ensure User entity has getProfileImage() or getImageUrl()
        dto.setIsActive(user.getIsActive());

        dto.setAddress(user.getAddress());
        dto.setCurrentLocationLat(user.getLatitude());
        dto.setCurrentLocationLng(user.getLongitude());

        // Driver details
        dto.setVehicleType(user.getVehicleType());
        dto.setVehicleNumber(user.getVehicleNumber());
        dto.setIsAvailable(user.getIsAvailable());
        dto.setRating(user.getRating());
        dto.setTotalDeliveries(user.getTotalDeliveries());
        return dto;
    }
}