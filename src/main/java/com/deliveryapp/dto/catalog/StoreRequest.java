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

    // 🔔 Telegram: Chat/Group/Channel ID for order notifications
    private String telegramChatId;

}