package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.StoreRequest;
import com.deliveryapp.entity.Category;
import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.SubCategory;
import com.deliveryapp.exception.InvalidDataException;
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
        return storeRepository.findBySubCategories_SubcategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(subCategoryId);
    }

    public List<Store> searchStores(String keyword) {
        if (keyword == null || keyword.trim().isEmpty())
            return new java.util.ArrayList<>();
        return storeRepository.searchStoresGlobal(keyword);
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
        if (request.getSubCategoryIds() != null && !request.getSubCategoryIds().isEmpty()) {
            List<SubCategory> subs = subCategoryRepository.findAllById(request.getSubCategoryIds());
            store.setSubCategories(subs);
        } else {
            store.setSubCategories(new java.util.ArrayList<>());
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
        // 🔔 Telegram Chat ID
        if (request.getTelegramChatId() != null)
            store.setTelegramChatId(request.getTelegramChatId());
        // 🆓 Free Delivery
        store.setFreeDelivery(Boolean.TRUE.equals(request.getFreeDelivery()));
        // 🛒 Free Delivery Threshold (null = disabled)
        store.setFreeDeliveryThreshold(request.getFreeDeliveryThreshold());
        // 🔘 Threshold enabled flag
        store.setFreeDeliveryThresholdEnabled(Boolean.TRUE.equals(request.getFreeDeliveryThresholdEnabled()));
        validateFreeDeliveryThreshold(store, request);
        // 💹 Price markup for customers (null = none)
        store.setPriceMarkupPercentage(validMarkup(
                request.getPriceMarkupPercentage() != null ? request.getPriceMarkupPercentage() : 0.0));
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
        // 🟢 FIX: Handle removing SubCategories
        if (request.getSubCategoryIds() != null) {
            if (request.getSubCategoryIds().isEmpty()
                    || (request.getSubCategoryIds().size() == 1 && request.getSubCategoryIds().get(0) <= 0)) {
                store.setSubCategories(new java.util.ArrayList<>());
            } else {
                List<SubCategory> subs = subCategoryRepository.findAllById(request.getSubCategoryIds());
                store.setSubCategories(subs);
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
        // 🔔 Telegram Chat ID (pass empty string to clear, or null to leave unchanged)
        if (request.getTelegramChatId() != null)
            store.setTelegramChatId(request.getTelegramChatId().isBlank() ? null : request.getTelegramChatId());
        // 🆓 Free Delivery (explicit null check: only update if provided)
        if (request.getFreeDelivery() != null)
            store.setFreeDelivery(request.getFreeDelivery());
        // 🛒 Free Delivery Threshold: only updated when sent (use freeDeliveryThresholdEnabled=false to turn it off)
        if (request.getFreeDeliveryThreshold() != null)
            store.setFreeDeliveryThreshold(request.getFreeDeliveryThreshold());
        // 🔘 Threshold enabled flag
        if (request.getFreeDeliveryThresholdEnabled() != null)
            store.setFreeDeliveryThresholdEnabled(request.getFreeDeliveryThresholdEnabled());
        validateFreeDeliveryThreshold(store, request);
        // 💹 Price markup (the vendor endpoint clears this field, so only admins reach here with it)
        if (request.getPriceMarkupPercentage() != null)
            store.setPriceMarkupPercentage(validMarkup(request.getPriceMarkupPercentage()));
        return storeRepository.save(store);
    }

    // 💹 Admin: set the % customers pay on top of this store's prices (0 turns it off)
    @Transactional
    public Store setPriceMarkup(Long storeId, Double percentage) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود برقم: " + storeId));
        store.setPriceMarkupPercentage(validMarkup(percentage));
        return storeRepository.save(store);
    }

    private double validMarkup(Double percentage) {
        if (percentage == null || percentage < 0 || percentage > 100) {
            throw new InvalidDataException("نسبة زيادة السعر يجب أن تكون بين 0 و 100");
        }
        return percentage;
    }

    // A threshold of 0 (or none) while enabled would silently make every order free
    private void validateFreeDeliveryThreshold(Store store, StoreRequest request) {
        if (request.getFreeDeliveryThreshold() != null && request.getFreeDeliveryThreshold() <= 0) {
            throw new InvalidDataException("الحد الأدنى للتوصيل المجاني يجب أن يكون أكبر من صفر");
        }
        if (Boolean.TRUE.equals(request.getFreeDeliveryThresholdEnabled()) && store.getFreeDeliveryThreshold() == null) {
            throw new InvalidDataException("يجب تحديد قيمة الحد الأدنى قبل تفعيل التوصيل المجاني عند الحد");
        }
    }

    public void deleteStore(Long id) {
        Store store = getStoreById(id);
        if (store.getLogo() != null)
            fileStorageService.deleteFile(store.getLogo());
        if (store.getCoverImage() != null)
            fileStorageService.deleteFile(store.getCoverImage());
        storeRepository.deleteById(id);
    }
    // ================= VENDOR APP =================

    @Transactional
    public Store toggleStoreBusyStatus(Long storeId, Boolean isBusy) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود برقم: " + storeId));

        store.setIsBusy(isBusy);
        return storeRepository.save(store);
    }

    // 🆓 Toggle free delivery on/off for a specific store
    @Transactional
    public Store toggleFreeDelivery(Long storeId, Boolean freeDelivery) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود برقم: " + storeId));
        store.setFreeDelivery(freeDelivery);
        return storeRepository.save(store);
    }

    // 🆓 Public: all active stores with any free delivery option
    // (freeDelivery=true OR freeDeliveryThresholdEnabled=true)
    public List<Store> getFreeDeliveryStores() {
        return storeRepository.findFreeDeliveryOrThresholdStores();
    }

}