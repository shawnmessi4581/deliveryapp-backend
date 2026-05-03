package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.StoreRequest;
import com.deliveryapp.entity.Category;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.SubCategory;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.CategoryRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.SubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final FileStorageService fileStorageService;

    // ================= PUBLIC / CATALOG =================
    public List<Store> getAllActiveStores() {
        return storeRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    public Store getStoreById(Long id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود برقم: " + id));
    }

    public List<Store> getStoresByCategory(Long categoryId) {
        return storeRepository.findByCategoryCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(categoryId);
    }

    public List<Store> getStoresBySubCategory(Long subCategoryId) {
        if (!subCategoryRepository.existsById(subCategoryId)) {
            throw new ResourceNotFoundException("الفئة الفرعية غير موجودة برقم: " + subCategoryId);
        }
        return storeRepository.findBySubCategorySubcategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(subCategoryId);
    }

    // ================= ADMIN CRUD =================
    public List<Store> getAllStores() {
        return storeRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional
    public Store createStore(StoreRequest request, MultipartFile logo, MultipartFile cover) {
        Store store = new Store();
        store.setName(request.getName());
        store.setDescription(request.getDescription());
        store.setPhone(request.getPhone());
        store.setAddress(request.getAddress());
        store.setLatitude(request.getLatitude());
        store.setLongitude(request.getLongitude());
        store.setDeliveryFeeKM(request.getDeliveryFeeKM());
        store.setMinimumOrder(request.getMinimumOrder());
        store.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime());
        store.setIsActive(true);
        store.setCreatedAt(LocalDateTime.now());
        store.setRating(5.0);
        store.setTotalOrders(0);
        store.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId()).orElseThrow();
            store.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId()).orElseThrow();
            store.setSubCategory(sub);
        }

        if (logo != null && !logo.isEmpty()) {
            store.setLogo(fileStorageService.storeFile(logo, "stores"));
        }
        if (cover != null && !cover.isEmpty()) {
            store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
        }

        store.setOpeningTime(request.getOpeningTime());
        store.setClosingTime(request.getClosingTime());
        store.setCommissionPercentage(
                request.getCommissionPercentage() != null ? request.getCommissionPercentage() : 0.0);
        store.setMinimumDeliveryFee(request.getMinimumDeliveryFee() != null ? request.getMinimumDeliveryFee() : 0.0);
        return storeRepository.save(store);
    }

    @Transactional
    public Store updateStore(Long id, StoreRequest request, Boolean isActive, MultipartFile logo, MultipartFile cover) {
        Store store = getStoreById(id);

        if (request.getName() != null)
            store.setName(request.getName());
        if (request.getDescription() != null)
            store.setDescription(request.getDescription());
        if (request.getPhone() != null)
            store.setPhone(request.getPhone());
        if (request.getAddress() != null)
            store.setAddress(request.getAddress());
        if (request.getLatitude() != null)
            store.setLatitude(request.getLatitude());
        if (request.getLongitude() != null)
            store.setLongitude(request.getLongitude());
        if (request.getDeliveryFeeKM() != null)
            store.setDeliveryFeeKM(request.getDeliveryFeeKM());
        if (request.getMinimumOrder() != null)
            store.setMinimumOrder(request.getMinimumOrder());
        if (request.getEstimatedDeliveryTime() != null)
            store.setEstimatedDeliveryTime(request.getEstimatedDeliveryTime());

        if (isActive != null)
            store.setIsActive(isActive);

        if (request.getDisplayOrder() != null)
            store.setDisplayOrder(request.getDisplayOrder());
        if (request.getOpeningTime() != null)
            store.setOpeningTime(request.getOpeningTime());
        if (request.getClosingTime() != null)
            store.setClosingTime(request.getClosingTime());
        if (request.getIsBusy() != null)
            store.setIsBusy(request.getIsBusy());

        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("الفئة غير موجودة"));
            store.setCategory(cat);
        }
        // 🟢 FIX: Handle removing SubCategory
        if (request.getSubCategoryId() != null) {
            // If the frontend sends 0 or -1, it means "Remove the SubCategory"
            if (request.getSubCategoryId() <= 0) {
                store.setSubCategory(null);
            } else {
                SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("الفئة الفرعية غير موجودة"));
                store.setSubCategory(sub);
            }
        }
        if (logo != null && !logo.isEmpty()) {
            if (store.getLogo() != null)
                fileStorageService.deleteFile(store.getLogo());
            store.setLogo(fileStorageService.storeFile(logo, "stores"));
        }
        if (cover != null && !cover.isEmpty()) {
            if (store.getCoverImage() != null)
                fileStorageService.deleteFile(store.getCoverImage());
            store.setCoverImage(fileStorageService.storeFile(cover, "stores"));
        }
        if (request.getCommissionPercentage() != null) {
            store.setCommissionPercentage(request.getCommissionPercentage());
        }
        if (request.getMinimumDeliveryFee() != null)
            store.setMinimumDeliveryFee(request.getMinimumDeliveryFee());
        return storeRepository.save(store);
    }

    public void deleteStore(Long id) {
        Store store = getStoreById(id);
        if (store.getLogo() != null)
            fileStorageService.deleteFile(store.getLogo());
        if (store.getCoverImage() != null)
            fileStorageService.deleteFile(store.getCoverImage());
        storeRepository.deleteById(id);
    }
}