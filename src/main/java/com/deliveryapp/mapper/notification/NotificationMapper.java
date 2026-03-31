package com.deliveryapp.mapper.notification;

import com.deliveryapp.dto.notification.NotificationResponse;
import com.deliveryapp.entity.Notification;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NotificationMapper {

    private final UrlUtil urlUtil;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final CatalogMapper catalogMapper;

    public NotificationResponse toNotificationResponse(Notification notification) {
        NotificationResponse dto = new NotificationResponse();
        dto.setNotificationId(notification.getNotificationId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setImageUrl(urlUtil.getFullUrl(notification.getImageUrl()));
        dto.setType(notification.getType());
        dto.setReferenceType(notification.getReferenceType());
        dto.setReferenceId(notification.getReferenceId());
        dto.setExternalUrl(notification.getExternalUrl());
        dto.setIsRead(notification.getIsRead());
        dto.setCreatedAt(notification.getCreatedAt());

        // --- FETCH RICH DATA BASED ON REFERENCE TYPE ---
        if (notification.getReferenceType() != null && notification.getReferenceId() != null) {

            String refType = notification.getReferenceType().trim().toLowerCase();

            if (refType.equals("store")) {
                // Fetch the store
                Optional<Store> storeOpt = storeRepository.findById(notification.getReferenceId());

                if (storeOpt.isPresent()) {
                    dto.setStore(catalogMapper.toStoreResponse(storeOpt.get()));
                } else {
                    System.err.println("❌ ERROR: Notification references Store ID " + notification.getReferenceId()
                            + ", but it does not exist.");
                }
            } else if (refType.equals("product")) {
                // Fetch the product
                Optional<Product> productOpt = productRepository.findById(notification.getReferenceId());

                if (productOpt.isPresent()) {
                    dto.setProduct(catalogMapper.toProductResponse(productOpt.get()));
                } else {
                    System.err.println("❌ ERROR: Notification references Product ID " + notification.getReferenceId()
                            + ", but it does not exist.");
                }
            } else {
                System.out.println("⚠️ Unknown referenceType: " + refType);
            }
        }

        return dto;
    }
}