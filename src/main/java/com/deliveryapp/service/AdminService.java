// package com.deliveryapp.service;

// import com.deliveryapp.dto.catalog.CategoryRequest;
// import com.deliveryapp.dto.catalog.ProductRequest;
// import com.deliveryapp.dto.catalog.StoreRequest;
// import com.deliveryapp.dto.catalog.SubCategoryRequest;
// import com.deliveryapp.dto.user.CreateDriverRequest;
// import com.deliveryapp.dto.user.CreateUserRequest;
// import com.deliveryapp.entity.*;
// import com.deliveryapp.enums.UserType;
// import com.deliveryapp.exception.DuplicateResourceException;
// import com.deliveryapp.exception.InvalidDataException;
// import com.deliveryapp.exception.ResourceNotFoundException;
// import com.deliveryapp.repository.*;
// import lombok.RequiredArgsConstructor;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;
// import org.springframework.web.multipart.MultipartFile;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.Pageable;

// import java.time.LocalDateTime;
// import java.util.ArrayList;
// import java.util.List;

// @Service
// @RequiredArgsConstructor
// public class AdminService {

// private final CategoryRepository categoryRepository;
// private final SubCategoryRepository subCategoryRepository;
// private final StoreRepository storeRepository;
// private final ProductRepository productRepository;
// private final ProductVariantRepository variantRepository;
// private final FileStorageService fileStorageService;
// private final UserRepository userRepository;
// private final PasswordEncoder passwordEncoder;
// private final ColorRepository colorRepository;
// private final StoreCategoryRepository storeCategoryRepository;

// // ==================== USER MANAGEMENT ====================

// public List<User> getAllUsers() {
// return userRepository.findAll();
// }

// public void deleteUser(Long userId) {
// User user = userRepository.findById(userId)
// .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود برقم: "
// + userId));

// // 🧹 Cleanup: Delete profile image if exists
// if (user.getProfileImage() != null) {
// fileStorageService.deleteFile(user.getProfileImage());
// }

// userRepository.deleteById(userId);
// }

// @Transactional
// public void updateUserStatus(Long userId, Boolean isActive) {
// User user = userRepository.findById(userId)
// .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود برقم: "
// + userId));

// user.setIsActive(isActive);
// userRepository.save(user);
// }

// @Transactional
// public User createDriver(CreateDriverRequest request, MultipartFile image) {
// if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
// throw new DuplicateResourceException("رقم الهاتف مسجل بالفعل");
// }

// User driver = new User();
// driver.setName(request.getName());
// driver.setPhoneNumber(request.getPhoneNumber());
// driver.setEmail(request.getEmail());
// driver.setPassword(passwordEncoder.encode(request.getPassword()));
// driver.setUserType(UserType.DRIVER);
// driver.setIsActive(true);

// driver.setVehicleType(request.getVehicleType());
// driver.setVehicleNumber(request.getVehicleNumber());
// driver.setIsAvailable(true);
// driver.setRating(5.0);
// driver.setTotalDeliveries(0);
// driver.setCreatedAt(LocalDateTime.now());

// if (image != null && !image.isEmpty()) {
// String imageUrl = fileStorageService.storeFile(image, "profiles");
// driver.setProfileImage(imageUrl);
// }

// return userRepository.save(driver);
// }

// public List<User> getAllDrivers() {
// return userRepository.findByUserType(UserType.DRIVER);
// }

// @Transactional
// public User createDashboardUser(CreateUserRequest request) {
// if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
// throw new DuplicateResourceException("رقم الهاتف موجود بالفعل");
// }

// User user = new User();
// user.setName(request.getName());
// user.setPhoneNumber(request.getPhoneNumber());
// user.setEmail(request.getEmail());
// user.setPassword(passwordEncoder.encode(request.getPassword()));

// if (request.getRole() == UserType.ADMIN || request.getRole() ==
// UserType.EMPLOYEE) {
// user.setUserType(request.getRole());
// } else {
// throw new InvalidDataException("دور غير صالح. استخدم ADMIN أو EMPLOYEE.");
// }

// user.setIsActive(true);
// user.setCreatedAt(LocalDateTime.now());

// return userRepository.save(user);
// }

// // ==================== CATEGORY CRUD ====================

// public List<Category> getAllCategories() {
// return categoryRepository.findAllByOrderByDisplayOrderAsc();
// }

