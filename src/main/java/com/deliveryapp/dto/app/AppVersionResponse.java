package com.deliveryapp.dto.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppVersionResponse {
    private String version;
    private LocalDateTime updatedAt;
}
