package com.deliveryapp.service;

import com.deliveryapp.entity.*;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    public Cart getCartByUser(Long userId) {
        return cartRepository.findByUserUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
            Cart newCart = new Cart();
            newCart.setUser(user);
            newCart.setCreatedAt(LocalDateTime.now());
            return cartRepository.save(newCart);
        });
    }

    @Transactional
    public Cart addToCart(Long userId, Long productId, Long variantId, Integer quantity, String notes) {
        Cart cart = getCartByUser(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        // --- UPDATED LOGIC FOR STORE MIXING ---
        // Check if the cart already has a store assigned and it's different from the new product's store
        if (cart.getStore() != null && !cart.getStore().getStoreId().equals(product.getStore().getStoreId())) {

            // Logic: Only strict "No Mixing" if the product is Category 1 (Food)
            Long categoryId = product.getCategory().getCategoryId();

            // If Category is 1 (Food), we BLOCK mixing.
            if (categoryId == 1L) {
                throw new InvalidDataException("You cannot mix stores when ordering Food (Category 1). Please clear your cart first.");
            }

            // If Category is NOT 1 (e.g., Electronics), we ALLOW mixing.
            // Note: The cart.store field will remain set to the FIRST store added.
        }

        // If the cart was empty/had no store, set the store
        if (cart.getStore() == null) {
            cart.setStore(product.getStore());
        }

        // Add or Update Item in Cart
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getProductId().equals(productId) &&
                        (variantId == null || (item.getVariant() != null && item.getVariant().getVariantId().equals(variantId))))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            cartItemRepository.save(item);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            newItem.setNotes(notes);
            newItem.setAddedAt(LocalDateTime.now());

            if (variantId != null) {
                ProductVariant variant = variantRepository.findById(variantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));
                newItem.setVariant(variant);
            }
            cartItemRepository.save(newItem);
        }

        cart.setUpdatedAt(LocalDateTime.now());
        return cartRepository.save(cart);
    }

    @Transactional
    public void removeFromCart(Long cartItemId) {
        if (!cartItemRepository.existsById(cartItemId)) {
            throw new ResourceNotFoundException("Cart Item not found with id: " + cartItemId);
        }
        cartItemRepository.deleteById(cartItemId);
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getCartByUser(userId);
        cartItemRepository.deleteByCartCartId(cart.getCartId());

        // Reset the store association when clearing
        cart.setStore(null);
        cartRepository.save(cart);
    }
}