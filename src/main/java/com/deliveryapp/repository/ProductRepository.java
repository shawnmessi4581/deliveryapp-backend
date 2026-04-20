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

        // ── All active products ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.isAvailable = true")
        Page<Product> findAllActiveProducts(Pageable pageable);

        // ── By store ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.store.storeId = :storeId AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.store.storeId = :storeId AND p.isAvailable = true")
        Page<Product> findByStoreStoreIdAndIsAvailableTrue(@Param("storeId") Long storeId, Pageable pageable);

        // ── Search global (no category filter) ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true")
        Page<Product> findByNameContainingIgnoreCaseAndIsAvailableTrue(@Param("keyword") String keyword,
                        Pageable pageable);

        // ── Search with category filter ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.category.categoryId = :categoryId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.category.categoryId = :categoryId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true")
        Page<Product> findByCategoryCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(
                        @Param("categoryId") Long categoryId,
                        @Param("keyword") String keyword,
                        Pageable pageable);

        // ── Search inside a specific store ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.store.storeId = :storeId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.store.storeId = :storeId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true")
        Page<Product> findByStoreStoreIdAndNameContainingIgnoreCaseAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("keyword") String keyword,
                        Pageable pageable);

        // ── By category ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.category.categoryId = :categoryId", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.category.categoryId = :categoryId")
        Page<Product> findByCategoryCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.category.categoryId = :categoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.category.categoryId = :categoryId AND p.isAvailable = true")
        Page<Product> findByCategoryCategoryIdAndIsAvailableTrue(@Param("categoryId") Long categoryId,
                        Pageable pageable);

        // ── By subcategory ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId")
        Page<Product> findBySubCategorySubcategoryId(@Param("subCategoryId") Long subcategoryId, Pageable pageable);

        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true")
        Page<Product> findBySubCategorySubcategoryIdAndIsAvailableTrue(@Param("subCategoryId") Long subcategoryId,
                        Pageable pageable);

        // ── By store + category ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId")
        Page<Product> findByStoreStoreIdAndCategoryCategoryId(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        Pageable pageable);

        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId AND p.isAvailable = true")
        Page<Product> findByStoreStoreIdAndCategoryCategoryIdAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        Pageable pageable);

        // ── By store + subcategory ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.store.storeId = :storeId AND p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.store.storeId = :storeId AND p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true")
        Page<Product> findByStoreStoreIdAndSubCategorySubcategoryIdAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("subCategoryId") Long subCategoryId,
                        Pageable pageable);

        // ── Under price ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.basePrice <= :price AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.basePrice <= :price AND p.isAvailable = true")
        Page<Product> findByBasePriceLessThanEqualAndIsAvailableTrue(@Param("price") Double price, Pageable pageable);

        // ── Newest products ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.isAvailable = true ORDER BY p.productId DESC", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.isAvailable = true")
        Page<Product> findByIsAvailableTrueOrderByProductIdDesc(Pageable pageable);

        // ── Trending ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE p.isTrending = true AND p.isAvailable = true", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE p.isTrending = true AND p.isAvailable = true")
        Page<Product> findByIsTrendingTrueAndIsAvailableTrue(Pageable pageable);

        // ── Admin filtered ──
        @Query(value = "SELECT DISTINCT p FROM Product p WHERE " +
                        "(:storeId IS NULL OR p.store.storeId = :storeId) AND " +
                        "(:categoryId IS NULL OR p.category.categoryId = :categoryId) AND " +
                        "(:subCategoryId IS NULL OR p.subCategory.subcategoryId = :subCategoryId)", countQuery = "SELECT COUNT(DISTINCT p) FROM Product p WHERE "
                                        +
                                        "(:storeId IS NULL OR p.store.storeId = :storeId) AND " +
                                        "(:categoryId IS NULL OR p.category.categoryId = :categoryId) AND " +
                                        "(:subCategoryId IS NULL OR p.subCategory.subcategoryId = :subCategoryId)")
        Page<Product> findAdminFilteredProducts(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        @Param("subCategoryId") Long subCategoryId,
                        Pageable pageable);

        Page<Product> findByStoreCategoryStoreCategoryIdAndIsAvailableTrue(Long storeCategoryId, Pageable pageable);

        Page<Product> findByStoreStoreIdAndStoreCategoryStoreCategoryIdAndIsAvailableTrue(
                        Long storeId, Long storeCategoryId, Pageable pageable);

        Page<Product> findByHasOfferTrueAndIsAvailableTrue(Pageable pageable);

}