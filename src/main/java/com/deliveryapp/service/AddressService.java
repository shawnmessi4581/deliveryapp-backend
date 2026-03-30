package com.deliveryapp.service;

import com.deliveryapp.dto.user.AddressRequest;
import com.deliveryapp.entity.User;
import com.deliveryapp.entity.UserAddress;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.UserAddressRepository;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final UserAddressRepository addressRepository;
    private final UserRepository userRepository;

    public List<UserAddress> getUserAddresses(Long userId) {
        return addressRepository.findByUserUserId(userId);
    }

    @Transactional
    public UserAddress addAddress(Long userId, AddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود"));

        UserAddress address = new UserAddress();
        address.setUser(user);
        address.setLabel(request.getLabel());
        address.setAddressLine(request.getAddressLine());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.unsetDefaultAddressesForUser(user.getUserId());
        }
        address.setIsDefault(request.getIsDefault() != null && request.getIsDefault());

        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(Long addressId) {
        // 1. Find the address
        UserAddress addressToDelete = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود برقم: " + addressId));

        Long userId = addressToDelete.getUser().getUserId();
        boolean wasDefault = Boolean.TRUE.equals(addressToDelete.getIsDefault());

        // 2. Delete the address
        addressRepository.delete(addressToDelete);
        addressRepository.flush(); // Ensure it's removed from DB context before querying again

        // 3. Auto-assign a new default if necessary
        if (wasDefault) {
            // Fetch remaining addresses for this user
            List<UserAddress> remainingAddresses = addressRepository.findByUserUserId(userId);

            // If there are other addresses, make the first one the new default
            if (!remainingAddresses.isEmpty()) {
                UserAddress newDefault = remainingAddresses.get(0);
                newDefault.setIsDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        // 1. Verify the address exists and belongs to the user
        UserAddress selectedAddress = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود"));

        if (!selectedAddress.getUser().getUserId().equals(userId)) {
            throw new InvalidDataException("هذا العنوان لا يخص المستخدم المحدد.");
        }

        // 2. Set ALL addresses for this user to isDefault = false
        addressRepository.unsetDefaultAddressesForUser(userId);

        // 3. Set the chosen address to isDefault = true
        selectedAddress.setIsDefault(true);
        addressRepository.save(selectedAddress);
    }
}