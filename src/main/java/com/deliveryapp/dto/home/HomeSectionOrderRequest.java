package com.deliveryapp.dto.home;

import lombok.Data;

import java.util.List;

/** Drag-and-drop reorder of the home sections. */
@Data
public class HomeSectionOrderRequest {

    /** Every section id exactly once, in the new home order. */
    private List<Long> sectionIds;
}
