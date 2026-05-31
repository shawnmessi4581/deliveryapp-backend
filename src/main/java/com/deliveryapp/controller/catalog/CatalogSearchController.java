package com.deliveryapp.controller.catalog;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.catalog.GlobalSearchResponse;
import com.deliveryapp.dto.catalog.ProductResponse;
import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.ProductService;
import com.deliveryapp.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalog/search")
@RequiredArgsConstructor
public class CatalogSearchController {

    private final StoreService storeService;
    private final ProductService productService;
    private final CatalogMapper catalogMapper;

    // 🌟 PROFESSIONAL GLOBAL SEARCH
    // Usage: /api/catalog/search/global?q=burger&page=0&size=10
    @GetMapping("/global")
    public ResponseEntity<GlobalSearchResponse> globalSearch(
            @RequestParam("q") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // 1. Search Stores (Limit to top 5 to keep UI clean)
        List<Store> matchingStores = storeService.searchStores(keyword);
        List<StoreResponse> storeResponses = matchingStores.stream()
                .limit(5) // Only return the top 5 most relevant stores
                .map(catalogMapper::toStoreResponse)
                .collect(Collectors.toList());

        // 2. Search Products (Paginated)
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> matchingProducts = productService.searchProductsGlobal(keyword, pageable);

        // Map Page<Product> to PagedResponse<ProductResponse>
        PagedResponse<ProductResponse> productResponses = createPagedResponse(matchingProducts);

        // 3. Combine and Return
        GlobalSearchResponse finalResponse = new GlobalSearchResponse(storeResponses, productResponses);

        return ResponseEntity.ok(finalResponse);
    }

    // --- HELPER METHOD TO CREATE PAGED RESPONSE ---
    private PagedResponse<ProductResponse> createPagedResponse(Page<Product> productPage) {
        List<ProductResponse> content = productPage.getContent().stream()
                .map(catalogMapper::toProductResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.isLast());
    }
}