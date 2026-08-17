package com.funwithactivity.recommender.domain.user;

public class UserNotFoundException extends RuntimeException {

    private final String userId;

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
