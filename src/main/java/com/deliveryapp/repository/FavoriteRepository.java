package com.deliveryapp.repository;

import com.deliveryapp.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserUserId(Long userId);

    // Check if a specific item is favorited
    Optional<Favorite> findByUserUserIdAndFavoritableTypeAndFavoritableId(
            Long userId, String favoritableType, Long favoritableId);
}