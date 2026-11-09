package com.deliveryapp.controller.admin;

import com.deliveryapp.dto.home.HomeSectionAdminResponse;
import com.deliveryapp.dto.home.HomeSectionOrderRequest;
import com.deliveryapp.dto.home.HomeSectionRequest;
import com.deliveryapp.service.HomeSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin settings for the customer home screen: order, title, icon and on/off of each section.
 * The sections themselves are created automatically, one per type.
 */
@RestController
@RequestMapping("/api/admin/home-sections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Home Layout", description = "Order, titles, icons and on/off of the customer home screen sections")
public class AdminHomeSectionController {

    private final HomeSectionService homeSectionService;

    // ── LIST ─────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "All home sections in display order, including the ones that are off")
    public ResponseEntity<List<HomeSectionAdminResponse>> getAllSections() {
        return ResponseEntity.ok(homeSectionService.getAllSections());
    }

    // ── EDIT ─────────────────────────────────────────────────────────────────

    @PutMapping(value = "/{sectionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Edit a section",
        description = "Multipart form: title, displayOrder, isActive, itemLimit (1–50, sections with show all), " +
                      "maxPrice (under-price section), removeIcon, and an optional 'icon' PNG (max 1 MB). " +
                      "Fields left out keep their value."
    )
    public ResponseEntity<HomeSectionAdminResponse> updateSection(
            @PathVariable Long sectionId,
            @ModelAttribute HomeSectionRequest request,
            @RequestParam(value = "icon", required = false) MultipartFile icon) {
        return ResponseEntity.ok(homeSectionService.updateSection(sectionId, request, icon));
    }

    // ── TOGGLE STATUS ────────────────────────────────────────────────────────

    @PatchMapping("/{sectionId}/status")
    @Operation(summary = "Turn a section on or off")
    public ResponseEntity<HomeSectionAdminResponse> toggleStatus(@PathVariable Long sectionId) {
        return ResponseEntity.ok(homeSectionService.toggleStatus(sectionId));
    }

    // ── REORDER ──────────────────────────────────────────────────────────────

    @PutMapping("/display-order")
    @Operation(
        summary = "Reorder all sections (drag and drop)",
        description = "Body: { \"sectionIds\": [...] } with every section id once, in the new order."
    )
    public ResponseEntity<List<HomeSectionAdminResponse>> reorderSections(@RequestBody HomeSectionOrderRequest request) {
        return ResponseEntity.ok(homeSectionService.reorderSections(request.getSectionIds()));
    }
}
