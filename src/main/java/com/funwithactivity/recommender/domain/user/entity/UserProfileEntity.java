package com.funwithactivity.recommender.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_profile")
public class UserProfileEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false, length = 64)
    private String userId;

    @Column(name = "height_cm", nullable = false)
    private double heightCm;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "gender", length = 32)
    private String gender;

    @Column(name = "activity_level", length = 32)
    private String activityLevel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfileEntity() {
        // required by JPA
    }

    public UserProfileEntity(
            String userId,
            double heightCm,
            double weightKg,
            LocalDate birthDate,
            String gender,
            String activityLevel,
            Instant updatedAt) {
        this.userId = userId;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.birthDate = birthDate;
        this.gender = gender;
        this.activityLevel = activityLevel;
        this.updatedAt = updatedAt;
    }

    public String getUserId() {
        return userId;
    }

    public double getHeightCm() {
        return heightCm;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getGender() {
        return gender;
    }

    public String getActivityLevel() {
        return activityLevel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
