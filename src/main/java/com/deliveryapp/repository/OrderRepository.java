package com.deliveryapp.repository;

import com.deliveryapp.entity.Order;
import com.deliveryapp.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    // --- NEW ADMIN METHODS (Desc = Newest First) ---

    // 1. No filters
    List<Order> findAllByOrderByCreatedAtDesc();

    // 2. Filter by Status
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    // 3. Filter by Date Range
    List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    // 4. Filter by Status AND Date Range
    List<Order> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(OrderStatus status, LocalDateTime start, LocalDateTime end);

    // 1. Get All Driver Orders (History)
    List<Order> findByDriverUserIdOrderByCreatedAtDesc(Long driverId);

    // 2. Get Driver Orders by specific status (e.g., Active ones)
    List<Order> findByDriverUserIdAndStatusInOrderByCreatedAtDesc(Long driverId, List<OrderStatus> statuses);
}