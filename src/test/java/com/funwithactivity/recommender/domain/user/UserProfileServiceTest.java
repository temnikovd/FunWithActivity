package com.funwithactivity.recommender.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funwithactivity.recommender.domain.user.repository.UserProfileRepository;
import com.funwithactivity.recommender.generated.model.UserProfile;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Exercises the real Liquibase-managed H2 schema and seed data
 * (db/changelog/changes/001-init-schema.yaml, 002-seed-users.yaml).
 */
@DataJpaTest
@Import(UserProfileService.class)
class UserProfileServiceTest {

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void seedDataIsPresentForAllThreeDemoUsers() {
        assertThat(userProfileRepository.findById("u-1001")).isPresent();
        assertThat(userProfileRepository.findById("u-1002")).isPresent();
        assertThat(userProfileRepository.findById("u-1003")).isPresent();
    }

    @Test
    void returnsSeededProfileMappedToApiModel() {
        UserProfile profile = userProfileService.getProfile("u-1001");

        assertThat(profile.getUserId()).isEqualTo("u-1001");
        assertThat(profile.getHeightCm()).isEqualTo(184.0);
        assertThat(profile.getWeightKg()).isEqualTo(84.0);
        assertThat(profile.getBirthDate()).isEqualTo(LocalDate.of(1990, 4, 12));
    }

    @Test
    void throwsUserNotFoundForUnknownUser() {
        assertThatThrownBy(() -> userProfileService.getProfile("does-not-exist"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
