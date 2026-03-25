package com.deliveryapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // Add this!
public class DeliveryappApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryappApplication.class, args);
    }

}
