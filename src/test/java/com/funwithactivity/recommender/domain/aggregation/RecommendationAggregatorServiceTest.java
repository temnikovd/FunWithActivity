package com.funwithactivity.recommender.domain.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.funwithactivity.recommender.config.ProviderProperties;
import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.domain.provider.ProviderAdapter;
import com.funwithactivity.recommender.domain.provider.ProviderException;
import com.funwithactivity.recommender.domain.user.UserNotFoundException;
import com.funwithactivity.recommender.domain.user.UserProfileService;
import com.funwithactivity.recommender.generated.model.ProviderStatus;
import com.funwithactivity.recommender.generated.model.Recommendation;
import com.funwithactivity.recommender.generated.model.RecommendationsResponse;
import com.funwithactivity.recommender.generated.model.UserProfile;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationAggregatorServiceTest {

    @Mock
    private UserProfileService userProfileService;

    private ExecutorService executor;
    private ProviderProperties properties;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        properties = new ProviderProperties();
        properties.getAggregation().setOverallTimeout(Duration.ofMillis(500));

        // Short, deterministic retry so tests stay fast; only ProviderException
        // is retried, same as the production application.yml config.
        retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(5))
                .retryExceptions(ProviderException.class)
                .build());
        circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());

        UserProfile profile = new UserProfile();
        profile.setUserId("u-1001");
        profile.setHeightCm(184.0);
        profile.setWeightKg(84.0);
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        lenient().when(userProfileService.getProfile("u-1001")).thenReturn(profile);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private RecommendationAggregatorService newAggregator(List<ProviderAdapter> adapters) {
        return new RecommendationAggregatorService(
                userProfileService, adapters, executor, properties, circuitBreakerRegistry, retryRegistry);
    }

    @Test
    void mergesAndRanksResultsFromAllSuccessfulProviders() {
        ProviderAdapter service1 = fakeAdapter("service1",
                new UnifiedRecommendation("service1", "Walk more", null, 0.4));
        ProviderAdapter service2 = fakeAdapter("service2",
                new UnifiedRecommendation("service2", "Drink water", "Stay hydrated", 0.9));

        RecommendationAggregatorService aggregator = newAggregator(List.of(service1, service2));

        RecommendationsResponse response = aggregator.getRecommendations("u-1001");

        assertThat(response.getUserId()).isEqualTo("u-1001");
        assertThat(response.getRecommendations()).extracting(Recommendation::getTitle)
                .containsExactly("Drink water", "Walk more"); // ranked by normalizedScore desc
        assertThat(response.getProviderStatuses()).extracting(ProviderStatus::getStatus)
                .containsExactly(ProviderStatus.StatusEnum.OK, ProviderStatus.StatusEnum.OK);
    }

    @Test
    void returnsPartialResultsWhenOneProviderFails() {
        ProviderAdapter service1 = fakeAdapter("service1",
                new UnifiedRecommendation("service1", "Walk more", null, 0.4));
        ProviderAdapter service2 = failingAdapter("service2", new RuntimeException("boom"));

        RecommendationAggregatorService aggregator = newAggregator(List.of(service1, service2));

        RecommendationsResponse response = aggregator.getRecommendations("u-1001");

        assertThat(response.getRecommendations()).extracting(Recommendation::getTitle).containsExactly("Walk more");
        assertThat(response.getProviderStatuses()).extracting(ProviderStatus::getProvider, ProviderStatus::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("service1", ProviderStatus.StatusEnum.OK),
                        org.assertj.core.groups.Tuple.tuple("service2", ProviderStatus.StatusEnum.FAILED));
    }

    @Test
    void reportsTimeoutWhenProviderExceedsOverallTimeout() {
        properties.getAggregation().setOverallTimeout(Duration.ofMillis(100));
        ProviderAdapter slowAdapter = new ProviderAdapter() {
            @Override
            public String providerName() {
                return "service1";
            }

            @Override
            public List<UnifiedRecommendation> fetchRecommendations(UserProfile profile) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of();
            }
        };

        RecommendationAggregatorService aggregator = newAggregator(List.of(slowAdapter));

        RecommendationsResponse response = aggregator.getRecommendations("u-1001");

        assertThat(response.getRecommendations()).isEmpty();
        assertThat(response.getProviderStatuses()).extracting(ProviderStatus::getStatus)
                .containsExactly(ProviderStatus.StatusEnum.TIMEOUT);
    }

    @Test
    void propagatesUserNotFoundWithoutCallingProviders() {
        when(userProfileService.getProfile("unknown")).thenThrow(new UserNotFoundException("unknown"));
        ProviderAdapter service1 = fakeAdapter("service1", new UnifiedRecommendation("service1", "x", null, 0.1));

        RecommendationAggregatorService aggregator = newAggregator(List.of(service1));

        assertThatThrownBy(() -> aggregator.getRecommendations("unknown"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void callsAllProvidersConcurrentlyRatherThanOneAfterAnother() {
        // Regression guard for the sequential-stream bug: two adapters that each
        // sleep ~300ms must overlap, not add up. Generous margin to avoid CI flakiness.
        ProviderAdapter slow1 = sleepingAdapter("service1", 300);
        ProviderAdapter slow2 = sleepingAdapter("service2", 300);

        RecommendationAggregatorService aggregator = newAggregator(List.of(slow1, slow2));

        long start = System.nanoTime();
        aggregator.getRecommendations("u-1001");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).as("both providers should be called in parallel, not sequentially").isLessThan(550);
    }

    @Test
    void retriesAProviderThatFailsOnceThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        ProviderAdapter flaky = new ProviderAdapter() {
            @Override
            public String providerName() {
                return "service1";
            }

            @Override
            public List<UnifiedRecommendation> fetchRecommendations(UserProfile profile) {
                if (attempts.getAndIncrement() == 0) {
                    throw new ProviderException("transient failure");
                }
                return List.of(new UnifiedRecommendation("service1", "Walk more", null, 0.4));
            }
        };

        RecommendationAggregatorService aggregator = newAggregator(List.of(flaky));

        RecommendationsResponse response = aggregator.getRecommendations("u-1001");

        assertThat(attempts.get()).as("adapter should have been retried after the first failure").isGreaterThan(1);
        assertThat(response.getProviderStatuses()).extracting(ProviderStatus::getStatus)
                .containsExactly(ProviderStatus.StatusEnum.OK);
        assertThat(response.getRecommendations()).extracting(Recommendation::getTitle).containsExactly("Walk more");
    }

    @Test
    void opensCircuitBreakerAfterRepeatedFailuresAndShortCircuitsFurtherCalls() {
        ProviderAdapter alwaysFails = failingAdapter("service1", new ProviderException("boom"));
        RecommendationAggregatorService aggregator = newAggregator(List.of(alwaysFails));

        // minimumNumberOfCalls=2 in the test registry: two failing calls trip the breaker.
        aggregator.getRecommendations("u-1001");
        aggregator.getRecommendations("u-1001");
        RecommendationsResponse response = aggregator.getRecommendations("u-1001");

        assertThat(response.getProviderStatuses()).extracting(ProviderStatus::getErrorMessage)
                .anySatisfy(message -> assertThat(message).containsIgnoringCase("circuitbreaker"));
    }

    private static ProviderAdapter sleepingAdapter(String name, long sleepMillis) {
        return new ProviderAdapter() {
            @Override
            public String providerName() {
                return name;
            }

            @Override
            public List<UnifiedRecommendation> fetchRecommendations(UserProfile profile) {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of(new UnifiedRecommendation(name, "rec-" + name, null, 0.5));
            }
        };
    }

    private static ProviderAdapter fakeAdapter(String name, UnifiedRecommendation... recommendations) {
        return new ProviderAdapter() {
            @Override
            public String providerName() {
                return name;
            }

            @Override
            public List<UnifiedRecommendation> fetchRecommendations(UserProfile profile) {
                return List.of(recommendations);
            }
        };
    }

    private static ProviderAdapter failingAdapter(String name, RuntimeException toThrow) {
        return new ProviderAdapter() {
            @Override
            public String providerName() {
                return name;
            }

            @Override
            public List<UnifiedRecommendation> fetchRecommendations(UserProfile profile) {
                throw toThrow;
            }
        };
    }
}
