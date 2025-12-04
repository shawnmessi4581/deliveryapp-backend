package com.deliveryapp.entity;

import com.deliveryapp.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    private String orderNumber;

    @ManyToOne
    @JoinColumn(name = "user_id") // The customer
    private User user;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne
    @JoinColumn(name = "driver_id") // The driver
    private User driver;

    // Delivery Details
    private String deliveryAddress;
    private Double deliveryLatitude;
    private Double deliveryLongitude;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // Financials
    private Double subtotal;
    private Double deliveryFee;
    private Double totalAmount;


    private String selectedInstruction; // Text copy of the instruction


    // Coupon Info
    private Long couponId;
    private Double discountAmount; // Stored as Double to match your existing financial fields


    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems;
}