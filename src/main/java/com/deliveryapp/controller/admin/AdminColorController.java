package com.deliveryapp.controller.admin;

import com.deliveryapp.dto.catalog.ColorRequest;
import com.deliveryapp.dto.catalog.ColorResponse;
import com.deliveryapp.service.ColorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/colors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class AdminColorController {

    private final ColorService colorService;

    @GetMapping
    public ResponseEntity<List<ColorResponse>> getAllColors() {
        List<ColorResponse> response = colorService.getAllColors().stream().map(c -> {
            ColorResponse dto = new ColorResponse();
            dto.setColorId(c.getColorId());
            dto.setName(c.getName());
            dto.setHexCode(c.getHexCode());
            return dto;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<String> createColor(@RequestBody ColorRequest request) {
        colorService.createColor(request.getName(), request.getHexCode());
        return ResponseEntity.ok("تمت إضافة اللون");
    }

    @PutMapping("/{id}")
    public ResponseEntity<ColorResponse> updateColor(@PathVariable Long id, @RequestBody ColorRequest request) {
        var updatedColor = colorService.updateColor(id, request.getName(), request.getHexCode());
        ColorResponse dto = new ColorResponse();
        dto.setColorId(updatedColor.getColorId());
        dto.setName(updatedColor.getName());
        dto.setHexCode(updatedColor.getHexCode());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteColor(@PathVariable Long id) {
        colorService.deleteColor(id);
        return ResponseEntity.ok("تم حذف اللون");
    }
}