package com.deliveryapp.service;

import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.FlashSale;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.PromotionalOffer;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreDeletionServiceTest {

    private static final long STORE_ID = 22L;

    @Mock private StoreService storeService;
    @Mock private StoreRepository storeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductService productService;
    @Mock private PromotionalOfferRepository offerRepository;
    @Mock private PromotionalOfferService offerService;
    @Mock private FlashSaleRepository flashSaleRepository;
    @Mock private FlashSaleService flashSaleService;
    @Mock private CouponRepository couponRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private BannerRepository bannerRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private StoreDeletionService service;

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Global Brands for men");
        store.setLogo("/uploads/stores/logo.png");
        store.setIsActive(true);
        store.setTelegramChatId("-100123");
        when(storeService.getStoreById(STORE_ID)).thenReturn(store);
    }

    @Test
    void orderInProgress_blocksTheDelete() {
        when(orderRepository.existsByStores_StoreIdAndStatusIn(eq(STORE_ID), anyList())).thenReturn(true);

        assertThrows(InvalidDataException.class, () -> service.deleteStore(STORE_ID));
        verify(productService, never()).deleteProduct(any());
        verify(storeRepository, never()).delete(any());
    }

    @Test
    void noHistory_removesTheStoreAndEverythingAroundIt() {
        Product product = new Product();
        product.setProductId(152L);
        when(productRepository.findByStoreStoreId(STORE_ID)).thenReturn(List.of(product));
        PromotionalOffer offer = new PromotionalOffer();
        offer.setOfferId(5L);
        when(offerRepository.findByStoreStoreId(STORE_ID)).thenReturn(List.of(offer));
        FlashSale vendorSale = new FlashSale();
        vendorSale.setFlashSaleId(8L);
        when(flashSaleRepository.findByStoreStoreId(STORE_ID)).thenReturn(List.of(vendorSale));
        FlashSale adminSale = new FlashSale();
        adminSale.setFlashSaleId(9L);
        when(flashSaleRepository.findByApplicableToAndApplicableId(Coupon.ApplicableTo.STORE, STORE_ID))
                .thenReturn(List.of(adminSale));

        assertTrue(service.deleteStore(STORE_ID));

        verify(offerService).adminDeleteOffer(5L);
        verify(flashSaleService).adminDelete(8L);
        verify(flashSaleService).adminDelete(9L);
        verify(productService).deleteProduct(152L);
        verify(fileStorageService).deleteFile("/uploads/stores/logo.png");
        verify(storeRepository).delete(store);
    }

    @Test
    void pastOrders_hideTheStore_unlinkVendors_andTurnOffItsCoupons() {
        when(orderRepository.existsByStores_StoreId(STORE_ID)).thenReturn(true);
        User vendor = new User();
        vendor.setManagedStore(store);
        when(userRepository.findByManagedStoreStoreId(STORE_ID)).thenReturn(List.of(vendor));
        Coupon coupon = new Coupon();
        coupon.setIsActive(true);
        when(couponRepository.findByApplicableToAndApplicableId(Coupon.ApplicableTo.STORE, STORE_ID))
                .thenReturn(List.of(coupon));

        assertFalse(service.deleteStore(STORE_ID));

        verify(storeRepository, never()).delete(any());
        verify(storeRepository).save(store);
        assertFalse(store.getIsActive());
        assertNull(store.getTelegramChatId());
        assertNotNull(store.getDeletedAt());
        assertNull(vendor.getManagedStore());
        assertFalse(coupon.getIsActive());
        verify(fileStorageService, never()).deleteFile(any()); // past orders still show the logo
    }
}
