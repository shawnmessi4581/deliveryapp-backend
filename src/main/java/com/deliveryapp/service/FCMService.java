package com.deliveryapp.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.BatchResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FCMService {

    // 1. Send to a Specific Device (User)
    public void sendToToken(String token, String title, String body, String imageUrl, String type,
            String richDataJson) {
        if (token == null || token.isEmpty()) {
            System.err.println("⚠️ Warning: Cannot send FCM. Token is null or empty.");
            return;
        }

        System.out.println("🛠️ Building FCM Message for Single Token...");
        Message message = buildMessage(title, body, imageUrl, type, richDataJson)
                .setToken(token)
                .build();

        try {
            System.out.println("📡 Dispatching message to Firebase Servers...");
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ FCM Success Response: " + response);
        } catch (Exception e) {
            System.err.println("❌ CRITICAL FCM ERROR: Failed to send message to single token.");
            System.err.println("❌ Reason: " + e.getMessage());
        }
    }

    // 2. Send to Multiple Devices Efficiently (Max 500 per batch)
    public void sendToManyTokens(List<String> tokens, String title, String body, String imageUrl, String type,
            String richDataJson) {
        if (tokens == null || tokens.isEmpty()) {
            System.err.println("⚠️ Warning: Cannot send FCM Multicast. Token list is empty.");
            return;
        }

        System.out.println("🛠️ Building FCM Multicast Message for " + tokens.size() + " tokens...");

        // Firebase limit is 500 tokens per multicast request. We partition the list.
        int partitionSize = 500;
        for (int i = 0; i < tokens.size(); i += partitionSize) {
            List<String> batch = tokens.subList(i, Math.min(i + partitionSize, tokens.size()));

            MulticastMessage message = buildMulticastMessage(title, body, imageUrl, type, richDataJson)
                    .addAllTokens(batch)
                    .build();

            try {
                System.out.println("📡 Dispatching Multicast Batch to Firebase Servers...");
                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                System.out.println("✅ FCM Multicast Batch Sent. Successes: " + response.getSuccessCount()
                        + ", Failures: " + response.getFailureCount());
            } catch (Exception e) {
                System.err.println("❌ CRITICAL FCM ERROR: Failed to send Multicast message.");
                System.err.println("❌ Reason: " + e.getMessage());
            }
        }
    }

    // --- Helpers to build payloads ---

    private Message.Builder buildMessage(String title, String body, String imageUrl, String type, String richDataJson) {
        Notification.Builder notificationBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            System.out.println("📎 Attaching Image URL to Payload: " + imageUrl);
            notificationBuilder.setImage(imageUrl);
        }

        return Message.builder()
                .setNotification(notificationBuilder.build())
                .putData("type", type != null ? type : "GENERAL")
                // Store the complete JSON string in the 'payload' key
                .putData("payload", richDataJson != null ? richDataJson : "{}");
    }

    private MulticastMessage.Builder buildMulticastMessage(String title, String body, String imageUrl, String type,
            String richDataJson) {
        Notification.Builder notificationBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            notificationBuilder.setImage(imageUrl);
        }

        return MulticastMessage.builder()
                .setNotification(notificationBuilder.build())
                .putData("type", type != null ? type : "GENERAL")
                // Store the complete JSON string in the 'payload' key
                .putData("payload", richDataJson != null ? richDataJson : "{}");
    }
}