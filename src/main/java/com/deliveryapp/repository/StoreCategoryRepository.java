package com.deliveryapp.repository;

import com.deliveryapp.entity.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreCategoryRepository extends JpaRepository<StoreCategory, Long> {

    // For Admin: Get all sections in a store
    List<StoreCategory> findByStoreStoreIdOrderByDisplayOrderAsc(Long storeId);

    // For User App: Get only active sections
    List<StoreCategory> findByStoreStoreIdAndIsActiveTrueOrderByDisplayOrderAsc(Long storeId);
}