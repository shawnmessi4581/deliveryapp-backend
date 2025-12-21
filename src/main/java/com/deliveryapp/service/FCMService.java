package com.deliveryapp.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class FCMService {

    // 1. Send to a Specific Device (User)
    public void sendToToken(String token, String title, String body, String imageUrl, String type, String referenceId) {
        if (token == null || token.isEmpty()) return;

        Message message = buildMessage(title, body, imageUrl, type, referenceId)
                .setToken(token) // <--- Targets specific device
                .build();

        send(message);
    }

    // 2. Send to a Topic (e.g., "all_users", "promo")
    public void sendToTopic(String topic, String title, String body, String imageUrl, String type, String referenceId) {
        if (topic == null || topic.isEmpty()) return;

        Message message = buildMessage(title, body, imageUrl, type, referenceId)
                .setTopic(topic) // <--- Targets all subscribers of this topic
                .build();

        send(message);
    }

    // Helper to build the common message structure
    private Message.Builder buildMessage(String title, String body, String imageUrl, String type, String referenceId) {
        Notification.Builder notificationBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            notificationBuilder.setImage(imageUrl);
        }

        return Message.builder()
                .setNotification(notificationBuilder.build())
                .putData("type", type)
                .putData("referenceId", referenceId != null ? referenceId : "");
    }

    private void send(Message message) {
        try {
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ FCM Sent: " + response);
        } catch (Exception e) {
            System.err.println("❌ FCM Error: " + e.getMessage());
        }
    }
}