package com.deliveryapp.enums;

public enum DriverOrderStatus {
    PENDING, // Initial state when admin assigns the order
    ACCEPTED, // Driver accepted
    REJECTED // Driver declined
}