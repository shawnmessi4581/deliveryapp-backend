package com.deliveryapp.dto.order;

import com.deliveryapp.enums.OrderStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VendorOrderResponse {
    private Long orderId;
    private String orderNumber;
    private OrderStatus status;
    private LocalDateTime createdAt;

    // Minimal Customer Info
    private String customerName;

    // Notes
    private String orderNote;

    // Vendor's Specific Financials
    private Double storeSubtotal; // Total of ONLY their items (full price)
    private Double storeDiscountAmount; // Discounts the store pays for (its own offers / flash sale)
    private Double commissionAmount; // App's cut (on the amount after store discounts)
    private Double storePayout; // What the driver pays the store in cash

    // ONLY the items that belong to this specific store
    private List<OrderItemResponse> items;
}