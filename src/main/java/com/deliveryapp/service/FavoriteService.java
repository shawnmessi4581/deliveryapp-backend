package com.deliveryapp.service;

import com.deliveryapp.dto.favorite.FavoriteListResponse;
import com.deliveryapp.entity.*;
import com.deliveryapp.exception.DuplicateResourceException;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.mapper.catalog.CatalogMapper; // 1. Import Mapper
import com.deliveryapp.repository.FavoriteRepository;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final CatalogMapper catalogMapper; // 2. Inject Mapper (Replaces UrlUtil)

    // --- GET FAVORITES (Enriched) ---
    public FavoriteListResponse getUserFavorites(Long userId) {
        List<Favorite> favorites = favoriteRepository.findByUserUserId(userId);

        List<Long> storeIds = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();

        // 1. Separate IDs by Type
        for (Favorite fav : favorites) {
            if ("STORE".equalsIgnoreCase(fav.getFavoritableType())) {
                storeIds.add(fav.getFavoritableId());
            } else if ("PRODUCT".equalsIgnoreCase(fav.getFavoritableType())) {
                productIds.add(fav.getFavoritableId());
            }
        }

        FavoriteListResponse response = new FavoriteListResponse();

        // 2. Fetch and Map Stores
        if (!storeIds.isEmpty()) {
            List<Store> stores = storeRepository.findAllById(storeIds);
            response.setFavoriteStores(stores.stream()
                    .map(catalogMapper::toStoreResponse) // Use shared Mapper
                    .collect(Collectors.toList()));
        } else {
            response.setFavoriteStores(Collections.emptyList());
        }

        // 3. Fetch and Map Products
        if (!productIds.isEmpty()) {
            List<Product> products = productRepository.findAllById(productIds);
            response.setFavoriteProducts(products.stream()
                    .map(catalogMapper::toProductResponse) // Use shared Mapper
                    .collect(Collectors.toList()));
        } else {
            response.setFavoriteProducts(Collections.emptyList());
        }

        return response;
    }

    // --- ADD FAVORITE ---
    @Transactional
    public void addFavorite(Long userId, String type, Long itemId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String itemType = type.toUpperCase();

        // 1. Validate Target Exists
        if (itemType.equals("STORE")) {
            if (!storeRepository.existsById(itemId))
                throw new ResourceNotFoundException("Store not found with id: " + itemId);
        } else if (itemType.equals("PRODUCT")) {
            if (!productRepository.existsById(itemId))
                throw new ResourceNotFoundException("Product not found with id: " + itemId);
        } else {
            throw new InvalidDataException("Invalid favorite type. Use STORE or PRODUCT");
        }

        // 2. Check Duplicate
        if (favoriteRepository.existsByUserUserIdAndFavoritableTypeAndFavoritableId(userId, itemType, itemId)) {
            throw new DuplicateResourceException("This item is already in your favorites");
        }

        // 3. Save
        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setFavoritableType(itemType);
        favorite.setFavoritableId(itemId);
        favorite.setCreatedAt(LocalDateTime.now());

        favoriteRepository.save(favorite);
    }

    // --- REMOVE FAVORITE ---
    @Transactional
    public void removeFavorite(Long userId, String type, Long itemId) {
        Favorite favorite = favoriteRepository.findByUserUserIdAndFavoritableTypeAndFavoritableId(
                userId, type.toUpperCase(), itemId
        ).orElseThrow(() -> new ResourceNotFoundException("Favorite not found"));

        favoriteRepository.delete(favorite);
    }
}