// @Transactional
// public Category createCategory(CategoryRequest request, MultipartFile image)
// {
// Category category = new Category();
// category.setName(request.getName());
// category.setIsActive(request.getIsActive() != null ? request.getIsActive() :
// true);
// category.setDisplayOrder(request.getDisplayOrder() != null ?
// request.getDisplayOrder() : 0);

// if (image != null && !image.isEmpty()) {
// String imageUrl = fileStorageService.storeFile(image, "categories");
// category.setIcon(imageUrl);
// }

// return categoryRepository.save(category);
// }

// @Transactional
// public Category updateCategory(Long id, CategoryRequest request,
// MultipartFile image) {
// Category category = categoryRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("الفئة غير موجودة برقم: " +
// id));

// if (request.getName() != null && !request.getName().trim().isEmpty()) {
// category.setName(request.getName());
// }
// if (request.getIsActive() != null) {
// category.setIsActive(request.getIsActive());
// }
// if (request.getDisplayOrder() != null) {
// category.setDisplayOrder(request.getDisplayOrder());
// }

// if (image != null && !image.isEmpty()) {
// // 🧹 Cleanup: Delete the old file before saving the new one
// if (category.getIcon() != null) {
// fileStorageService.deleteFile(category.getIcon());
// }
// String imageUrl = fileStorageService.storeFile(image, "categories");
// category.setIcon(imageUrl);
// }

// return categoryRepository.save(category);
// }

// public void deleteCategory(Long id) {
// Category category = categoryRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("الفئة غير موجودة"));

// // 🧹 Cleanup: Delete category icon
// if (category.getIcon() != null) {
// fileStorageService.deleteFile(category.getIcon());
// }

// categoryRepository.delete(category);
// }

// // ==================== SUBCATEGORY CRUD ====================

// public List<SubCategory> getAllSubCategories() {
// return subCategoryRepository.findAllByOrderByDisplayOrderAsc();
// }

// @Transactional
// public SubCategory createSubCategory(SubCategoryRequest request,
// MultipartFile image) {
// Category parent = categoryRepository.findById(request.getCategoryId())
// .orElseThrow(() -> new ResourceNotFoundException("الفئة الأصلية غير
// موجودة"));

// SubCategory sub = new SubCategory();
// sub.setName(request.getName());
// sub.setCategory(parent);
// sub.setIsActive(request.getIsActive() != null ? request.getIsActive() :
// true);
// sub.setDisplayOrder(request.getDisplayOrder() != null ?
// request.getDisplayOrder() : 0);

// if (image != null && !image.isEmpty()) {
// String imageUrl = fileStorageService.storeFile(image, "subcategories");
// sub.setIcon(imageUrl);
// }

// return subCategoryRepository.save(sub);
// }

// @Transactional
// public SubCategory updateSubCategory(Long id, SubCategoryRequest request,
// MultipartFile image) {
// SubCategory subCategory = subCategoryRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("الفئة الفرعية غير موجودة
// برقم: " + id));

// if (request.getName() != null && !request.getName().trim().isEmpty()) {
// subCategory.setName(request.getName());
// }
// if (request.getCategoryId() != null) {
// Category newParent = categoryRepository.findById(request.getCategoryId())
// .orElseThrow(() -> new ResourceNotFoundException(
// "الفئة غير موجودة برقم: " + request.getCategoryId()));
// subCategory.setCategory(newParent);
// }
// if (request.getIsActive() != null) {
// subCategory.setIsActive(request.getIsActive());
// }
// if (request.getDisplayOrder() != null) {
// subCategory.setDisplayOrder(request.getDisplayOrder());
// }

// if (image != null && !image.isEmpty()) {
// // 🧹 Cleanup: Delete old subcategory icon
// if (subCategory.getIcon() != null) {
// fileStorageService.deleteFile(subCategory.getIcon());
// }
// String imageUrl = fileStorageService.storeFile(image, "subcategories");
// subCategory.setIcon(imageUrl);
// }

// return subCategoryRepository.save(subCategory);
// }

// public void deleteSubCategory(Long id) {
// SubCategory subCategory = subCategoryRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("الفئة الفرعية غير موجودة
// برقم: " + id));

// // 🧹 Cleanup: Delete icon
// if (subCategory.getIcon() != null) {
// fileStorageService.deleteFile(subCategory.getIcon());
// }

// subCategoryRepository.deleteById(id);
// }

// // ==================== STORE CRUD ====================

// public List<Store> getAllStores() {
// return storeRepository.findAllByOrderByDisplayOrderAsc();
// }

