package com.deliveryapp.repository;

import com.deliveryapp.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    // Find the latest OTP for a phone number
    Optional<OtpVerification> findFirstByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    // Verify specific OTP
    Optional<OtpVerification> findByPhoneNumberAndOtpCode(String phoneNumber, String otpCode);
    // Clean up old OTPs
    void deleteByPhoneNumber(String phoneNumber);

}