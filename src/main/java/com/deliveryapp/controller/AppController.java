package com.deliveryapp.controller;

import com.deliveryapp.dto.app.AppVersionRequest;
import com.deliveryapp.dto.app.AppVersionResponse;
import com.deliveryapp.service.AppSettingService;
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
    private final AppSettingService appSettingService;

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

    // --- 4. ADMIN: Set App Version ---
    @PostMapping("/version")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppVersionResponse> setAppVersion(@RequestBody AppVersionRequest request) {
        if (request.getVersion() == null || request.getVersion().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        AppVersionResponse response = appSettingService.setAppVersion(request.getVersion().trim());
        return ResponseEntity.ok(response);
    }

    // --- 5. PUBLIC: Get App Version ---
    @GetMapping("/version")
    public ResponseEntity<AppVersionResponse> getAppVersion() {
        return ResponseEntity.ok(appSettingService.getAppVersion());
    }
}