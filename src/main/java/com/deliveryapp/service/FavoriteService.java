package com.deliveryapp.service;

import com.deliveryapp.entity.Favorite;
import com.deliveryapp.entity.User;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.FavoriteRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;

    public List<Favorite> getUserFavorites(Long userId) {
        return favoriteRepository.findByUserUserId(userId);
    }

    @Transactional
    public Favorite addFavorite(Long userId, String type, Long id) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String itemType = type.toUpperCase();

        if (itemType.equals("STORE")) {
            if (!storeRepository.existsById(id))
                throw new ResourceNotFoundException("Store not found with id: " + id);
        } else if (itemType.equals("PRODUCT")) {
            if (!productRepository.existsById(id))
                throw new ResourceNotFoundException("Product not found with id: " + id);
        } else {
            throw new ResourceNotFoundException("Invalid favorite type. Use STORE or PRODUCT");
        }

        if (favoriteRepository.findByUserUserIdAndFavoritableTypeAndFavoritableId(userId, itemType, id).isPresent()) {
            throw new DuplicateResourceException("This item is already in your favorites");
        }

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setFavoritableType(itemType);
        favorite.setFavoritableId(id);
        favorite.setCreatedAt(LocalDateTime.now());

        return favoriteRepository.save(favorite);
    }

    // NEW: Remove a single favorite item
    @Transactional
    public void removeFavorite(Long userId, Long favoriteId) {
        Favorite favorite = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite item not found with id: " + favoriteId));

        if (!favorite.getUser().getUserId().equals(userId)) {
            // Usually treated as 404 to hide existence, or 403 Forbidden
            throw new ResourceNotFoundException("Favorite item not found or you are not authorized");
        }

        favoriteRepository.delete(favorite);
    }
}