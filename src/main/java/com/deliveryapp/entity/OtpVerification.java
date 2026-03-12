package com.deliveryapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verifications")
@Data
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "otp_id")
    private Long otpId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String otpCode;
    private String phoneNumber;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Boolean isVerified;

    @Column(nullable = false)
    private int attemptCount = 0;

    public static final int MAX_ATTEMPTS = 3;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public boolean isLocked() {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void incrementAttempts() {
        this.attemptCount++;
    }
}