package com.deliveryapp.dto.banners;


import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BannerResponse {
    private Long bannerId;
    private String title;
    private String imageUrl; // Mapped from 'image'
    private String linkType;
    private Long linkId;
    private Integer displayOrder;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean isActive;
}