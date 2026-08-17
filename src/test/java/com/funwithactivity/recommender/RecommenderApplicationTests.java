package com.funwithactivity.recommender;

import static org.assertj.core.api.Assertions.assertThat;

import com.funwithactivity.recommender.domain.user.UserProfileService;
import com.funwithactivity.recommender.generated.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RecommenderApplicationTests {

    @Autowired
    private UserProfileService userProfileService;

    @Test
    void contextLoadsAndLiquibaseSeedDataIsReachableThroughTheFullStack() {
        UserProfile profile = userProfileService.getProfile("u-1002");

        assertThat(profile.getHeightCm()).isEqualTo(178.5);
        assertThat(profile.getWeightKg()).isEqualTo(92.3);
    }
}
