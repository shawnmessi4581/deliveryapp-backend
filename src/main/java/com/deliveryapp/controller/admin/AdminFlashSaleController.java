package com.deliveryapp.controller.admin;

import com.deliveryapp.dto.PagedResponse;
import com.deliveryapp.dto.flashsale.FlashSaleRequest;
import com.deliveryapp.dto.flashsale.FlashSaleResponse;
import com.deliveryapp.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Admin CRUD for flash sales.
 * Admins can manage flash sales for any store or globally (store = null).
 */
@RestController
@RequestMapping("/api/admin/flash-sales")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin – Flash Sales", description = "Admin CRUD for flash sales with auto-managed coupon codes")
public class AdminFlashSaleController {

    private final FlashSaleService flashSaleService;

    private Long currentAdminId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaim("userId");
    }

    // ── GET all (paged) ──────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all flash sales (paged)")
    public ResponseEntity<PagedResponse<FlashSaleResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<FlashSaleResponse> result = flashSaleService.adminGetAll(pageable);

        return ResponseEntity.ok(new PagedResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast()));
    }

    // ── GET by ID ────────────────────────────────────────────────────────────

    @GetMapping("/{flashSaleId}")
    @Operation(summary = "Get a single flash sale by ID")
    public ResponseEntity<FlashSaleResponse> getById(@PathVariable Long flashSaleId) {
        return ResponseEntity.ok(flashSaleService.adminGetById(flashSaleId));
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a flash sale", description = "Creates a new flash sale and auto-generates (or uses the provided) coupon code. "
            +
            "The backing Coupon entity is created and managed automatically.")
    public ResponseEntity<FlashSaleResponse> create(@Valid @RequestBody FlashSaleRequest request) {
        return ResponseEntity.ok(flashSaleService.adminCreate(request, currentAdminId()));
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @PutMapping("/{flashSaleId}")
    @Operation(summary = "Update a flash sale", description = "Updates the flash sale and keeps the backing Coupon in sync automatically.")
    public ResponseEntity<FlashSaleResponse> update(
            @PathVariable Long flashSaleId,
            @Valid @RequestBody FlashSaleRequest request) {
        return ResponseEntity.ok(flashSaleService.adminUpdate(flashSaleId, request, currentAdminId()));
    }

    // ── TOGGLE STATUS ────────────────────────────────────────────────────────

    @PatchMapping("/{flashSaleId}/status")
    @Operation(summary = "Toggle flash sale active/inactive", description = "Syncs the backing Coupon active status as well.")
    public ResponseEntity<FlashSaleResponse> toggleStatus(@PathVariable Long flashSaleId) {
        return ResponseEntity.ok(flashSaleService.adminToggleStatus(flashSaleId));
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{flashSaleId}")
    @Operation(summary = "Delete a flash sale", description = "Deletes the flash sale AND its backing Coupon entity permanently.")
    public ResponseEntity<String> delete(@PathVariable Long flashSaleId) {
        flashSaleService.adminDelete(flashSaleId);
        return ResponseEntity.ok("تم حذف الفلاش سيل والكوبون المرتبط به بنجاح");
    }
}
