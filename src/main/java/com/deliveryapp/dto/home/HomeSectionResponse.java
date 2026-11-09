package com.deliveryapp.dto.home;

import com.deliveryapp.enums.HomeSectionType;
import lombok.Data;

import java.util.List;

/**
 * One home screen section with its items. The items have the same shape as the existing list endpoints,
 * picked by {@code type}:
 * <ul>
 *   <li>BANNERS → BannerResponse</li>
 *   <li>STORES, FREE_DELIVERY_STORES → StoreResponse</li>
 *   <li>FLASH_SALES → FlashSaleResponse</li>
 *   <li>OFFERS → OfferResponse</li>
 *   <li>CATEGORIES → CategoryResponse</li>
 *   <li>TRENDING_PRODUCTS, NEW_PRODUCTS, UNDER_PRICE_PRODUCTS, PRODUCT_OFFERS → ProductResponse</li>
 * </ul>
 */
@Data
public class HomeSectionResponse {

    private Long sectionId;
    private HomeSectionType type;
    private String title;
    private String iconUrl;
    private Integer displayOrder;

    /** True for the stores, products and offers sections, which have a "show all" button. */
    private Boolean showAll;

    /**
     * The existing paged list "show all" opens, e.g. /api/catalog/products/trending. Add page and size
     * (and userLat / userLng for stores). Null when the section has no "show all".
     */
    private String showAllPath;

    /** More items exist than the ones in {@code items}. */
    private Boolean hasMore;
    private Long totalItems;

    /** Under-price section only: the price limit set by the admin, in SYP. */
    private Double maxPrice;

    private List<?> items;
}
