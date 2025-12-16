package com.deliveryapp.dto.order;

import com.deliveryapp.enums.OrderStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private Long orderId;
    private String orderNumber;

    // Store Info
    private Long storeId;
    private String storeName;

    // Driver Info (can be null if not yet assigned)
    private Long driverId;
    private String driverName;
    private String driverPhone;

    // Delivery Info
    private String deliveryAddress;
    private Double deliveryLatitude;
    private Double deliveryLongitude;

    // Status & Dates
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;

    // Financials
    private Double subtotal;
    private Double deliveryFee;
    private Double totalAmount;

    // Items
    private List<OrderItemResponse> items;
    // NEW: Add the customer details
    private OrderCustomerResponse customerDetails;
    // NEW FIELDS FOR COUPONS
    private Double discountAmount; // How much was saved
    private Long couponId;         // Which coupon was used (optional)

}