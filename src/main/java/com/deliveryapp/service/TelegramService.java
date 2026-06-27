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

/**
 * TelegramService — sends each store its own section of a new order
 * via a Telegram Bot.
 *
 * <p>When an order is placed (possibly spanning multiple stores), this
 * service groups the order items by store and dispatches one formatted
 * Arabic message per store that has a configured {@code telegramChatId}.
 * All operations are fully asynchronous and will never block the
 * main order-placement flow.</p>
 *
 * <p>Configuration required in {@code application.properties}:</p>
 * <pre>
 * telegram.bot.token=YOUR_BOT_TOKEN_HERE
 * </pre>
 *
 * <p>Each store must have its {@code telegramChatId} set (via the admin
 * API or directly in the database) to receive notifications.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";
    private static final String DIVIDER = "━━━━━━━━━━━━━━━━━━━━";
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("hh:mm a");

    @Value("${telegram.bot.token}")
    private String botToken;

    // Shared HttpClient — thread-safe and reusable
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ===========================================================================
    // PUBLIC API
    // ===========================================================================

    /**
     * Entry point called from {@link OrderService} after an order is saved.
     * Groups all order items by their store and fires one Telegram message
     * per store that has a {@code telegramChatId} configured.
     *
     * @param order the freshly saved order (with items already populated)
     */
    @Async
    public void notifyAllStoresOfOrder(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            log.warn("[Telegram] Order #{} has no items — skipping store notifications.",
                    order.getOrderNumber());
            return;
        }

        // Group items by their store
        Map<Store, List<OrderItem>> itemsByStore = order.getOrderItems().stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getStore() != null)
                .collect(Collectors.groupingBy(item -> item.getProduct().getStore()));

        if (itemsByStore.isEmpty()) {
            log.warn("[Telegram] Could not resolve any store for order #{} — skipping.",
                    order.getOrderNumber());
            return;
        }

        for (Map.Entry<Store, List<OrderItem>> entry : itemsByStore.entrySet()) {
            Store store = entry.getKey();
            List<OrderItem> storeItems = entry.getValue();

            if (store.getTelegramChatId() == null || store.getTelegramChatId().isBlank()) {
                log.info("[Telegram] Store '{}' has no Telegram Chat ID configured — skipping.",
                        store.getName());
                continue;
            }

            notifyStoreOfOrder(order, store, storeItems);
        }
    }

    // ===========================================================================
    // INTERNAL HELPERS
    // ===========================================================================

    /**
     * Builds a rich Arabic Telegram message for one store's slice of the order
     * and dispatches it.
     */
    private void notifyStoreOfOrder(Order order, Store store, List<OrderItem> storeItems) {
        String message = buildStoreMessage(order, store, storeItems);
        sendMessage(store.getTelegramChatId(), message);
    }

    /**
     * Builds the formatted Arabic message for a single store.
     *
     * <p>The message includes:</p>
     * <ul>
     *   <li>Order number header</li>
     *   <li>Store name</li>
     *   <li>Itemised product list with variant, quantity, price, and item notes</li>
     *   <li>Store subtotal</li>
     *   <li>Overall delivery fee and total</li>
     *   <li>Delivery address and optional customer order note</li>
     *   <li>Timestamp</li>
     * </ul>
     */
    private String buildStoreMessage(Order order, Store store, List<OrderItem> storeItems) {
        StringBuilder sb = new StringBuilder();

        // ── Header ──────────────────────────────────────────────────────────────
        sb.append("🛒 *طلب جديد!*\n");
        sb.append("🔢 رقم الطلب: `#").append(order.getOrderNumber()).append("`\n");
        sb.append(DIVIDER).append("\n\n");

        // ── Store Section ────────────────────────────────────────────────────────
        sb.append("🏪 *المتجر:* ").append(escapeMarkdown(store.getName())).append("\n");
        sb.append("📦 *المنتجات:*\n");

        double storeSubtotal = 0.0;

        for (OrderItem item : storeItems) {
            sb.append("\n");

            // Product name + variant (if any)
            String productName = escapeMarkdown(
                    item.getProductName() != null ? item.getProductName() : "منتج");

            if (item.getVariantDetails() != null && !item.getVariantDetails().isBlank()) {
                sb.append("  • ").append(productName)
                  .append(" _(").append(escapeMarkdown(item.getVariantDetails())).append(")_\n");
            } else {
                sb.append("  • ").append(productName).append("\n");
            }

            // Color (if selected)
            if (item.getSelectedColor() != null && item.getSelectedColor().getName() != null) {
                sb.append("    🎨 اللون: ").append(escapeMarkdown(item.getSelectedColor().getName())).append("\n");
            }

            // Quantity × unit price → item total
            sb.append("    الكمية: ×").append(item.getQuantity())
              .append("  |  السعر: ").append(formatPrice(item.getUnitPrice()))
              .append("  ←  *").append(formatPrice(item.getTotalPrice())).append("*\n");

            // Item-level notes (e.g. "بدون بصل")
            if (item.getNotes() != null && !item.getNotes().isBlank()) {
                sb.append("    📝 _").append(escapeMarkdown(item.getNotes())).append("_\n");
            }

            storeSubtotal += item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;
        }

        // ── Store Subtotal ───────────────────────────────────────────────────────
        sb.append("\n").append(DIVIDER).append("\n");
        sb.append("💰 *إجمالي هذا المتجر:* ").append(formatPrice(storeSubtotal)).append("\n");

        // ── Order-Level Financials ───────────────────────────────────────────────
        if (order.getDeliveryFee() != null && order.getDeliveryFee() > 0) {
            sb.append("🚗 *رسوم التوصيل:* ").append(formatPrice(order.getDeliveryFee())).append("\n");
        }
        if (order.getDiscountAmount() != null && order.getDiscountAmount() > 0) {
            sb.append("🎁 *الخصم:* \\-").append(formatPrice(order.getDiscountAmount())).append("\n");
        }
        sb.append("💳 *إجمالي الطلب الكلي:* *").append(formatPrice(order.getTotalAmount())).append("*\n");

        // ── Delivery Details ─────────────────────────────────────────────────────
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

        // ── Customer Info ────────────────────────────────────────────────────────
        if (order.getUser() != null) {
            sb.append("👤 *العميل:* ").append(escapeMarkdown(order.getUser().getName())).append("\n");
            if (order.getUser().getPhoneNumber() != null) {
                sb.append("📞 ").append(escapeMarkdown(order.getUser().getPhoneNumber())).append("\n");
            }
        }

        // ── Timestamp ────────────────────────────────────────────────────────────
        sb.append("\n");
        LocalDateTime now = order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now();
        sb.append("⏰ ").append(now.format(TIME_FORMATTER));

        return sb.toString();
    }

    /**
     * Sends a plain-text or MarkdownV2 message to a Telegram chat via the
     * Bot API {@code sendMessage} endpoint.
     *
     * <p>Uses Java 11+ {@link HttpClient} with a 15-second read timeout. Errors
     * are logged but never thrown, so a Telegram failure cannot affect the
     * order-placement transaction.</p>
     *
     * @param chatId Telegram chat ID (can be a group, channel, or private chat)
     * @param text   The message body (MarkdownV2 formatted)
     */
    private void sendMessage(String chatId, String text) {
        if (botToken == null || botToken.isBlank() || "YOUR_BOT_TOKEN_HERE".equals(botToken)) {
            log.warn("[Telegram] Bot token is not configured — message not sent to chat {}.", chatId);
            return;
        }

        try {
            String url = TELEGRAM_API_BASE + botToken + "/sendMessage";

            // Build JSON body manually (no extra library needed)
            String jsonBody = String.format(
                    "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"MarkdownV2\"}",
                    chatId,
                    escapeJson(text)
            );

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

    // ===========================================================================
    // FORMATTING UTILITIES
    // ===========================================================================

    /**
     * Formats a monetary value as a Syrian Pound string (e.g. "12,500 ل.س").
     * Handles null by returning "0 ل.س".
     */
    private String formatPrice(Double amount) {
        if (amount == null) return "0 ل\\.س";
        long rounded = Math.round(amount);
        // Add thousands separator manually for Arabic locale readability
        String formatted = String.format("%,d", rounded).replace(",", "،");
        return escapeMarkdown(formatted + " ل.س");
    }

    /**
     * Escapes characters that are special in Telegram's MarkdownV2 mode.
     * Required for all user-generated content to prevent parse errors.
     *
     * @see <a href="https://core.telegram.org/bots/api#markdownv2-style">Telegram MarkdownV2</a>
     */
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        // Characters that must be escaped in MarkdownV2
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

    /**
     * Escapes a string for embedding inside a JSON double-quoted string literal.
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
