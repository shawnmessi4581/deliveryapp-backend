package com.deliveryapp.service;

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

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;

    // ================= CATEGORIES =================
    public List<Category> getAllActiveCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    // ================= SUBCATEGORIES =================
    public List<SubCategory> getSubCategoriesByCategoryId(Long categoryId) {
        // Validate category exists first
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }
        return subCategoryRepository.findByCategoryCategoryId(categoryId);
    }

    // ================= STORES =================
    public List<Store> getAllActiveStores() {
        return storeRepository.findByIsActiveTrue();
    }

    public Store getStoreById(Long id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + id));
    }

    public List<Store> getStoresByCategory(Long categoryId) {
        return storeRepository.findByCategoryCategoryId(categoryId);
    }
    // NEW: Get Stores by SubCategory
    public List<Store> getStoresBySubCategory(Long subCategoryId) {
        // Validate subcategory exists (optional, but good for error messaging)
        if (!subCategoryRepository.existsById(subCategoryId)) {
            throw new ResourceNotFoundException("SubCategory not found with id: " + subCategoryId);
        }
        return storeRepository.findBySubCategorySubcategoryId(subCategoryId);
    }


    // ================= PRODUCTS =================
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public List<Product> getProductsByStore(Long storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new ResourceNotFoundException("Store not found with id: " + storeId);
        }
        return productRepository.findByStoreStoreIdAndIsAvailableTrue(storeId);
    }

    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryCategoryIdAndIsAvailableTrue(categoryId);
    }

    public List<Product> getProductsBySubCategory(Long subCategoryId) {
        return productRepository.findBySubCategorySubcategoryIdAndIsAvailableTrue(subCategoryId);
    }

    public List<Product> searchProducts(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword);
    }
}