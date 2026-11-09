package com.deliveryapp.controller;

import com.deliveryapp.dto.home.HomeResponse;
import com.deliveryapp.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (unauthenticated) endpoint for the customer app's home screen, in one request.
 * The admin decides which sections show and in what order (see AdminHomeSectionController).
 */
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@Tag(name = "Home", description = "The customer app's home screen in one request")
public class HomeController {

    private final HomeService homeService;

    /**
     * GET /api/home?userLat=&userLng=
     */
    @GetMapping
    @Operation(
        summary = "Get the home screen",
        description = "The sections the admin turned on, in display order, each with its type, title, icon and first items. " +
                      "Sections with nothing to show are left out. The location (optional) fills the stores' predicted delivery fee. " +
                      "Apps must skip section types they don't know."
    )
    public ResponseEntity<HomeResponse> getHome(
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        return ResponseEntity.ok(homeService.getHome(userLat, userLng));
    }
}
