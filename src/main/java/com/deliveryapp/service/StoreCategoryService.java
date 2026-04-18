package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.StoreCategoryRequest;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.StoreCategory;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.StoreCategoryRepository;
import com.deliveryapp.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreCategoryService {

    private final StoreCategoryRepository storeCategoryRepository;
    private final StoreRepository storeRepository;

    // =================================================================================
    // PUBLIC / USER APP
    // =================================================================================

    // Get only ACTIVE categories for a specific store (for the customer menu tabs)
    public List<StoreCategory> getActiveCategoriesForStore(Long storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new ResourceNotFoundException("Store not found with id: " + storeId);
        }
        return storeCategoryRepository.findByStoreStoreIdAndIsActiveTrueOrderByDisplayOrderAsc(storeId);
    }

    // =================================================================================
    // ADMIN DASHBOARD
    // =================================================================================

    // Get ALL categories for a specific store (including inactive ones)
    public List<StoreCategory> getAllCategoriesForStore(Long storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new ResourceNotFoundException("Store not found with id: " + storeId);
        }
        return storeCategoryRepository.findByStoreStoreIdOrderByDisplayOrderAsc(storeId);
    }

    @Transactional
    public StoreCategory createStoreCategory(StoreCategoryRequest request) {
        if (request.getStoreId() == null)
            throw new InvalidDataException("Store ID is required");

        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        StoreCategory category = new StoreCategory();
        category.setStore(store);
        category.setName(request.getName());
        category.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        category.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        return storeCategoryRepository.save(category);
    }

    @Transactional
    public StoreCategory updateStoreCategory(Long id, StoreCategoryRequest request) {
        StoreCategory category = storeCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store Category not found with id: " + id));

        // Note: We usually don't allow changing the Store ID after creation to prevent
        // moving sections between stores
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            category.setName(request.getName());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        return storeCategoryRepository.save(category);
    }

    @Transactional
    public void deleteStoreCategory(Long id) {
        if (!storeCategoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Store Category not found with id: " + id);
        }

        // IMPORTANT: If products are linked to this category, you should either:
        // 1. Throw an exception (prevent deletion)
        // 2. Or set the products' store_category_id to null before deleting.
        // For safety, let the database throw a constraint violation if products exist,
        // or ensure your Product entity handles the cascade/nullification.

        storeCategoryRepository.deleteById(id);
    }
}