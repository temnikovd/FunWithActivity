package com.funwithactivity.recommender.domain.model;

/** Common normalized shape every provider adapter maps its payload into. */
public record UnifiedRecommendation(String sourceProvider, String title, String details, double normalizedScore) {
}
