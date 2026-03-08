package com.deliveryapp.controller;

import com.deliveryapp.dto.user.AddressRequest;
import com.deliveryapp.dto.user.AddressResponse;
import com.deliveryapp.entity.UserAddress;
import com.deliveryapp.mapper.user.AddressMapper; // Import Mapper
import com.deliveryapp.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final AddressMapper addressMapper; // Inject Mapper

    @GetMapping("/{userId}")
    public ResponseEntity<List<AddressResponse>> getUserAddresses(@PathVariable Long userId) {
        List<AddressResponse> response = addressService.getUserAddresses(userId).stream()
                .map(addressMapper::toAddressResponse) // Convert to DTO
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}")
    public ResponseEntity<AddressResponse> addAddress(@PathVariable Long userId, @RequestBody AddressRequest request) {
        UserAddress savedAddress = addressService.addAddress(userId, request);
        return ResponseEntity.ok(addressMapper.toAddressResponse(savedAddress)); // Convert to DTO
    }

    @PatchMapping("/{userId}/default/{addressId}")
    public ResponseEntity<String> setDefaultAddress(
            @PathVariable Long userId,
            @PathVariable Long addressId) {

        addressService.setDefaultAddress(userId, addressId);
        return ResponseEntity.ok("Default address updated successfully");
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<String> deleteAddress(@PathVariable Long addressId) {
        addressService.deleteAddress(addressId);
        return ResponseEntity.ok("Address deleted");
    }
}