package com.deliveryapp.dto.home;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** The customer app's home screen: {@code GET /api/home}. */
@Data
@AllArgsConstructor
public class HomeResponse {

    /** The sections that are on and have items, in display order. */
    private List<HomeSectionResponse> sections;
}
