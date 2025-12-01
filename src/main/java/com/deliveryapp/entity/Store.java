package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
@Data
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "store_id")
    private Long storeId;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "subcategory_id")
    private SubCategory subCategory;

    private String name;
    private String description;
    private String logo;
    private String coverImage;
    private String phone;
    private String email;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private Integer totalOrders;
    private Double deliveryFeeKM;
    private Double minimumOrder;
    private String estimatedDeliveryTime;
    private Boolean isActive;
    private LocalDateTime createdAt;
}