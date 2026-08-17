package com.funwithactivity.recommender.domain.provider;

import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.generated.model.UserProfile;
import com.funwithactivity.recommender.generated.service2.model.Service2RecommendationItem;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adapter for Service2: input in lb/feet + unix birth_date + per-request GUID;
 * output priority (1..1000, higher = more important) normalized to 0..1.
 */
@Component
public class Service2Adapter implements ProviderAdapter {

    public static final String PROVIDER_NAME = "service2";
    private static final double MAX_PRIORITY = 1000.0d;

    private final Service2Client client;

    public Service2Adapter(Service2Client client) {
        this.client = client;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<UnifiedRecommendation> fetchRecommendations(UserProfile profile) {
        List<Service2RecommendationItem> items =
                client.fetchRecommendations(profile.getHeightCm(), profile.getWeightKg(), profile.getBirthDate());

        return items.stream()
                .map(item -> new UnifiedRecommendation(
                        PROVIDER_NAME, item.getTitle(), item.getDetails(), item.getPriority() / MAX_PRIORITY))
                .toList();
    }
}
