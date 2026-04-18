package com.deliveryapp.controller.admin;

import com.deliveryapp.dto.catalog.*;
import com.deliveryapp.entity.Category;
import com.deliveryapp.entity.SubCategory;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.service.CategoryService;
import lombok.RequiredArgsConstructor;
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
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final CatalogMapper catalogMapper;

    // --- CATEGORIES ---
    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories().stream()
                .map(catalogMapper::toCategoryResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CategoryResponse> createCategory(
            @ModelAttribute CategoryRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        Category cat = categoryService.createCategory(request, image);
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(cat));
    }

    @PutMapping(value = "/categories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @ModelAttribute CategoryRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        Category cat = categoryService.updateCategory(id, request, image);
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(cat));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok("تم حذف الفئة");
    }

    // --- SUBCATEGORIES ---
    @GetMapping("/subcategories")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<SubCategoryResponse>> getAllSubCategories() {
        return ResponseEntity.ok(categoryService.getAllSubCategories().stream()
                .map(catalogMapper::toSubCategoryResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/subcategories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SubCategoryResponse> createSubCategory(
            @ModelAttribute SubCategoryRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        SubCategory sub = categoryService.createSubCategory(request, image);
        return ResponseEntity.ok(catalogMapper.toSubCategoryResponse(sub));
    }

    @PutMapping(value = "/subcategories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<SubCategoryResponse> updateSubCategory(
            @PathVariable Long id,
            @ModelAttribute SubCategoryRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        SubCategory sub = categoryService.updateSubCategory(id, request, image);
        return ResponseEntity.ok(catalogMapper.toSubCategoryResponse(sub));
    }

    @DeleteMapping("/subcategories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteSubCategory(@PathVariable Long id) {
        categoryService.deleteSubCategory(id);
        return ResponseEntity.ok("تم حذف الفئة الفرعية بنجاح");
    }
}