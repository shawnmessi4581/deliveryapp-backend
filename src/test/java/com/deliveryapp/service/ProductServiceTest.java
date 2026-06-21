package com.deliveryapp.service;

import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.ProductVariant;
import com.deliveryapp.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SubCategoryRepository subCategoryRepository;

    @Mock
    private ColorRepository colorRepository;

    @Mock
    private StoreCategoryRepository storeCategoryRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void deleteProductVariant_shouldDetachOrderItemsBeforeDeletingVariant() {
        Long variantId = 10L;
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(variantId);

        OrderItem item = mock(OrderItem.class);

        when(variantRepository.existsById(variantId)).thenReturn(true);
        when(orderItemRepository.findByVariantVariantId(variantId)).thenReturn(List.of(item));

        productService.deleteProductVariant(variantId);

        verify(orderItemRepository).findByVariantVariantId(variantId);
        verify(item).setVariant(null);
        verify(orderItemRepository).saveAll(List.of(item));
        verify(variantRepository).deleteById(variantId);
    }
}