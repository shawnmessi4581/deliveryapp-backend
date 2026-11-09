package com.deliveryapp.repository;

import com.deliveryapp.entity.Product;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

        // ── Fetch full entities by IDs (used by fetchPage helper) ──
        @Query("SELECT p FROM Product p WHERE p.productId IN :ids")
        List<Product> findByProductIdIn(@Param("ids") List<Long> ids);

        // ── All active products (sorted by displayOrder for catalog) ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isAvailable = true")
        Page<Long> findAllActiveProductIds(Pageable pageable);

        // ── By store ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndIsAvailableTrue(@Param("storeId") Long storeId, Pageable pageable);

        // ── Search global (no category filter) — relevance order, no displayOrder sort
        // ──
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

        @Query(value = "SELECT p.productId FROM Product p WHERE p.category.categoryId = :categoryId AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.category.categoryId = :categoryId AND p.isAvailable = true")
        Page<Long> findIdsByCategoryCategoryIdAndIsAvailableTrue(@Param("categoryId") Long categoryId,
                        Pageable pageable);

        // ── By subcategory ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId")
        Page<Long> findIdsBySubCategorySubcategoryId(@Param("subCategoryId") Long subcategoryId, Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true")
        Page<Long> findIdsBySubCategorySubcategoryIdAndIsAvailableTrue(@Param("subCategoryId") Long subcategoryId,
                        Pageable pageable);

        // ── By store + category ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId")
        Page<Long> findIdsByStoreStoreIdAndCategoryCategoryId(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.category.categoryId = :categoryId AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndCategoryCategoryIdAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        Pageable pageable);

        // ── By store + subcategory ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.subCategory.subcategoryId = :subCategoryId AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndSubCategorySubcategoryIdAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("subCategoryId") Long subCategoryId,
                        Pageable pageable);

        // ── Under price ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.basePrice <= :price AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.basePrice <= :price AND p.isAvailable = true")
        Page<Long> findIdsByBasePriceLessThanEqualAndIsAvailableTrue(@Param("price") Double price, Pageable pageable);

        // ── Newest products (sorted by productId DESC — intentionally no displayOrder)
        // ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.isAvailable = true ORDER BY p.productId DESC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isAvailable = true")
        Page<Long> findIdsByIsAvailableTrueOrderByProductIdDesc(Pageable pageable);

        // ── Trending ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.isTrending = true AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isTrending = true AND p.isAvailable = true")
        Page<Long> findIdsByIsTrendingTrueAndIsAvailableTrue(Pageable pageable);

        // ── Admin filtered (with optional name search, sorted by displayOrder) ──
        @Query(value = """
                        SELECT p.product_id
                        FROM products p
                        WHERE
                        (:storeId IS NULL OR p.store_id = :storeId)
                        AND (:categoryId IS NULL OR p.category_id = :categoryId)
                        AND (:subCategoryId IS NULL OR p.subcategory_id = :subCategoryId)
                        AND (:keyword IS NULL OR p.name ILIKE CONCAT('%', :keyword, '%'))
                        ORDER BY p.display_order ASC, p.product_id ASC
                        """, countQuery = """
                        SELECT COUNT(*)
                        FROM products p
                        WHERE
                        (:storeId IS NULL OR p.store_id = :storeId)
                        AND (:categoryId IS NULL OR p.category_id = :categoryId)
                        AND (:subCategoryId IS NULL OR p.subcategory_id = :subCategoryId)
                        AND (:keyword IS NULL OR p.name ILIKE CONCAT('%', :keyword, '%'))
                        """, nativeQuery = true)
        Page<Long> findAdminFilteredProductIds(
                        @Param("storeId") Long storeId,
                        @Param("categoryId") Long categoryId,
                        @Param("subCategoryId") Long subCategoryId,
                        @Param("keyword") String keyword,
                        Pageable pageable);

        // ── By store category ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.storeCategory.storeCategoryId = :storeCategoryId AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.storeCategory.storeCategoryId = :storeCategoryId AND p.isAvailable = true")
        Page<Long> findIdsByStoreCategoryStoreCategoryIdAndIsAvailableTrue(
                        @Param("storeCategoryId") Long storeCategoryId, Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.storeCategory.storeCategoryId = :storeCategoryId AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.storeCategory.storeCategoryId = :storeCategoryId AND p.isAvailable = true")
        Page<Long> findIdsByStoreStoreIdAndStoreCategoryStoreCategoryIdAndIsAvailableTrue(
                        @Param("storeId") Long storeId,
                        @Param("storeCategoryId") Long storeCategoryId,
                        Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId")
        Page<Long> findIdsByStoreStoreId(@Param("storeId") Long storeId, Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.store.storeId = :storeId AND p.storeCategory.storeCategoryId = :storeCategoryId ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.store.storeId = :storeId AND p.storeCategory.storeCategoryId = :storeCategoryId")
        Page<Long> findIdsByStoreStoreIdAndStoreCategoryStoreCategoryId(
                        @Param("storeId") Long storeId,
                        @Param("storeCategoryId") Long storeCategoryId,
                        Pageable pageable);

        // ── Has offer ──
        @Query(value = "SELECT p.productId FROM Product p WHERE p.hasOffer = true AND p.isAvailable = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.hasOffer = true AND p.isAvailable = true")
        Page<Long> findIdsByHasOfferTrueAndIsAvailableTrue(Pageable pageable);

        // ── Non-paginated (unchanged) ──
        List<Product> findBySubCategorySubcategoryId(Long subCategoryId);

        List<Product> findByStoreStoreId(Long storeId);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.isAvailable = true AND " +
                        "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isAvailable = true AND "
                                        +
                                        "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                                        "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<Long> findIdsByDeepSearchGlobal(String keyword, Pageable pageable);

        // ── Home sections: like the lists above, minus products of turned-off stores (checkout rejects them) ──

        @Query(value = "SELECT p.productId FROM Product p WHERE p.isTrending = true AND p.isAvailable = true AND p.store.isActive = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isTrending = true AND p.isAvailable = true AND p.store.isActive = true")
        Page<Long> findHomeTrendingIds(Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.isAvailable = true AND p.store.isActive = true ORDER BY p.productId DESC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isAvailable = true AND p.store.isActive = true")
        Page<Long> findHomeNewestIds(Pageable pageable);

        @Query(value = "SELECT p.productId FROM Product p WHERE p.hasOffer = true AND p.isAvailable = true AND p.store.isActive = true ORDER BY p.displayOrder ASC, p.productId ASC", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.hasOffer = true AND p.isAvailable = true AND p.store.isActive = true")
        Page<Long> findHomeDiscountedIds(Pageable pageable);

        /**
         * Products whose customer price — the price the app shows — is at most :maxPrice. Mirrors
         * PricingService.getCustomerFinalPrice, so keep the two in step:
         * <ul>
         *   <li>store price: the discounted price when hasOffer, else the regular one; USD × :usdRate,
         *       rounded up to the next 10</li>
         *   <li>customer price: store price + the store's markup %, rounded up to the next 10
         *       (no markup, or a price of 0 → unchanged)</li>
         * </ul>
         * :usdRate ≤ 0 (no exchange rate set) leaves USD products out, and so does a USD product without a
         * price (NULL stays NULL; the app couldn't price it either). Shared by the query and its count.
         */
        String HOME_UNDER_PRICE_SQL = """
                        FROM (
                            SELECT p.product_id, p.display_order,
                                   CASE WHEN p.is_usd THEN
                                            CASE WHEN :usdRate > 0 THEN
                                                CEIL(COALESCE(CASE WHEN p.has_offer THEN p.offer_usd_price END, p.usd_price) * :usdRate / 10) * 10
                                            END
                                        ELSE COALESCE(CASE WHEN p.has_offer THEN p.offer_base_price END, p.base_price, 0)
                                   END AS store_price,
                                   COALESCE(s.price_markup_percentage, 0) AS markup
                            FROM products p
                            JOIN stores s ON s.store_id = p.store_id
                            WHERE p.is_available = true AND s.is_active = true
                        ) q
                        WHERE CASE WHEN q.markup <= 0 OR q.store_price = 0 THEN q.store_price
                                   ELSE CEIL(CAST(q.store_price AS numeric) * (100 + q.markup) / 1000) * 10
                              END <= :maxPrice
                        """;

        @Query(value = "SELECT q.product_id " + HOME_UNDER_PRICE_SQL
                        + " ORDER BY q.display_order ASC, q.product_id ASC", countQuery = "SELECT COUNT(*) "
                                        + HOME_UNDER_PRICE_SQL, nativeQuery = true)
        Page<Long> findHomeIdsUnderCustomerPrice(@Param("maxPrice") double maxPrice,
                        @Param("usdRate") double usdRate,
                        Pageable pageable);
}