package com.deliveryapp.dto.offer;

import com.deliveryapp.enums.OfferType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO used by both Admin and Vendor to create or update a {@link com.deliveryapp.entity.PromotionalOffer}.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>Products: send {@code buyProductIds} (+ optional {@code rewardProductIds}), all from one store.
 *       Older dashboards send just {@code productId}, used for both lists. On update, sending neither
 *       (or the offer's current main {@code productId}) keeps the current lists.</li>
 *   <li>{@code storeId} is injected server-side for Vendor requests — ignored if provided.</li>
 *   <li>{@code discountPercent} is required when {@code offerType = BUY_X_GET_Y_PERCENT_OFF}.</li>
 *   <li>{@code discountValue} is required when {@code offerType = BUY_X_GET_Y_FIXED_OFF}.</li>
 * </ul>
 */
@Data
public class OfferRequest {

    @NotBlank(message = "عنوان العرض مطلوب")
    private String title;

    private String description;

    @NotNull(message = "نوع العرض مطلوب")
    private OfferType offerType;

    /** Single-product offer (older dashboards): used as both lists when the lists are not sent. */
    private Long productId;

    /** The options the customer pays for to earn the deal. */
    private List<Long> buyProductIds;

    /** The products the customer chooses the reward from. Empty = the same as {@code buyProductIds}. */
    private List<Long> rewardProductIds;

    /** Optional: how many times the deal can apply in one order. Null = no limit. */
    @Min(value = 1, message = "عدد مرات تطبيق العرض يجب أن يكون 1 على الأقل")
    private Integer maxUsesPerOrder;

    /**
     * Number of units customer must buy.
     * Minimum value: 1.
     */
    @NotNull(message = "كمية الشراء مطلوبة")
    @Min(value = 1, message = "كمية الشراء يجب أن تكون 1 على الأقل")
    private Integer buyQuantity;

    /**
     * Number of rewarded (free / discounted) units.
     * Minimum value: 1.
     */
    @NotNull(message = "كمية الحصول مطلوبة")
    @Min(value = 1, message = "كمية الحصول يجب أن تكون 1 على الأقل")
    private Integer getQuantity;

    /**
     * Discount percentage for {@link OfferType#BUY_X_GET_Y_PERCENT_OFF}.
     * Must be in range (0, 100].
     */
    private Double discountPercent;

    /**
     * Fixed SYP discount per rewarded unit for {@link OfferType#BUY_X_GET_Y_FIXED_OFF}.
     */
    private Double discountValue;

    /**
     * Admin only — who pays the discount: true = the product's store (deducted from its payout),
     * false = the platform. Optional: defaults to the platform on create and keeps the current value
     * on update. Ignored for vendors: their offers are always paid by their own store.
     */
    private Boolean fundedByStore;

    // ── Scheduling ───────────────────────────────────────────────────────────

    /** Optional: when the offer becomes active. Null = immediate. */
    private LocalDateTime startDate;

    /** Optional: when the offer expires. Null = never expires. */
    private LocalDateTime endDate;

    private Boolean isActive = true;
}
