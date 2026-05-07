package com.deliveryapp.repository;

import com.deliveryapp.entity.Product;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

        // ── Fetch full entities by IDs (used by fetchPage helper) ──
        @Query("SELECT p FROM Product p WHERE p.productId IN :ids")
        List<Product> findByProductIdIn(@Param("ids") List<Long> ids);

        // ── All active products ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isAvailable = true")
        Page<Long> findAllActiveProductIds(Pageable pageable);

        // ── By store ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndIsAvailableTrue(@Param("storeId") Long storeId, Pageable pageable);

        // ── Search global (no category filter) ──
        @Query(value = "SELECT p.productId FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true")
        Page<Long> findIdsByNameContainingIgnoreCaseAndIsAvailableTrue(@Param("keyword") String keyword,
                        Pageable pageable);

        // ── Search with category filter ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.category.categoryId = :categoryId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.category.categoryId = :categoryId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true")
        Page<Long> findIdsByCategoryCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(
                        @Param("categoryId") Long categoryId,
                        @Param("keyword") String keyword,
                        Pageable pageable);

        // ── Search inside a specific store ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndNameContainingIgnoreCaseAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("keyword") String keyword,
                        Pageable pageable);

        // ── By category ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.category.categoryId = :categoryId", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.category.categoryId = :categoryId")
        Page<Long> findIdsByCategoryCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.category.categoryId = :categoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.category.categoryId = :categoryId AND p.isAvailable = true")
        Page<Long> findIdsByCategoryCategoryIdAndIsAvailableTrue(@Param("categoryId") Long categoryId,
                        Pageable pageable);

        // ── By subcategory ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId")
        Page<Long> findIdsBySubCategorySubcategoryId(@Param("subCategoryId") Long subcategoryId, Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true")
        Page<Long> findIdsBySubCategorySubcategoryIdAndIsAvailableTrue(@Param("subCategoryId") Long subcategoryId,
                        Pageable pageable);

        // ── By store + category ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId")
        Page<Long> findIdsByStoreStoreIdAndCategoryCategoryId(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndCategoryCategoryIdAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        Pageable pageable);

        // ── By store + subcategory ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndSubCategorySubcategoryIdAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("subCategoryId") Long subCategoryId,
                        Pageable pageable);

        // ── Under price ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.basePrice <= :price AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.basePrice <= :price AND p.isAvailable = true")
        Page<Long> findIdsByBasePriceLessThanEqualAndIsAvailableTrue(@Param("price") Double price, Pageable pageable);

        // ── Newest products ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.isAvailable = true ORDER BY p.productId DESC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isAvailable = true")
        Page<Long> findIdsByIsAvailableTrueOrderByProductIdDesc(Pageable pageable);

        // ── Trending ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.isTrending = true AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isTrending = true AND p.isAvailable = true")
        Page<Long> findIdsByIsTrendingTrueAndIsAvailableTrue(Pageable pageable);

        // ── Admin filtered ──
        @Query(value = "SELECT p.productId FROM Product p WHERE " +
                        "(:storeId IS NULL OR p.store.storeId = :storeId) AND " +
                        "(:categoryId IS NULL OR p.category.categoryId = :categoryId) AND " +
                        "(:subCategoryId IS NULL OR p.subCategory.subcategoryId = :subCategoryId)", countQuery = "SELECT COUNT(p) FROM Product p WHERE "
                                        +
                                        "(:storeId IS NULL OR p.store.storeId = :storeId) AND " +
                                        "(:categoryId IS NULL OR p.category.categoryId = :categoryId) AND " +
                                        "(:subCategoryId IS NULL OR p.subCategory.subcategoryId = :subCategoryId)")
        Page<Long> findAdminFilteredProductIds(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        @Param("subCategoryId") Long subCategoryId,
                        Pageable pageable);

        // ── By store category ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.storeCategory.storeCategoryId = :storeCategoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.storeCategory.storeCategoryId = :storeCategoryId AND p.isAvailable = true")
        Page<Long> findIdsByStoreCategoryStoreCategoryIdAndIsAvailableTrue(
                        @Param("storeCategoryId") Long storeCategoryId, Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.storeCategory.storeCategoryId = :storeCategoryId AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.storeCategory.storeCategoryId = :storeCategoryId AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndStoreCategoryStoreCategoryIdAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("storeCategoryId") Long storeCategoryId,
                        Pageable pageable);

        // ── Has offer ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.hasOffer = true AND p.isAvailable = true", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.hasOffer = true AND p.isAvailable = true")
        Page<Long> findIdsByHasOfferTrueAndIsAvailableTrue(Pageable pageable);

        // ── Non-paginated (unchanged) ──
        List<Product> findBySubCategorySubcategoryId(Long subCategoryId);
}