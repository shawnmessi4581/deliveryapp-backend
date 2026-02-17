package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "colors")
@Data
public class Color {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long colorId;

    private String name; // e.g., "Red", "Sky Blue"

    private String hexCode; // e.g., "#FF0000"
}