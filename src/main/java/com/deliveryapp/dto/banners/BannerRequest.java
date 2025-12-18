package com.deliveryapp.dto.banners;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat; // Import this

import java.time.LocalDateTime;

@Data
public class BannerRequest {
    private String title;
    private String linkType;
    private Long linkId;
    private Integer displayOrder;

    // Fix: Add this annotation to handle the String -> LocalDateTime conversion
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDate;

    private Boolean isActive;
}