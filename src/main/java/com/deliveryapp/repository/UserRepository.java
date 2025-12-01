package com.deliveryapp.repository;

import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Login / Auth lookups
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByEmail(String email);

    // Find specific types of users
    List<User> findByUserType(UserType userType);

    // Find available drivers
    List<User> findByUserTypeAndIsAvailableTrue(UserType userType);
}