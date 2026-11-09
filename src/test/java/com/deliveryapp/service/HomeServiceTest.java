package com.deliveryapp.service;

import com.deliveryapp.dto.banners.BannerResponse;
import com.deliveryapp.dto.catalog.ProductResponse;
import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.flashsale.FlashSaleResponse;
import com.deliveryapp.dto.home.HomeResponse;
import com.deliveryapp.dto.home.HomeSectionResponse;
import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.entity.Banner;
import com.deliveryapp.entity.Category;
import com.deliveryapp.entity.HomeSection;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.enums.HomeSectionType;
import com.deliveryapp.mapper.banner.BannerMapper;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.repository.HomeSectionRepository;
import com.deliveryapp.util.UrlUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.LongStream;

import static com.deliveryapp.enums.HomeSectionType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock private HomeSectionRepository homeSectionRepository;
    @Mock private BannerService bannerService;
    @Mock private StoreService storeService;
    @Mock private CategoryService categoryService;
    @Mock private FlashSaleService flashSaleService;
    @Mock private PromotionalOfferService offerService;
    @Mock private ProductService productService;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private OrderCalculationService orderCalculationService;
    @Mock private BannerMapper bannerMapper;
    @Mock private PricingService pricingService;

    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(homeSectionRepository, bannerService, storeService, categoryService,
                flashSaleService, offerService, productService, exchangeRateService, orderCalculationService,
                bannerMapper, new CatalogMapper(new UrlUtil(), pricingService), new UrlUtil());
    }

    @Test
    void sectionsKeepTheAdminsOrder_andEmptyOnesAreLeftOut() {
        HomeSection categories = section(1, CATEGORIES, 1);
        categories.setTitle("تسوق حسب الفئة");
        categories.setIcon("/uploads/home-sections/grid.png");
        givenSections(categories, section(2, FLASH_SALES, 2), section(3, STORES, 3));
        when(categoryService.getAllActiveCategories()).thenReturn(List.of(category(1)));
        when(flashSaleService.getPublicActive()).thenReturn(List.of());
        when(storeService.getAllActiveStores()).thenReturn(List.of(store(1, 1)));

        HomeResponse home = homeService.getHome(null, null);

        assertEquals(List.of(CATEGORIES, STORES), types(home));
        HomeSectionResponse first = home.getSections().get(0);
        assertEquals(1L, first.getSectionId());
        assertEquals("تسوق حسب الفئة", first.getTitle());
        assertEquals("/uploads/home-sections/grid.png", first.getIconUrl());
        assertEquals(1, first.getDisplayOrder());
    }

    @Test
    void aFailingSection_isSkipped_andTheRestStillLoads() {
        givenSections(section(1, TRENDING_PRODUCTS, 1), section(2, CATEGORIES, 2));
        when(productService.getHomeTrendingProducts(any())).thenThrow(new IllegalStateException("database hiccup"));
        when(categoryService.getAllActiveCategories()).thenReturn(List.of(category(1)));

        assertEquals(List.of(CATEGORIES), types(homeService.getHome(null, null)));
    }

    @Test
    void stores_openFirst_cutToTheItemLimit_feeOnlyForTheStoresShown() {
        HomeSection stores = section(1, STORES, 1);
        stores.setItemLimit(2);
        givenSections(stores);
        // Opens in an hour → closed now (also correct when the window crosses midnight)
        Store closed = store(1, 1);
        closed.setOpeningTime(LocalTime.now().plusHours(1));
        closed.setClosingTime(LocalTime.now().plusHours(2));
        when(storeService.getAllActiveStores()).thenReturn(List.of(closed, store(2, 2), store(3, 3)));
        when(orderCalculationService.computeFeeForStore(any(Store.class), eq(33.5), eq(36.3))).thenReturn(5850.0);

        HomeSectionResponse section = homeService.getHome(33.5, 36.3).getSections().get(0);

        assertEquals(List.of(2L, 3L), section.getItems().stream().map(s -> ((StoreResponse) s).getStoreId()).toList());
        assertEquals(5850.0, ((StoreResponse) section.getItems().get(0)).getPredictedDeliveryFee());
        verify(orderCalculationService, never()).computeFeeForStore(closed, 33.5, 36.3);
        assertEquals(3L, section.getTotalItems());
        assertTrue(section.getHasMore());
        assertTrue(section.getShowAll());
        assertEquals("/api/catalog/stores/paged", section.getShowAllPath());
    }

    @Test
    void stores_withoutLocation_leaveTheFeeEmpty() {
        givenSections(section(1, FREE_DELIVERY_STORES, 1));
        when(storeService.getFreeDeliveryStores()).thenReturn(List.of(store(1, 1)));

        HomeSectionResponse section = homeService.getHome(null, null).getSections().get(0);

        assertNull(((StoreResponse) section.getItems().get(0)).getPredictedDeliveryFee());
        verifyNoInteractions(orderCalculationService);
        assertFalse(section.getHasMore());
        assertEquals("/api/catalog/stores/paged/free-delivery", section.getShowAllPath());
    }

    @Test
    void productSections_useTheHomeQueries_withTheSectionsItemLimit() {
        HomeSection trending = section(1, TRENDING_PRODUCTS, 1);
        trending.setItemLimit(5);
        givenSections(trending, section(2, NEW_PRODUCTS, 2), section(3, PRODUCT_OFFERS, 3));
        when(productService.getHomeTrendingProducts(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(product(1)), PageRequest.of(0, 5), 12));
        when(productService.getHomeNewestProducts(PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(product(2)), PageRequest.of(0, 10), 1));
        when(productService.getHomeDiscountedProducts(PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(product(3)), PageRequest.of(0, 10), 1));

        List<HomeSectionResponse> sections = homeService.getHome(null, null).getSections();

        assertEquals(List.of(TRENDING_PRODUCTS, NEW_PRODUCTS, PRODUCT_OFFERS),
                sections.stream().map(HomeSectionResponse::getType).toList());
        HomeSectionResponse first = sections.get(0);
        assertEquals(1L, ((ProductResponse) first.getItems().get(0)).getProductId());
        assertEquals(12L, first.getTotalItems());
        assertTrue(first.getHasMore());
        assertEquals("/api/catalog/products/trending", first.getShowAllPath());
        assertFalse(sections.get(1).getHasMore());
        assertEquals("/api/catalog/products/new", sections.get(1).getShowAllPath());
        assertEquals("/api/catalog/products/offers", sections.get(2).getShowAllPath());
    }

    @Test
    void offers_useTheHomeQuery() {
        givenSections(section(1, OFFERS, 1));
        OfferResponse offer = new OfferResponse();
        offer.setOfferId(5L);
        when(offerService.getHomeOffers(PageRequest.of(0, 10))).thenReturn(new PageImpl<>(List.of(offer)));

        HomeSectionResponse section = homeService.getHome(null, null).getSections().get(0);

        assertEquals(List.of(offer), section.getItems());
        assertEquals("/api/offers", section.getShowAllPath());
    }

    @Test
    void underPrice_usesTheAdminsPriceAndTheRate_andShowAllCarriesThePrice() {
        HomeSection underPrice = section(1, UNDER_PRICE_PRODUCTS, 1);
        underPrice.setMaxPrice(10000.0);
        givenSections(underPrice);
        when(exchangeRateService.getCurrentRate()).thenReturn(14500.0);
        when(productService.getHomeProductsUnderPrice(10000.0, 14500.0, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(product(1))));

        HomeSectionResponse section = homeService.getHome(null, null).getSections().get(0);

        assertEquals(10000.0, section.getMaxPrice());
        assertEquals("/api/catalog/products/price?max=10000", section.getShowAllPath());
    }

    @Test
    void underPrice_withoutAnExchangeRate_leavesUsdProductsOut() {
        HomeSection underPrice = section(1, UNDER_PRICE_PRODUCTS, 1);
        underPrice.setMaxPrice(25000.5);
        givenSections(underPrice);
        when(exchangeRateService.getCurrentRate()).thenThrow(new RuntimeException("Exchange rate not set."));
        when(productService.getHomeProductsUnderPrice(25000.5, 0.0, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(product(1))));

        HomeSectionResponse section = homeService.getHome(null, null).getSections().get(0);

        assertEquals("/api/catalog/products/price?max=25000.5", section.getShowAllPath());
    }

    @Test
    void underPrice_withoutAPrice_isLeftOut_withoutQuerying() {
        givenSections(section(1, UNDER_PRICE_PRODUCTS, 1));

        assertTrue(homeService.getHome(null, null).getSections().isEmpty());
        verifyNoInteractions(productService, exchangeRateService);
    }

    @Test
    void sectionsWithoutShowAll_showEverything() {
        givenSections(section(1, BANNERS, 1), section(2, CATEGORIES, 2), section(3, FLASH_SALES, 3));
        when(bannerService.getActiveBanners()).thenReturn(List.of(new Banner(), new Banner()));
        when(bannerMapper.toBannerResponse(any(Banner.class))).thenReturn(new BannerResponse());
        when(categoryService.getAllActiveCategories())
                .thenReturn(LongStream.rangeClosed(1, 12).mapToObj(HomeServiceTest::category).toList());
        when(flashSaleService.getPublicActive()).thenReturn(List.of(new FlashSaleResponse()));

        List<HomeSectionResponse> sections = homeService.getHome(null, null).getSections();

        assertEquals(List.of(2, 12, 1), sections.stream().map(s -> s.getItems().size()).toList());
        for (HomeSectionResponse section : sections) {
            assertFalse(section.getShowAll());
            assertNull(section.getShowAllPath());
            assertFalse(section.getHasMore());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void givenSections(HomeSection... sections) {
        when(homeSectionRepository.findByIsActiveTrueOrderByDisplayOrderAscHomeSectionIdAsc())
                .thenReturn(List.of(sections));
    }

    private static List<HomeSectionType> types(HomeResponse home) {
        return home.getSections().stream().map(HomeSectionResponse::getType).toList();
    }

    private static HomeSection section(long id, HomeSectionType type, int displayOrder) {
        HomeSection section = new HomeSection();
        section.setHomeSectionId(id);
        section.setType(type);
        section.setTitle(type.getDefaultTitle());
        section.setDisplayOrder(displayOrder);
        section.setIsActive(true);
        section.setItemLimit(type.hasShowAll() ? 10 : null);
        return section;
    }

    private static Store store(long id, int displayOrder) {
        Store store = new Store();
        store.setStoreId(id);
        store.setName("Store " + id);
        store.setIsActive(true);
        store.setDisplayOrder(displayOrder);
        return store;
    }

    private static Product product(long id) {
        Product product = new Product();
        product.setProductId(id);
        product.setName("Product " + id);
        return product;
    }

    private static Category category(long id) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName("Category " + id);
        category.setIsActive(true);
        return category;
    }
}
