package com.funwithactivity.recommender.domain.model;

/**
 * Common normalized shape every provider adapter maps its provider-specific
 * payload into (system-design-v2.md §7.5 "Merge &amp; Rank").
 */
public record UnifiedRecommendation(String sourceProvider, String title, String details, double normalizedScore) {
}
