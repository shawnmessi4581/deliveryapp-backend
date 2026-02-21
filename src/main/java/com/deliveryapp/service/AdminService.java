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

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 🧹 Cleanup: Delete profile image if exists
        if (user.getProfileImage() != null) {
            fileStorageService.deleteFile(user.getProfileImage());
        }

        userRepository.deleteById(userId);
    }

    @Transactional
    public void updateUserStatus(Long userId, Boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setIsActive(isActive);
        userRepository.save(user);
    }

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

    public List<User> getAllDrivers() {
        return userRepository.findByUserType(UserType.DRIVER);
    }

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
        System.out.println("Attempting to create category: " + name);
        try {
            Category category = new Category();
            category.setName(name);
            category.setIsActive(true);
            category.setDisplayOrder(0);

            if (image != null && !image.isEmpty()) {
                System.out.println("Image received. Name: " + image.getOriginalFilename());
                String imageUrl = fileStorageService.storeFile(image, "categories");
                System.out.println("Image saved at: " + imageUrl);
                category.setIcon(imageUrl);
            }
            return categoryRepository.save(category);
        } catch (Exception e) {
            System.err.println("CRASHED IN CREATE CATEGORY:");
            e.printStackTrace();
            throw new RuntimeException("Error creating category: " + e.getMessage());
        }
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // 🧹 Cleanup: Delete category icon
        if (category.getIcon() != null) {
            fileStorageService.deleteFile(category.getIcon());
        }

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
            // 🧹 Cleanup: Delete the old file before saving the new one
            if (category.getIcon() != null) {
                fileStorageService.deleteFile(category.getIcon());
            }
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
            // 🧹 Cleanup: Delete old subcategory icon
            if (subCategory.getIcon() != null) {
                fileStorageService.deleteFile(subCategory.getIcon());
            }
            String imageUrl = fileStorageService.storeFile(image, "subcategories");
            subCategory.setIcon(imageUrl);
        }

        return subCategoryRepository.save(subCategory);
    }

    public void deleteSubCategory(Long id) {
        SubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubCategory not found with id: " + id));

        // 🧹 Cleanup: Delete icon
        if (subCategory.getIcon() != null) {
            fileStorageService.deleteFile(subCategory.getIcon());
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
            // 🧹 Cleanup: Delete old logo
            if (store.getLogo() != null)
                fileStorageService.deleteFile(store.getLogo());
            store.setLogo(fileStorageService.storeFile(logo, "stores"));
        }
        if (cover != null && !cover.isEmpty()) {
            // 🧹 Cleanup: Delete old cover
            if (store.getCoverImage() != null)
                fileStorageService.deleteFile(store.getCoverImage());
            store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
        }

        if (request.getOpeningTime() != null)
            store.setOpeningTime(request.getOpeningTime());
        if (request.getClosingTime() != null)
            store.setClosingTime(request.getClosingTime());
        if (request.getIsBusy() != null)
            store.setIsBusy(request.getIsBusy());

        return storeRepository.save(store);
    }

    public void deleteStore(Long id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        // 🧹 Cleanup: Delete store images
        if (store.getLogo() != null)
            fileStorageService.deleteFile(store.getLogo());
        if (store.getCoverImage() != null)
            fileStorageService.deleteFile(store.getCoverImage());

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

        if (mainImage != null && !mainImage.isEmpty()) {
            product.setImage(fileStorageService.storeFile(mainImage, "products"));
        }
        if (request.getColorIds() != null && !request.getColorIds().isEmpty()) {
            List<Color> selectedColors = colorRepository.findAllById(request.getColorIds());
            product.setColors(selectedColors);
        }

        if (galleryImages != null && !galleryImages.isEmpty()) {
            List<String> imagePaths = new ArrayList<>();
            for (MultipartFile file : galleryImages) {
                if (!file.isEmpty()) {
                    imagePaths.add(fileStorageService.storeFile(file, "products"));
                }
            }
            product.setImages(imagePaths);
        }
        if (request.getIsTrending() != null) {
            product.setIsTrending(request.getIsTrending());
        } else {
            product.setIsTrending(false);
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
            // 🧹 Cleanup: Delete old main image
            if (product.getImage() != null) {
                fileStorageService.deleteFile(product.getImage());
            }
            product.setImage(fileStorageService.storeFile(mainImage, "products"));
        }

        if (request.getColorIds() != null && !request.getColorIds().isEmpty()) {
            List<Color> selectedColors = colorRepository.findAllById(request.getColorIds());
            product.setColors(selectedColors);
        }

        if (galleryImages != null && !galleryImages.isEmpty()) {
            // 🧹 Cleanup: Delete ALL old gallery images from the server disk before
            // replacing the list
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                for (String oldImagePath : product.getImages()) {
                    fileStorageService.deleteFile(oldImagePath);
                }
                product.getImages().clear(); // Clear DB list
            }

            // Save new images
            for (MultipartFile file : galleryImages) {
                if (!file.isEmpty()) {
                    product.getImages().add(fileStorageService.storeFile(file, "products"));
                }
            }
        }

        if (request.getIsTrending() != null) {
            product.setIsTrending(request.getIsTrending());
        }

        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // 🧹 Cleanup: Delete main image
        if (product.getImage() != null) {
            fileStorageService.deleteFile(product.getImage());
        }

        // 🧹 Cleanup: Delete all gallery images
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            for (String imagePath : product.getImages()) {
                fileStorageService.deleteFile(imagePath);
            }
        }

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