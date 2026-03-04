package com.deliveryapp.dto.banners;

import com.deliveryapp.dto.catalog.ProductResponse;
import com.deliveryapp.dto.catalog.StoreResponse;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BannerResponse {
    private Long bannerId;
    private String title;
    private String imageUrl;
    private String linkType;
    private Long linkId;

    // NEW FIELDS: Rich Data
    private String externalUrl;
    private StoreResponse store; // Populated if linkType == "store"
    private ProductResponse product; // Populated if linkType == "product"

    private Integer displayOrder;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean isActive;
}