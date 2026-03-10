package com.deliveryapp.service;

import com.deliveryapp.exception.InvalidDataException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String rootDir;

    /**
     * Stores a file in a specific sub-directory.
     * @param file The file to upload
     * @param subDirectory The folder name (e.g., "stores", "categories", "products")
     * @return The public URL to access the file
     */
    public String storeFile(MultipartFile file, String subDirectory) {
        try {
            // 1. Validate file
            if (file.isEmpty()) {
                throw new InvalidDataException("Cannot upload empty file");
            }

            // 2. Validate file type (Images only)
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new InvalidDataException("Only image files are allowed");
            }

            // 3. Prepare Paths
            // Root + SubDirectory (e.g., uploads/products)
            Path uploadPath = Paths.get(rootDir, subDirectory).toAbsolutePath().normalize();

            // 4. Create directory if it doesn't exist
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 5. Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            // Result: "uuid-1234.jpg"
            String newFilename = UUID.randomUUID().toString() + fileExtension;

            // 6. Store file
            Path targetLocation = uploadPath.resolve(newFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // 7. Return the URL (e.g., /uploads/products/uuid.jpg)
            return "/uploads/" + subDirectory + "/" + newFilename;

        } catch (IOException ex) {
            throw new InvalidDataException("Could not store file. Please try again! Error: " + ex.getMessage());
        }
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) return;

        try {
            // fileUrl example: /uploads/products/abc.jpg
            // We need to remove "/uploads/" to find the path relative to root
            String relativePath = fileUrl.replace("/uploads/", "");

            Path filePath = Paths.get(rootDir).resolve(relativePath).normalize();

            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            System.err.println("Could not delete file: " + fileUrl);
        }
    }
    /**
     * Stores the APK file as "Allin.apk" so the download link never changes.
     */
    public String storeApkFile(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new com.deliveryapp.exception.InvalidDataException("Cannot upload empty APK file");
            }

            // Ensure it's an APK
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".apk")) {
                throw new com.deliveryapp.exception.InvalidDataException("Only .apk files are allowed");
            }

            // Target folder: /uploads/app/
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(rootDir, "app").toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            // Target file: /uploads/app/Allin.apk
            java.nio.file.Path targetLocation = uploadPath.resolve("Allin.apk");
            
            // StandardCopyOption.REPLACE_EXISTING ensures the old app is overwritten
            java.nio.file.Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/app/Allin.apk";

        } catch (java.io.IOException ex) {
            throw new com.deliveryapp.exception.InvalidDataException("Could not store APK file. " + ex.getMessage());
        }
    }
}