package com.deliveryapp.dto.catalog;

import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
public class StoreRequest {
    private String name;
    private String description;
    private String phone;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double deliveryFeeKM;
    private Double minimumOrder;
    private String estimatedDeliveryTime; // e.g., "30-45 min"
    private Long categoryId;
    private List<Long> subCategoryIds;
    private Boolean isBusy;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime openingTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime closingTime;
    //
    private Integer displayOrder;
    //
    private Double commissionPercentage;
    private Double minimumDeliveryFee;

    // 💹 Admin only: % added to this store's prices for customers (0 = none)
    private Double priceMarkupPercentage;

    // 🔔 Telegram: Chat/Group/Channel ID for order notifications
    private String telegramChatId;

    // 🆓 Free Delivery toggle
    private Boolean freeDelivery;

    // 🛒 Free Delivery Threshold (e.g. 10000.0 SYP) — null = disabled
    private Double freeDeliveryThreshold;

    // 🔘 Toggle threshold on/off without losing the saved value
    private Boolean freeDeliveryThresholdEnabled;
}