// @Transactional
// public Store createStore(StoreRequest request, MultipartFile logo,
// MultipartFile cover) {
// Store store = new Store();
// store.setName(request.getName());
// store.setDescription(request.getDescription());
// store.setPhone(request.getPhone());
// store.setAddress(request.getAddress());
// store.setLatitude(request.getLatitude());
// store.setLongitude(request.getLongitude());
// store.setDeliveryFeeKM(request.getDeliveryFeeKM());
// store.setMinimumOrder(request.getMinimumOrder());
// store.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime());
// store.setIsActive(true);
// store.setCreatedAt(LocalDateTime.now());
// store.setRating(5.0);
// store.setTotalOrders(0);
// store.setDisplayOrder(request.getDisplayOrder() != null ?
// request.getDisplayOrder() : 0);

// if (request.getCategoryId() != null) {
// Category cat =
// categoryRepository.findById(request.getCategoryId()).orElseThrow();
// store.setCategory(cat);
// }
// if (request.getSubCategoryId() != null) {
// SubCategory sub =
// subCategoryRepository.findById(request.getSubCategoryId()).orElseThrow();
// store.setSubCategory(sub);
// }

// if (logo != null && !logo.isEmpty()) {
// store.setLogo(fileStorageService.storeFile(logo, "stores"));
// }
// if (cover != null && !cover.isEmpty()) {
// store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
// }

// store.setOpeningTime(request.getOpeningTime());
// store.setClosingTime(request.getClosingTime());
// store.setCommissionPercentage(
// request.getCommissionPercentage() != null ? request.getCommissionPercentage()
// : 0.0);
// return storeRepository.save(store);
// }

// @Transactional
// public Store updateStore(Long id, StoreRequest request, Boolean isActive,
// MultipartFile logo, MultipartFile cover) {
// Store store = storeRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود برقم: " +
// id));

// if (request.getName() != null)
// store.setName(request.getName());
// if (request.getDescription() != null)
// store.setDescription(request.getDescription());
// if (request.getPhone() != null)
// store.setPhone(request.getPhone());
// if (request.getAddress() != null)
// store.setAddress(request.getAddress());
// if (request.getLatitude() != null)
// store.setLatitude(request.getLatitude());
// if (request.getLongitude() != null)
// store.setLongitude(request.getLongitude());
// if (request.getDeliveryFeeKM() != null)
// store.setDeliveryFeeKM(request.getDeliveryFeeKM());
// if (request.getMinimumOrder() != null)
// store.setMinimumOrder(request.getMinimumOrder());
// if (request.getEstimatedDeliveryTime() != null)
// store.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime());

// if (isActive != null)
// store.setIsActive(isActive);

// if (request.getDisplayOrder() != null)
// store.setDisplayOrder(request.getDisplayOrder());
// if (request.getOpeningTime() != null)
// store.setOpeningTime(request.getOpeningTime());
// if (request.getClosingTime() != null)
// store.setClosingTime(request.getClosingTime());
// if (request.getIsBusy() != null)
// store.setIsBusy(request.getIsBusy());

// if (request.getCategoryId() != null) {
// Category cat = categoryRepository.findById(request.getCategoryId())
// .orElseThrow(() -> new ResourceNotFoundException("الفئة غير موجودة"));
// store.setCategory(cat);
// }
// if (request.getSubCategoryId() != null) {
// SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId())
// .orElseThrow(() -> new ResourceNotFoundException("الفئة الفرعية غير
// موجودة"));
// store.setSubCategory(sub);
// }

// if (logo != null && !logo.isEmpty()) {
// // 🧹 Cleanup: Delete old logo
// if (store.getLogo() != null)
// fileStorageService.deleteFile(store.getLogo());
// store.setLogo(fileStorageService.storeFile(logo, "stores"));
// }
// if (cover != null && !cover.isEmpty()) {
// // 🧹 Cleanup: Delete old cover
// if (store.getCoverImage() != null)
// fileStorageService.deleteFile(store.getCoverImage());
// store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
// }
// if (request.getCommissionPercentage() != null) {
// store.setCommissionPercentage(request.getCommissionPercentage());
// }

// return storeRepository.save(store);
// }

// public void deleteStore(Long id) {
// Store store = storeRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

// // 🧹 Cleanup: Delete store images
// if (store.getLogo() != null)
// fileStorageService.deleteFile(store.getLogo());
// if (store.getCoverImage() != null)
// fileStorageService.deleteFile(store.getCoverImage());

