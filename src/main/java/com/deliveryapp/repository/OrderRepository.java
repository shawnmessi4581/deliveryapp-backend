package com.deliveryapp.repository;

import com.deliveryapp.entity.Order;
import com.deliveryapp.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Customer history
    List<Order> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    // Store orders (e.g. for restaurant dashboard)
    List<Order> findByStoreStoreId(Long storeId);

    // Driver orders
    List<Order> findByDriverUserId(Long driverId);

    // Find open orders by status (e.g. find all PENDING orders)
    List<Order> findByStatus(OrderStatus status);
}