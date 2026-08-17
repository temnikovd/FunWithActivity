package com.funwithactivity.recommender.domain.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funwithactivity.recommender.config.ProviderProperties;
import com.funwithactivity.recommender.generated.service2.model.Service2LambdaEnvelope;
import com.funwithactivity.recommender.generated.service2.model.Service2Measurements;
import com.funwithactivity.recommender.generated.service2.model.Service2RecommendationItem;
import com.funwithactivity.recommender.generated.service2.model.Service2Request;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP client for Service2. Converts our SI-unit profile into the
 * imperial units Service2 expects, and tolerates both the documented
 * ({"recommendations": [...]}) and the observed live-mock (bare array)
 * success shapes - see openapi/service2-api.yaml.
 */
@Component
public class Service2Client {

    private static final double CM_PER_FOOT = 30.48d;
    private static final double LB_PER_KG = 2.20462262185d;

    private final RestClient restClient;
    private final ProviderProperties.Provider properties;
    private final ObjectMapper objectMapper;

    public Service2Client(
            @Qualifier("service2RestClient") RestClient restClient,
            ProviderProperties providerProperties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = providerProperties.getProviders().getService2();
        this.objectMapper = objectMapper;
    }

    public List<Service2RecommendationItem> fetchRecommendations(double heightCm, double weightKg, LocalDate birthDate) {
        Service2Measurements measurements = new Service2Measurements(weightKg * LB_PER_KG, heightCm / CM_PER_FOOT);
        long birthDateEpochSeconds = birthDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        Service2Request request = new Service2Request(measurements, birthDateEpochSeconds, UUID.randomUUID().toString());

        Service2LambdaEnvelope envelope;
        try {
            envelope = restClient.post()
                    .uri(properties.getPath())
                    .body(request)
                    .retrieve()
                    .body(Service2LambdaEnvelope.class);
        } catch (RestClientException e) {
            throw new ProviderException("service2 call failed: " + e.getMessage(), e);
        }

        if (envelope == null || envelope.getBody() == null) {
            throw new ProviderException("service2 returned an empty response");
        }

        return parseBody(envelope.getBody());
    }

    private List<Service2RecommendationItem> parseBody(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode itemsNode = root.isArray() ? root : root.path("recommendations");
            return objectMapper.convertValue(itemsNode, new TypeReference<List<Service2RecommendationItem>>() {
            });
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ProviderException("service2 response body could not be parsed", e);
        }
    }
}
