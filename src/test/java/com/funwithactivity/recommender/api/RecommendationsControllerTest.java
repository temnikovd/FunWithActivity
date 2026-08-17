package com.funwithactivity.recommender.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funwithactivity.recommender.domain.aggregation.RecommendationAggregatorService;
import com.funwithactivity.recommender.domain.user.UserNotFoundException;
import com.funwithactivity.recommender.generated.model.ProviderStatus;
import com.funwithactivity.recommender.generated.model.Recommendation;
import com.funwithactivity.recommender.generated.model.RecommendationsResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationsController.class)
class RecommendationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationAggregatorService aggregatorService;

    @Test
    void returnsAggregatedRecommendationsAsJson() throws Exception {
        RecommendationsResponse response = new RecommendationsResponse();
        response.setUserId("u-1001");
        response.setGeneratedAt(OffsetDateTime.now());
        response.setRecommendations(List.of(new Recommendation("service2", "Drink water", 0.9)));
        response.setProviderStatuses(List.of(
                new ProviderStatus("service1", ProviderStatus.StatusEnum.FAILED),
                new ProviderStatus("service2", ProviderStatus.StatusEnum.OK)));
        when(aggregatorService.getRecommendations("u-1001")).thenReturn(response);

        mockMvc.perform(get("/recommendations/u-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u-1001"))
                .andExpect(jsonPath("$.recommendations[0].title").value("Drink water"))
                .andExpect(jsonPath("$.providerStatuses[0].status").value("FAILED"));
    }

    @Test
    void returns404WhenUserUnknown() throws Exception {
        when(aggregatorService.getRecommendations("unknown")).thenThrow(new UserNotFoundException("unknown"));

        mockMvc.perform(get("/recommendations/unknown"))
                .andExpect(status().isNotFound());
    }
}
