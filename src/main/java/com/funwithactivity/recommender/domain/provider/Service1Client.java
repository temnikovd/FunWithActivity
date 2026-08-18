package com.funwithactivity.recommender.domain.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funwithactivity.recommender.config.ProviderProperties;
import com.funwithactivity.recommender.generated.service1.model.Service1LambdaEnvelope;
import com.funwithactivity.recommender.generated.service1.model.Service1RecommendationItem;
import com.funwithactivity.recommender.generated.service1.model.Service1Request;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP client for Service1. Unwraps the Lambda envelope, whose "body"
 * field holds the actual JSON payload as a string.
 */
@Component
public class Service1Client {

    private static final Logger log = LoggerFactory.getLogger(Service1Client.class);
    private static final int MAX_LOGGED_BODY_CHARS = 500;
    private static final List<String> ERROR_MESSAGE_FIELDS = List.of("errorMessage", "error", "message", "detail");

    private final RestClient restClient;
    private final ProviderProperties.Provider properties;
    private final ObjectMapper objectMapper;

    public Service1Client(
            @Qualifier("service1RestClient") RestClient restClient,
            ProviderProperties providerProperties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = providerProperties.getProviders().getService1();
        this.objectMapper = objectMapper;
    }

    public List<Service1RecommendationItem> fetchRecommendations(double heightCm, double weightKg) {
        Service1Request request = new Service1Request(heightCm, weightKg, properties.getToken());

        log.debug("Calling service1");

        Service1LambdaEnvelope envelope;
        try {
            envelope = restClient.post()
                    .uri(properties.getPath())
                    .body(request)
                    .retrieve()
                    .body(Service1LambdaEnvelope.class);
        } catch (RestClientException e) {
            log.warn("service1 HTTP call failed: {}", e.toString());
            throw new ProviderException("service1 call failed: " + e.getMessage(), e);
        }

        if (envelope == null || envelope.getBody() == null) {
            log.warn("service1 returned an empty envelope (envelope={})", envelope);
            throw new ProviderException("service1 returned an empty response");
        }

        Integer statusCode = envelope.getStatusCode();
        log.debug("service1 envelope statusCode={} bodyLength={}", statusCode, envelope.getBody().length());

        if (statusCode == null || statusCode < 200 || statusCode >= 300) {
            String errorMessage = extractErrorMessage(envelope.getBody());
            log.warn("service1 returned statusCode={}: {}", statusCode, truncate(envelope.getBody()));
            throw new ProviderException("service1 returned statusCode=" + statusCode + ": " + errorMessage);
        }

        return parseSuccessBody(envelope.getBody());
    }

    private List<Service1RecommendationItem> parseSuccessBody(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            log.warn("service1 success body was not valid JSON: {}", truncate(body));
            throw new ProviderException("service1 response body could not be parsed", e);
        }

        if (!root.isArray()) {
            log.warn("service1 success body was not a JSON array: {}", truncate(body));
            throw new ProviderException("service1 success response was not a recommendations array");
        }

        try {
            List<Service1RecommendationItem> items = objectMapper.convertValue(
                    root, objectMapper.getTypeFactory().constructCollectionType(List.class, Service1RecommendationItem.class));
            log.debug("service1 parsed {} recommendation item(s)", items.size());
            return items;
        } catch (IllegalArgumentException e) {
            log.warn("service1 array could not be mapped to the expected shape: {}", truncate(body));
            throw new ProviderException("service1 response body could not be parsed", e);
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
