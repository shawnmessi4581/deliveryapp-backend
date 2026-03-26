package com.deliveryapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class SmsService {

    @Value("${sms.relay.url}")
    private String relayUrl;

    @Value("${sms.relay.api-key}")
    private String relayApiKey;

    @Async
    public void sendSms(String phoneNumber, String otpCode) {
        try {
            // 1. Prepare the message
            String message = "Your Verification Code is: " + otpCode;

            // 2. URL Encode the parameters
            String encodedPhone = URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8.toString());
            String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());

            // 3. Build the URL to YOUR Syrian Relay Server
            String fullUrl = String.format("%s?phoneNumber=%s&messageText=%s",
                    relayUrl, encodedPhone, encodedMsg);

            System.out.println("📡 Forwarding SMS request to Syrian Relay for: " + phoneNumber);

            // 4. Make the HTTP POST request to the Relay
            URL url = new URL(fullUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");

            // Add the secret API Key so your relay accepts it
            connection.setRequestProperty("X-API-KEY", relayApiKey);

            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // 5. Read the Response from the Relay
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                System.out.println("✅ Relay Response: " + response.toString());
            } else {
                System.err.println("❌ Relay Server returned HTTP " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to connect to Relay Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}