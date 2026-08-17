package com.funwithactivity.recommender.domain.provider;

import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.generated.model.UserProfile;
import com.funwithactivity.recommender.generated.service1.model.Service1RecommendationItem;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adapter for Service1: input in cm/kg + constant session token; output
 * confidence (0..1) already matches our unified score range.
 */
@Component
public class Service1Adapter implements ProviderAdapter {

    public static final String PROVIDER_NAME = "service1";

    private final Service1Client client;

    public Service1Adapter(Service1Client client) {
        this.client = client;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<UnifiedRecommendation> fetchRecommendations(UserProfile profile) {
        List<Service1RecommendationItem> items =
                client.fetchRecommendations(profile.getHeightCm(), profile.getWeightKg());

        return items.stream()
                .map(item -> new UnifiedRecommendation(PROVIDER_NAME, item.getRecommendation(), null, item.getConfidence()))
                .toList();
    }
}
