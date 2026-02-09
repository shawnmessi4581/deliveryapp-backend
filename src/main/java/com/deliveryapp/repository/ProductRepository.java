package com.deliveryapp.repository;

import com.deliveryapp.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Fetch all active products in random order (PostgreSQL specific)
    @Query(value = "SELECT * FROM products WHERE is_available = true ORDER BY RANDOM()", nativeQuery = true)
    List<Product> findAllActiveProductsRandomly();

    // 1. Existing: Get menu for a specific store
    List<Product> findByStoreStoreIdAndIsAvailableTrue(Long storeId);

    // 1. Search Global (Category ID = 0)
    List<Product> findByNameContainingIgnoreCaseAndIsAvailableTrue(String keyword);

    // 2. Search Specific Category
    List<Product> findByCategoryCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(Long categoryId,
            String keyword);
    // // 2. Existing: Search products
    // List<Product> findByNameContainingIgnoreCase(String keyword);

    // List<Product> findByNameContainingIgnoreCaseAndCategoryCategoryId(String
    // keyword, long categoryId);

    List<Product> findByNameContainingIgnoreCaseAndStoreStoreId(String keyword, long storeId);

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

    // 5. Find by Store AND Category (Useful for filtering a specific restaurant's
    // menu)
    List<Product> findByStoreStoreIdAndCategoryCategoryId(Long storeId, Long categoryId);

    // Get products belonging to a specific Store AND specific Category
    List<Product> findByStoreStoreIdAndCategoryCategoryIdAndIsAvailableTrue(Long storeId, Long categoryId);

    // Get products belonging to a specific Store AND specific SubCategory
    List<Product> findByStoreStoreIdAndSubCategorySubcategoryIdAndIsAvailableTrue(Long storeId, Long subCategoryId);

    //
    List<Product> findByBasePriceLessThanEqualAndIsAvailableTrue(Double price);

    //
    List<Product> findTop10ByIsAvailableTrueOrderByProductIdDesc();

}