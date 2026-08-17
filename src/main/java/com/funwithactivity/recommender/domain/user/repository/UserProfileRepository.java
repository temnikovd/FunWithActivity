package com.funwithactivity.recommender.domain.user.repository;

import com.funwithactivity.recommender.domain.user.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, String> {
}
