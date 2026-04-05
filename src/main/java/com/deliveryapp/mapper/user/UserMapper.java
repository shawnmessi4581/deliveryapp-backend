package com.deliveryapp.mapper.user;

import com.deliveryapp.dto.order.OrderCustomerResponse;
import com.deliveryapp.dto.user.UserProfileResponse;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.User;
import com.deliveryapp.entity.UserAddress;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final UrlUtil urlUtil;

    public UserResponse toUserResponse(User user) {
        UserResponse dto = new UserResponse();
        dto.setUserId(user.getUserId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setUserType(user.getUserType());
        dto.setIsActive(user.getIsActive());
        dto.setProfileImage(urlUtil.getFullUrl(user.getProfileImage()));

        if (user.getUserType() == UserType.DRIVER) {
            dto.setVehicleType(user.getVehicleType());
            dto.setVehicleNumber(user.getVehicleNumber());
            dto.setIsAvailable(user.getIsAvailable());
            dto.setRating(user.getRating() != null ? user.getRating() : 5.0);
            dto.setTotalDeliveries(user.getTotalDeliveries() != null ? user.getTotalDeliveries() : 0);
            dto.setCurrentLocationLat(user.getCurrentLocationLat());
            dto.setCurrentLocationLng(user.getCurrentLocationLng());
        }
        return dto;
    }

    public OrderCustomerResponse toOrderCustomerResponse(User user) {
        OrderCustomerResponse dto = new OrderCustomerResponse();
        dto.setUserId(user.getUserId());
        dto.setName(user.getName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setProfileAddress(user.getAddress());
        return dto;
    }

    // --- NEW: Map User Profile (App View) ---
    public UserProfileResponse toUserProfileResponse(User user, List<UserAddress> addresses) {
        UserProfileResponse dto = new UserProfileResponse();
        dto.setUserId(user.getUserId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setUserType(user.getUserType().name());

        // Use UrlUtil to get full image link
        dto.setProfileImage(urlUtil.getFullUrl(user.getProfileImage()));

        dto.setPrimaryAddress(user.getAddress());
        dto.setSavedAddresses(addresses);

        // Driver Fields
        if (user.getUserType() == UserType.DRIVER) {
            dto.setIsAvailable(user.getIsAvailable());
            dto.setTotalDeliveries(user.getTotalDeliveries());
            dto.setVehicleType(user.getVehicleType());
            dto.setRating(user.getRating());
        }

        return dto;
    }
}