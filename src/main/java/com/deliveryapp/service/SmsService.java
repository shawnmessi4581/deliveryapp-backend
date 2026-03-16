package com.deliveryapp.service;

import lombok.extern.slf4j.Slf4j;
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
import java.security.cert.X509Certificate;

@Slf4j
@Service
public class SmsService {

    @Value("${bms.api.url}")
    private String apiUrl;

    @Value("${bms.api.username}")
    private String username;

    @Value("${bms.api.password}")
    private String password;

    @Value("${bms.api.sender}")
    private String sender;

    @Async("smsTaskExecutor")
    public void sendSms(String toPhoneNumber, String message) {
        String normalizedNumber;
        try {
            normalizedNumber = normalizePhoneNumber(toPhoneNumber);
        } catch (IllegalArgumentException e) {
            // Don't crash the async thread — log and skip
            log.error("SMS skipped — could not normalize '{}': {}", toPhoneNumber, e.getMessage());
            return;
        }

        try {
            disableSslVerification();

            String url = apiUrl
                    + "?user_name=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                    + "&msg=" + URLEncoder.encode(message, StandardCharsets.UTF_8)
                    + "&sender=" + URLEncoder.encode(sender, StandardCharsets.UTF_8)
                    + "&to=" + URLEncoder.encode(normalizedNumber, StandardCharsets.UTF_8);

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String response = reader.readLine();
                log.info("BMS response for [...{}]: {}",
                        normalizedNumber.substring(normalizedNumber.length() - 4), response);
                if (response == null || !response.startsWith("#")) {
                    log.error("BMS error for [...{}]: {}",
                            normalizedNumber.substring(normalizedNumber.length() - 4), response);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send SMS to [...{}]: {}",
                    normalizedNumber.substring(Math.max(0, normalizedNumber.length() - 4)),
                    e.getMessage(), e);
        }
    }

    /**
     * Normalizes any phone number to BMS-required format: 963XXXXXXXXX (12 digits)
     *
     * Handles all real-world cases including intl_phone_field output:
     *
     * +963993123456 → 963993123456 (standard package output)
     * 963993123456 → 963993123456 (already correct)
     * +9630993123456 → 963993123456 (package prepended 963 to a 0-prefixed local
     * number → 13 digits)
     * 9630993123456 → 963993123456 (same without +)
     * 0993123456 → 963993123456 (local Syrian format, 10 digits)
     * 993123456 → 963993123456 (9 digits, no prefix)
     */
    public String normalizePhoneNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }

        String digits = raw.replaceAll("[^0-9]", "");

        // intl_phone_field bug: user typed 0993123456, package prepended 963 →
        // 9630993123456 (13 digits)
        // Fix: drop the 0 that sits right after 963
        if (digits.startsWith("9630") && digits.length() == 13) {
            digits = "963" + digits.substring(4);
        }

        // Already correct
        if (digits.startsWith("963") && digits.length() == 12) {
            return digits;
        }

        // Local format: 0XXXXXXXXX (10 digits)
        if (digits.startsWith("0") && digits.length() == 10) {
            return "963" + digits.substring(1);
        }

        // Bare digits without any prefix (9 digits)
        if (digits.length() == 9) {
            return "963" + digits;
        }

        throw new IllegalArgumentException(
                "Invalid phone number format: " + raw + ". Expected 963XXXXXXXXX (got: " + digits + ")");
    }

    private void disableSslVerification() throws Exception {
        TrustManager[] trustAll = new TrustManager[] {
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
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }
}