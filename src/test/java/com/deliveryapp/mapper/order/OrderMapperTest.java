package com.deliveryapp.mapper.order;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.mapper.user.UserMapper;
import com.deliveryapp.service.StorePayoutService;
import com.deliveryapp.util.DistanceUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMapperTest {

    @Mock private CatalogMapper catalogMapper;
    @Mock private UserMapper userMapper;

    private OrderMapper mapper;
    private Order order;

    @BeforeEach
    void setUp() {
        mapper = new OrderMapper(catalogMapper, userMapper, new DistanceUtil(), new StorePayoutService());

        Store store = new Store();
        store.setStoreId(1L);
        store.setName("Store");
        store.setCommissionPercentage(10.0);
        Product product = new Product();
        product.setProductId(5L);
        product.setStore(store);

        // 10% markup: customer pays 11,000, the store's price is 10,000
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(11_000.0);
        item.setTotalPrice(11_000.0);
        item.setStoreUnitPrice(10_000.0);
        item.setStoreTotalPrice(10_000.0);

        order = new Order();
        order.setStores(List.of(store));
        order.setOrderItems(List.of(item));
        when(catalogMapper.toStoreResponse(store)).thenReturn(new StoreResponse());
    }

    @Test
    void customerView_neverContainsStorePricesOrPayouts() {
        OrderResponse response = mapper.toCustomerOrderResponse(order);
        OrderItemResponse item = response.getItems().get(0);

        assertEquals(11_000.0, item.getUnitPrice());
        assertNull(item.getStoreUnitPrice());
        assertNull(item.getStoreTotalPrice());
        assertNull(item.getStoreCommissionPercentage());
        assertNull(response.getStorePayouts());
    }

    @Test
    void staffAndDriverView_hasBothPricesAndThePayout() {
        OrderResponse response = mapper.toOrderResponse(order);
        OrderItemResponse item = response.getItems().get(0);

        assertEquals(11_000.0, item.getUnitPrice());
        assertEquals(10_000.0, item.getStoreUnitPrice());
        assertEquals(9_000.0, response.getStorePayouts().get(0).getStorePayout()); // 10,000 − 10%
    }
}
