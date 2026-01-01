package com.stationly.backend.service;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseCacheService {

    private static final String CACHE_ROOT = "cache/metadata";

    /**
     * Save an object to Firebase Cache
     * 
     * @param key   Cache key
     * @param value Object to store
     */
    public void save(String key, Object value) {
        if (com.google.firebase.FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference(CACHE_ROOT).child(sanitizeKey(key));
            ref.setValueAsync(value);
            log.trace("Saved to Firebase Cache: key={}", key);
        } catch (Exception e) {
            log.error("Failed to save to Firebase Cache for key: {}", key, e);
        }
    }

    /**
     * Get an object from Firebase Cache
     * 
     * @param key       Cache key
     * @param valueType Class type to deserialize to
     * @return Deserialized object or null if not found/error
     */
    public <T> T get(String key, Class<T> valueType) {
        if (com.google.firebase.FirebaseApp.getApps().isEmpty()) {
            return null;
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(CACHE_ROOT).child(sanitizeKey(key));

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    try {
                        T value = dataSnapshot.getValue(valueType);
                        future.complete(value);
                    } catch (Exception e) {
                        log.error("Failed to deserialize Firebase Cache data for key: {}", key, e);
                        future.complete(null);
                    }
                } else {
                    future.complete(null);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                log.error("Firebase Cache read cancelled for key: {}. Error: {}", key, databaseError.getMessage());
                future.complete(null);
            }
        });

        try {
            // Wait for up to 5 seconds for metadata cache hit
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.warn("Timeout/Error fetching from Firebase Cache for key: {}", key);
            return null;
        }
    }

    /**
     * Delete a key from Firebase Cache
     * 
     * @param key Cache key
     */
    public void delete(String key) {
        if (com.google.firebase.FirebaseApp.getApps().isEmpty()) {
            return;
        }
        FirebaseDatabase.getInstance().getReference(CACHE_ROOT).child(sanitizeKey(key)).removeValueAsync();
    }

    /**
     * Clear the entire metadata cache
     */
    public void flushAll() {
        if (com.google.firebase.FirebaseApp.getApps().isEmpty()) {
            return;
        }
        FirebaseDatabase.getInstance().getReference(CACHE_ROOT).removeValueAsync();
        log.info("🧹 Firebase Metadata Cache cleared successfully");
    }

    private String sanitizeKey(String key) {
        // Firebase keys cannot contain ., $, #, [, ], or /
        return key.replace(".", "_")
                .replace("$", "_")
                .replace("#", "_")
                .replace("[", "_")
                .replace("]", "_")
                .replace("/", "_")
                .replace(":", "_");
    }
}
