package com.deliveryapp.dto.catalog;

import com.deliveryapp.dto.PagedResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResponse {
    // We return a simple list for stores (usually we just show top 3-5 matching
    // stores in the UI)
    private List<StoreResponse> stores;

    // We return paginated results for products (since there could be hundreds of
    // items)
    private PagedResponse<ProductResponse> products;
}