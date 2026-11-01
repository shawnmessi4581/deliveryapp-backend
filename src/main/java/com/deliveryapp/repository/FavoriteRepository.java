package com.deliveryapp.repository;

import com.deliveryapp.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // Get all favorites for a user
    List<Favorite> findByUserUserId(Long userId);

    // Check if specific item is already favorited
    Optional<Favorite> findByUserUserIdAndFavoritableTypeAndFavoritableId(Long userId, String favoritableType,
            Long favoritableId);

    // Check existence
    boolean existsByUserUserIdAndFavoritableTypeAndFavoritableId(Long userId, String favoritableType,
            Long favoritableId);

    void deleteByUserUserId(Long userId);

    /** Removes everyone's favorites of the given stores / products (type: "STORE" or "PRODUCT"). */
    @Modifying
    @Query("DELETE FROM Favorite f WHERE UPPER(f.favoritableType) = :type AND f.favoritableId IN :ids")
    void deleteByTypeAndIds(@Param("type") String type, @Param("ids") Collection<Long> ids);
}