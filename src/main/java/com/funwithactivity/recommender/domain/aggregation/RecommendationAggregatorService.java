package com.funwithactivity.recommender.domain.aggregation;

import com.funwithactivity.recommender.config.ProviderProperties;
import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.domain.provider.ProviderAdapter;
import com.funwithactivity.recommender.domain.user.UserProfileService;
import com.funwithactivity.recommender.generated.model.ProviderStatus;
import com.funwithactivity.recommender.generated.model.Recommendation;
import com.funwithactivity.recommender.generated.model.RecommendationsResponse;
import com.funwithactivity.recommender.generated.model.UserProfile;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 0 vertical slice of the target-state Recommendations Aggregator
 * (system-design-v2.md §7.5): scatter-gather across every configured
 * {@link ProviderAdapter} in parallel, tolerate individual provider failures
 * or timeouts, merge and rank the successful results.
 *
 * Adding Service3 requires only a new {@link ProviderAdapter} bean - Spring
 * injects every implementation into {@code providerAdapters} automatically,
 * so this class never changes.
 */
@Service
public class RecommendationAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationAggregatorService.class);

    private final UserProfileService userProfileService;
    private final List<ProviderAdapter> providerAdapters;
    private final ExecutorService providerCallExecutor;
    private final long overallTimeoutMillis;

    public RecommendationAggregatorService(
            UserProfileService userProfileService,
            List<ProviderAdapter> providerAdapters,
            ExecutorService providerCallExecutor,
            ProviderProperties providerProperties) {
        this.userProfileService = userProfileService;
        this.providerAdapters = providerAdapters;
        this.providerCallExecutor = providerCallExecutor;
        this.overallTimeoutMillis = providerProperties.getAggregation().getOverallTimeout().toMillis();
    }

    public RecommendationsResponse getRecommendations(String userId) {
        UserProfile profile = userProfileService.getProfile(userId);

        log.info("Aggregating recommendations for userId={} across {} provider(s)", userId, providerAdapters.size());

        List<ProviderOutcome> outcomes = providerAdapters.stream()
                .map(adapter -> callWithTimeout(adapter, profile))
                .map(CompletableFuture::join)
                .toList();

        List<Recommendation> recommendations = outcomes.stream()
                .flatMap(outcome -> outcome.recommendations().stream())
                .sorted(Comparator.comparingDouble(UnifiedRecommendation::normalizedScore).reversed())
                .map(this::toApiModel)
                .toList();

        List<ProviderStatus> statuses = outcomes.stream().map(this::toApiModel).toList();

        RecommendationsResponse response = new RecommendationsResponse();
        response.setUserId(userId);
        response.setGeneratedAt(OffsetDateTime.now());
        response.setRecommendations(recommendations);
        response.setProviderStatuses(statuses);
        return response;
    }

    private CompletableFuture<ProviderOutcome> callWithTimeout(ProviderAdapter adapter, UserProfile profile) {
        long start = System.nanoTime();
        return CompletableFuture.supplyAsync(() -> callProvider(adapter, profile, start), providerCallExecutor)
                .orTimeout(overallTimeoutMillis, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    long latencyMs = elapsedMillis(start);
                    log.warn("Provider {} timed out after {}ms", adapter.providerName(), latencyMs);
                    return ProviderOutcome.timeout(adapter.providerName(), latencyMs);
                });
    }

    private ProviderOutcome callProvider(ProviderAdapter adapter, UserProfile profile, long start) {
        try {
            List<UnifiedRecommendation> recommendations = adapter.fetchRecommendations(profile);
            long latencyMs = elapsedMillis(start);
            log.info(
                    "Provider {} returned {} recommendation(s) in {}ms",
                    adapter.providerName(), recommendations.size(), latencyMs);
            return ProviderOutcome.success(adapter.providerName(), recommendations, latencyMs);
        } catch (RuntimeException e) {
            long latencyMs = elapsedMillis(start);
            log.warn("Provider {} failed after {}ms: {}", adapter.providerName(), latencyMs, e.getMessage());
            return ProviderOutcome.failure(adapter.providerName(), e.getMessage(), latencyMs);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private Recommendation toApiModel(UnifiedRecommendation recommendation) {
        Recommendation dto = new Recommendation(
                recommendation.sourceProvider(), recommendation.title(), recommendation.normalizedScore());
        dto.setDetails(recommendation.details());
        return dto;
    }

    private ProviderStatus toApiModel(ProviderOutcome outcome) {
        ProviderStatus dto = new ProviderStatus(outcome.provider(), outcome.status());
        dto.setErrorMessage(outcome.errorMessage());
        dto.setLatencyMs(outcome.latencyMs());
        return dto;
    }
}
