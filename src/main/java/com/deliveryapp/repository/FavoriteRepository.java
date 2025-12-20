package com.deliveryapp.repository;

import com.deliveryapp.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // Get all favorites for a user
    List<Favorite> findByUserUserId(Long userId);

    // Check if specific item is already favorited
    Optional<Favorite> findByUserUserIdAndFavoritableTypeAndFavoritableId(Long userId, String favoritableType, Long favoritableId);

    // Check existence
    boolean existsByUserUserIdAndFavoritableTypeAndFavoritableId(Long userId, String favoritableType, Long favoritableId);
}