package com.deliveryapp.service;

import com.deliveryapp.dto.offer.OfferRequest;
import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.PromotionalOffer;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.OfferType;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.PromotionalOfferRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.UserRepository;
import com.deliveryapp.util.MathUtil;
import com.deliveryapp.util.UrlUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionalOfferServiceTest {

    private static final long PRODUCT_ID = 1L;
    private static final long STORE_ID = 5L;
    private static final long VENDOR_ID = 50L;
    private static final long ADMIN_ID = 60L;

    @Mock private PromotionalOfferRepository offerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private UserRepository userRepository;
    @Mock private UrlUtil urlUtil;

    private PromotionalOfferService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new PromotionalOfferService(offerRepository, productRepository, storeRepository,
                userRepository, new MathUtil(), urlUtil);
        Store store = new Store();
        store.setStoreId(STORE_ID);
        product = new Product();
        product.setProductId(PRODUCT_ID);
        product.setStore(store);
    }

    @Test
    void buyOneGetOne_poolsVariantsAndFreesTheCheapestUnit() {
        // Customer picks one Large and one Regular — separate lines, same product
        OrderItem large = line(15_000, 1);
        OrderItem regular = line(10_000, 1);
        offers(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID));
        user(VENDOR_ID, UserType.VENDOR);

        service.applyOffers(List.of(large, regular));

        assertEquals(10_000.0, regular.getOfferDiscountAmount());
        assertEquals(1L, regular.getAppliedOfferId());
        assertTrue(regular.getOfferFundedByStore());
        assertEquals(0.0, large.getOfferDiscountAmount());
        assertNull(large.getAppliedOfferId());
    }

    @Test
    void partialDealCycle_givesNoDiscount() {
        OrderItem item = line(10_000, 2);
        offers(offer(1L, OfferType.BUY_X_GET_Y_FREE, 2, 1, VENDOR_ID)); // needs 3 units

        service.applyOffers(List.of(item));

        assertEquals(0.0, item.getOfferDiscountAmount());
        assertNull(item.getAppliedOfferId());
    }

    @Test
    void picksTheOfferWorthMostToTheCustomer() {
        OrderItem item = line(10_000, 3);
        // Latest offer: buy 1 get 1 at 50% → 1 unit × 5,000. Older: buy 2 get 1 free → 1 unit × 10,000
        PromotionalOffer halfOff = offer(2L, OfferType.BUY_X_GET_Y_PERCENT_OFF, 1, 1, ADMIN_ID);
        halfOff.setDiscountPercent(50.0);
        offers(halfOff, offer(1L, OfferType.BUY_X_GET_Y_FREE, 2, 1, ADMIN_ID));
        user(ADMIN_ID, UserType.ADMIN);

        service.applyOffers(List.of(item));

        assertEquals(10_000.0, item.getOfferDiscountAmount());
        assertEquals(1L, item.getAppliedOfferId());
    }

    @Test
    void percentDiscount_isRoundedToWholePounds() {
        OrderItem item = line(12_345, 2);
        PromotionalOffer offer = offer(1L, OfferType.BUY_X_GET_Y_PERCENT_OFF, 1, 1, VENDOR_ID);
        offer.setDiscountPercent(33.0); // 4,073.85
        offers(offer);
        user(VENDOR_ID, UserType.VENDOR);

        service.applyOffers(List.of(item));

        assertEquals(4_074.0, item.getOfferDiscountAmount());
    }

    @Test
    void withAMarkup_theStoresShareIsAtItsOwnPrice() {
        // Customer price 11,000, store price 10,000 (10% markup)
        OrderItem item = line(11_000, 2);
        item.setStoreUnitPrice(10_000.0);
        PromotionalOffer offer = offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID);
        offer.setFundedByStore(true);
        offers(offer);

        service.applyOffers(List.of(item));

        assertEquals(11_000.0, item.getOfferDiscountAmount());     // what the customer saved
        assertEquals(10_000.0, item.getStoreOfferDiscountAmount()); // what the store pays
    }

    @Test
    void withAMarkup_fixedDiscountsAreSplitInTheSameRatio() {
        OrderItem item = line(11_000, 2);
        item.setStoreUnitPrice(10_000.0);
        PromotionalOffer offer = offer(1L, OfferType.BUY_X_GET_Y_FIXED_OFF, 1, 1, VENDOR_ID);
        offer.setDiscountValue(5_000.0);
        offer.setFundedByStore(true);
        offers(offer);

        service.applyOffers(List.of(item));

        assertEquals(5_000.0, item.getOfferDiscountAmount());
        assertEquals(4_545.0, item.getStoreOfferDiscountAmount()); // 5,000 × 10,000 / 11,000
    }

    @Test
    void legacyAdminOffer_withoutFlag_isPaidByThePlatform() {
        OrderItem item = line(10_000, 2);
        offers(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, ADMIN_ID)); // saved before fundedByStore existed
        user(ADMIN_ID, UserType.ADMIN);

        service.applyOffers(List.of(item));

        assertEquals(10_000.0, item.getOfferDiscountAmount());
        assertFalse(item.getOfferFundedByStore());
    }

    @Test
    void adminOfferMarkedAsPaidByStore_isChargedToTheStore() {
        OrderItem item = line(10_000, 2);
        PromotionalOffer offer = offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, ADMIN_ID);
        offer.setFundedByStore(true);
        offers(offer);

        service.applyOffers(List.of(item));

        assertTrue(item.getOfferFundedByStore());
    }

    // ── Who pays: admin flag / vendor offers ──────────────────────────────────

    @Test
    void adminCreate_isPaidByThePlatformUnlessFlagged() {
        stubProductAndSave();
        user(ADMIN_ID, UserType.ADMIN);

        OfferResponse byDefault = service.adminCreateOffer(request(null), ADMIN_ID);
        OfferResponse flagged = service.adminCreateOffer(request(true), ADMIN_ID);

        assertFalse(byDefault.getFundedByStore());
        assertTrue(flagged.getFundedByStore());
        assertFalse(flagged.getCreatedByVendor());
    }

    @Test
    void adminUpdate_withoutTheFlag_keepsWhoPays() {
        PromotionalOffer existing = offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, ADMIN_ID);
        existing.setFundedByStore(true);
        when(offerRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubProductAndSave();
        user(ADMIN_ID, UserType.ADMIN);

        OfferResponse updated = service.adminUpdateOffer(1L, request(null), ADMIN_ID);

        assertTrue(updated.getFundedByStore());
    }

    @Test
    void vendorCreate_isAlwaysPaidByTheVendorsStore() {
        stubProductAndSave();
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(product.getStore()));
        user(VENDOR_ID, UserType.VENDOR);

        OfferResponse created = service.vendorCreateOffer(request(false), STORE_ID, VENDOR_ID);

        assertTrue(created.getFundedByStore());
        assertTrue(created.getCreatedByVendor());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OrderItem line(double unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTotalPrice(unitPrice * quantity);
        return item;
    }

    private PromotionalOffer offer(long id, OfferType type, int buy, int get, long createdBy) {
        PromotionalOffer offer = new PromotionalOffer();
        offer.setOfferId(id);
        offer.setOfferType(type);
        offer.setBuyQuantity(buy);
        offer.setGetQuantity(get);
        offer.setCreatedBy(createdBy);
        return offer;
    }

    private void offers(PromotionalOffer... offers) {
        when(offerRepository.findActiveOffersForProduct(eq(PRODUCT_ID), any())).thenReturn(List.of(offers));
    }

    private void user(long id, UserType type) {
        User user = new User();
        user.setUserId(id);
        user.setUserType(type);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
    }

    private OfferRequest request(Boolean fundedByStore) {
        OfferRequest request = new OfferRequest();
        request.setTitle("Buy 1 Get 1");
        request.setOfferType(OfferType.BUY_X_GET_Y_FREE);
        request.setProductId(PRODUCT_ID);
        request.setBuyQuantity(1);
        request.setGetQuantity(1);
        request.setFundedByStore(fundedByStore);
        return request;
    }

    private void stubProductAndSave() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(offerRepository.save(any(PromotionalOffer.class))).thenAnswer(call -> call.getArgument(0));
    }
}
