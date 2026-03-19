package com.deliveryapp.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class FCMService {

    public void sendToToken(String token, String title, String body, String imageUrl, String type, String referenceId) {
        if (token == null || token.isEmpty()) {
            System.err.println("⚠️ Warning: Cannot send FCM. Token is null or empty.");
            return;
        }

        System.out.println("🛠️ Building FCM Message for Token...");
        Message message = buildMessage(title, body, imageUrl, type, referenceId)
                .setToken(token)
                .build();

        send(message);
    }

    public void sendToTopic(String topic, String title, String body, String imageUrl, String type, String referenceId) {
        if (topic == null || topic.isEmpty()) {
            System.err.println("⚠️ Warning: Cannot send FCM. Topic is null or empty.");
            return;
        }

        System.out.println("🛠️ Building FCM Message for Topic: " + topic);
        // Firebase topics cannot have spaces or special characters
        String safeTopic = topic.replaceAll("[^a-zA-Z0-9-_.~%]+", "_");

        Message message = buildMessage(title, body, imageUrl, type, referenceId)
                .setTopic(safeTopic)
                .build();

        send(message);
    }

    private Message.Builder buildMessage(String title, String body, String imageUrl, String type, String referenceId) {
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
                .putData("referenceId", referenceId != null ? referenceId : "");
    }

    private void send(Message message) {
        try {
            System.out.println("📡 Dispatching message to Firebase Servers...");
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ FCM Success Response: " + response);
        } catch (Exception e) {
            System.err.println("❌ CRITICAL FCM ERROR: Failed to send message.");
            System.err.println("❌ Reason: " + e.getMessage());
            e.printStackTrace();
        }
    }
}