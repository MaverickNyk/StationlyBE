package com.stationly.backend.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class FcmService {

    @Value("${fcm.service-account-path}")
    private String serviceAccountPath;

    @Value("${firebase.database-url:}")
    private String databaseUrl;

    private final ObjectMapper objectMapper;
    private boolean fcmEnabled = false;

    // Shared executor for FCM batch operations to prevent thread exhaustion
    private final java.util.concurrent.ExecutorService fcmExecutor = java.util.concurrent.Executors
            .newFixedThreadPool(10);

    public FcmService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        // Check if JSON string is provided (Lambda/environment variable)
        if (serviceAccountPath != null && !serviceAccountPath.isEmpty()) {
            try {
                FileInputStream serviceAccount = new FileInputStream(serviceAccountPath);
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setDatabaseUrl(databaseUrl)
                        .setThreadManager(new BoundedThreadManager())
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
                    .setDatabaseUrl(databaseUrl)
                    .setThreadManager(new BoundedThreadManager())
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
     * Custom ThreadManager to restrict the number of threads created by Firebase
     * SDK.
     * Prevents "firebase-default-xx" thread explosion.
     */
    private static class BoundedThreadManager extends com.google.firebase.ThreadManager {
        @Override
        protected java.util.concurrent.ExecutorService getExecutor(com.google.firebase.FirebaseApp app) {
            // User requested 800 threads for high-throughput prediction sync
            return java.util.concurrent.Executors.newFixedThreadPool(800, r -> {
                Thread t = new Thread(r);
                t.setName("firebase-bounded-" + t.getId());
                t.setDaemon(true);
                return t;
            });
        }

        @Override
        protected java.util.concurrent.ThreadFactory getThreadFactory() {
            return r -> {
                Thread t = new Thread(r);
                t.setName("firebase-daemon-" + t.getId());
                t.setDaemon(true);
                return t;
            };
        }

        @Override
        protected void releaseExecutor(com.google.firebase.FirebaseApp app,
                java.util.concurrent.ExecutorService executor) {
            executor.shutdown();
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
            byte[] payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

            if (payloadBytes.length > 4000) {
                log.error("❌ FCM payload for topic {} is too big ({} bytes). Skipping send.",
                        topic, payloadBytes.length);
                return;
            }

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

    /**
     * Publish multiple messages to FCM topics in batch
     * 
     * @param topicPayloads Map of topic name to payload object
     */
    public void publishAll(Map<String, Object> topicPayloads) {
        if (!fcmEnabled || topicPayloads == null || topicPayloads.isEmpty()) {
            return;
        }

        log.info("🚀 Preparing to send {} FCM topic updates...", topicPayloads.size());
        long start = System.currentTimeMillis();

        try {
            List<com.google.firebase.messaging.Message> messages = topicPayloads.entrySet().stream()
                    .map(entry -> {
                        try {
                            String jsonPayload = objectMapper.writeValueAsString(entry.getValue());
                            return com.google.firebase.messaging.Message.builder()
                                    .setTopic(entry.getKey())
                                    .putData("payload", jsonPayload)
                                    .build();
                        } catch (Exception e) {
                            log.error("❌ Error creating FCM message for topic: {}", entry.getKey(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            // Firebase Limit: sendEachAsync supports many messages, but it's good to batch
            // them
            // into chunks of 500 for optimal processing and to stay under concurrent fanout
            // limits.
            int batchSize = 500;
            List<List<com.google.firebase.messaging.Message>> batches = new ArrayList<>();
            for (int i = 0; i < messages.size(); i += batchSize) {
                batches.add(messages.subList(i, Math.min(i + batchSize, messages.size())));
            }

            log.info("📦 Partitioned into {} batches of up to {}.", batches.size(), batchSize);

            // Send batches in parallel using shared thread pool
            try {
                List<CompletableFuture<com.google.firebase.messaging.BatchResponse>> batchFutures = new ArrayList<>();
                for (List<com.google.firebase.messaging.Message> batch : batches) {
                    batchFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            return FirebaseMessaging.getInstance().sendEach(batch);
                        } catch (Exception e) {
                            log.error("❌ Batch send failed", e);
                            return null;
                        }
                    }, fcmExecutor));
                }

                long successCount = 0;
                for (CompletableFuture<com.google.firebase.messaging.BatchResponse> future : batchFutures) {
                    com.google.firebase.messaging.BatchResponse response = future.join();
                    if (response != null) {
                        successCount += response.getSuccessCount();
                    }
                }

                long duration = System.currentTimeMillis() - start;
                log.info("✅ Finished sending FCM messages. Total: {}, Success: {}, Time: {}ms",
                        topicPayloads.size(), successCount, duration);
            } catch (Exception e) {
                log.error("❌ Error during batch sending", e);
            }

        } catch (Exception e) {
            log.error("❌ Critical error during FCM publishing", e);
        }
    }

    /**
     * Send a special signal to a topic instructing clients to clear their state
     * 
     * @param topic FCM topic
     */
    public void sendClearSignal(String topic) {
        if (!fcmEnabled)
            return;
        try {
            Message message = Message.builder()
                    .setTopic(topic)
                    .putData("action", "CLEAR")
                    .build();
            FirebaseMessaging.getInstance().send(message);
            log.debug("Sent CLEAR signal to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to send CLEAR signal to topic: {}", topic, e);
        }
    }
}
