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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public Page<Product> getAllProductsRandomly(Pageable pageable) {
        // Warning: True randomness breaks pagination. We return standard active
        // products here.
        return productRepository.findAllActiveProducts(pageable);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public Page<Product> getProductsByStore(Long storeId, Pageable pageable) {
        if (!storeRepository.existsById(storeId)) {
            throw new ResourceNotFoundException("Store not found with id: " + storeId);
        }
        return productRepository.findByStoreStoreIdAndIsAvailableTrue(storeId, pageable);
    }

    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("category not found with id: " + categoryId);
        }
        return productRepository.findByCategoryCategoryIdAndIsAvailableTrue(categoryId, pageable);
    }

    public Page<Product> getProductsBySubCategory(Long subCategoryId, Pageable pageable) {
        if (!subCategoryRepository.existsById(subCategoryId)) {
            throw new ResourceNotFoundException("subCategory not found with id: " + subCategoryId);
        }
        return productRepository.findBySubCategorySubcategoryIdAndIsAvailableTrue(subCategoryId, pageable);
    }

    public Page<Product> searchProducts(String keyword, Long categoryId, Pageable pageable) {
        if (keyword == null)
            keyword = "";
        if (categoryId == null || categoryId == 0) {
            return productRepository.findByNameContainingIgnoreCaseAndIsAvailableTrue(keyword, pageable);
        } else {
            return productRepository.findByCategoryCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(categoryId,
                    keyword, pageable);
        }
    }

    public Page<Product> searchProductsInStore(String keyword, Long storeId, Pageable pageable) {
        if (!storeRepository.existsById(storeId)) {
            throw new ResourceNotFoundException("Store not found with id: " + storeId);
        }
        if (keyword == null)
            keyword = "";
        return productRepository.findByStoreStoreIdAndNameContainingIgnoreCaseAndIsAvailableTrue(storeId, keyword,
                pageable);
    }

    public Page<Product> getProductsByStoreAndCategory(Long storeId, Long categoryId, Pageable pageable) {
        return productRepository.findByStoreStoreIdAndCategoryCategoryIdAndIsAvailableTrue(storeId, categoryId,
                pageable);
    }

    public Page<Product> getProductsByStoreAndSubCategory(Long storeId, Long subCategoryId, Pageable pageable) {
        return productRepository.findByStoreStoreIdAndSubCategorySubcategoryIdAndIsAvailableTrue(storeId, subCategoryId,
                pageable);
    }

    //
    public Page<Product> getProductsUnderPrice(Double price, Pageable pageable) {
        return productRepository.findByBasePriceLessThanEqualAndIsAvailableTrue(price, pageable);
    }

    public Page<Product> getNewestProducts(Pageable pageable) {
        return productRepository.findByIsAvailableTrueOrderByProductIdDesc(pageable);
    }

    public Page<Product> getTrendingProducts(Pageable pageable) {
        return productRepository.findByIsTrendingTrueAndIsAvailableTrue(pageable);
    }
}