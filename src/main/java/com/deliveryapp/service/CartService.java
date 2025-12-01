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

        // Exception: Mixing stores
        if (cart.getStore() != null && !cart.getStore().getStoreId().equals(product.getStore().getStoreId())) {
            throw new InvalidDataException("You can only order from one store at a time. Please clear your cart first.");
        }

        if (cart.getStore() == null) {
            cart.setStore(product.getStore());
        }

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
        cart.setStore(null);
        cartRepository.save(cart);
    }
}