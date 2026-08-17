package com.funwithactivity.recommender.domain.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * Thin HTTP client for Service1. Handles the Lambda Function URL envelope
 * quirk (see openapi/service1-api.yaml) - the real payload is a JSON string
 * nested inside the "body" field of the HTTP response, not the response body itself.
 */
@Component
public class Service1Client {

    private static final Logger log = LoggerFactory.getLogger(Service1Client.class);

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

        Service1LambdaEnvelope envelope;
        try {
            envelope = restClient.post()
                    .uri(properties.getPath())
                    .body(request)
                    .retrieve()
                    .body(Service1LambdaEnvelope.class);
        } catch (RestClientException e) {
            throw new ProviderException("service1 call failed: " + e.getMessage(), e);
        }

        if (envelope == null || envelope.getBody() == null) {
            throw new ProviderException("service1 returned an empty response");
        }

        try {
            return List.of(objectMapper.readValue(envelope.getBody(), Service1RecommendationItem[].class));
        } catch (JsonProcessingException e) {
            throw new ProviderException("service1 response body could not be parsed", e);
        }
    }
}
