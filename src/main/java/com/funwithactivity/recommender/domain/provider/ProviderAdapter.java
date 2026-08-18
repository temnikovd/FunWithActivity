package com.funwithactivity.recommender.domain.provider;

import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.generated.model.UserProfile;
import java.util.List;

/**
 * A single external activities/health-tips provider integration. A new
 * provider is added by implementing this interface; the aggregator picks
 * it up automatically.
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
