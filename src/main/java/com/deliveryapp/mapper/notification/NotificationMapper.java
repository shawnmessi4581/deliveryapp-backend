package com.deliveryapp.mapper.notification;

import com.deliveryapp.dto.notification.NotificationResponse;
import com.deliveryapp.entity.Notification;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

            // Clean the string just in case it has spaces
            String refType = notification.getReferenceType().trim().toLowerCase();

            if (refType.equals("store")) {
                storeRepository.findById(notification.getReferenceId()).ifPresentOrElse(
                        store -> {
                            // Success: Map the store
                            dto.setStore(catalogMapper.toStoreResponse(store));
                        },
                        () -> {
                            // Debugging: If it fails, print why
                            System.err.println("Notification linked to Store ID " + notification.getReferenceId()
                                    + ", but store was not found in DB.");
                        });
            } else if (refType.equals("product")) {
                productRepository.findById(notification.getReferenceId()).ifPresentOrElse(
                        product -> {
                            // Success: Map the product
                            dto.setProduct(catalogMapper.toProductResponse(product));
                        },
                        () -> {
                            // Debugging: If it fails, print why
                            System.err.println("Notification linked to Product ID " + notification.getReferenceId()
                                    + ", but product was not found in DB.");
                        });
            }
        }

        return dto;
    }
}