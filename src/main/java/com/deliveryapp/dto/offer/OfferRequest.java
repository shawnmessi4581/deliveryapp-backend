package com.deliveryapp.dto.offer;

import com.deliveryapp.enums.OfferType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request DTO used by both Admin and Vendor to create or update a {@link com.deliveryapp.entity.PromotionalOffer}.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>{@code productId} is always required.</li>
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

    /** Product the offer is attached to. */
    @NotNull(message = "معرّف المنتج مطلوب")
    private Long productId;

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

    // ── Scheduling ───────────────────────────────────────────────────────────

    /** Optional: when the offer becomes active. Null = immediate. */
    private LocalDateTime startDate;

    /** Optional: when the offer expires. Null = never expires. */
    private LocalDateTime endDate;

    private Boolean isActive = true;
}
