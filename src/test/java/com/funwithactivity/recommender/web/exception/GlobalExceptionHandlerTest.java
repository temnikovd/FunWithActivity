package com.funwithactivity.recommender.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.funwithactivity.recommender.domain.user.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsUserNotFoundTo404ProblemDetail() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/u-404/profile");

        ProblemDetail problem = handler.handleUserNotFound(new UserNotFoundException("u-404"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("User not found");
        assertThat(problem.getDetail()).contains("u-404");
    }

    @Test
    void mapsUnexpectedExceptionTo500ProblemDetailWithoutLeakingDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/recommendations/u-1");

        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("sensitive internal detail"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("sensitive internal detail");
    }
}
