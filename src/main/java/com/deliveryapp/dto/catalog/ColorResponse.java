package com.deliveryapp.dto.catalog;

import lombok.Data;

@Data
public class ColorResponse {
    private Long colorId;
    private String name;
    private String hexCode;
}