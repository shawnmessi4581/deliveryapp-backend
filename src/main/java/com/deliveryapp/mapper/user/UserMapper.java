package com.deliveryapp.mapper.user;

import com.deliveryapp.dto.order.OrderCustomerResponse;
import com.deliveryapp.dto.user.UserResponse;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}