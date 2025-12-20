package com.deliveryapp.dto.favorite;

import com.deliveryapp.dto.catalog.ProductResponse; // Reusing existing DTO
import com.deliveryapp.dto.catalog.StoreResponse;   // Reusing existing DTO
import lombok.Data;
import java.util.List;

@Data
public class FavoriteListResponse {
    // We separate them so the Frontend can easily show two different horizontal lists or tabs
    private List<StoreResponse> favoriteStores;
    private List<ProductResponse> favoriteProducts;
}