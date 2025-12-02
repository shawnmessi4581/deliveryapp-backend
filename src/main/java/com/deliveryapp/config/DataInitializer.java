package com.deliveryapp.config;

import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // 1. Create ADMIN User
        if (userRepository.findByPhoneNumber("0000000000").isEmpty()) {
            User admin = new User();
            admin.setName("Super Admin");
            admin.setPhoneNumber("0000000000"); // Login with this
            admin.setEmail("admin@deliveryapp.com");
            admin.setPassword(passwordEncoder.encode("admin123")); // Password
            admin.setUserType(UserType.ADMIN);
            admin.setIsActive(true);
            admin.setCreatedAt(LocalDateTime.now());

            userRepository.save(admin);
            System.out.println("✅ Admin user created: Phone=0000000000, Pass=admin123");
        }

        // 2. Create Normal User (CUSTOMER)
        if (userRepository.findByPhoneNumber("1111111111").isEmpty()) {
            User customer = new User();
            customer.setName("John Customer");
            customer.setPhoneNumber("1111111111"); // Login with this
            customer.setEmail("john@example.com");
            customer.setPassword(passwordEncoder.encode("user123")); // Password
            customer.setUserType(UserType.CUSTOMER);
            customer.setAddress("123 Main Street, Apt 4B");
            customer.setLatitude(30.0444);
            customer.setLongitude(31.2357);
            customer.setIsActive(true);
            customer.setCreatedAt(LocalDateTime.now());

            userRepository.save(customer);
            System.out.println("✅ Customer user created: Phone=1111111111, Pass=user123");
        }

        // 3. Create DRIVER User
        if (userRepository.findByPhoneNumber("2222222222").isEmpty()) {
            User driver = new User();
            driver.setName("Mike Driver");
            driver.setPhoneNumber("2222222222"); // Login with this
            driver.setEmail("driver@deliveryapp.com");
            driver.setPassword(passwordEncoder.encode("driver123")); // Password
            driver.setUserType(UserType.DRIVER);

            // Driver Specific Fields
            driver.setVehicleType("Motorcycle");
            driver.setVehicleNumber("ABC-1234");
            driver.setIsAvailable(true);
            driver.setCurrentLocationLat(30.0500);
            driver.setCurrentLocationLng(31.2400);
            driver.setRating(5.0);
            driver.setTotalDeliveries(0);

            driver.setIsActive(true);
            driver.setCreatedAt(LocalDateTime.now());

            userRepository.save(driver);
            System.out.println("✅ Driver user created: Phone=2222222222, Pass=driver123");
        }
    }
}