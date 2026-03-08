package com.deliveryapp.repository;

import com.deliveryapp.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserUserId(Long userId);

    // NEW: Quickly set all addresses for a user to NOT default
    @Modifying
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.user.userId = :userId")
    void unsetDefaultAddressesForUser(Long userId);
}
