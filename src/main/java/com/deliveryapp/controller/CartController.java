package com.deliveryapp.controller;

import com.deliveryapp.dto.cart.AddToCartRequest;
import com.deliveryapp.dto.cart.CartItemResponse;
import com.deliveryapp.dto.cart.CartResponse;
import com.deliveryapp.entity.Cart;
import com.deliveryapp.entity.CartItem;
import com.deliveryapp.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // Get Cart
    @GetMapping("/{userId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable Long userId) {
        Cart cart = cartService.getCartByUser(userId);
        return ResponseEntity.ok(mapToCartResponse(cart));
    }

    // Add Item to Cart
    @PostMapping("/add")
    public ResponseEntity<CartResponse> addToCart(@RequestBody AddToCartRequest request) {
        Cart cart = cartService.addToCart(
                request.getUserId(),
                request.getProductId(),
                request.getVariantId(),
                request.getQuantity(),
                request.getNotes()
        );
        return ResponseEntity.ok(mapToCartResponse(cart));
    }

    // Remove Item
    @DeleteMapping("/item/{cartItemId}")
    public ResponseEntity<String> removeCartItem(@PathVariable Long cartItemId) {
        cartService.removeFromCart(cartItemId);
        return ResponseEntity.ok("Item removed from cart successfully");
    }

    // Clear Cart
    @DeleteMapping("/{userId}")
    public ResponseEntity<String> clearCart(@PathVariable Long userId) {
        cartService.clearCart(userId);
        return ResponseEntity.ok("Cart cleared successfully");
    }

    // --- Manual Mapping Logic (Entity -> DTO) ---
    private CartResponse mapToCartResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setCartId(cart.getCartId());

        if (cart.getStore() != null) {
            response.setStoreId(cart.getStore().getStoreId());
            response.setStoreName(cart.getStore().getName());
        }

        List<CartItemResponse> itemResponses;
        if (cart.getItems() != null && !cart.getItems().isEmpty()) {
            itemResponses = cart.getItems().stream().map(item -> {
                CartItemResponse itemRes = new CartItemResponse();
                itemRes.setCartItemId(item.getCartItemId());
                itemRes.setProductId(item.getProduct().getProductId());
                itemRes.setProductName(item.getProduct().getName());
                itemRes.setQuantity(item.getQuantity());
                itemRes.setNotes(item.getNotes());

                // Calculate price logic
                double price = item.getProduct().getBasePrice();
                if (item.getVariant() != null) {
                    price += item.getVariant().getPriceAdjustment();
                    itemRes.setVariantName(item.getVariant().getVariantValue());
                }
                itemRes.setUnitPrice(price);
                itemRes.setTotalPrice(price * item.getQuantity());

                return itemRes;
            }).collect(Collectors.toList());
        } else {
            itemResponses = Collections.emptyList();
        }

        response.setItems(itemResponses);

        // Calculate total for display
        double total = itemResponses.stream().mapToDouble(CartItemResponse::getTotalPrice).sum();
        response.setTotalEstimatedPrice(total);

        return response;
    }
}