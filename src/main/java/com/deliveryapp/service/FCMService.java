package com.deliveryapp.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class FCMService {

    public void sendPushNotification(String token, String title, String body, String imageUrl, String type, String referenceId) {
        if (token == null || token.isEmpty()) {
            System.out.println("⚠️ User has no FCM Token. Skipping Push Notification.");
            return;
        }

        try {
            // Build the Notification Payload
            Notification.Builder notificationBuilder = Notification.builder()
                    .setTitle(title)
                    .setBody(body);

            if (imageUrl != null && !imageUrl.isEmpty()) {
                notificationBuilder.setImage(imageUrl);
            }

            // Build the Message
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(notificationBuilder.build())
                    // Add Data payload for Flutter click handling
                    .putData("type", type)
                    .putData("referenceId", referenceId != null ? referenceId : "");

            // Send
            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            System.out.println("✅ Successfully sent message: " + response);

        } catch (Exception e) {
            System.err.println("❌ Error sending FCM message: " + e.getMessage());
        }
    }
}