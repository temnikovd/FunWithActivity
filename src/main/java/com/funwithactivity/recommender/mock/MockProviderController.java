package com.funwithactivity.recommender.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funwithactivity.recommender.generated.service1.model.Service1LambdaEnvelope;
import com.funwithactivity.recommender.generated.service1.model.Service1RecommendationItem;
import com.funwithactivity.recommender.generated.service1.model.Service1Request;
import com.funwithactivity.recommender.generated.service2.model.Service2LambdaEnvelope;
import com.funwithactivity.recommender.generated.service2.model.Service2RecommendationItem;
import com.funwithactivity.recommender.generated.service2.model.Service2Request;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local stand-in for the deployed provider Lambdas, active only under the
 * "local-mock" profile. Mirrors the same envelope shape as the real providers.
 */
@RestController
@Profile("local-mock")
public class MockProviderController {

    private final ObjectMapper objectMapper;

    public MockProviderController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping("/services/service1")
    public Service1LambdaEnvelope service1(@RequestBody Service1Request request) {
        List<Service1RecommendationItem> items = List.of(
                new Service1RecommendationItem(0.9, "Drink more water"),
                new Service1RecommendationItem(0.7, "Take a 20-minute walk"));
        return new Service1LambdaEnvelope(200, writeJson(items));
    }

    @PostMapping("/services/service2")
    public Service2LambdaEnvelope service2(@RequestBody Service2Request request) {
        List<Service2RecommendationItem> items = List.of(
                new Service2RecommendationItem(1, "Stretch", "5-minute morning stretch routine"),
                new Service2RecommendationItem(2, "Hydrate", "Drink a glass of water"));
        return new Service2LambdaEnvelope(200, writeJson(items));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("mock provider failed to serialize response", e);
        }
    }
}
