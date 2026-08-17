package com.funwithactivity.recommender.api;

import com.funwithactivity.recommender.domain.aggregation.RecommendationAggregatorService;
import com.funwithactivity.recommender.generated.api.RecommendationsApi;
import com.funwithactivity.recommender.generated.model.RecommendationsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationsController implements RecommendationsApi {

    private static final Logger log = LoggerFactory.getLogger(RecommendationsController.class);

    private final RecommendationAggregatorService aggregatorService;

    public RecommendationsController(RecommendationAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @Override
    public ResponseEntity<RecommendationsResponse> getRecommendations(String userId) {
        log.info("GET /recommendations/{}", userId);
        return ResponseEntity.ok(aggregatorService.getRecommendations(userId));
    }
}
