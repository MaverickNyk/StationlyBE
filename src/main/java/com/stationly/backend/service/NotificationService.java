package com.stationly.backend.service;

public interface NotificationService {
    void publishToTopic(String topic, Object payload);
}
