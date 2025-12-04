package com.deliveryapp.repository;

import com.deliveryapp.entity.DeliveryInstruction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryInstructionRepository extends JpaRepository<DeliveryInstruction, Long> {
    List<DeliveryInstruction> findByIsActiveTrue();

}
