package com.deliveryapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
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
            System.out.println("📡 Preparing to send SMS via Syrian Relay to: " + phoneNumber);

            // 1. Prepare the message text
            String messageText = "Your All In verification code is: " + otpCode
                    + ". Valid for 10 minutes. Do not share it with anyone.";

            // 2. Format the phone number (Ensure it's just the numbers, no '+')
            String formattedPhone = phoneNumber.replace("+", "");

            // 3. Build the Form Data Body (x-www-form-urlencoded)
            String urlParameters = "phoneNumber=" + URLEncoder.encode(formattedPhone, StandardCharsets.UTF_8.toString())
                    + "&messageText=" + URLEncoder.encode(messageText, StandardCharsets.UTF_8.toString());

            // 4. Setup the Connection
            URL url = new URL(relayUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Replicate the cURL request parameters
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-API-KEY", relayApiKey);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // Enable writing to the body of the request
            connection.setDoOutput(true);

            // Set Timeouts (Important so your backend doesn't freeze if the relay is down)
            connection.setConnectTimeout(9000); // 9 seconds to connect
            connection.setReadTimeout(20000); // 20 seconds to read response

            // 5. Write the Data to the Body
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.writeBytes(urlParameters);
                wr.flush();
            }

            // 6. Read the Response from Laravel
            int responseCode = connection.getResponseCode();

            if (responseCode == 200 || responseCode == 201) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                System.out.println("✅ SMS Relay Success: " + response.toString());
            } else {
                // Read Error Stream if it fails
                BufferedReader errorIn = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                String errorLine;
                StringBuilder errorResponse = new StringBuilder();
                while ((errorLine = errorIn.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                errorIn.close();

                System.err.println("❌ SMS Relay Failed! HTTP " + responseCode);
                System.err.println("❌ Relay Error Response: " + errorResponse.toString());
            }

        } catch (Exception e) {
            System.err.println("🚨 Network Error connecting to Syrian Relay Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}