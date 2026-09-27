package com.deliveryapp.repository;

import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 🔍 Admin: paginated search with optional keyword + optional UserType filter.
     * Passing null for keyword or userType disables that filter.
     */
    @Query("SELECT u FROM User u WHERE " +
            "(:keyword IS NULL OR " +
            "  LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "  LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "  LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:userType IS NULL OR u.userType = :userType)")
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("userType") UserType userType,
            Pageable pageable);

    // Legacy non-paginated search (kept for backward-compat if used elsewhere)
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchUsers(@Param("keyword") String keyword);

}