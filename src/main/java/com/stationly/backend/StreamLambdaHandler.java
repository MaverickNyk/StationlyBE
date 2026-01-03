package com.stationly.backend;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.stationly.backend.config.ApplicationContextHolder;
import com.stationly.backend.service.TflPollingService;
import com.stationly.backend.service.LineService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamLambdaHandler implements RequestStreamHandler {
    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(StationlyApplication.class);
        } catch (ContainerInitializationException e) {
            // if we fail here. We re-throw the exception to force another cold start
            e.printStackTrace();
            throw new RuntimeException("Could not initialize Spring Boot application", e);
        }
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)
            throws IOException {

        // Convert input stream to string for manual inspection
        byte[] inputBytes = inputStream.readAllBytes();
        String inputString = new String(inputBytes);

        // Check if this is an AWS EventBridge Scheduled Event
        if (inputString.contains("aws.events") && inputString.contains("Scheduled Event")) {
            context.getLogger().log("⏰ Detected EventBridge Scheduled Event. Triggering manual refresh...");
            try {
                TflPollingService pollingService = ApplicationContextHolder.getBean(TflPollingService.class);
                pollingService.refreshAll();

                LineService lineService = ApplicationContextHolder.getBean(LineService.class);
                lineService.pollLineStatuses();

                context.getLogger().log("✅ Scheduled refresh (Predictions & Line Status) completed successfully.");
                return;
            } catch (Exception e) {
                context.getLogger().log("❌ Error during scheduled refresh: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }

        // Otherwise, proceed with normal API Proxy (Rest Controllers)
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(inputBytes);
        handler.proxyStream(bais, outputStream, context);
    }
}
