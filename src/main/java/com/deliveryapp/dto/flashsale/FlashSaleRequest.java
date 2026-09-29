package com.deliveryapp.dto.flashsale;

import com.deliveryapp.entity.Coupon;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request DTO for creating or updating a {@link com.deliveryapp.entity.FlashSale}.
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>{@code couponCode} — optional. If omitted, the service auto-generates {@code FLASH-XXXXXXXX}.</li>
 *   <li>{@code storeId} — ignored on Vendor endpoints (injected server-side). Optional on Admin endpoints
 *       (null = global flash sale not tied to a specific store).</li>
 *   <li>{@code applicableTo + applicableId} — for Admin: can scope to STORE / CATEGORY / SUBCATEGORY / PRODUCT.
 *       Vendor flash sales are always auto-scoped to {@code STORE} with their store ID.</li>
 * </ul>
 */
@Data
public class FlashSaleRequest {

    // ── Display ──────────────────────────────────────────────────────────────

    @NotBlank(message = "عنوان الفلاش سيل مطلوب")
    private String title;

    private String description;

    /** Optional banner image URL. */
    private String bannerImage;

    // ── Coupon params ────────────────────────────────────────────────────────

    /**
     * Optional custom coupon code.
     * If blank, the service auto-generates one in the format {@code FLASH-XXXXXXXX}.
     */
    private String couponCode;

    @NotNull(message = "نوع الخصم مطلوب")
    private Coupon.DiscountType discountType;

    @NotNull(message = "قيمة الخصم مطلوبة")
    @DecimalMin(value = "0.01", message = "قيمة الخصم يجب أن تكون أكبر من صفر")
    private BigDecimal discountValue;

    /** Minimum order value to be eligible. Null = no minimum. */
    private BigDecimal minOrderAmount;

    /** Maximum discount cap (useful for PERCENTAGE type). Null = uncapped. */
    private BigDecimal maxDiscountAmount;

    // ── Scope ────────────────────────────────────────────────────────────────

    /** Defaults to ALL. Vendor flash sales are always forced to STORE server-side. */
    private Coupon.ApplicableTo applicableTo = Coupon.ApplicableTo.ALL;

    /**
     * Entity ID matching {@code applicableTo}.
     * E.g. if applicableTo = PRODUCT, this is the productId.
     */
    private Long applicableId;

    // ── Usage limits ─────────────────────────────────────────────────────────

    /** Max total uses across all customers. Null = unlimited. */
    private Integer totalUsageLimit;

    /** Max uses per individual customer. Default 1. */
    private Integer maxUsagePerUser = 1;

    // ── Scheduling ───────────────────────────────────────────────────────────

    @NotNull(message = "تاريخ بداية الفلاش سيل مطلوب")
    private LocalDateTime startDate;

    @NotNull(message = "وقت انتهاء العد التنازلي مطلوب")
    private LocalDateTime countdownEndsAt;

    private Boolean isActive = true;
}
