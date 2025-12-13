package com.deliveryapp.repository;

import com.deliveryapp.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // 1. Existing: Get menu for a specific store
    List<Product> findByStoreStoreIdAndIsAvailableTrue(Long storeId);

    // 2. Existing: Search products
    List<Product> findByNameContainingIgnoreCase(String keyword);
    List<Product> findByNameContainingIgnoreCaseAndCategoryCategoryId(String keyword,long categoryId);
    List<Product> findByNameContainingIgnoreCaseAndStoreStoreId(String keyword,long storeId);

    // --- NEW METHODS ADDED BELOW ---

    // 3. Find by Category
    List<Product> findByCategoryCategoryId(Long categoryId);

    // Variation: Find by Category only if available
    List<Product> findByCategoryCategoryIdAndIsAvailableTrue(Long categoryId);

    // 4. Find by SubCategory
    // Note: The Entity field is named 'subCategory', so we use 'SubCategory' here
    List<Product> findBySubCategorySubcategoryId(Long subcategoryId);

    // Variation: Find by SubCategory only if available
    List<Product> findBySubCategorySubcategoryIdAndIsAvailableTrue(Long subcategoryId);

    // 5. Find by Store AND Category (Useful for filtering a specific restaurant's menu)
    List<Product> findByStoreStoreIdAndCategoryCategoryId(Long storeId, Long categoryId);

    // Get products belonging to a specific Store AND specific Category
    List<Product> findByStoreStoreIdAndCategoryCategoryIdAndIsAvailableTrue(Long storeId, Long categoryId);

    // Get products belonging to a specific Store AND specific SubCategory
    List<Product> findByStoreStoreIdAndSubCategorySubcategoryIdAndIsAvailableTrue(Long storeId, Long subCategoryId);
}