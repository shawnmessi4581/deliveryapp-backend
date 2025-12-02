package com.deliveryapp.entity;

import com.deliveryapp.enums.UserType;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    private String phoneNumber;
    private String name;
    private String email;
    private String password;

    private String profileImage;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    // Address fields
    private String address;
    private Double latitude;
    private Double longitude;

    // Driver specific fields
    private String vehicleType;
    private String vehicleNumber;
    private Boolean isAvailable;
    private Double currentLocationLat;
    private Double currentLocationLng;
    private Double rating;
    private Integer totalDeliveries;

    private String fcmToken;
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}