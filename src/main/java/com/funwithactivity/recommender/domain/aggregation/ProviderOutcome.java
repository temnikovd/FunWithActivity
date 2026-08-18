package com.funwithactivity.recommender.domain.aggregation;

import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.generated.model.ProviderStatus;
import java.util.List;

/** Result of calling a single provider, successful or not. */
record ProviderOutcome(
        String provider,
        ProviderStatus.StatusEnum status,
        String errorMessage,
        long latencyMs,
        List<UnifiedRecommendation> recommendations) {

    static ProviderOutcome success(String provider, List<UnifiedRecommendation> recommendations, long latencyMs) {
        return new ProviderOutcome(provider, ProviderStatus.StatusEnum.OK, null, latencyMs, recommendations);
    }

    static ProviderOutcome failure(String provider, String errorMessage, long latencyMs) {
        return new ProviderOutcome(provider, ProviderStatus.StatusEnum.FAILED, errorMessage, latencyMs, List.of());
    }

    static ProviderOutcome timeout(String provider, long latencyMs) {
        return new ProviderOutcome(provider, ProviderStatus.StatusEnum.TIMEOUT, "provider call timed out", latencyMs, List.of());
    }
}
