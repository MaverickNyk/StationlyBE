package com.stationly.backend.service;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.stationly.backend.client.TflApiClient;
import com.stationly.backend.model.LineStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineStatusService {

    private final TflApiClient tflApiClient;

    @Value("${tfl.transport.modes}")
    private String tflTransportModes;

    @Scheduled(fixedRateString = "${tfl.status.polling.interval:300000}") // Default 5 mins
    public List<LineStatusResponse> pollLineStatuses() {
        if (com.google.firebase.FirebaseApp.getApps().isEmpty()) {
            log.warn(
                    "⚠️ Firebase not initialized. Skipping line status polling. Please ensure FCM_SERVICE_ACCOUNT_PATH or FCM_SERVICE_ACCOUNT_JSON is set.");
            return Collections.emptyList();
        }

        String[] modes = tflTransportModes.split(",");
        List<LineStatusResponse> allStatuses = new ArrayList<>();

        for (String mode : modes) {
            String trimmedMode = mode.trim();
            if (trimmedMode.isEmpty())
                continue;

            log.info("🚇 Starting line status polling for mode: {}", trimmedMode);
            try {
                List<Map<String, Object>> rawStatuses = tflApiClient.getLineStatuses(trimmedMode);
                if (rawStatuses == null || rawStatuses.isEmpty()) {
                    log.warn("⚠️ No line statuses received from TfL for mode: {}", trimmedMode);
                    continue;
                }

                List<LineStatusResponse> modeStatuses = rawStatuses.stream()
                        .map(this::mapToLineStatusResponse)
                        .collect(Collectors.toList());
                allStatuses.addAll(modeStatuses);

            } catch (Exception e) {
                log.error("❌ Error polling line statuses for mode: {}", trimmedMode, e);
            }
        }

        if (!allStatuses.isEmpty()) {
            saveToFirebase(allStatuses);
            log.info("✅ Successfully polled and saved {} line statuses to Firebase", allStatuses.size());
        }
        return allStatuses;
    }

    private LineStatusResponse mapToLineStatusResponse(Map<String, Object> l) {
        String id = (String) l.get("id");
        String name = (String) l.get("name");
        List<Map<String, Object>> lineStatuses = (List<Map<String, Object>>) l.get("lineStatuses");

        String statusSeverityDescription = "Unknown";
        String reason = null;

        if (lineStatuses != null && !lineStatuses.isEmpty()) {
            Map<String, Object> firstStatus = lineStatuses.get(0);
            statusSeverityDescription = (String) firstStatus.get("statusSeverityDescription");
            reason = (String) firstStatus.get("reason");
        }

        return LineStatusResponse.builder()
                .id(id)
                .name(name)
                .statusSeverityDescription(statusSeverityDescription)
                .reason(reason)
                .lastUpdatedTime(java.time.LocalDateTime.now().toString())
                .build();
    }

    private void saveToFirebase(List<LineStatusResponse> statuses) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("lineStatuses");
        // Convert to map for easy storage in Firebase
        Map<String, Object> statusMap = statuses.stream()
                .collect(Collectors.toMap(LineStatusResponse::getId, s -> s));

        ref.setValueAsync(statusMap);
    }

    public List<LineStatusResponse> getLineStatusesFromFirebase() {
        if (com.google.firebase.FirebaseApp.getApps().isEmpty()) {
            log.warn("⚠️ Firebase not initialized. Cannot fetch line statuses from Firebase.");
            return Collections.emptyList();
        }
        CompletableFuture<List<LineStatusResponse>> future = new CompletableFuture<>();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("lineStatuses");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<LineStatusResponse> statuses = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    LineStatusResponse status = snapshot.getValue(LineStatusResponse.class);
                    if (status != null) {
                        statuses.add(status);
                    }
                }
                future.complete(statuses);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                log.error("❌ Firebase read cancelled: {}", databaseError.getMessage());
                future.completeExceptionally(databaseError.toException());
            }
        });

        try {
            return future.get();
        } catch (Exception e) {
            log.error("❌ Error fetching line statuses from Firebase", e);
            return Collections.emptyList();
        }
    }
}
