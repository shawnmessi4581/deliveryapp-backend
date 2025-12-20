package com.deliveryapp.controller;

import com.deliveryapp.dto.favorite.FavoriteListResponse;
import com.deliveryapp.dto.favorite.FavoriteRequest;
import com.deliveryapp.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    // 1. GET Favorites
    @GetMapping("/{userId}")
    public ResponseEntity<FavoriteListResponse> getUserFavorites(@PathVariable Long userId) {
        return ResponseEntity.ok(favoriteService.getUserFavorites(userId));
    }

    // 2. ADD Favorite
    @PostMapping
    public ResponseEntity<String> addFavorite(@RequestBody FavoriteRequest request) {
        favoriteService.addFavorite(request.getUserId(), request.getType(), request.getItemId());
        return ResponseEntity.ok("Added to favorites");
    }

    // 3. REMOVE Favorite (Using Query Params is cleaner here)
    // Usage: DELETE /api/favorites?userId=1&type=STORE&itemId=5
    @DeleteMapping
    public ResponseEntity<String> removeFavorite(
            @RequestParam Long userId,
            @RequestParam String type,
            @RequestParam Long itemId) {

        favoriteService.removeFavorite(userId, type, itemId);
        return ResponseEntity.ok("Removed from favorites");
    }
}