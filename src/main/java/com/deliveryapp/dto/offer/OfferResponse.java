package com.deliveryapp.dto.offer;

import com.deliveryapp.enums.OfferType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Public-safe response DTO for a {@link com.deliveryapp.entity.PromotionalOffer}.
 * Returned to all callers: admin, vendor, and public customers.
 */
@Data
public class OfferResponse {

    private Long offerId;
    private String title;
    private String description;

    // ── Offer type & parameters ──────────────────────────────────────────────
    private OfferType offerType;
    private Integer buyQuantity;
    private Integer getQuantity;
    private Double discountPercent;  // null unless BUY_X_GET_Y_PERCENT_OFF
    private Double discountValue;    // null unless BUY_X_GET_Y_FIXED_OFF

    // ── Human-readable summary ───────────────────────────────────────────────
    /** E.g. "اشتر 1 واحصل على 1 مجاناً" — generated server-side. */
    private String summary;

    // ── Product snapshot ─────────────────────────────────────────────────────
    private Long productId;
    private String productName;
    private String productImage;

    // ── Store snapshot ───────────────────────────────────────────────────────
    private Long storeId;    // store of the offer's product
    private String storeName;

    /** True = the store pays (deducted from its payout); false = the platform pays. */
    private Boolean fundedByStore;

    /** True = created by the store's vendor, who can edit it. Admin offers are read-only for vendors. */
    private Boolean createdByVendor;

    // ── Scheduling & status ──────────────────────────────────────────────────
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean isActive;

    // ── Audit ────────────────────────────────────────────────────────────────
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
