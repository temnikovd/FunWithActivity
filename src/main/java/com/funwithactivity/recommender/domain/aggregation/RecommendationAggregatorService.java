package com.funwithactivity.recommender.domain.aggregation;

import com.funwithactivity.recommender.config.ProviderProperties;
import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.domain.provider.ProviderAdapter;
import com.funwithactivity.recommender.domain.user.UserProfileService;
import com.funwithactivity.recommender.generated.model.ProviderStatus;
import com.funwithactivity.recommender.generated.model.Recommendation;
import com.funwithactivity.recommender.generated.model.RecommendationsResponse;
import com.funwithactivity.recommender.generated.model.UserProfile;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Scatter-gathers across every configured {@link ProviderAdapter} in parallel,
 * tolerating individual failures or timeouts, then merges and ranks the results.
 * Each call is wrapped in a per-provider {@link Retry} + {@link CircuitBreaker}
 * on top of the overall timeout.
 */
@Service
public class RecommendationAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationAggregatorService.class);

    private final UserProfileService userProfileService;
    private final List<ProviderAdapter> providerAdapters;
    private final ExecutorService providerCallExecutor;
    private final long overallTimeoutMillis;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public RecommendationAggregatorService(
            UserProfileService userProfileService,
            List<ProviderAdapter> providerAdapters,
            ExecutorService providerCallExecutor,
            ProviderProperties providerProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.userProfileService = userProfileService;
        this.providerAdapters = providerAdapters;
        this.providerCallExecutor = providerCallExecutor;
        this.overallTimeoutMillis = providerProperties.getAggregation().getOverallTimeout().toMillis();
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    public RecommendationsResponse getRecommendations(String userId) {
        UserProfile profile = userProfileService.getProfile(userId);

        log.info("Aggregating recommendations for userId={} across {} provider(s)", userId, providerAdapters.size());

        // Start every call before joining any of them, so they run concurrently.
        List<CompletableFuture<ProviderOutcome>> pending =
                providerAdapters.stream().map(adapter -> callWithTimeout(adapter, profile)).toList();
        List<ProviderOutcome> outcomes = pending.stream().map(CompletableFuture::join).toList();

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
        Supplier<ProviderOutcome> resilientCall = decorate(adapter, profile, start);

        return CompletableFuture.supplyAsync(resilientCall, providerCallExecutor)
                .orTimeout(overallTimeoutMillis, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    long latencyMs = elapsedMillis(start);
                    log.warn("Provider {} did not complete within {}ms overall timeout",
                            adapter.providerName(), overallTimeoutMillis);
                    return ProviderOutcome.timeout(adapter.providerName(), latencyMs);
                });
    }

    /**
     * Wraps the raw provider call with a per-provider retry and circuit breaker,
     * converting the outcome (success or final exception) into a {@link ProviderOutcome}.
     */
    private Supplier<ProviderOutcome> decorate(ProviderAdapter adapter, UserProfile profile, long start) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(adapter.providerName());
        Retry retry = retryRegistry.retry(adapter.providerName());

        // Retry wraps the circuit-breaker-guarded call, so each attempt is recorded
        // by the breaker; once open, calls short-circuit without being retried.
        Supplier<List<UnifiedRecommendation>> rawCall = () -> adapter.fetchRecommendations(profile);
        Supplier<List<UnifiedRecommendation>> withCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, rawCall);
        Supplier<List<UnifiedRecommendation>> resilientCall = Retry.decorateSupplier(retry, withCircuitBreaker);

        return () -> {
            try {
                List<UnifiedRecommendation> recommendations = resilientCall.get();
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
        };
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
