package com.deliveryapp.entity;

import com.deliveryapp.enums.HomeSectionType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One section of the customer app's home screen (banners, stores, trending products, ...).
 * There is one row per {@link HomeSectionType}, created at startup; the admin edits them.
 */
@Entity
@Table(name = "home_sections")
@Data
public class HomeSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "home_section_id")
    private Long homeSectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private HomeSectionType type;

    @Column(nullable = false, length = 150)
    private String title;

    /** Uploaded PNG, e.g. /uploads/home-sections/uuid.png. Null = no icon. */
    private String icon;

    /** Position on the home screen, smallest first. */
    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean isActive;

    /** How many items the home shows. Only sections with "show all" use it; the rest show everything. */
    private Integer itemLimit;

    /** Under-price section only: the most a product may cost the customer, in SYP. */
    private Double maxPrice;

    private LocalDateTime updatedAt;
}
