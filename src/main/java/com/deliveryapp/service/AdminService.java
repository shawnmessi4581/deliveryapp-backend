package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.ProductRequest;
import com.deliveryapp.dto.catalog.StoreRequest;
import com.deliveryapp.dto.user.CreateDriverRequest;
import com.deliveryapp.dto.user.CreateUserRequest;
import com.deliveryapp.entity.*;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final PasswordEncoder passwordEncoder;
    private final ColorRepository colorRepository;

    // ==================== USER MANAGEMENT ====================

    // 1. Get All Users (Returns Entities)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // 2. Permanently Delete User
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        userRepository.deleteById(userId);
    }

    // 3. Toggle Active Status
    @Transactional
    public void updateUserStatus(Long userId, Boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setIsActive(isActive);
        userRepository.save(user);
    }

    // 4. Create Driver
    @Transactional
    public User createDriver(CreateDriverRequest request, MultipartFile image) {
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new DuplicateResourceException("Phone number already registered");
        }

        User driver = new User();
        driver.setName(request.getName());
        driver.setPhoneNumber(request.getPhoneNumber());
        driver.setEmail(request.getEmail());
        driver.setPassword(passwordEncoder.encode(request.getPassword()));
        driver.setUserType(UserType.DRIVER);
        driver.setIsActive(true);

        // Driver Specifics
        driver.setVehicleType(request.getVehicleType());
        driver.setVehicleNumber(request.getVehicleNumber());
        driver.setIsAvailable(true);
        driver.setRating(5.0);
        driver.setTotalDeliveries(0);
        driver.setCreatedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "profiles");
            driver.setProfileImage(imageUrl);
        }

        return userRepository.save(driver);
    }

    // 5. Get All Drivers
    public List<User> getAllDrivers() {
        return userRepository.findByUserType(UserType.DRIVER);
    }

    // ... imports ...

    @Transactional
    public User createDashboardUser(CreateUserRequest request) {
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new DuplicateResourceException("Phone number already exists");
        }

        User user = new User();
        user.setName(request.getName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Ensure role is valid (Only allow creating ADMIN or EMPLOYEE here)
        if (request.getRole() == UserType.ADMIN || request.getRole() == UserType.EMPLOYEE) {
            user.setUserType(request.getRole());
        } else {
            throw new InvalidDataException("Invalid role. Use ADMIN or EMPLOYEE.");
        }

        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
    // ==================== CATEGORY CRUD ====================

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category createCategory(String name, MultipartFile image) {
        Category category = new Category();
        category.setName(name);
        category.setIsActive(true);
        category.setDisplayOrder(0);

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "categories");
            category.setIcon(imageUrl);
        }

        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        categoryRepository.delete(category);
    }

    public Category updateCategory(Long id, String name, Boolean isActive, MultipartFile image) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        if (name != null && !name.trim().isEmpty()) {
            category.setName(name);
        }
        if (isActive != null) {
            category.setIsActive(isActive);
        }
        if (image != null && !image.isEmpty()) {
            // Optional: fileStorageService.deleteFile(category.getIcon());
            String imageUrl = fileStorageService.storeFile(image, "categories");
            category.setIcon(imageUrl);
        }

        return categoryRepository.save(category);
    }

    // ==================== SUBCATEGORY CRUD ====================

    public List<SubCategory> getAllSubCategories() {
        return subCategoryRepository.findAll();
    }

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

    @Transactional
    public SubCategory updateSubCategory(Long id, String name, Long categoryId, Boolean isActive, MultipartFile image) {
        SubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubCategory not found with id: " + id));

        if (name != null && !name.trim().isEmpty()) {
            subCategory.setName(name);
        }
        if (categoryId != null) {
            Category newParent = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));
            subCategory.setCategory(newParent);
        }
        if (isActive != null) {
            subCategory.setIsActive(isActive);
        }
        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "subcategories");
            subCategory.setIcon(imageUrl);
        }

        return subCategoryRepository.save(subCategory);
    }

    public void deleteSubCategory(Long id) {
        if (!subCategoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("SubCategory not found with id: " + id);
        }
        subCategoryRepository.deleteById(id);
    }

    // ==================== STORE CRUD ====================

    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }

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
        store.setRating(5.0);
        store.setTotalOrders(0);

        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId()).orElseThrow();
            store.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId()).orElseThrow();
            store.setSubCategory(sub);
        }

        if (logo != null && !logo.isEmpty()) {
            store.setLogo(fileStorageService.storeFile(logo, "stores"));
        }
        if (cover != null && !cover.isEmpty()) {
            store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
        }
        // inside createStore:
        store.setOpeningTime(request.getOpeningTime());
        store.setClosingTime(request.getClosingTime());

        return storeRepository.save(store);
    }

    @Transactional
    public Store updateStore(Long id, StoreRequest request, Boolean isActive, MultipartFile logo, MultipartFile cover) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + id));

        if (request.getName() != null)
            store.setName(request.getName());
        if (request.getDescription() != null)
            store.setDescription(request.getDescription());
        if (request.getPhone() != null)
            store.setPhone(request.getPhone());
        if (request.getAddress() != null)
            store.setAddress(request.getAddress());
        if (request.getLatitude() != null)
            store.setLatitude(request.getLatitude());
        if (request.getLongitude() != null)
            store.setLongitude(request.getLongitude());
        if (request.getDeliveryFeeKM() != null)
            store.setDeliveryFeeKM(request.getDeliveryFeeKM());
        if (request.getMinimumOrder() != null)
            store.setMinimumOrder(request.getMinimumOrder());
        if (request.getEstimatedDeliveryTime() != null)
            store.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime());

        if (isActive != null)
            store.setIsActive(isActive);

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

        if (logo != null && !logo.isEmpty()) {
            store.setLogo(fileStorageService.storeFile(logo, "stores"));
        }
        if (cover != null && !cover.isEmpty()) {
            store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
        }
        // inside updateStore:
        if (request.getOpeningTime() != null)
            store.setOpeningTime(request.getOpeningTime());
        if (request.getClosingTime() != null)
            store.setClosingTime(request.getClosingTime());
        if (request.getIsBusy() != null)
            store.setIsBusy(request.getIsBusy());

        return storeRepository.save(store);
    }

    public void deleteStore(Long id) {
        storeRepository.deleteById(id);
    }

    // ==================== PRODUCT CRUD ====================

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public Product createProduct(ProductRequest request, MultipartFile mainImage, List<MultipartFile> galleryImages) {
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setIsAvailable(true);
        product.setStore(store);

        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId()).orElseThrow();
            product.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId()).orElseThrow();
            product.setSubCategory(sub);
        }

        // 2. Main Image
        if (mainImage != null && !mainImage.isEmpty()) {
            product.setImage(fileStorageService.storeFile(mainImage, "products"));
        }
        if (request.getColorIds() != null && !request.getColorIds().isEmpty()) {
            List<Color> selectedColors = colorRepository.findAllById(request.getColorIds());
            product.setColors(selectedColors);
        }
        // 3. Gallery Images (Loop and Save)
        if (galleryImages != null && !galleryImages.isEmpty()) {
            List<String> imagePaths = new ArrayList<>();
            for (MultipartFile file : galleryImages) {
                if (!file.isEmpty()) {
                    imagePaths.add(fileStorageService.storeFile(file, "products"));
                }
            }
            product.setImages(imagePaths);
        }

        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest request, MultipartFile mainImage,
            List<MultipartFile> galleryImages) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (request.getName() != null)
            product.setName(request.getName());
        if (request.getDescription() != null)
            product.setDescription(request.getDescription());
        if (request.getBasePrice() != null)
            product.setBasePrice(request.getBasePrice());
        if (request.getIsAvailable() != null)
            product.setIsAvailable(request.getIsAvailable());

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

        if (mainImage != null && !mainImage.isEmpty()) {
            product.setImage(fileStorageService.storeFile(mainImage, "products"));
        }

        if (request.getColorIds() != null && !request.getColorIds().isEmpty()) {
            List<Color> selectedColors = colorRepository.findAllById(request.getColorIds());
            product.setColors(selectedColors);
        }
        if (galleryImages != null && !galleryImages.isEmpty()) {
            // Optional: Clear old images if you want full replace
            // product.getImages().clear();

            for (MultipartFile file : galleryImages) {
                if (!file.isEmpty()) {
                    product.getImages().add(fileStorageService.storeFile(file, "products"));
                }
            }
        }

        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
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

    public void deleteProductVariant(Long variantId) {
        if (!variantRepository.existsById(variantId)) {
            throw new ResourceNotFoundException("Variant not found with id: " + variantId);
        }
        variantRepository.deleteById(variantId);
    }

    // 1. Create Color
    public Color createColor(String name, String hexCode) {
        Color color = new Color();
        color.setName(name);
        color.setHexCode(hexCode);
        return colorRepository.save(color);
    }

    // 2. Get All Colors
    public List<Color> getAllColors() {
        return colorRepository.findAll();
    }

    // 3. Delete Color
    public void deleteColor(Long id) {
        colorRepository.deleteById(id);
    }
}