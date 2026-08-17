package com.funwithactivity.recommender.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.funwithactivity.recommender.domain.model.UnifiedRecommendation;
import com.funwithactivity.recommender.generated.model.UserProfile;
import com.funwithactivity.recommender.generated.service1.model.Service1RecommendationItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Service1AdapterTest {

    @Mock
    private Service1Client client;

    @Test
    void mapsConfidenceAndRecommendationToUnifiedModel() {
        Service1Adapter adapter = new Service1Adapter(client);

        UserProfile profile = new UserProfile();
        profile.setUserId("u-1");
        profile.setHeightCm(184.0);
        profile.setWeightKg(84.0);
        profile.setBirthDate(LocalDate.of(1990, 1, 1));

        when(client.fetchRecommendations(184.0, 84.0))
                .thenReturn(List.of(new Service1RecommendationItem(0.6, "Walk more")));

        List<UnifiedRecommendation> result = adapter.fetchRecommendations(profile);

        assertThat(result).containsExactly(new UnifiedRecommendation("service1", "Walk more", null, 0.6));
        verify(client).fetchRecommendations(184.0, 84.0);
    }
}
