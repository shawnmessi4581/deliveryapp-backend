package com.deliveryapp.repository;

import com.deliveryapp.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByProductProductId(Long productId);

    List<OrderItem> findByVariantVariantId(Long variantId);
}