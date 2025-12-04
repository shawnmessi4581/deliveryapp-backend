package com.deliveryapp.controller;

import com.deliveryapp.entity.DeliveryInstruction;
import com.deliveryapp.repository.DeliveryInstructionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final DeliveryInstructionRepository instructionRepository;

    @GetMapping("/instructions")
    public ResponseEntity<List<DeliveryInstruction>> getInstructions() {
        return ResponseEntity.ok(instructionRepository.findByIsActiveTrue());
    }
}