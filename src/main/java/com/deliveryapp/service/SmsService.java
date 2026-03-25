package com.deliveryapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Service
public class SmsService {

    @Value("${syriatel.sms.user-name}")
    private String userName;

    @Value("${syriatel.sms.password}")
    private String password;

    @Value("${syriatel.sms.sender}")
    private String senderId;

    private static final String SYRIATEL_API_URL = "https://bms.syriatel.sy/API/SendSMS.aspx";

    // Run asynchronously so it doesn't block the user's signup request
    @Async
    public void sendSms(String phoneNumber, String otpCode) {
        try {
            // 1. Format the phone number (Syriatel requires 963XXXXXXXXX without '+')
            String formattedPhone = phoneNumber.replace("+", "");

            // 2. Prepare the message and URL encode it (Required for URLs with
            // spaces/special characters)
            String message = "Your Verification Code is: " + otpCode;
            String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
            String jobName = URLEncoder.encode("OTP_Verification", StandardCharsets.UTF_8.toString());
            String encodedSender = URLEncoder.encode(senderId, StandardCharsets.UTF_8.toString());

            // 3. Build the final URL
            String requestUrl = String.format("%s?job_name=%s&user_name=%s&password=%s&msg=%s&sender=%s&to=%s",
                    SYRIATEL_API_URL, jobName, userName, password, encodedMsg, encodedSender, formattedPhone);

            System.out.println("📞 Sending SMS via Syriatel to: " + formattedPhone);

            // 4. IGNORE SSL CERTIFICATE (As requested by Syriatel Documentation)
            bypassSSLValidation();

            // 5. Make the HTTP Request
            URL url = new URL(requestUrl);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Set timeouts to prevent the "Read timed out" error hanging the thread (10
            // seconds)
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // 6. Read the Response
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                System.out.println("✅ Syriatel Response: " + response.toString());
            } else {
                System.err.println("❌ Syriatel SMS Failed. HTTP Error Code: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to send SMS to [" + phoneNumber + "]: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * This method disables SSL certificate checking.
     * Syriatel's API uses a self-signed certificate which Java blocks by default.
     */
    private void bypassSSLValidation() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = (hostname, session) -> true;
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }
}