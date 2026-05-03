package com.deliveryapp.repository;

import com.deliveryapp.entity.OtpVerification;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findFirstByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    Optional<OtpVerification> findByPhoneNumberAndOtpCode(String phoneNumber, String otpCode);

    @Modifying
    @Transactional
    void deleteByPhoneNumber(String phoneNumber);
}