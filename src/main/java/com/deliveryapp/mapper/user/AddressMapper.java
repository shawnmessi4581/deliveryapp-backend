package com.deliveryapp.mapper.user;

import com.deliveryapp.dto.user.AddressResponse;
import com.deliveryapp.entity.UserAddress;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public AddressResponse toAddressResponse(UserAddress address) {
        AddressResponse dto = new AddressResponse();
        dto.setAddressId(address.getAddressId());
        dto.setAddressLine(address.getAddressLine());
        dto.setLabel(address.getLabel());
        dto.setLatitude(address.getLatitude());
        dto.setLongitude(address.getLongitude());
        dto.setIsDefault(address.getIsDefault());
        return dto;
    }
}