package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "categories")
@Data
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    private String name;
    private String icon;
    private Boolean isActive;
    @Column(columnDefinition = "integer default 0")
    private Integer displayOrder = 0;
}