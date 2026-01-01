package com.stationly.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration to enable Spring Scheduling only when NOT in the lambda
 * profile.
 * This prevents the background polling thread from running on AWS Lambda,
 * where polling is triggered externally by EventBridge.
 */
@Configuration
@EnableScheduling
@Profile("!lambda")
public class SchedulingConfig {
}
