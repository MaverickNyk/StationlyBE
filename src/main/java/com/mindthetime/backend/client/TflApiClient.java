package com.mindthetime.backend.client;

import com.mindthetime.backend.model.ArrivalPrediction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Component
public class TflApiClient {

    private final WebClient webClient;

    @Value("${tfl.app.key}")
    private String appKey;

    @Value("${tfl.arrival.prediction.count}")
    private int arrivalPredictionCount;

    @Value("${tfl.api.timeout}")
    private int apiTimeout;

    public TflApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.tfl.gov.uk")
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(2 * 1024 * 1024)) // 2MB
                .build();
    }

    public List<ArrivalPrediction> getArrivals(String stationId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/StopPoint/{stationId}/Arrivals")
                        .queryParam("app_key", appKey)
                        .build(stationId))
                .retrieve()
                .bodyToFlux(ArrivalPrediction.class)
                .timeout(java.time.Duration.ofSeconds(apiTimeout))
                .collectList()
                .block();
    }

    public List<ArrivalPrediction> getArrivalsByMode(String mode) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/Mode/{mode}/Arrivals")
                        .queryParam("app_key", appKey)
                        .queryParam("count", arrivalPredictionCount)
                        .build(mode))
                .retrieve()
                .bodyToFlux(ArrivalPrediction.class)
                .timeout(java.time.Duration.ofSeconds(apiTimeout))
                .collectList()
                .block();
    }

    public List<java.util.Map<String, Object>> getTransportModes() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/Journey/Meta/Modes")
                        .queryParam("app_key", appKey)
                        .build())
                .retrieve()
                .bodyToMono(
                        new org.springframework.core.ParameterizedTypeReference<List<java.util.Map<String, Object>>>() {
                        })
                .timeout(java.time.Duration.ofSeconds(apiTimeout))
                .block();
    }

    public List<java.util.Map<String, Object>> getLinesByMode(String mode) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/Line/Mode/{mode}")
                        .queryParam("app_key", appKey)
                        .build(mode))
                .retrieve()
                .bodyToMono(
                        new org.springframework.core.ParameterizedTypeReference<List<java.util.Map<String, Object>>>() {
                        })
                .timeout(java.time.Duration.ofSeconds(apiTimeout))
                .block();
    }

    public List<java.util.Map<String, Object>> getStopPointsByLine(String lineId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/Line/{lineId}/StopPoints")
                        .queryParam("app_key", appKey)
                        .build(lineId))
                .retrieve()
                .bodyToMono(
                        new org.springframework.core.ParameterizedTypeReference<List<java.util.Map<String, Object>>>() {
                        })
                .timeout(java.time.Duration.ofSeconds(apiTimeout))
                .block();
    }
}
