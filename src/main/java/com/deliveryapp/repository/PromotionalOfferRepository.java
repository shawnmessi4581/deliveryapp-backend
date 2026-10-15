package com.deliveryapp.repository;

import com.deliveryapp.entity.PromotionalOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionalOfferRepository extends JpaRepository<PromotionalOffer, Long> {

    // ── Admin / Public queries ────────────────────────────────────────────────

    /** All active offers that are currently within their date window (for public listing), newest first. */
    @Query("SELECT o FROM PromotionalOffer o " +
           "WHERE o.isActive = true " +
           "  AND (o.startDate IS NULL OR o.startDate <= :now) " +
           "  AND (o.endDate   IS NULL OR o.endDate   >= :now) " +
           "ORDER BY o.createdAt DESC, o.offerId DESC")
    Page<PromotionalOffer> findAllActiveAndValid(@Param("now") LocalDateTime now, Pageable pageable);

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

    // ── Offer lookup for a cart (used by the checkout offer engine) ──────────

    /**
     * All active & valid offers that any of the cart's products can take part in — as the main
     * product, a buy option or a reward option — latest first (ties between offers go to the latest).
     */
    @Query("SELECT DISTINCT o FROM PromotionalOffer o " +
           "LEFT JOIN o.buyProducts bp " +
           "LEFT JOIN o.rewardProducts rp " +
           "WHERE o.isActive = true " +
           "  AND (o.startDate IS NULL OR o.startDate <= :now) " +
           "  AND (o.endDate   IS NULL OR o.endDate   >= :now) " +
           "  AND (o.product.productId IN :productIds " +
           "       OR bp.productId IN :productIds " +
           "       OR rp.productId IN :productIds) " +
           "ORDER BY o.offerId DESC")
    List<PromotionalOffer> findActiveOffersForProducts(@Param("productIds") Collection<Long> productIds,
                                                        @Param("now") LocalDateTime now);

    /** Every offer that uses a product anywhere — cleaned up before the product is deleted. */
    @Query("SELECT DISTINCT o FROM PromotionalOffer o " +
           "LEFT JOIN o.buyProducts bp " +
           "LEFT JOIN o.rewardProducts rp " +
           "WHERE o.product.productId = :productId " +
           "   OR bp.productId = :productId " +
           "   OR rp.productId = :productId")
    List<PromotionalOffer> findAllUsingProduct(@Param("productId") Long productId);

    // ── Public: active offers for a specific store (includes admin/global offers tied to store products) ──

    @Query("SELECT o FROM PromotionalOffer o " +
           "WHERE o.product.store.storeId = :storeId " +
           "  AND o.isActive = true " +
           "  AND (o.startDate IS NULL OR o.startDate <= :now) " +
           "  AND (o.endDate   IS NULL OR o.endDate   >= :now) " +
           "ORDER BY o.createdAt DESC, o.offerId DESC")
    Page<PromotionalOffer> findActiveOffersForProductsInStore(@Param("storeId") Long storeId,
                                                               @Param("now") LocalDateTime now,
                                                               Pageable pageable);

    Optional<PromotionalOffer> findByOfferIdAndStoreStoreId(Long offerId, Long storeId);
}
