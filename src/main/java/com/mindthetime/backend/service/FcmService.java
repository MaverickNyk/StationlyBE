package com.mindthetime.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class FcmService {

    @Value("${fcm.service-account-path}")
    private String serviceAccountPath;

    @Value("${fcm.service-account-json}")
    private String serviceAccountJson;

    private final ObjectMapper objectMapper;
    private boolean fcmEnabled = false;

    public FcmService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        // Check if JSON string is provided (Lambda/environment variable)
        if (serviceAccountJson != null && !serviceAccountJson.isEmpty()) {
            try {
                ByteArrayInputStream serviceAccount = new ByteArrayInputStream(
                        serviceAccountJson.getBytes(StandardCharsets.UTF_8));
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                }
                fcmEnabled = true;
                log.info("✅ Firebase Cloud Messaging initialized successfully (from JSON string)");
                return;
            } catch (IOException e) {
                log.error("❌ Failed to initialize Firebase Cloud Messaging from JSON string", e);
                return;
            }
        }

        // Fallback to file path (local development)
        if (serviceAccountPath == null || serviceAccountPath.isEmpty()) {
            log.warn("⚠️  FCM service account not configured. FCM notifications will be disabled.");
            log.warn("💡 Set either FCM_SERVICE_ACCOUNT_JSON (for Lambda) or FCM_SERVICE_ACCOUNT_PATH (for local dev)");
            return;
        }

        try {
            FileInputStream serviceAccount = new FileInputStream(serviceAccountPath);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            fcmEnabled = true;
            log.info("✅ Firebase Cloud Messaging initialized successfully (from file: {})", serviceAccountPath);
        } catch (IOException e) {
            log.error("❌ Failed to initialize Firebase Cloud Messaging from file: {}", serviceAccountPath, e);
        }
    }

    /**
     * Publish a message to an FCM topic
     * 
     * @param topic   Topic name (e.g., "StationId-LineId-Direction")
     * @param payload Data to send
     */
    public void publishToTopic(String topic, Object payload) {
        if (!fcmEnabled) {
            log.debug("FCM is disabled. Skipping notification for topic: {}", topic);
            return;
        }

        try {
            // FCM data messages require a Map<String, String>.
            // Since our payload is a complex object (DirectionPredictions),
            // we serialize it to a JSON string.
            String jsonPayload = objectMapper.writeValueAsString(payload);

            Message message = Message.builder()
                    .setTopic(topic)
                    .putData("payload", jsonPayload)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.debug("Successfully sent FCM message to topic {}: {}", topic, response);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM message to topic: {}", topic, e);
        } catch (Exception e) {
            log.error("Error preparing FCM message for topic: {}", topic, e);
        }
    }
}
