package com.deliveryapp.util;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class UrlUtil {

    /**
     * Converts a relative path (e.g., "/uploads/img.jpg")
     * into a full URL (e.g., "http://localhost:8080/uploads/img.jpg").
     */
    public String getFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        if (relativePath.startsWith("http")) return relativePath; // Already a full URL

        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(relativePath)
                    .toUriString();
        } catch (Exception e) {
            // Fallback for cases where RequestContext is unavailable (e.g., background jobs)
            return relativePath;
        }
    }
}