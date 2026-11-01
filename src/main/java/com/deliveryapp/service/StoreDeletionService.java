package com.deliveryapp.service;

import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.PromotionalOffer;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.repository.BannerRepository;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.FavoriteRepository;
import com.deliveryapp.repository.FlashSaleRepository;
import com.deliveryapp.repository.OrderItemRepository;
import com.deliveryapp.repository.OrderRepository;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.PromotionalOfferRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes stores.
 *
 * <ul>
 *   <li>Refused while the store has an order in progress.</li>
 *   <li>Everything that only exists for the store is removed: its offers and flash sales (including
 *       admin flash sales aimed at it), banners and favorites pointing to it or its products,
 *       and its products (a product that was ordered before is hidden instead, like a normal product
 *       delete). Coupons scoped to the store are turned off — their usage history stays.</li>
 *   <li>Vendor accounts are unlinked from the store; the accounts themselves stay.</li>
 *   <li>No order history → the store is deleted completely, images included.</li>
 *   <li>Order history → the store is hidden everywhere (customers, admin lists, editing, new orders,
 *       Telegram notifications) and kept only so past orders, payouts and profit reports stay correct.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDeletionService {

    private final StoreService storeService;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final PromotionalOfferRepository offerRepository;
    private final PromotionalOfferService offerService;
    private final FlashSaleRepository flashSaleRepository;
    private final FlashSaleService flashSaleService;
    private final CouponRepository couponRepository;
    private final FavoriteRepository favoriteRepository;
    private final BannerRepository bannerRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    /**
     * @return true when the store was deleted completely, false when it was hidden and kept for past orders
     */
    @Transactional
    public boolean deleteStore(Long storeId) {
        Store store = storeService.getStoreById(storeId); // already-deleted stores are "not found"

        if (orderRepository.existsByStores_StoreIdAndStatusIn(storeId, UserDeletionService.IN_PROGRESS)) {
            throw new InvalidDataException("لا يمكن حذف المتجر لوجود طلبات قيد التنفيذ. يجب تسليمها أو إلغاؤها أولاً.");
        }
        boolean hasHistory = orderRepository.existsByStores_StoreId(storeId)
                || orderItemRepository.existsByProductStoreStoreId(storeId);

        removePromotions(storeId);
        unlinkVendors(storeId);

        List<Product> products = productRepository.findByStoreStoreId(storeId);
        favoriteRepository.deleteByTypeAndIds("STORE", Set.of(storeId));
        bannerRepository.deleteByLinkTypeAndLinkIds("store", Set.of(storeId));
        // Removes each product from favorites, banners and offers; ordered ones are only hidden
        products.forEach(product -> productService.deleteProduct(product.getProductId()));

        if (!hasHistory) {
            if (store.getLogo() != null) fileStorageService.deleteFile(store.getLogo());
            if (store.getCoverImage() != null) fileStorageService.deleteFile(store.getCoverImage());
            storeRepository.delete(store); // its store categories go with it
            log.info("Store {} deleted completely", storeId);
            return true;
        }

        store.setIsActive(false);
        store.setFreeDelivery(false);
        store.setFreeDeliveryThresholdEnabled(false);
        store.setTelegramChatId(null); // no more order notifications
        store.setDeletedAt(LocalDateTime.now());
        storeRepository.save(store);
        log.info("Store {} deleted — kept hidden for past orders", storeId);
        return false;
    }

    /** Offers and flash sales go; coupons are only turned off because their usage history stays. */
    private void removePromotions(Long storeId) {
        offerRepository.findByStoreStoreId(storeId).stream()
                .map(PromotionalOffer::getOfferId)
                .forEach(offerService::adminDeleteOffer);

        Set<Long> flashSaleIds = new LinkedHashSet<>();
        flashSaleRepository.findByStoreStoreId(storeId).forEach(fs -> flashSaleIds.add(fs.getFlashSaleId()));
        flashSaleRepository.findByApplicableToAndApplicableId(Coupon.ApplicableTo.STORE, storeId)
                .forEach(fs -> flashSaleIds.add(fs.getFlashSaleId()));
        flashSaleIds.forEach(flashSaleService::adminDelete);

        List<Coupon> coupons = couponRepository.findByApplicableToAndApplicableId(Coupon.ApplicableTo.STORE, storeId);
        coupons.forEach(coupon -> coupon.setIsActive(false));
        couponRepository.saveAll(coupons);
    }

    private void unlinkVendors(Long storeId) {
        List<User> vendors = userRepository.findByManagedStoreStoreId(storeId);
        vendors.forEach(vendor -> vendor.setManagedStore(null));
        userRepository.saveAll(vendors);
    }
}
