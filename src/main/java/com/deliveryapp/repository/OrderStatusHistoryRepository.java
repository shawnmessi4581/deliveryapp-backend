package com.deliveryapp.repository;

import com.deliveryapp.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrderOrderIdOrderByCreatedAtDesc(Long orderId);

    void deleteByOrderOrderId(Long orderId);

}