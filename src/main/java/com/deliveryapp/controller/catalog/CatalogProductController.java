package com.deliveryapp.controller.catalog;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.catalog.ProductResponse;
import com.deliveryapp.entity.Product;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class CatalogProductController {

    private final ProductService productService;
    private final CatalogMapper catalogMapper;

    @GetMapping("/all")
    public ResponseEntity<PagedResponse<ProductResponse>> getAllProductsRandom(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());
        return ResponseEntity.ok(createPagedResponse(productService.getAllProductsRandomly(pageable)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long productId) {
        return ResponseEntity.ok(catalogMapper.toProductResponse(productService.getProductById(productId)));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<PagedResponse<ProductResponse>> getProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());
        return ResponseEntity.ok(createPagedResponse(productService.getProductsByStore(storeId, pageable)));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<PagedResponse<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());
        return ResponseEntity.ok(createPagedResponse(productService.getProductsByCategory(categoryId, pageable)));
    }

    @GetMapping("/subcategory/{subCategoryId}")
    public ResponseEntity<PagedResponse<ProductResponse>> getProductsBySubCategory(
            @PathVariable Long subCategoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());
        return ResponseEntity.ok(createPagedResponse(productService.getProductsBySubCategory(subCategoryId, pageable)));
    }

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<ProductResponse>> searchProducts(
            @RequestParam("q") String keyword,
            @RequestParam(value = "categoryId", defaultValue = "0") Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(createPagedResponse(productService.searchProducts(keyword, categoryId, pageable)));
    }

    @GetMapping("/store/search")
    public ResponseEntity<PagedResponse<ProductResponse>> searchProductsInStore(
            @RequestParam("q") String keyword,
            @RequestParam("storeId") long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(createPagedResponse(productService.searchProductsInStore(keyword, storeId, pageable)));
    }

    @GetMapping("/store/{storeId}/category/{categoryId}")
    public ResponseEntity<PagedResponse<ProductResponse>> getProductsByStoreAndCategory(
            @PathVariable Long storeId,
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());
        return ResponseEntity
                .ok(createPagedResponse(productService.getProductsByStoreAndCategory(storeId, categoryId, pageable)));
    }

    @GetMapping("/store/{storeId}/subcategory/{subCategoryId}")
    public ResponseEntity<PagedResponse<ProductResponse>> getProductsByStoreAndSubCategory(
            @PathVariable Long storeId,
            @PathVariable Long subCategoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());
        return ResponseEntity.ok(
                createPagedResponse(productService.getProductsByStoreAndSubCategory(storeId, subCategoryId, pageable)));
    }

    @GetMapping("/price")
    public ResponseEntity<PagedResponse<ProductResponse>> getProductsByPrice(
            @RequestParam Double max,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());
        return ResponseEntity.ok(createPagedResponse(productService.getProductsUnderPrice(max, pageable)));
    }

    @GetMapping("/new")
    public ResponseEntity<PagedResponse<ProductResponse>> getNewestProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(createPagedResponse(productService.getNewestProducts(pageable)));
    }

    @GetMapping("/trending")
    public ResponseEntity<PagedResponse<ProductResponse>> getTrendingProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(createPagedResponse(productService.getTrendingProducts(pageable)));
    }

    private PagedResponse<ProductResponse> createPagedResponse(Page<Product> productPage) {
        List<ProductResponse> content = productPage.getContent().stream()
                .map(catalogMapper::toProductResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content, productPage.getNumber(), productPage.getSize(),
                productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast());
    }
}