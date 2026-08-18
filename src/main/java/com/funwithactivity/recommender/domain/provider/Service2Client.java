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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP client for Service2. Converts SI units to the imperial units
 * Service2 expects, and accepts both a bare array and a
 * {"recommendations": [...]} success shape.
 */
@Component
public class Service2Client {

    private static final Logger log = LoggerFactory.getLogger(Service2Client.class);
    private static final double CM_PER_FOOT = 30.48d;
    private static final double LB_PER_KG = 2.20462262185d;
    private static final int MAX_LOGGED_BODY_CHARS = 500;
    private static final List<String> ERROR_MESSAGE_FIELDS = List.of("errorMessage", "error", "message", "detail");

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
        String sessionToken = UUID.randomUUID().toString();
        Service2Request request = new Service2Request(measurements, birthDateEpochSeconds, sessionToken);

        log.debug("Calling service2 sessionToken={}", sessionToken);

        Service2LambdaEnvelope envelope;
        try {
            envelope = restClient.post()
                    .uri(properties.getPath())
                    .body(request)
                    .retrieve()
                    .body(Service2LambdaEnvelope.class);
        } catch (RestClientException e) {
            log.warn("service2 HTTP call failed: {}", e.toString());
            throw new ProviderException("service2 call failed: " + e.getMessage(), e);
        }

        if (envelope == null || envelope.getBody() == null) {
            log.warn("service2 returned an empty envelope (envelope={})", envelope);
            throw new ProviderException("service2 returned an empty response");
        }

        Integer statusCode = envelope.getStatusCode();
        log.debug("service2 envelope statusCode={} bodyLength={}", statusCode, envelope.getBody().length());

        if (statusCode == null || statusCode < 200 || statusCode >= 300) {
            String errorMessage = extractErrorMessage(envelope.getBody());
            log.warn("service2 returned statusCode={}: {}", statusCode, truncate(envelope.getBody()));
            throw new ProviderException("service2 returned statusCode=" + statusCode + ": " + errorMessage);
        }

        return parseSuccessBody(envelope.getBody());
    }

    private List<Service2RecommendationItem> parseSuccessBody(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            log.warn("service2 success body was not valid JSON: {}", truncate(body));
            throw new ProviderException("service2 response body could not be parsed", e);
        }

        JsonNode itemsNode = root.isArray() ? root : root.path("recommendations");
        if (!itemsNode.isArray()) {
            log.warn("service2 success body had no recommendations array: {}", truncate(body));
            throw new ProviderException("service2 success response did not contain a recommendations array");
        }

        try {
            List<Service2RecommendationItem> items =
                    objectMapper.convertValue(itemsNode, new TypeReference<List<Service2RecommendationItem>>() {
                    });
            log.debug("service2 parsed {} recommendation item(s)", items.size());
            return items;
        } catch (IllegalArgumentException e) {
            log.warn("service2 recommendations array could not be mapped to the expected shape: {}", truncate(body));
            throw new ProviderException("service2 response body could not be parsed", e);
        }
    }

    /** Extracts a human-readable error message by probing known error fields. */
    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            for (String field : ERROR_MESSAGE_FIELDS) {
                JsonNode node = root.path(field);
                if (!node.isMissingNode() && !node.isNull()) {
                    return node.isTextual() ? node.asText() : node.toString();
                }
            }
        } catch (JsonProcessingException ignored) {
        }
        return truncate(body);
    }

    private static String truncate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= MAX_LOGGED_BODY_CHARS ? text : text.substring(0, MAX_LOGGED_BODY_CHARS) + "...(truncated)";
    }
}
