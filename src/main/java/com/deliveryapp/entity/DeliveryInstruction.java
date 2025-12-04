package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "delivery_instructions")
@Data
public class DeliveryInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String instruction; // e.g., "Meet at door", "Leave with security"

    private Boolean isActive = true;
}