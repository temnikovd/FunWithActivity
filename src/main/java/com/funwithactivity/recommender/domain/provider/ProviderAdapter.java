package com.funwithactivity.recommender.domain.provider;

import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.generated.model.UserProfile;
import java.util.List;

/**
 * A single external activities/health-tips provider integration.
 * Adding a new provider = a new implementation of this interface, wired up
 * in {@link com.funwithactivity.recommender.domain.aggregation.RecommendationAggregatorService}
 * - zero changes to the aggregation/scatter-gather logic
 * (system-design-v2.md §7.5, §9 "New provider / new device").
 */
public interface ProviderAdapter {

    /** Stable identifier used in API responses and logs, e.g. "service1". */
    String providerName();

    /**
     * Calls the external provider and maps its response to the unified model.
     *
     * @throws ProviderException if the call fails, times out, or the response cannot be parsed
     */
    List<UnifiedRecommendation> fetchRecommendations(UserProfile profile);
}
