package com.deliveryapp.repository;

import com.deliveryapp.entity.PromotionalOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionalOfferRepository extends JpaRepository<PromotionalOffer, Long> {

    // ── Admin / Public queries ────────────────────────────────────────────────

    /** All active offers that are currently within their date window (for public listing). */
    @Query("SELECT o FROM PromotionalOffer o " +
           "WHERE o.isActive = true " +
           "  AND (o.startDate IS NULL OR o.startDate <= :now) " +
           "  AND (o.endDate   IS NULL OR o.endDate   >= :now)")
    List<PromotionalOffer> findAllActiveAndValid(@Param("now") LocalDateTime now);

    /** Paged list of ALL offers — for admin dashboard. */
    Page<PromotionalOffer> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ── Vendor-scoped queries ────────────────────────────────────────────────

    /** Paged list of offers owned by a specific store — for vendor dashboard. */
    Page<PromotionalOffer> findByStoreStoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);

    /** Active & valid offers for a specific store (vendor-scoped public display). */
    @Query("SELECT o FROM PromotionalOffer o " +
           "WHERE o.store.storeId = :storeId " +
           "  AND o.isActive = true " +
           "  AND (o.startDate IS NULL OR o.startDate <= :now) " +
           "  AND (o.endDate   IS NULL OR o.endDate   >= :now)")
    List<PromotionalOffer> findActiveAndValidByStore(@Param("storeId") Long storeId,
                                                      @Param("now") LocalDateTime now);

    // ── Offer lookup per product (used by OrderService engine) ───────────────

    /**
     * All active & valid offers for a product at checkout time, latest first.
     * The engine evaluates every candidate and applies the one worth the most to the customer
     * (ties go to the latest).
     */
    @Query("SELECT o FROM PromotionalOffer o " +
           "WHERE o.product.productId = :productId " +
           "  AND o.isActive = true " +
           "  AND (o.startDate IS NULL OR o.startDate <= :now) " +
           "  AND (o.endDate   IS NULL OR o.endDate   >= :now) " +
           "ORDER BY o.offerId DESC")
    List<PromotionalOffer> findActiveOffersForProduct(@Param("productId") Long productId,
                                                       @Param("now") LocalDateTime now);

    // ── Public: active offers for a specific store (includes admin/global offers tied to store products) ──

    @Query("SELECT o FROM PromotionalOffer o " +
           "WHERE o.product.store.storeId = :storeId " +
           "  AND o.isActive = true " +
           "  AND (o.startDate IS NULL OR o.startDate <= :now) " +
           "  AND (o.endDate   IS NULL OR o.endDate   >= :now)")
    List<PromotionalOffer> findActiveOffersForProductsInStore(@Param("storeId") Long storeId,
                                                               @Param("now") LocalDateTime now);

    Optional<PromotionalOffer> findByOfferIdAndStoreStoreId(Long offerId, Long storeId);
}
