package com.deliveryapp.controller.admin;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.catalog.AdminProductResponse;
import com.deliveryapp.dto.catalog.AdminProductVariantResponse;
import com.deliveryapp.dto.catalog.ProductRequest;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.ProductVariant;
import com.deliveryapp.mapper.catalog.AdminCatalogMapper;
import com.deliveryapp.service.PricingService;
import com.deliveryapp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class AdminProductController {

    private final ProductService productService;
    private final PricingService pricingService;
    private final AdminCatalogMapper adminCatalogMapper;

    @GetMapping("/products")
    public ResponseEntity<PagedResponse<AdminProductResponse>> getAllProducts(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("displayOrder").ascending());
        Page<Product> productPage = productService.getAllProductsAdmin(storeId, categoryId, subCategoryId, pageable);

        List<AdminProductResponse> content = productPage.getContent().stream()
                .map(adminCatalogMapper::toAdminProductResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(
                content, productPage.getNumber(), productPage.getSize(),
                productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast()));
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminProductResponse> createProduct(
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "gallery", required = false) List<MultipartFile> gallery) {
        Product product = productService.createProduct(request, image, gallery);
        return ResponseEntity.ok(adminCatalogMapper.toAdminProductResponse(product));
    }

    @PutMapping(value = "/products/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminProductResponse> updateProduct(
            @PathVariable Long id,
            @ModelAttribute ProductRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "gallery", required = false) List<MultipartFile> gallery) {
        Product product = productService.updateProduct(id, request, image, gallery);
        return ResponseEntity.ok(adminCatalogMapper.toAdminProductResponse(product));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok("تم حذف المنتج");
    }

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<AdminProductVariantResponse> addVariant(
            @PathVariable Long productId,
            @RequestParam("name") String name,
            @RequestParam("price") Double priceAdjustment) {

        ProductVariant variant = productService.addProductVariant(productId, name, priceAdjustment);

        AdminProductVariantResponse responseDto = new AdminProductVariantResponse();
        responseDto.setVariantId(variant.getVariantId());
        responseDto.setVariantName(variant.getVariantValue());
        responseDto.setPriceAdjustment(variant.getPriceAdjustment());
        responseDto.setCalculatedPriceAdjustment(pricingService.getVariantFinalPriceInSYP(variant));

        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/variants/{variantId}")
    public ResponseEntity<String> deleteProductVariant(@PathVariable Long variantId) {
        productService.deleteProductVariant(variantId);
        return ResponseEntity.ok("تم حذف النوع بنجاح");
    }
}