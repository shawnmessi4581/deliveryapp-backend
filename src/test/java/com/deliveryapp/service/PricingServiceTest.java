package com.deliveryapp.service;

import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.ProductVariant;
import com.deliveryapp.entity.Store;
import com.deliveryapp.util.MathUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock private ExchangeRateService exchangeRateService;

    private PricingService service;
    private Store store;

    @BeforeEach
    void setUp() {
        service = new PricingService(exchangeRateService, new MathUtil());
        store = new Store();
        store.setPriceMarkupPercentage(10.0);
    }

    @Test
    void markup_isAddedAndRoundedUpToTheNearestTen() {
        // Exact math: plain doubles give 11,000.000000000002 and would round to 11,010
        assertEquals(11_000.0, service.getCustomerFinalPrice(product(10_000)));
        assertEquals(13_580.0, service.getCustomerFinalPrice(product(12_345))); // 13,579.5 → 13,580
    }

    @Test
    void storeWithoutMarkup_keepsItsExactPrice() {
        store.setPriceMarkupPercentage(0.0);

        assertEquals(12_345.0, service.getCustomerFinalPrice(product(12_345)));
    }

    @Test
    void storePrice_isUnchangedForPayouts() {
        assertEquals(10_000.0, service.getFinalPriceInSYP(product(10_000)));
    }

    @Test
    void variantPrices_areMarkedUpToo_includingCheaperSizes() {
        Product product = product(10_000);

        assertEquals(2_200.0, service.getCustomerVariantPrice(variant(product, 2_000)));
        // Small = -2,000 → -2,200, so 11,000 - 2,200 = 8,800 = (10,000 - 2,000) + 10%
        assertEquals(-2_200.0, service.getCustomerVariantPrice(variant(product, -2_000)));
    }

    @Test
    void usdPriceShownToCustomers_getsTheMarkup() {
        assertEquals(5.50, service.applyStoreMarkupToUsd(5.0, store));
    }

    @Test
    void offerBadge_isComputedFromCustomerPrices() {
        Product product = product(10_000);
        product.setHasOffer(true);
        product.setOfferBasePrice(8_000.0);

        assertEquals(8_800.0, service.getCustomerFinalPrice(product));
        assertEquals(11_000.0, service.getCustomerRegularPrice(product));
        assertEquals(20, service.getDiscountPercentage(product));
    }

    private Product product(double basePrice) {
        Product product = new Product();
        product.setBasePrice(basePrice);
        product.setIsUsd(false);
        product.setStore(store);
        return product;
    }

    private ProductVariant variant(Product product, double priceAdjustment) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setPriceAdjustment(priceAdjustment);
        return variant;
    }
}
