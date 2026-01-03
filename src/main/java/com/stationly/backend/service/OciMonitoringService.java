package com.stationly.backend.service;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.monitoring.MonitoringClient;
import com.oracle.bmc.monitoring.model.Datapoint;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class OciMonitoringService implements MonitoringService {

    private MonitoringClient monitoringClient;

    @Value("${oci.monitoring.compartment-id:}")
    private String compartmentId;

    @Value("${oci.monitoring.namespace:StationlyPolling}")
    private String namespace;

    @Value("${oci.monitoring.enabled:false}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("OCI Monitoring is disabled.");
            return;
        }

        try {
            InstancePrincipalsAuthenticationDetailsProvider provider = InstancePrincipalsAuthenticationDetailsProvider
                    .builder().build();
            monitoringClient = MonitoringClient.builder().build(provider);

            // Set the correct ingestion endpoint as required for PostMetricData
            // The error message specifies:
            // https://telemetry-ingestion.uk-london-1.oraclecloud.com
            // We can also make this configurable via properties if needed.
            String region = provider.getRegion().getRegionId();
            String endpoint = String.format("https://telemetry-ingestion.%s.oraclecloud.com", region);
            monitoringClient.setEndpoint(endpoint);

            log.info("OCI Monitoring Client initialized (Region: {}, Endpoint: {})", region, endpoint);
        } catch (Exception e) {
            log.error("Failed to initialize OCI Monitoring Client. Metrics will not be exported.", e);
            enabled = false;
        }
    }

    @Override
    public void recordPollingDuration(String mode, long durationMs, String status) {
        if (!enabled)
            return;

        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("mode", mode);
        dimensions.put("status", status);

        postMetric("PollingDuration", (double) durationMs, "milliseconds", dimensions);
    }

    @Override
    public void recordArrivalsCount(String mode, int count) {
        if (!enabled)
            return;

        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("mode", mode);

        postMetric("ArrivalsCount", (double) count, "count", dimensions);
    }

    private void postMetric(String name, Double value, String unit, Map<String, String> dimensions) {
        // Sanitize namespace: must be lowercase and match pattern
        // ^[a-z][a-z0-9_]*[a-z0-9]$
        String sanitizedNamespace = namespace.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        // Ensure it starts with a letter (if it doesn't, prefix it or handle it)
        if (!sanitizedNamespace.isEmpty() && !Character.isLetter(sanitizedNamespace.charAt(0))) {
            sanitizedNamespace = "n_" + sanitizedNamespace;
        }

        try {
            MetricDataDetails metricDetails = MetricDataDetails.builder()
                    .namespace(sanitizedNamespace)
                    .compartmentId(compartmentId)
                    .name(name)
                    .metadata(Collections.singletonMap("unit", unit))
                    .dimensions(dimensions)
                    .datapoints(Collections.singletonList(
                            Datapoint.builder()
                                    .timestamp(new Date())
                                    .value(value)
                                    .count(1)
                                    .build()))
                    .build();

            PostMetricDataRequest request = PostMetricDataRequest.builder()
                    .postMetricDataDetails(PostMetricDataDetails.builder()
                            .metricData(Collections.singletonList(metricDetails))
                            .build())
                    .build();

            monitoringClient.postMetricData(request);
            log.info("Published metric to OCI: {} = {} {} in namespace {}", name, value, unit, sanitizedNamespace);
        } catch (Exception e) {
            log.warn("Failed to publish metric to OCI: {}. Namespace: {}. Error: {}", name, sanitizedNamespace,
                    e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (monitoringClient != null) {
            monitoringClient.close();
        }
    }
}
