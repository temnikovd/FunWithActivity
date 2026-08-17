package com.funwithactivity.recommender.domain.user;

import com.funwithactivity.recommender.domain.user.entity.UserProfileEntity;
import com.funwithactivity.recommender.domain.user.repository.UserProfileRepository;
import com.funwithactivity.recommender.generated.model.UserProfile;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(String userId) {
        log.debug("Loading profile for userId={}", userId);

        UserProfileEntity entity = userProfileRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Profile lookup failed, unknown userId={}", userId);
                    return new UserNotFoundException(userId);
                });

        return toDto(entity);
    }

    private UserProfile toDto(UserProfileEntity entity) {
        UserProfile dto = new UserProfile();
        dto.setUserId(entity.getUserId());
        dto.setHeightCm(entity.getHeightCm());
        dto.setWeightKg(entity.getWeightKg());
        dto.setBirthDate(entity.getBirthDate());
        dto.setGender(entity.getGender());
        dto.setActivityLevel(entity.getActivityLevel());
        dto.setUpdatedAt(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
        return dto;
    }
}
