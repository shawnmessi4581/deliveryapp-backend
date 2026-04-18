package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "store_categories")
@Data
public class StoreCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long storeCategoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private String name; // e.g., "Burgers", "Cold Drinks", "T-Shirts"

    private Boolean isActive = true;
    
    @Column(columnDefinition = "integer default 0")
    private Integer displayOrder = 0;
}