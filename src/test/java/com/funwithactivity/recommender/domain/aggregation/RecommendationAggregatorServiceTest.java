package com.funwithactivity.recommender.domain.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.funwithactivity.recommender.config.ProviderProperties;
import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.domain.provider.ProviderAdapter;
import com.funwithactivity.recommender.domain.user.UserNotFoundException;
import com.funwithactivity.recommender.domain.user.UserProfileService;
import com.funwithactivity.recommender.generated.model.ProviderStatus;
import com.funwithactivity.recommender.generated.model.Recommendation;
import com.funwithactivity.recommender.generated.model.RecommendationsResponse;
import com.funwithactivity.recommender.generated.model.UserProfile;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        properties = new ProviderProperties();
        properties.getAggregation().setOverallTimeout(Duration.ofMillis(500));

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

    @Test
    void mergesAndRanksResultsFromAllSuccessfulProviders() {
        ProviderAdapter service1 = fakeAdapter("service1",
                new UnifiedRecommendation("service1", "Walk more", null, 0.4));
        ProviderAdapter service2 = fakeAdapter("service2",
                new UnifiedRecommendation("service2", "Drink water", "Stay hydrated", 0.9));

        RecommendationAggregatorService aggregator =
                new RecommendationAggregatorService(userProfileService, List.of(service1, service2), executor, properties);

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

        RecommendationAggregatorService aggregator =
                new RecommendationAggregatorService(userProfileService, List.of(service1, service2), executor, properties);

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

        RecommendationAggregatorService aggregator =
                new RecommendationAggregatorService(userProfileService, List.of(slowAdapter), executor, properties);

        RecommendationsResponse response = aggregator.getRecommendations("u-1001");

        assertThat(response.getRecommendations()).isEmpty();
        assertThat(response.getProviderStatuses()).extracting(ProviderStatus::getStatus)
                .containsExactly(ProviderStatus.StatusEnum.TIMEOUT);
    }

    @Test
    void propagatesUserNotFoundWithoutCallingProviders() {
        when(userProfileService.getProfile("unknown")).thenThrow(new UserNotFoundException("unknown"));
        ProviderAdapter service1 = fakeAdapter("service1", new UnifiedRecommendation("service1", "x", null, 0.1));

        RecommendationAggregatorService aggregator =
                new RecommendationAggregatorService(userProfileService, List.of(service1), executor, properties);

        assertThatThrownBy(() -> aggregator.getRecommendations("unknown"))
                .isInstanceOf(UserNotFoundException.class);
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
