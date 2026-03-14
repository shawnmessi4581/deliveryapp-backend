package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "subcategories")
@Data
public class SubCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subcategory_id")
    private Long subcategoryId;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String name;
    private String icon;
    private Boolean isActive;
    @Column(columnDefinition = "integer default 0")
    private Integer displayOrder = 0;
}