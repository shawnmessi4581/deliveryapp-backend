package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.CategoryRequest;
import com.deliveryapp.dto.catalog.SubCategoryRequest;
import com.deliveryapp.entity.Category;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.SubCategory;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.CategoryRepository;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.SubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final FileStorageService fileStorageService;

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    // ================= PUBLIC / CATALOG =================

    public List<Category> getAllActiveCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("الفئة غير موجودة برقم: " + id));
    }

    public List<SubCategory> getSubCategoriesByCategoryId(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("الفئة غير موجودة برقم: " + categoryId);
        }
        return subCategoryRepository.findByCategoryCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(categoryId);
    }

    // ================= ADMIN CRUD =================
    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional
    public Category createCategory(CategoryRequest request, MultipartFile image) {
        Category category = new Category();
        category.setName(request.getName());
        category.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        category.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "categories");
            category.setIcon(imageUrl);
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(Long id, CategoryRequest request, MultipartFile image) {
        Category category = getCategoryById(id);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            category.setName(request.getName());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }
        if (image != null && !image.isEmpty()) {
            if (category.getIcon() != null) {
                fileStorageService.deleteFile(category.getIcon());
            }
            String imageUrl = fileStorageService.storeFile(image, "categories");
            category.setIcon(imageUrl);
        }
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        Category category = getCategoryById(id);
        if (category.getIcon() != null) {
            fileStorageService.deleteFile(category.getIcon());
        }
        categoryRepository.delete(category);
    }

    public List<SubCategory> getAllSubCategories() {
        return subCategoryRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional
    public SubCategory createSubCategory(SubCategoryRequest request, MultipartFile image) {
        Category parent = getCategoryById(request.getCategoryId());

        SubCategory sub = new SubCategory();
        sub.setName(request.getName());
        sub.setCategory(parent);
        sub.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        sub.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.storeFile(image, "subcategories");
            sub.setIcon(imageUrl);
        }
        return subCategoryRepository.save(sub);
    }

    @Transactional
    public SubCategory updateSubCategory(Long id, SubCategoryRequest request, MultipartFile image) {
        SubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("الفئة الفرعية غير موجودة برقم: " + id));

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            subCategory.setName(request.getName());
        }
        if (request.getCategoryId() != null) {
            Category newParent = getCategoryById(request.getCategoryId());
            subCategory.setCategory(newParent);
        }
        if (request.getIsActive() != null) {
            subCategory.setIsActive(request.getIsActive());
        }
        if (request.getDisplayOrder() != null) {
            subCategory.setDisplayOrder(request.getDisplayOrder());
        }
        if (image != null && !image.isEmpty()) {
            if (subCategory.getIcon() != null) {
                fileStorageService.deleteFile(subCategory.getIcon());
            }
            String imageUrl = fileStorageService.storeFile(image, "subcategories");
            subCategory.setIcon(imageUrl);
        }
        return subCategoryRepository.save(subCategory);
    }

    @Transactional
    public void deleteSubCategory(Long id) {
        SubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("الفئة الفرعية غير موجودة برقم: " + id));

        // 1. UNLINK STORES
        // Find all stores using this subcategory and set subcategory to null
        List<Store> linkedStores = storeRepository.findBySubCategorySubcategoryIdOrderByDisplayOrderAsc(id);
        for (Store store : linkedStores) {
            store.setSubCategory(null);
        }
        storeRepository.saveAll(linkedStores);

        // 2. UNLINK PRODUCTS
        // Find all products using this subcategory and set subcategory to null
        // Note: You need a method in ProductRepository that returns a List, not a Page
        // for this cleanup
        // If you don't have one, add this to ProductRepository: List<Product>
        // findBySubCategorySubcategoryId(Long id);
        List<Product> linkedProducts = productRepository.findBySubCategorySubcategoryId(id);
        if (linkedProducts != null && !linkedProducts.isEmpty()) {
            for (Product product : linkedProducts) {
                product.setSubCategory(null);
            }
            productRepository.saveAll(linkedProducts);
        }

        // 3. 🧹 Cleanup: Delete icon from server storage
        if (subCategory.getIcon() != null) {
            fileStorageService.deleteFile(subCategory.getIcon());
        }

        // 4. Finally, delete the subcategory
        subCategoryRepository.deleteById(id);
    }
}