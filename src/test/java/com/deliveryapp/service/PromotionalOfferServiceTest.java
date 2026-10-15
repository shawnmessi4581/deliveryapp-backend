package com.deliveryapp.service;

import com.deliveryapp.dto.offer.AvailableRewardResponse;
import com.deliveryapp.dto.offer.OfferRequest;
import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.PromotionalOffer;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.OfferType;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.mapper.catalog.CatalogMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
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
    @Mock private PricingService pricingService;

    private PromotionalOfferService service;
    private Store store;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new PromotionalOfferService(offerRepository, productRepository, storeRepository,
                userRepository, new MathUtil(), urlUtil, new CatalogMapper(urlUtil, pricingService));
        store = new Store();
        store.setStoreId(STORE_ID);
        product = product(PRODUCT_ID, store);
    }

    // ── Single-product offers (unchanged behaviour, also older app versions) ──

    @Test
    void buyOneGetOne_poolsVariantsAndFreesTheCheapestUnit() {
        // Customer picks one Large and one Regular — separate lines, same product
        OrderItem large = line(product, 15_000, 1);
        OrderItem regular = line(product, 10_000, 1);
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
        OrderItem item = line(product, 10_000, 2);
        offers(offer(1L, OfferType.BUY_X_GET_Y_FREE, 2, 1, VENDOR_ID)); // needs 3 units

        service.applyOffers(List.of(item));

        assertEquals(0.0, item.getOfferDiscountAmount());
        assertNull(item.getAppliedOfferId());
    }

    @Test
    void picksTheOfferWorthMostToTheCustomer() {
        OrderItem item = line(product, 10_000, 3);
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
        OrderItem item = line(product, 12_345, 2);
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
        OrderItem item = line(product, 11_000, 2);
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
        OrderItem item = line(product, 11_000, 2);
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
        OrderItem item = line(product, 10_000, 2);
        offers(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, ADMIN_ID)); // saved before fundedByStore existed
        user(ADMIN_ID, UserType.ADMIN);

        service.applyOffers(List.of(item));

        assertEquals(10_000.0, item.getOfferDiscountAmount());
        assertFalse(item.getOfferFundedByStore());
    }

    @Test
    void adminOfferMarkedAsPaidByStore_isChargedToTheStore() {
        OrderItem item = line(product, 10_000, 2);
        PromotionalOffer offer = offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, ADMIN_ID);
        offer.setFundedByStore(true);
        offers(offer);

        service.applyOffers(List.of(item));

        assertTrue(item.getOfferFundedByStore());
    }

    // ── Buy options + reward options ─────────────────────────────────────────

    @Test
    void chosenReward_fromTheRewardList_isFree() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        Product sevenUp = product(21L, store);
        PromotionalOffer offer = lists(paidByStore(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID)),
                List.of(burger), List.of(pepsi, sevenUp));
        offers(offer);
        OrderItem paid = line(burger, 30_000, 1);
        OrderItem reward = rewardLine(sevenUp, 6_000, 1, 1L);

        service.applyOffers(List.of(paid, reward));

        assertEquals(6_000.0, reward.getOfferDiscountAmount());
        assertEquals(1L, reward.getAppliedOfferId());
        assertEquals(0.0, paid.getOfferDiscountAmount());
    }

    @Test
    void chosenReward_thatIsNotEarned_staysFullPrice() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        offers(lists(paidByStore(offer(1L, OfferType.BUY_X_GET_Y_FREE, 2, 1, VENDOR_ID)),
                List.of(burger), List.of(pepsi)));
        OrderItem paid = line(burger, 30_000, 1); // needs 2 burgers
        OrderItem reward = rewardLine(pepsi, 5_000, 1, 1L);

        service.applyOffers(List.of(paid, reward));

        assertEquals(0.0, reward.getOfferDiscountAmount());
        assertNull(reward.getAppliedOfferId());
    }

    @Test
    void morePicksThanEarned_theDearestPickIsRewarded() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        Product juice = product(22L, store);
        offers(lists(paidByStore(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID)),
                List.of(burger), List.of(pepsi, juice)));
        OrderItem paid = line(burger, 30_000, 1);
        OrderItem cheapPick = rewardLine(pepsi, 5_000, 1, 1L);
        OrderItem dearPick = rewardLine(juice, 9_000, 1, 1L);

        service.applyOffers(List.of(paid, cheapPick, dearPick));

        assertEquals(9_000.0, dearPick.getOfferDiscountAmount());
        assertEquals(0.0, cheapPick.getOfferDiscountAmount());
    }

    @Test
    void sameProductsInBothLists_theCustomersPickIsFree_evenTheDearest() {
        Product margherita = product(30L, store);
        Product mixed = product(31L, store);
        offers(lists(paidByStore(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID)),
                List.of(margherita, mixed), List.of(margherita, mixed)));
        OrderItem paid = line(margherita, 20_000, 1);
        OrderItem pick = rewardLine(mixed, 30_000, 1, 1L);

        service.applyOffers(List.of(paid, pick));

        assertEquals(30_000.0, pick.getOfferDiscountAmount());
        assertEquals(0.0, paid.getOfferDiscountAmount());
    }

    @Test
    void noPick_rewardListProductInTheCart_isRewardedAutomatically() {
        // Older app versions don't send rewardOfferId
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        offers(lists(paidByStore(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID)),
                List.of(burger), List.of(pepsi)));
        OrderItem paid = line(burger, 30_000, 1);
        OrderItem drink = line(pepsi, 5_000, 1);

        service.applyOffers(List.of(paid, drink));

        assertEquals(5_000.0, drink.getOfferDiscountAmount());
        assertEquals(0.0, paid.getOfferDiscountAmount());
    }

    @Test
    void pickForAProductOutsideTheRewardList_isIgnored() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        Product fries = product(40L, store);
        offers(lists(paidByStore(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID)),
                List.of(burger), List.of(pepsi)));
        OrderItem paid = line(burger, 30_000, 1);
        OrderItem wrongPick = rewardLine(fries, 8_000, 1, 1L);

        service.applyOffers(List.of(paid, wrongPick));

        assertEquals(0.0, wrongPick.getOfferDiscountAmount());
    }

    @Test
    void maxUsesPerOrder_capsHowOftenTheDealRepeats() {
        PromotionalOffer offer = paidByStore(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID));
        offer.setMaxUsesPerOrder(1);
        offers(offer);
        OrderItem item = line(product, 10_000, 4); // would earn 2 without the cap

        service.applyOffers(List.of(item));

        assertEquals(10_000.0, item.getOfferDiscountAmount());
    }

    @Test
    void aUnitCountsTowardOneOfferOnly() {
        // Both offers need the same single burger as payment — only the better one applies
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        Product fries = product(40L, store);
        offers(lists(paidByStore(offer(2L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID)), List.of(burger), List.of(pepsi)),
                lists(paidByStore(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID)), List.of(burger), List.of(fries)));
        OrderItem paid = line(burger, 30_000, 1);
        OrderItem drink = rewardLine(pepsi, 5_000, 1, 2L);
        OrderItem side = rewardLine(fries, 8_000, 1, 1L);

        service.applyOffers(List.of(paid, drink, side));

        assertEquals(8_000.0, side.getOfferDiscountAmount());
        assertEquals(0.0, drink.getOfferDiscountAmount());
    }

    @Test
    void availableRewards_listsRewardsEarnedButNotPicked() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        Product sevenUp = product(21L, store);
        PromotionalOffer offer = lists(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID),
                List.of(burger), List.of(pepsi, sevenUp));
        offer.setTitle("وجبة + مشروب مجاني");
        offers(offer);
        List<OrderItem> cart = List.of(line(burger, 30_000, 2), rewardLine(pepsi, 5_000, 1, 1L));

        List<AvailableRewardResponse> rewards = service.findAvailableRewards(cart);

        assertEquals(1, rewards.size());
        assertEquals(1L, rewards.get(0).getOfferId());
        assertEquals(1, rewards.get(0).getRewardQuantity()); // 2 earned, 1 already picked
        assertEquals(List.of(20L, 21L), rewards.get(0).getRewardProductIds());
    }

    @Test
    void availableRewards_countsTheSecondRewardOfABuyOneGetTwoDeal() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        offers(lists(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 2, VENDOR_ID), List.of(burger), List.of(pepsi)));
        List<OrderItem> cart = List.of(line(burger, 30_000, 1), rewardLine(pepsi, 5_000, 1, 1L));

        List<AvailableRewardResponse> rewards = service.findAvailableRewards(cart);

        assertEquals(1, rewards.size());
        assertEquals(1, rewards.get(0).getRewardQuantity());
    }

    @Test
    void availableRewards_onlyListsProductsTheCustomerCanAdd() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        pepsi.setIsAvailable(false);
        Product sevenUp = product(21L, store);
        offers(lists(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID), List.of(burger), List.of(pepsi, sevenUp)));

        List<AvailableRewardResponse> rewards = service.findAvailableRewards(List.of(line(burger, 30_000, 1)));

        assertEquals(List.of(21L), rewards.get(0).getRewardProductIds());
    }

    @Test
    void availableRewards_skipsAnOfferWhoseRewardsAreAllUnavailable() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        pepsi.setIsAvailable(false);
        offers(lists(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID), List.of(burger), List.of(pepsi)));

        assertTrue(service.findAvailableRewards(List.of(line(burger, 30_000, 1))).isEmpty());
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
        existing.setProduct(product(99L, store));
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
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        user(VENDOR_ID, UserType.VENDOR);

        OfferResponse created = service.vendorCreateOffer(request(false), STORE_ID, VENDOR_ID);

        assertTrue(created.getFundedByStore());
        assertTrue(created.getCreatedByVendor());
    }

    // ── Product lists on create / update ─────────────────────────────────────

    @Test
    void create_withLists_savesBothLists_andTheFirstBuyOptionIsTheMainProduct() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        when(productRepository.findAllById(anyList()))
                .thenReturn(List.of(burger))
                .thenReturn(List.of(pepsi));
        when(offerRepository.save(any(PromotionalOffer.class))).thenAnswer(call -> call.getArgument(0));
        OfferRequest request = request(null);
        request.setBuyProductIds(List.of(10L));
        request.setRewardProductIds(List.of(20L));

        OfferResponse created = service.adminCreateOffer(request, ADMIN_ID);

        assertEquals(10L, created.getProductId());
        assertEquals(List.of(10L), created.getBuyProducts().stream().map(p -> p.getProductId()).toList());
        assertEquals(List.of(20L), created.getRewardProducts().stream().map(p -> p.getProductId()).toList());
    }

    @Test
    void create_withProductsFromTwoStores_isRejected() {
        Store otherStore = new Store();
        otherStore.setStoreId(6L);
        when(productRepository.findAllById(anyList()))
                .thenReturn(List.of(product(10L, store), product(11L, otherStore)));
        OfferRequest request = request(null);
        request.setBuyProductIds(List.of(10L, 11L));

        assertThrows(InvalidDataException.class, () -> service.adminCreateOffer(request, ADMIN_ID));
    }

    @Test
    void vendorCreate_withAnotherStoresProduct_isRejected() {
        Store otherStore = new Store();
        otherStore.setStoreId(6L);
        when(productRepository.findAllById(anyList())).thenReturn(List.of(product(11L, otherStore)));
        OfferRequest request = request(null);
        request.setBuyProductIds(List.of(11L));

        assertThrows(InvalidDataException.class, () -> service.vendorCreateOffer(request, STORE_ID, VENDOR_ID));
    }

    @Test
    void create_withoutProducts_isRejected() {
        OfferRequest request = request(null);
        request.setBuyProductIds(null);

        assertThrows(InvalidDataException.class, () -> service.adminCreateOffer(request, ADMIN_ID));
    }

    @Test
    void update_withoutLists_keepsTheCurrentLists() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        PromotionalOffer existing = lists(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, ADMIN_ID),
                List.of(burger), List.of(pepsi));
        when(offerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(offerRepository.save(any(PromotionalOffer.class))).thenAnswer(call -> call.getArgument(0));
        OfferRequest request = request(null);
        request.setBuyProductIds(null);

        OfferResponse updated = service.adminUpdateOffer(1L, request, ADMIN_ID);

        assertEquals(List.of(10L), updated.getBuyProducts().stream().map(p -> p.getProductId()).toList());
        assertEquals(List.of(20L), updated.getRewardProducts().stream().map(p -> p.getProductId()).toList());
    }

    @Test
    void update_withOnlyARewardList_keepsTheBuyListAndReplacesTheRewards() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        Product sevenUp = product(21L, store);
        PromotionalOffer existing = lists(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, ADMIN_ID),
                List.of(burger), List.of(pepsi));
        when(offerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.findAllById(List.of(21L))).thenReturn(List.of(sevenUp));
        when(offerRepository.save(any(PromotionalOffer.class))).thenAnswer(call -> call.getArgument(0));
        OfferRequest request = request(null);
        request.setBuyProductIds(null);
        request.setRewardProductIds(List.of(21L));

        OfferResponse updated = service.adminUpdateOffer(1L, request, ADMIN_ID);

        assertEquals(List.of(10L), updated.getBuyProducts().stream().map(p -> p.getProductId()).toList());
        assertEquals(List.of(21L), updated.getRewardProducts().stream().map(p -> p.getProductId()).toList());
    }

    @Test
    void update_ofAnOlderSingleProductOffer_withoutLists_keepsItsProduct() {
        PromotionalOffer existing = offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, ADMIN_ID); // lists empty
        when(offerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(offerRepository.save(any(PromotionalOffer.class))).thenAnswer(call -> call.getArgument(0));
        OfferRequest request = request(null);
        request.setBuyProductIds(null);

        OfferResponse updated = service.adminUpdateOffer(1L, request, ADMIN_ID);

        assertEquals(List.of(PRODUCT_ID), updated.getBuyProducts().stream().map(p -> p.getProductId()).toList());
        assertEquals(List.of(PRODUCT_ID), updated.getRewardProducts().stream().map(p -> p.getProductId()).toList());
    }

    // ── Product deletion ─────────────────────────────────────────────────────

    @Test
    void removingTheMainProduct_movesTheOfferToTheNextBuyOption() {
        Product burger = product(10L, store);
        Product chicken = product(11L, store);
        Product pepsi = product(20L, store);
        PromotionalOffer offer = lists(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID),
                List.of(burger, chicken), List.of(pepsi));
        when(offerRepository.findAllUsingProduct(10L)).thenReturn(List.of(offer));

        service.removeProductFromOffers(10L);

        assertEquals(chicken, offer.getProduct());
        assertEquals(List.of(chicken), offer.getBuyProducts());
        verify(offerRepository).save(offer);
    }

    @Test
    void removingTheLastRewardOption_deletesTheOffer() {
        Product burger = product(10L, store);
        Product pepsi = product(20L, store);
        PromotionalOffer offer = lists(offer(1L, OfferType.BUY_X_GET_Y_FREE, 1, 1, VENDOR_ID),
                List.of(burger), List.of(pepsi));
        when(offerRepository.findAllUsingProduct(20L)).thenReturn(List.of(offer));

        service.removeProductFromOffers(20L);

        verify(offerRepository).delete(offer);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Product product(long id, Store store) {
        Product product = new Product();
        product.setProductId(id);
        product.setStore(store);
        return product;
    }

    private OrderItem line(Product product, double unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setTotalPrice(unitPrice * quantity);
        return item;
    }

    private OrderItem rewardLine(Product product, double unitPrice, int quantity, long rewardOfferId) {
        OrderItem item = line(product, unitPrice, quantity);
        item.setRewardOfferId(rewardOfferId);
        return item;
    }

    /** A single-product offer on {@code product}, like the ones saved before product lists existed. */
    private PromotionalOffer offer(long id, OfferType type, int buy, int get, long createdBy) {
        PromotionalOffer offer = new PromotionalOffer();
        offer.setOfferId(id);
        offer.setOfferType(type);
        offer.setBuyQuantity(buy);
        offer.setGetQuantity(get);
        offer.setCreatedBy(createdBy);
        offer.setProduct(product);
        return offer;
    }

    private static PromotionalOffer lists(PromotionalOffer offer, List<Product> buy, List<Product> reward) {
        offer.setProduct(buy.get(0));
        offer.setBuyProducts(new ArrayList<>(buy));
        offer.setRewardProducts(new ArrayList<>(reward));
        return offer;
    }

    /** Skips the who-created-it lookup in engine tests that don't care who pays. */
    private static PromotionalOffer paidByStore(PromotionalOffer offer) {
        offer.setFundedByStore(true);
        return offer;
    }

    private void offers(PromotionalOffer... offers) {
        when(offerRepository.findActiveOffersForProducts(any(), any())).thenReturn(List.of(offers));
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
        request.setBuyProductIds(List.of(PRODUCT_ID));
        request.setBuyQuantity(1);
        request.setGetQuantity(1);
        request.setFundedByStore(fundedByStore);
        return request;
    }

    private void stubProductAndSave() {
        when(productRepository.findAllById(List.of(PRODUCT_ID))).thenReturn(List.of(product));
        when(offerRepository.save(any(PromotionalOffer.class))).thenAnswer(call -> call.getArgument(0));
    }
}
