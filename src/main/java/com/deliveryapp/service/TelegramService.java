package com.deliveryapp.service;

import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";
    private static final String DIVIDER = "━━━━━━━━━━━━━━━━━━━━";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    @Value("${telegram.bot.token}")
    private String botToken;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Async
    public void notifyAllStoresOfOrder(Order order) {
        try {
            if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
                log.warn("[Telegram] Order #{} has no items — skipping store notifications.",
                        order != null ? order.getOrderNumber() : "unknown");
                return;
            }

            Map<Long, List<OrderItem>> itemsByStoreId = order.getOrderItems().stream()
                    .filter(item -> item.getProduct() != null && item.getProduct().getStore() != null)
                    .collect(Collectors.groupingBy(item -> item.getProduct().getStore().getStoreId()));

            if (itemsByStoreId.isEmpty()) {
                log.warn("[Telegram] Could not resolve any store for order #{} — skipping.",
                        order.getOrderNumber());
                return;
            }

            for (Map.Entry<Long, List<OrderItem>> entry : itemsByStoreId.entrySet()) {
                List<OrderItem> storeItems = entry.getValue();
                Store store = storeItems.get(0).getProduct().getStore();

                if (store.getTelegramChatId() == null || store.getTelegramChatId().isBlank()) {
                    log.info("[Telegram] Store '{}' has no Telegram Chat ID configured — skipping.",
                            store.getName());
                    continue;
                }

                notifyStoreOfOrder(order, store, storeItems);
            }
        } catch (Exception e) {
            log.error("[Telegram] 🚨 CRITICAL ERROR inside async notifyAllStoresOfOrder for order #{}: {}",
                    (order != null ? order.getOrderNumber() : "unknown"), e.getMessage(), e);
        }
    }

    private void notifyStoreOfOrder(Order order, Store store, List<OrderItem> storeItems) {
        String message = buildStoreMessage(order, store, storeItems);
        sendMessage(store.getTelegramChatId(), message);
    }

    private String buildStoreMessage(Order order, Store store, List<OrderItem> storeItems) {
        StringBuilder sb = new StringBuilder();

        sb.append("🛒 *طلب جديد\\!*\n");
        sb.append("🔢 رقم الطلب: `#").append(order.getOrderNumber()).append("`\n");
        sb.append(DIVIDER).append("\n\n");

        sb.append("🏪 *المتجر:* ").append(escapeMarkdown(store.getName())).append("\n");
        sb.append("📦 *المنتجات:*\n");

        double storeSubtotal = 0.0;

        for (OrderItem item : storeItems) {
            sb.append("\n");

            String productName = escapeMarkdown(
                    item.getProductName() != null ? item.getProductName() : "منتج");

            if (item.getVariantDetails() != null && !item.getVariantDetails().isBlank()) {
                // FIX: the literal "(" and ")" here were never escaped — only their
                // contents went through escapeMarkdown(). Any variant text caused
                // Telegram's MarkdownV2 parser to reject the whole message.
                sb.append("  • ").append(productName)
                        .append(" _\\(").append(escapeMarkdown(item.getVariantDetails())).append("\\)_\n");
            } else {
                sb.append("  • ").append(productName).append("\n");
            }

            if (item.getSelectedColor() != null && item.getSelectedColor().getName() != null) {
                sb.append("    🎨 اللون: ").append(escapeMarkdown(item.getSelectedColor().getName())).append("\n");
            }

            sb.append("    الكمية: ×").append(item.getQuantity())
                    .append("  \\|  السعر: ").append(formatPrice(item.getUnitPrice()))
                    .append("  ←  *").append(formatPrice(item.getTotalPrice())).append("*\n");

            if (item.getNotes() != null && !item.getNotes().isBlank()) {
                sb.append("    📝 _").append(escapeMarkdown(item.getNotes())).append("_\n");
            }

            storeSubtotal += item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;
        }

        sb.append("\n").append(DIVIDER).append("\n");
        sb.append("💰 *إجمالي هذا المتجر:* ").append(formatPrice(storeSubtotal)).append("\n");

        if (order.getDeliveryFee() != null && order.getDeliveryFee() > 0) {
            sb.append("🚗 *رسوم التوصيل:* ").append(formatPrice(order.getDeliveryFee())).append("\n");
        }
        if (order.getDiscountAmount() != null && order.getDiscountAmount() > 0) {
            sb.append("🎁 *الخصم:* \\-").append(formatPrice(order.getDiscountAmount())).append("\n");
        }
        sb.append("💳 *إجمالي الطلب الكلي:* *").append(formatPrice(order.getTotalAmount())).append("*\n");

        sb.append("\n");
        if (order.getDeliveryAddress() != null) {
            sb.append("📍 *العنوان:* ").append(escapeMarkdown(order.getDeliveryAddress())).append("\n");
        }
        if (order.getSelectedInstruction() != null && !order.getSelectedInstruction().isBlank()) {
            sb.append("📋 *تعليمات التوصيل:* ").append(escapeMarkdown(order.getSelectedInstruction())).append("\n");
        }
        if (order.getOrderNote() != null && !order.getOrderNote().isBlank()) {
            sb.append("💬 *ملاحظة العميل:* _").append(escapeMarkdown(order.getOrderNote())).append("_\n");
        }

        if (order.getUser() != null) {
            sb.append("👤 *العميل:* ").append(escapeMarkdown(order.getUser().getName())).append("\n");
            if (order.getUser().getPhoneNumber() != null) {
                sb.append("📞 ").append(escapeMarkdown(order.getUser().getPhoneNumber())).append("\n");
            }
        }

        sb.append("\n");
        LocalDateTime now = order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now();
        sb.append("⏰ ").append(escapeMarkdown(now.format(TIME_FORMATTER)));

        return sb.toString();
    }

    private void sendMessage(String chatId, String text) {
        if (botToken == null || botToken.isBlank() || "YOUR_BOT_TOKEN_HERE".equals(botToken)) {
            log.warn("[Telegram] Bot token is not configured — message not sent to chat {}.", chatId);
            return;
        }

        try {
            String url = TELEGRAM_API_BASE + botToken + "/sendMessage";

            String jsonBody = String.format(
                    "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"MarkdownV2\"}",
                    chatId,
                    escapeJson(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("[Telegram] ✅ Message sent to chat {} successfully.", chatId);
            } else {
                log.error("[Telegram] ❌ Failed to send message to chat {}. Status: {} | Body: {}",
                        chatId, response.statusCode(), response.body());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Telegram] 🚨 Thread interrupted while sending message to chat {}: {}",
                    chatId, e.getMessage());
        } catch (Exception e) {
            log.error("[Telegram] 🚨 Error sending message to chat {}: {}", chatId, e.getMessage());
        }
    }

    public String sendTestMessage(String chatId, String text, String parseMode) throws Exception {
        if (botToken == null || botToken.isBlank() || "YOUR_BOT_TOKEN_HERE".equals(botToken)) {
            throw new IllegalStateException("Telegram bot token is not configured in application.properties");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("Chat ID must not be null or blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Message text must not be null or blank");
        }

        String url = TELEGRAM_API_BASE + botToken + "/sendMessage";

        String jsonBody;
        if (parseMode != null && !parseMode.isBlank()) {
            jsonBody = String.format(
                    "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"%s\"}",
                    chatId,
                    escapeJson(text),
                    escapeJson(parseMode));
        } else {
            jsonBody = String.format(
                    "{\"chat_id\":\"%s\",\"text\":\"%s\"}",
                    chatId,
                    escapeJson(text));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Telegram API returned HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private String formatPrice(Double amount) {
        if (amount == null)
            return "0 ل\\.س";
        long rounded = Math.round(amount);
        String formatted = String.format("%,d", rounded).replace(",", "،");
        return escapeMarkdown(formatted + " ل.س");
    }

    private String escapeMarkdown(String text) {
        if (text == null)
            return "";
        return text
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }

    private String escapeJson(String text) {
        if (text == null)
            return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}