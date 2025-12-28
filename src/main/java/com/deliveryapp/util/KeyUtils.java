package com.deliveryapp.util;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Component
public class KeyUtils {

    private static final String PRIVATE_KEY_FILE = "app.key";
    private static final String PUBLIC_KEY_FILE = "app.pub";

    public KeyPair getRsaKeyPair() {
        try {
            File privateKeyFile = new File(PRIVATE_KEY_FILE);
            File publicKeyFile = new File(PUBLIC_KEY_FILE);

            if (privateKeyFile.exists() && publicKeyFile.exists()) {
                // 1. Load existing keys
                return loadKeys(privateKeyFile, publicKeyFile);
            } else {
                // 2. Generate and save new keys
                return generateAndSaveKeys(privateKeyFile, publicKeyFile);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error handling RSA keys", e);
        }
    }

    private KeyPair loadKeys(File privateFile, File publicFile) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        byte[] privateBytes = Files.readAllBytes(privateFile.toPath());
        PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateBytes);
        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(privateSpec);

        byte[] publicBytes = Files.readAllBytes(publicFile.toPath());
        X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicBytes);
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(publicSpec);

        System.out.println("🔑 Loaded existing RSA keys from file.");
        return new KeyPair(publicKey, privateKey);
    }

    private KeyPair generateAndSaveKeys(File privateFile, File publicFile) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        try (FileOutputStream fos = new FileOutputStream(privateFile)) {
            fos.write(keyPair.getPrivate().getEncoded());
        }

        try (FileOutputStream fos = new FileOutputStream(publicFile)) {
            fos.write(keyPair.getPublic().getEncoded());
        }

        System.out.println("⚠️ Generated NEW RSA keys and saved to file.");
        return keyPair;
    }
}