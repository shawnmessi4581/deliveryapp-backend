package com.deliveryapp.repository;

import com.deliveryapp.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // Get only active categories sorted by display order
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();
}