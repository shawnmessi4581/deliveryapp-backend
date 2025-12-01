package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "product_variants")
@Data
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id")
    private Long variantId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private String variantType; // e.g., "Size", "Color"
    private String variantValue; // e.g., "Small", "Red"
    private Double priceAdjustment;
    private Boolean isAvailable;
}