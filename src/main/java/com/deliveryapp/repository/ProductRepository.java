package com.deliveryapp.repository;

import com.deliveryapp.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

        // Note: True random sorting is generally not compatible with standard
        // pagination,
        // because page 2 might show items from page 1 as the random order reshuffles.
        // If you need random products, it's often better to just return a standard Page
        // or use a custom native query that doesn't use Pageable.
        // For now, replacing the random query with a standard paged query for active
        // products:
        @Query("SELECT p FROM Product p WHERE p.isAvailable = true")
        Page<Product> findAllActiveProducts(Pageable pageable);

        // 1. Existing: Get menu for a specific store
        Page<Product> findByStoreStoreIdAndIsAvailableTrue(Long storeId, Pageable pageable);

        // 1. Search Global (Category ID = 0)
        Page<Product> findByNameContainingIgnoreCaseAndIsAvailableTrue(String keyword, Pageable pageable);

        // 2. Search Specific Category
        Page<Product> findByCategoryCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(Long categoryId,
                        String keyword,
                        Pageable pageable);

        // Search in Store
        // 6. Search inside specific Store (THIS FIXES YOUR ERROR)
        Page<Product> findByStoreStoreIdAndNameContainingIgnoreCaseAndIsAvailableTrue(Long storeId, String keyword,
                        Pageable pageable);
        // --- NEW METHODS ADDED BELOW ---

        // 3. Find by Category
        Page<Product> findByCategoryCategoryId(Long categoryId, Pageable pageable);

        // Variation: Find by Category only if available
        Page<Product> findByCategoryCategoryIdAndIsAvailableTrue(Long categoryId, Pageable pageable);

        // 4. Find by SubCategory
        Page<Product> findBySubCategorySubcategoryId(Long subcategoryId, Pageable pageable);

        // Variation: Find by SubCategory only if available
        Page<Product> findBySubCategorySubcategoryIdAndIsAvailableTrue(Long subcategoryId, Pageable pageable);

        // 5. Find by Store AND Category (Useful for filtering a specific restaurant's
        // menu)
        Page<Product> findByStoreStoreIdAndCategoryCategoryId(Long storeId, Long categoryId, Pageable pageable);

        // Get products belonging to a specific Store AND specific Category
        Page<Product> findByStoreStoreIdAndCategoryCategoryIdAndIsAvailableTrue(Long storeId, Long categoryId,
                        Pageable pageable);

        // Get products belonging to a specific Store AND specific SubCategory
        Page<Product> findByStoreStoreIdAndSubCategorySubcategoryIdAndIsAvailableTrue(Long storeId, Long subCategoryId,
                        Pageable pageable);

        // Under Price
        Page<Product> findByBasePriceLessThanEqualAndIsAvailableTrue(Double price, Pageable pageable);

        // Newest Products (Order by Product ID Descending)
        // Note: Instead of Top10, we use Pageable to control the limit dynamically
        Page<Product> findByIsAvailableTrueOrderByProductIdDesc(Pageable pageable);

        // Trending
        Page<Product> findByIsTrendingTrueAndIsAvailableTrue(Pageable pageable);

        @Query("SELECT p FROM Product p WHERE " +
                        "(:storeId IS NULL OR p.store.storeId = :storeId) AND " +
                        "(:categoryId IS NULL OR p.category.categoryId = :categoryId) AND " +
                        "(:subCategoryId IS NULL OR p.subCategory.subcategoryId = :subCategoryId)")
        Page<Product> findAdminFilteredProducts(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        @Param("subCategoryId") Long subCategoryId,
                        Pageable pageable);
}