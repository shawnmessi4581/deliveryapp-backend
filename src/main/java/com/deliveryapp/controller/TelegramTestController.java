package com.deliveryapp.controller;

import com.deliveryapp.service.TelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramTestController {

    private final TelegramService telegramService;

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testTelegram(
            @RequestParam(value = "chatId", required = false) String chatId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "parseMode", required = false) String parseMode,
            @RequestBody(required = false) Map<String, String> requestBody) {

        Map<String, Object> response = new HashMap<>();

        // Support both request body and query/form parameters
        String targetChatId = chatId;
        String targetMessage = message;
        String targetParseMode = parseMode;

        if (requestBody != null) {
            if (requestBody.containsKey("chatId")) {
                targetChatId = requestBody.get("chatId");
            }
            if (requestBody.containsKey("message")) {
                targetMessage = requestBody.get("message");
            }
            if (requestBody.containsKey("parseMode")) {
                targetParseMode = requestBody.get("parseMode");
            }
        }

        if (targetChatId == null || targetChatId.isBlank()) {
            response.put("success", false);
            response.put("error", "chatId parameter/property is required");
            return ResponseEntity.badRequest().body(response);
        }

        if (targetMessage == null || targetMessage.isBlank()) {
            response.put("success", false);
            response.put("error", "message parameter/property is required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String telegramResponse = telegramService.sendTestMessage(targetChatId, targetMessage, targetParseMode);
            response.put("success", true);
            response.put("telegramResponse", telegramResponse);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorType", e.getClass().getName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
