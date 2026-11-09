package com.deliveryapp.enums;

/**
 * The sections the customer app's home screen can show ({@code GET /api/home}).
 * Each type is one row in {@code home_sections}, where the admin sets its title, icon, position and on/off.
 *
 * <p>{@code showAllPath} is the existing paged list the app opens from the section's "show all" button,
 * or null when the section has no such button. The app adds {@code page} and {@code size} to it.
 *
 * <p>Released apps must skip types they don't know, so new types can be added without breaking them.
 */
public enum HomeSectionType {

    BANNERS("الإعلانات", null),
    STORES("المتاجر", "/api/catalog/stores/paged"),
    FREE_DELIVERY_STORES("توصيل مجاني", "/api/catalog/stores/paged/free-delivery"),
    FLASH_SALES("عروض الفلاش", null),
    /** Promotional offers (buy X get Y). */
    OFFERS("العروض", "/api/offers"),
    CATEGORIES("الفئات", null),
    TRENDING_PRODUCTS("الأكثر رواجاً", "/api/catalog/products/trending"),
    NEW_PRODUCTS("وصل حديثاً", "/api/catalog/products/new"),
    /** Products the customer pays at most the section's {@code maxPrice} for. */
    UNDER_PRICE_PRODUCTS("بأسعار مناسبة", "/api/catalog/products/price"),
    /** Products with a discounted price ({@code hasOffer}). */
    PRODUCT_OFFERS("الخصومات", "/api/catalog/products/offers");

    private final String defaultTitle;
    private final String showAllPath;

    HomeSectionType(String defaultTitle, String showAllPath) {
        this.defaultTitle = defaultTitle;
        this.showAllPath = showAllPath;
    }

    /** The title a new section starts with; the admin can change it. */
    public String getDefaultTitle() {
        return defaultTitle;
    }

    /** The existing paged list behind the "show all" button, or null when the section has none. */
    public String getShowAllPath() {
        return showAllPath;
    }

    /** Sections with "show all" show only their first items on the home; the others show everything. */
    public boolean hasShowAll() {
        return showAllPath != null;
    }
}
