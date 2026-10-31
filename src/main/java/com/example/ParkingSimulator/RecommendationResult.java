package com.example.ParkingSimulator;

/** The result of a car-park recommendation for a driver. */
public record RecommendationResult(
        CarPark carPark,
        int matchScore,
        String badgeText) {

    public RecommendationResult {
        if (carPark == null) {
            throw new IllegalArgumentException("Car park is required");
        }
        if (matchScore < 0 || matchScore > 100) {
            throw new IllegalArgumentException("Match score must be between 0 and 100");
        }
        if (badgeText == null || badgeText.isBlank()) {
            throw new IllegalArgumentException("Badge text is required");
        }
    }
}
