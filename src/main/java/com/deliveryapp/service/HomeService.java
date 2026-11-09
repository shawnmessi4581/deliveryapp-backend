package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.home.HomeResponse;
import com.deliveryapp.dto.home.HomeSectionResponse;
import com.deliveryapp.entity.HomeSection;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.enums.HomeSectionType;
import com.deliveryapp.mapper.banner.BannerMapper;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.repository.HomeSectionRepository;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the customer app's home screen ({@code GET /api/home}) from the sections the admin turned on.
 * Each section returns the same item shapes as the existing list endpoints, which stay unchanged.
 *
 * <p>Not transactional on purpose: each section reads on its own, so one failed query can't spoil the rest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    private final HomeSectionRepository homeSectionRepository;
    private final BannerService bannerService;
    private final StoreService storeService;
    private final CategoryService categoryService;
    private final FlashSaleService flashSaleService;
    private final PromotionalOfferService offerService;
    private final ProductService productService;
    private final ExchangeRateService exchangeRateService;
    private final OrderCalculationService orderCalculationService;
    private final BannerMapper bannerMapper;
    private final CatalogMapper catalogMapper;
    private final UrlUtil urlUtil;

    /**
     * The sections that are on, in display order, each with its first items. Sections without items are
     * left out, and a section that fails is skipped (and logged) so the rest of the home still loads.
     * The location (optional) fills the stores' predicted delivery fee.
     */
    public HomeResponse getHome(Double userLat, Double userLng) {
        List<HomeSectionResponse> sections = new ArrayList<>();
        for (HomeSection section : homeSectionRepository.findByIsActiveTrueOrderByDisplayOrderAscHomeSectionIdAsc()) {
            try {
                SectionItems items = loadItems(section, userLat, userLng);
                if (!items.list().isEmpty()) {
                    sections.add(toResponse(section, items));
                }
            } catch (RuntimeException e) {
                log.error("Home section {} ({}) could not be loaded and was skipped",
                        section.getHomeSectionId(), section.getType(), e);
            }
        }
        return new HomeResponse(sections);
    }

    /** A section's items and how many there are in all, for "show all". */
    private record SectionItems(List<?> list, long total) {

        static SectionItems all(List<?> list) {
            return new SectionItems(list, list.size());
        }

        static SectionItems of(Page<?> page) {
            return new SectionItems(page.getContent(), page.getTotalElements());
        }
    }

    private SectionItems loadItems(HomeSection section, Double userLat, Double userLng) {
        int limit = itemLimitOf(section);
        Pageable firstPage = PageRequest.of(0, limit);
        return switch (section.getType()) {
            case BANNERS -> SectionItems.all(bannerService.getActiveBanners().stream()
                    .map(bannerMapper::toBannerResponse)
                    .toList());
            case STORES -> stores(storeService.getAllActiveStores(), limit, userLat, userLng);
            case FREE_DELIVERY_STORES -> stores(storeService.getFreeDeliveryStores(), limit, userLat, userLng);
            case FLASH_SALES -> SectionItems.all(flashSaleService.getPublicActive());
            case OFFERS -> SectionItems.of(offerService.getHomeOffers(firstPage));
            case CATEGORIES -> SectionItems.all(categoryService.getAllActiveCategories().stream()
                    .map(catalogMapper::toCategoryResponse)
                    .toList());
            case TRENDING_PRODUCTS -> products(productService.getHomeTrendingProducts(firstPage));
            case NEW_PRODUCTS -> products(productService.getHomeNewestProducts(firstPage));
            // The admin can't turn this section on without a price; nothing to show until then
            case UNDER_PRICE_PRODUCTS -> section.getMaxPrice() == null
                    ? SectionItems.all(List.of())
                    : products(productService.getHomeProductsUnderPrice(section.getMaxPrice(), usdRate(), firstPage));
            case PRODUCT_OFFERS -> products(productService.getHomeDiscountedProducts(firstPage));
        };
    }

    /**
     * Same order as GET /api/catalog/stores/paged (open first, then displayOrder), so "show all" starts
     * with the stores the home shows. Only the stores shown get a delivery fee.
     */
    private SectionItems stores(List<Store> stores, int limit, Double userLat, Double userLng) {
        List<Store> sorted = new ArrayList<>(stores);
        sorted.sort(Comparator
                .comparing((Store s) -> !catalogMapper.isStoreOpen(s))
                .thenComparing(s -> s.getDisplayOrder() != null ? s.getDisplayOrder() : Integer.MAX_VALUE)
                .thenComparing(Store::getStoreId));

        List<StoreResponse> shown = sorted.subList(0, Math.min(limit, sorted.size())).stream()
                .map(store -> toStoreCard(store, userLat, userLng))
                .toList();
        return new SectionItems(shown, sorted.size());
    }

    private StoreResponse toStoreCard(Store store, Double userLat, Double userLng) {
        StoreResponse dto = catalogMapper.toStoreResponse(store);
        // No location → leave the fee empty instead of showing a "free" 0, like the store lists
        if (userLat != null && userLng != null) {
            dto.setPredictedDeliveryFee(orderCalculationService.computeFeeForStore(store, userLat, userLng));
        }
        return dto;
    }

    private SectionItems products(Page<Product> page) {
        return new SectionItems(
                page.getContent().stream().map(catalogMapper::toProductResponse).toList(),
                page.getTotalElements());
    }

    /** The USD → SYP rate, or 0 when none is set yet — the under-price query then leaves USD products out. */
    private double usdRate() {
        try {
            Double rate = exchangeRateService.getCurrentRate();
            return rate != null ? rate : 0.0;
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    /** Sections with "show all" show their first items; the others (banners, flash sales, categories) everything. */
    private int itemLimitOf(HomeSection section) {
        Integer limit = section.getItemLimit();
        return limit != null && limit > 0
                ? Math.min(limit, HomeSectionService.MAX_ITEM_LIMIT)
                : HomeSectionService.DEFAULT_ITEM_LIMIT;
    }

    private HomeSectionResponse toResponse(HomeSection section, SectionItems items) {
        HomeSectionResponse dto = new HomeSectionResponse();
        dto.setSectionId(section.getHomeSectionId());
        dto.setType(section.getType());
        dto.setTitle(section.getTitle());
        dto.setIconUrl(urlUtil.getFullUrl(section.getIcon()));
        dto.setDisplayOrder(section.getDisplayOrder());
        dto.setShowAll(section.getType().hasShowAll());
        dto.setShowAllPath(showAllPath(section));
        dto.setHasMore(items.total() > items.list().size());
        dto.setTotalItems(items.total());
        dto.setMaxPrice(section.getMaxPrice());
        dto.setItems(items.list());
        return dto;
    }

    /** The under-price list is the old ?max= endpoint, so its path carries the admin's price. */
    private String showAllPath(HomeSection section) {
        String path = section.getType().getShowAllPath();
        if (path != null && section.getType() == HomeSectionType.UNDER_PRICE_PRODUCTS) {
            return path + "?max=" + BigDecimal.valueOf(section.getMaxPrice()).stripTrailingZeros().toPlainString();
        }
        return path;
    }
}
