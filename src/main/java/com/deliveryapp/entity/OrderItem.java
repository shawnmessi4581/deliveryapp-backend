package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "order_items")
@Data
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @ManyToOne // <--- This annotation was missing!
    @JoinColumn(name = "selected_color_id") // This creates a Foreign Key in DB
    private Color selectedColor;

    private String productName; // Snapshot of name at time of order
    private String variantDetails;
    private Integer quantity;
    private Double unitPrice;  // What the customer pays per unit (store markup included)
    private Double totalPrice; // unitPrice × quantity
    private String notes;

    // 💹 The store's own price (before the admin's markup) — what the store is paid on.
    // Null on orders placed before markups existed: those use the customer price.
    private Double storeUnitPrice;
    private Double storeTotalPrice;

    // ── Promotional Offer tracking ────────────────────────────────────────────
    /** ID of the PromotionalOffer applied to this line-item, or null if none. */
    private Long appliedOfferId;

    /** Total SYP discount awarded by the promotional offer for this line-item. Defaults to 0. */
    @Column(columnDefinition = "numeric(15,2) default 0")
    private Double offerDiscountAmount = 0.0;

    /**
     * True when the applied offer was created by the store's vendor, so its discount is
     * deducted from the store payout. False = admin offer, paid by the platform.
     */
    @Column(columnDefinition = "boolean default false")
    private Boolean offerFundedByStore = false;

    /**
     * The same offer discount at the store's own price (same ratio as store price / customer price).
     * This is what the store pays when {@code offerFundedByStore}; the platform absorbs the rest.
     */
    private Double storeOfferDiscountAmount;

    /** Line total after the promotional-offer discount (what the customer pays for this line, before coupons). */
    public double getNetTotalPrice() {
        double total = totalPrice != null ? totalPrice : 0.0;
        double discount = offerDiscountAmount != null ? offerDiscountAmount : 0.0;
        return Math.max(total - discount, 0.0);
    }

    // ── Store-side amounts, falling back to the customer amounts on pre-markup orders ──

    public double getEffectiveStoreUnitPrice() {
        return storeUnitPrice != null ? storeUnitPrice : (unitPrice != null ? unitPrice : 0.0);
    }

    public double getEffectiveStoreTotalPrice() {
        return storeTotalPrice != null ? storeTotalPrice : (totalPrice != null ? totalPrice : 0.0);
    }

    public double getEffectiveStoreOfferDiscount() {
        if (storeOfferDiscountAmount != null) return storeOfferDiscountAmount;
        return offerDiscountAmount != null ? offerDiscountAmount : 0.0;
    }

    /** Store-price line total after the offer discount. */
    public double getStoreNetTotalPrice() {
        return Math.max(getEffectiveStoreTotalPrice() - getEffectiveStoreOfferDiscount(), 0.0);
    }
}