// storeRepository.deleteById(id);
// }

// // ==================== PRODUCT CRUD ====================

// public Page<Product> getAllProducts(Long storeId, Long categoryId, Long
// subCategoryId, Pageable pageable) {
// return productRepository.findAdminFilteredProducts(storeId, categoryId,
// subCategoryId, pageable);
// }

// @Transactional
// public Product createProduct(ProductRequest request, MultipartFile mainImage,
// List<MultipartFile> galleryImages) {
// Store store = storeRepository.findById(request.getStoreId())
// .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

// Product product = new Product();
// product.setName(request.getName());
// product.setDescription(request.getDescription());
// if (request.getIsUsd() != null) {
// product.setIsUsd(request.getIsUsd());
// if (request.getIsUsd()) {
// product.setUsdPrice(request.getUsdPrice());
// product.setBasePrice(0.0); // Clear SYP if USD is active
// } else {
// product.setBasePrice(request.getBasePrice());
// product.setUsdPrice(0.0); // Clear USD if SYP is active
// }
// } else {
// product.setIsUsd(false);
// product.setBasePrice(request.getBasePrice() != null ? request.getBasePrice()
// : 0.0);
// product.setUsdPrice(0.0);
// }
// product.setIsAvailable(true);
// product.setStore(store);
// product.setDisplayOrder(request.getDisplayOrder() != null ?
// request.getDisplayOrder() : 0);

// if (request.getCategoryId() != null) {
// Category cat =
// categoryRepository.findById(request.getCategoryId()).orElseThrow();
// product.setCategory(cat);
// }
// if (request.getSubCategoryId() != null) {
// SubCategory sub =
// subCategoryRepository.findById(request.getSubCategoryId()).orElseThrow();
// product.setSubCategory(sub);
// }

// if (mainImage != null && !mainImage.isEmpty()) {
// product.setImage(fileStorageService.storeFile(mainImage, "products"));
// }

// if (request.getColorIds() != null && !request.getColorIds().isEmpty()) {
// List<Color> selectedColors =
// colorRepository.findAllById(request.getColorIds());
// product.setColors(selectedColors);
// }

// if (galleryImages != null && !galleryImages.isEmpty()) {
// List<String> imagePaths = new ArrayList<>();
// for (MultipartFile file : galleryImages) {
// if (!file.isEmpty()) {
// imagePaths.add(fileStorageService.storeFile(file, "products"));
// }
// }
// product.setImages(imagePaths);
// }

// product.setIsTrending(request.getIsTrending() != null ?
// request.getIsTrending() : false);
// // store category
// if (request.getStoreCategoryId() != null) {
// StoreCategory storeCategory =
// storeCategoryRepository.findById(request.getStoreCategoryId())
// .orElseThrow(() -> new ResourceNotFoundException("Store Category not
// found"));

// // Safety check: Make sure this category belongs to the store the product is
// in
// if
// (!storeCategory.getStore().getStoreId().equals(product.getStore().getStoreId()))
// {
// throw new InvalidDataException("This category belongs to a different
// store!");
// }

// product.setStoreCategory(storeCategory);
// } else {
// // If they send null during an update, it clears the section
// product.setStoreCategory(null);
// }
// return productRepository.save(product);
// }

// @Transactional
// public Product updateProduct(Long id, ProductRequest request, MultipartFile
// mainImage,
// List<MultipartFile> galleryImages) {
// Product product = productRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود برقم: " +
// id));

// if (request.getName() != null)
// product.setName(request.getName());
// if (request.getDescription() != null)
// product.setDescription(request.getDescription());
// if (request.getIsUsd() != null) {
// product.setIsUsd(request.getIsUsd());
// if (request.getIsUsd()) {
// if (request.getUsdPrice() != null)
// product.setUsdPrice(request.getUsdPrice());
// product.setBasePrice(0.0); // Clear SYP
// } else {
// if (request.getBasePrice() != null)
// product.setBasePrice(request.getBasePrice());
// product.setUsdPrice(0.0); // Clear USD
// }
// }
// if (request.getIsAvailable() != null)
// product.setIsAvailable(request.getIsAvailable());
// if (request.getDisplayOrder() != null)
// product.setDisplayOrder(request.getDisplayOrder());
// if (request.getIsTrending() != null)
// product.setIsTrending(request.getIsTrending());

