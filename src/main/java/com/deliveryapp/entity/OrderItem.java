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
    private Double unitPrice;
    private Double totalPrice;
    private String notes;

    // ── Promotional Offer tracking ────────────────────────────────────────────
    /** ID of the PromotionalOffer applied to this line-item, or null if none. */
    private Long appliedOfferId;

    /** Total SYP discount awarded by the promotional offer for this line-item. Defaults to 0. */
    @Column(columnDefinition = "numeric(15,2) default 0")
    private Double offerDiscountAmount = 0.0;
}