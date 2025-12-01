package com.deliveryapp.repository;

import com.deliveryapp.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByIsActiveTrue();

    // Find stores in a specific category
    List<Store> findByCategoryCategoryId(Long categoryId);

    // Search stores by name
    List<Store> findByNameContainingIgnoreCase(String name);
    // NEW: Find stores by subcategory
    List<Store> findBySubCategorySubcategoryId(Long subcategoryId);
}