// if (request.getStoreId() != null) {
// Store store = storeRepository.findById(request.getStoreId())
// .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
// product.setStore(store);
// }
// if (request.getCategoryId() != null) {
// Category cat = categoryRepository.findById(request.getCategoryId())
// .orElseThrow(() -> new ResourceNotFoundException("الفئة غير موجودة"));
// product.setCategory(cat);
// }
// if (request.getSubCategoryId() != null) {
// SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId())
// .orElseThrow(() -> new ResourceNotFoundException("الفئة الفرعية غير
// موجودة"));
// product.setSubCategory(sub);
// }

// if (mainImage != null && !mainImage.isEmpty()) {
// // 🧹 Cleanup: Delete old main image
// if (product.getImage() != null) {
// fileStorageService.deleteFile(product.getImage());
// }
// product.setImage(fileStorageService.storeFile(mainImage, "products"));
// }

// // ✅ FIX: Color clear logic. If they send an empty array/string, it will
// clear
// // colors.
// if (request.getColorIds() != null) {
// List<Color> selectedColors =
// colorRepository.findAllById(request.getColorIds());
// product.setColors(selectedColors);
// }

// if (galleryImages != null && !galleryImages.isEmpty()) {
// // 🧹 Cleanup: Delete ALL old gallery images from the server disk before
// // replacing
// if (product.getImages() != null && !product.getImages().isEmpty()) {
// for (String oldImagePath : product.getImages()) {
// fileStorageService.deleteFile(oldImagePath);
// }
// product.getImages().clear(); // Clear DB list
// }

// // Save new images
// for (MultipartFile file : galleryImages) {
// if (!file.isEmpty()) {
// product.getImages().add(fileStorageService.storeFile(file, "products"));
// }
// }
// }
// // store category
// if (request.getStoreCategoryId() != null) {
// StoreCategory storeCategory =
// storeCategoryRepository.findById(request.getStoreCategoryId())
// .orElseThrow(() -> new ResourceNotFoundException("Store Category not
// found"));

// // Safety check: Make sure this category belongs to the store the product is
// in
// if
// (!storeCategory.getStore().getStoreId().equals(product.getStore().getStoreId()))
// {
// throw new InvalidDataException("This category belongs to a different
// store!");
// }

// product.setStoreCategory(storeCategory);
// } else {
// // If they send null during an update, it clears the section
// product.setStoreCategory(null);
// }
// return productRepository.save(product);
// }

// public void deleteProduct(Long id) {
// Product product = productRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود"));

// // 🧹 Cleanup: Delete main image
// if (product.getImage() != null) {
// fileStorageService.deleteFile(product.getImage());
// }

// // 🧹 Cleanup: Delete all gallery images
// if (product.getImages() != null && !product.getImages().isEmpty()) {
// for (String imagePath : product.getImages()) {
// fileStorageService.deleteFile(imagePath);
// }
// }

// productRepository.deleteById(id);
// }

// public ProductVariant addProductVariant(Long productId, String name, Double
// priceAdjustment) {
// Product product = productRepository.findById(productId)
// .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود"));

// ProductVariant variant = new ProductVariant();
// variant.setProduct(product);
// variant.setVariantValue(name);
// variant.setPriceAdjustment(priceAdjustment);
// variant.setIsAvailable(true);
// return variantRepository.save(variant);
// }

// public void deleteProductVariant(Long variantId) {
// if (!variantRepository.existsById(variantId)) {
// throw new ResourceNotFoundException("النوع غير موجود برقم: " + variantId);
// }
// variantRepository.deleteById(variantId);
// }

// // ==================== COLORS ====================

// public Color createColor(String name, String hexCode) {
// Color color = new Color();
// color.setName(name);
// color.setHexCode(hexCode);
// return colorRepository.save(color);
// }

// // 4. Update Color
// @Transactional
// public Color updateColor(Long id, String name, String hexCode) {
// Color color = colorRepository.findById(id)
// .orElseThrow(() -> new ResourceNotFoundException("اللون غير موجود برقم: " +
// id));

// if (name != null && !name.trim().isEmpty()) {
// color.setName(name);
// }
// if (hexCode != null && !hexCode.trim().isEmpty()) {
// // Optional: You can add validation here to ensure it starts with '#'
// color.setHexCode(hexCode);
// }

// return colorRepository.save(color);
// }

// public List<Color> getAllColors() {
// return colorRepository.findAll();
// }

// public void deleteColor(Long id) {
// colorRepository.deleteById(id);
// }
// }