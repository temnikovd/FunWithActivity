package com.funwithactivity.recommender.api;

import com.funwithactivity.recommender.domain.user.UserProfileService;
import com.funwithactivity.recommender.generated.api.UsersApi;
import com.funwithactivity.recommender.generated.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController implements UsersApi {

    private static final Logger log = LoggerFactory.getLogger(UsersController.class);

    private final UserProfileService userProfileService;

    public UsersController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Override
    public ResponseEntity<UserProfile> getUserProfile(String userId) {
        log.info("GET /users/{}/profile", userId);
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }
}
