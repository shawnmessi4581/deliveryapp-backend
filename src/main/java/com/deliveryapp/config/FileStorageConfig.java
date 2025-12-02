package com.deliveryapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Convert to absolute path
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        String resourceLocation = "file:" + uploadPath.toString() + "/";

        // Handle Windows paths logic (from your snippet)
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            resourceLocation = "file:///" + uploadPath.toString().replace("\\", "/") + "/";
        }

        System.out.println("Serving uploaded files from: " + resourceLocation);

        // Serve anything inside uploads folder (e.g. localhost:8080/uploads/stores/img.jpg)
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourceLocation);
    }
}