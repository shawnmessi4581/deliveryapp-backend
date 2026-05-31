package com.deliveryapp.repository;

import com.deliveryapp.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    // List<Store> findByIsActiveTrue();

    // Find stores in a specific category
    // List<Store> findByCategoryCategoryId(Long categoryId);

    // Search stores by name
    List<Store> findByNameContainingIgnoreCase(String name);

    // NEW: Find stores by subcategory
    // List<Store> findBySubCategorySubcategoryId(Long subcategoryId);

    List<Store> findAllByOrderByDisplayOrderAsc();

    List<Store> findByIsActiveTrueOrderByDisplayOrderAsc();

    // For User App: Only Active Stores, ordered by displayOrder
    List<Store> findByCategoryCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(Long categoryId);

    List<Store> findBySubCategorySubcategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(Long subCategoryId);

    List<Store> findBySubCategorySubcategoryIdOrderByDisplayOrderAsc(Long subCategoryId);

    @Query("SELECT s FROM Store s WHERE s.isActive = true AND " +
            "(LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY s.rating DESC, s.totalOrders DESC")
    List<Store> searchStoresGlobal(String keyword);

}