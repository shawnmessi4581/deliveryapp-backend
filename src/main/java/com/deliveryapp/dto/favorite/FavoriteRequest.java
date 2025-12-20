package com.deliveryapp.dto.favorite;

import lombok.Data;

@Data
public class FavoriteRequest {
    private Long userId; // Can be inferred from token, but good to have
    private String type; // "STORE" or "PRODUCT"
    private Long itemId; // The StoreID or ProductID
}