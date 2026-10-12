package com.deliveryapp.controller.catalog;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.OrderCalculationService;
import com.deliveryapp.service.PricingService;
import com.deliveryapp.service.StoreService;
import com.deliveryapp.util.UrlUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogStoreControllerTest {

    @Mock private StoreService storeService;
    @Mock private OrderCalculationService orderCalculationService;
    @Mock private PricingService pricingService;

    private CatalogStoreController controller;
    private Store closed;
    private Store openSecond;
    private Store openThird;

    @BeforeEach
    void setUp() {
        controller = new CatalogStoreController(
                storeService, new CatalogMapper(new UrlUtil(), pricingService), orderCalculationService);

        // Opens in an hour → closed now (also correct when the window crosses midnight)
        closed = store(1L, 1);
        closed.setOpeningTime(LocalTime.now().plusHours(1));
        closed.setClosingTime(LocalTime.now().plusHours(2));
        openSecond = store(2L, 2);
        openThird = store(3L, 3);

        when(storeService.getAllActiveStores()).thenReturn(List.of(closed, openSecond, openThird));
    }

    @Test
    void pagedStores_openFirst_thenDisplayOrder_slicedByPage() {
        PagedResponse<StoreResponse> first = controller.getAllActiveStoresPaged(0, 2, null, null).getBody();
        PagedResponse<StoreResponse> second = controller.getAllActiveStoresPaged(1, 2, null, null).getBody();

        assertEquals(List.of(2L, 3L), ids(first));
        assertEquals(List.of(1L), ids(second));
        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertFalse(first.isLast());
        assertTrue(second.isLast());
    }

    @Test
    void pagedStores_priceOnlyTheReturnedPage() {
        when(orderCalculationService.computeFeeForStore(any(Store.class), eq(33.5), eq(36.3))).thenReturn(5850.0);

        PagedResponse<StoreResponse> page = controller.getAllActiveStoresPaged(0, 2, 33.5, 36.3).getBody();

        assertEquals(5850.0, page.getContent().get(0).getPredictedDeliveryFee());
        verify(orderCalculationService, never()).computeFeeForStore(closed, 33.5, 36.3);
    }

    @Test
    void pagedStores_pastTheEnd_isEmptyNotAnError() {
        PagedResponse<StoreResponse> page = controller.getAllActiveStoresPaged(5, 2, null, null).getBody();

        assertTrue(page.getContent().isEmpty());
        assertEquals(3, page.getTotalElements());
    }

    private static Store store(Long id, int displayOrder) {
        Store store = new Store();
        store.setStoreId(id);
        store.setName("Store " + id);
        store.setDisplayOrder(displayOrder);
        return store;
    }

    private static List<Long> ids(PagedResponse<StoreResponse> page) {
        return page.getContent().stream().map(StoreResponse::getStoreId).toList();
    }
}
