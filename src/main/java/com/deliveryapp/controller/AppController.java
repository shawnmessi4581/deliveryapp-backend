package com.deliveryapp.controller;

import com.deliveryapp.service.FileStorageService;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppController {

    private final FileStorageService fileStorageService;
    private final UrlUtil urlUtil;

    // --- 1. ADMIN: Upload New APK ---
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> uploadApk(@RequestParam("file") MultipartFile file) {
        String relativePath = fileStorageService.storeApkFile(file);
        String fullUrl = urlUtil.getFullUrl(relativePath);
        
        return ResponseEntity.ok("Allin App uploaded successfully. Download link: " + fullUrl);
    }

    // --- 2. PUBLIC: Get Download Link (JSON) ---
    @GetMapping("/link")
    public ResponseEntity<String> getAppDownloadLink() {
        return ResponseEntity.ok(urlUtil.getFullUrl("/uploads/app/Allin.apk"));
    }

    // --- 3. PUBLIC: Direct Download Redirect ---
    @GetMapping("/download")
    public ResponseEntity<Void> downloadApp() {
        String fileUrl = urlUtil.getFullUrl("/uploads/app/Allin.apk");
        
        // This HTTP 302 Redirect tells the browser to instantly start downloading the APK
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(fileUrl))
                .build();
    }
}