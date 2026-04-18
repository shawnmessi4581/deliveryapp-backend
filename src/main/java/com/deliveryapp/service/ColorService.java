package com.deliveryapp.service;

import com.deliveryapp.entity.Color;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.ColorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ColorService {

    private final ColorRepository colorRepository;

    public List<Color> getAllColors() {
        return colorRepository.findAll();
    }

    public Color createColor(String name, String hexCode) {
        Color color = new Color();
        color.setName(name);
        color.setHexCode(hexCode);
        return colorRepository.save(color);
    }

    @Transactional
    public Color updateColor(Long id, String name, String hexCode) {
        Color color = colorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("اللون غير موجود برقم: " + id));

        if (name != null && !name.trim().isEmpty()) {
            color.setName(name);
        }
        if (hexCode != null && !hexCode.trim().isEmpty()) {
            color.setHexCode(hexCode);
        }

        return colorRepository.save(color);
    }

    public void deleteColor(Long id) {
        colorRepository.deleteById(id);
    }
}