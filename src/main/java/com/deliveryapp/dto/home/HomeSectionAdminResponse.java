package com.deliveryapp.dto.home;

import com.deliveryapp.enums.HomeSectionType;
import lombok.Data;

import java.time.LocalDateTime;

/** A home section's settings, as the admin dashboard edits them. */
@Data
public class HomeSectionAdminResponse {

    private Long sectionId;
    private HomeSectionType type;
    private String title;
    private String iconUrl;
    private Integer displayOrder;
    private Boolean isActive;

    /** True when the section has a "show all" button; only these sections use {@code itemLimit}. */
    private Boolean showAll;

    /** How many items the home shows; null for sections without "show all" (they show everything). */
    private Integer itemLimit;

    /** Under-price section only: the most a product may cost the customer, in SYP. */
    private Double maxPrice;

    private LocalDateTime updatedAt;
}
