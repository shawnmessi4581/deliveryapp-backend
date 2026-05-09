package com.deliveryapp.repository;

import com.deliveryapp.entity.Order;
import com.deliveryapp.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

        // ==========================================================
        // ORIGINAL METHODS (Returns List - Kept for compatibility)
        // ==========================================================

        // Customer history
        List<Order> findByUserUserIdOrderByCreatedAtDesc(Long userId);

        // Driver orders
        List<Order> findByDriverUserId(Long driverId);

        // Find open orders by status (e.g. find all PENDING orders)
        List<Order> findByStatus(OrderStatus status);

        // Admin: No filters
        List<Order> findAllByOrderByCreatedAtDesc();

        // Admin: Filter by Status
        List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

        // Admin: Filter by Date Range
        List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

        // Admin: Filter by Status AND Date Range
        List<Order> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(OrderStatus status, LocalDateTime start,
                        LocalDateTime end);

        // Driver: Get All Driver Orders (History)
        List<Order> findByDriverUserIdOrderByCreatedAtDesc(Long driverId);

        // Driver: Get Driver Orders by specific status (e.g., Active ones)
        List<Order> findByDriverUserIdAndStatusInOrderByCreatedAtDesc(Long driverId, List<OrderStatus> statuses);

        // Store: Notice 'Stores' (Plural) and the underscore '_' to traverse the list
        List<Order> findByStores_StoreId(Long storeId);

        // Check if the user has orders currently in progress (For Delete Account logic)
        boolean existsByUserUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

        // ==========================================================
        // NEW PAGINATED METHODS (Returns Page - Used by Controllers)
        // ==========================================================

        // Customer history (Paginated)
        Page<Order> findByUserUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

        // Driver orders (Paginated)
        Page<Order> findByDriverUserIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

        Page<Order> findByDriverUserIdAndStatusInOrderByCreatedAtDesc(Long driverId, List<OrderStatus> statuses,
                        Pageable pageable);

        // ADMIN: Filters (Paginated & Descending)
        Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

        Page<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end,
                        Pageable pageable);

        Page<Order> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(OrderStatus status, LocalDateTime start,
                        LocalDateTime end, Pageable pageable);

}