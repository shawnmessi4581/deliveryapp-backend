package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.BatchSize;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Data
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "subcategory_id")
    private SubCategory subCategory;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_category_id")
    private StoreCategory storeCategory;

    private String name;
    private String description;
    private Double basePrice;
    //
    private Double usdPrice;

    // true = priced in USD. false = priced in SYP.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean isUsd = false;
    // 🟢 NEW: Offer Pricing Fields
    private Boolean hasOffer = false; // Is the discount active right now?
    private Double offerBasePrice; // Discounted price in SYP
    private Double offerUsdPrice; // Discounted price in USD
    //
    private String image;
    private Boolean isAvailable;
    private LocalDateTime createdAt;
    // NEW: Additional Images (Gallery)
    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    @BatchSize(size = 50)
    private List<String> images = new ArrayList<>();
    @ManyToMany
    @JoinTable(name = "product_colors", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "color_id"))
    @BatchSize(size = 50)
    private List<Color> colors;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    private List<ProductVariant> variants;
    private Boolean isTrending = false; // Default to false
    @Column(columnDefinition = "integer default 0")
    private Integer displayOrder = 0;

}