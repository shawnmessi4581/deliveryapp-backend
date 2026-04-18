package com.deliveryapp.controller.admin;

import com.deliveryapp.entity.DeliveryInstruction;
import com.deliveryapp.repository.DeliveryInstructionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/instructions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class AdminInstructionController {

    private final DeliveryInstructionRepository instructionRepository;

    @GetMapping
    public ResponseEntity<List<DeliveryInstruction>> getAllInstructions() {
        return ResponseEntity.ok(instructionRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<DeliveryInstruction> createInstruction(@RequestParam String text) {
        DeliveryInstruction instruction = new DeliveryInstruction();
        instruction.setInstruction(text);
        instruction.setIsActive(true);
        return ResponseEntity.ok(instructionRepository.save(instruction));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteInstruction(@PathVariable Long id) {
        instructionRepository.deleteById(id);
        return ResponseEntity.ok("تم حذف التعليمات");
    }
}