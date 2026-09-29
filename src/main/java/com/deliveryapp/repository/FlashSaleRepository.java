package com.deliveryapp.repository;

import com.deliveryapp.entity.FlashSale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    // ── Public queries ────────────────────────────────────────────────────────

    /**
     * All active flash sales currently within their time window.
     * Used for the public customer listing (shows all stores).
     */
    @Query("SELECT fs FROM FlashSale fs " +
           "WHERE fs.isActive = true " +
           "  AND fs.startDate        <= :now " +
           "  AND fs.countdownEndsAt  >= :now " +
           "ORDER BY fs.countdownEndsAt ASC")
    List<FlashSale> findAllActiveAndValid(@Param("now") LocalDateTime now);

    /**
     * Active flash sales for products / store of a specific store.
     * Matches flash sales where {@code store.storeId = storeId}
     * (vendor-created) OR where {@code applicableTo = STORE AND applicableId = storeId}.
     */
    @Query("SELECT fs FROM FlashSale fs " +
           "WHERE fs.isActive = true " +
           "  AND fs.startDate        <= :now " +
           "  AND fs.countdownEndsAt  >= :now " +
           "  AND (" +
           "      fs.store.storeId = :storeId " +
           "      OR (fs.applicableTo = 'STORE' AND fs.applicableId = :storeId)" +
           "  ) " +
           "ORDER BY fs.countdownEndsAt ASC")
    List<FlashSale> findActiveAndValidByStore(@Param("storeId") Long storeId,
                                              @Param("now") LocalDateTime now);

    // ── Admin queries ────────────────────────────────────────────────────────

    /** All flash sales paged — for admin dashboard. */
    Page<FlashSale> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ── Vendor queries ────────────────────────────────────────────────────────

    /** Flash sales scoped to a specific store — for vendor dashboard. */
    Page<FlashSale> findByStoreStoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);

    Optional<FlashSale> findByFlashSaleIdAndStoreStoreId(Long flashSaleId, Long storeId);

    // ── Expiry cleanup (used by @Scheduled job) ───────────────────────────────

    /**
     * Finds all flash sales whose countdown has ended but are still marked active.
     * Used by the scheduled deactivation job.
     */
    @Query("SELECT fs FROM FlashSale fs " +
           "WHERE fs.isActive = true AND fs.countdownEndsAt < :now")
    List<FlashSale> findExpiredActiveFlashSales(@Param("now") LocalDateTime now);

    /** Check for duplicate coupon code (case-insensitive). */
    boolean existsByCouponCodeIgnoreCase(String couponCode);
}
