package com.funwithactivity.recommender.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funwithactivity.recommender.domain.user.UserNotFoundException;
import com.funwithactivity.recommender.domain.user.UserProfileService;
import com.funwithactivity.recommender.generated.model.UserProfile;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsersController.class)
class UsersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    @Test
    void returnsProfileAsJson() throws Exception {
        UserProfile profile = new UserProfile();
        profile.setUserId("u-1001");
        profile.setHeightCm(184.0);
        profile.setWeightKg(84.0);
        profile.setBirthDate(LocalDate.of(1990, 4, 12));
        when(userProfileService.getProfile("u-1001")).thenReturn(profile);

        mockMvc.perform(get("/users/u-1001/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u-1001"))
                .andExpect(jsonPath("$.heightCm").value(184.0))
                .andExpect(jsonPath("$.weightKg").value(84.0));
    }

    @Test
    void returns404WhenUserUnknown() throws Exception {
        when(userProfileService.getProfile("unknown")).thenThrow(new UserNotFoundException("unknown"));

        mockMvc.perform(get("/users/unknown/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }
}
