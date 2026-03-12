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

    /**
     * Sends an SMS asynchronously via the Syriatel BMS API.
     * 
     * @Async means this runs on a background thread — BMS latency won't block the
     *        HTTP response.
     */
    @Async("smsTaskExecutor")
    public void sendSms(String toPhoneNumber, String message) {
        String normalizedNumber = normalizePhoneNumber(toPhoneNumber);

        try {
            disableSslVerification(); // Required — BMS uses a self-signed certificate

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
                log.info("BMS SMS response for [...{}]: {}",
                        normalizedNumber.substring(normalizedNumber.length() - 4), response);

                // BMS returns "#JobID" on success (e.g. "#354")
                if (response == null || !response.startsWith("#")) {
                    log.error("SMS provider error for [...{}]: {}",
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
     * Normalizes phone numbers to BMS-required format: 963XXXXXXXXX
     *
     * Handles:
     * +963 993 123 456 → 963993123456
     * 00963993123456 → 963993123456
     * 0993123456 → 963993123456 (local Syrian format)
     * 963993123456 → 963993123456 (already correct)
     */
    public String normalizePhoneNumber(String raw) {
        if (raw == null)
            throw new IllegalArgumentException("Phone number cannot be null");

        String digits = raw.replaceAll("[^0-9]", "");

        // Local Syrian format: 0XXXXXXXXX (10 digits)
        if (digits.startsWith("0") && digits.length() == 10) {
            digits = "963" + digits.substring(1);
        }

        if (digits.startsWith("963") && digits.length() == 12) {
            return digits;
        }

        throw new IllegalArgumentException(
                "Invalid phone number format: " + raw + ". Expected 963XXXXXXXXX");
    }

    /**
     * BMS uses a self-signed SSL certificate.
     * Disabling validation is required as per their official documentation.
     */
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