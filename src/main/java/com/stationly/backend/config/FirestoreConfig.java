package com.stationly.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Configuration for Google Cloud Firestore.
 * Supports credentials via file path or JSON string (for serverless
 * deployments).
 */
@Configuration
@Slf4j
public class FirestoreConfig {

    @Value("${firestore.project-id:}")
    private String projectId;

    @Value("${fcm.service-account-path:}")
    private String credentialsPath;

    @Value("${fcm.service-account-json:}")
    private String credentialsJson;

    @Bean
    public Firestore firestore() throws IOException {
        GoogleCredentials credentials = getCredentials();

        if (credentials == null) {
            log.warn("⚠️ Firestore credentials not configured. Firestore will not be available.");
            return null;
        }

        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
                .setCredentials(credentials);

        if (projectId != null && !projectId.isEmpty()) {
            builder.setProjectId(projectId);
        }

        Firestore firestore = builder.build().getService();
        log.info("✅ Firestore initialized successfully for project: {}",
                projectId != null && !projectId.isEmpty() ? projectId : "(default)");
        return firestore;
    }

    private GoogleCredentials getCredentials() throws IOException {
        // Priority 1: JSON string (for Lambda/serverless)
        if (credentialsJson != null && !credentialsJson.isEmpty()) {
            log.info("🔐 Loading Firestore credentials from JSON string");
            InputStream stream = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
            return GoogleCredentials.fromStream(stream);
        }

        // Priority 2: File path
        if (credentialsPath != null && !credentialsPath.isEmpty()) {
            log.info("🔐 Loading Firestore credentials from file: {}", credentialsPath);
            return GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
        }

        // Priority 3: Default credentials (GCP environment)
        try {
            log.info("🔐 Attempting to load default Firestore credentials");
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            log.warn("⚠️ No Firestore credentials found");
            return null;
        }
    }
}
