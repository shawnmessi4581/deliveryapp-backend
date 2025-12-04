package com.deliveryapp.service;

import com.deliveryapp.dto.user.AddressRequest;
import com.deliveryapp.entity.User;
import com.deliveryapp.entity.UserAddress;
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserAddress address = new UserAddress();
        address.setUser(user);
        address.setLabel(request.getLabel());
        address.setAddressLine(request.getAddressLine());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setIsDefault(request.getIsDefault() != null && request.getIsDefault());

        return addressRepository.save(address);
    }

    public void deleteAddress(Long addressId) {
        addressRepository.deleteById(addressId);
    }
}