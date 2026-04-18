package com.deliveryapp.controller.catalog;

import com.deliveryapp.dto.catalog.CategoryResponse;
import com.deliveryapp.dto.catalog.SubCategoryResponse;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalog/categories")
@RequiredArgsConstructor
public class CatalogCategoryController {

    private final CategoryService categoryService;
    private final CatalogMapper catalogMapper;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllActiveCategories().stream()
                .map(catalogMapper::toCategoryResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long categoryId) {
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(categoryService.getCategoryById(categoryId)));
    }

    @GetMapping("/{categoryId}/subcategories")
    public ResponseEntity<List<SubCategoryResponse>> getSubCategories(@PathVariable Long categoryId) {
        return ResponseEntity.ok(categoryService.getSubCategoriesByCategoryId(categoryId).stream()
                .map(catalogMapper::toSubCategoryResponse)
                .collect(Collectors.toList()));
    }
}