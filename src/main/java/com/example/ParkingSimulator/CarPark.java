package com.example.ParkingSimulator;

/** Immutable domain model for a multi-lot car park. */
public record CarPark(
        String id,
        String name,
        String location,
        double distanceKm,
        double hourlyRate,
        int totalCapacity,
        int occupiedCount,
        int availableCount,
        int reservedCount) {

    public CarPark {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Car park id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Car park name is required");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Car park location is required");
        }
        if (!Double.isFinite(distanceKm) || distanceKm < 0) {
            throw new IllegalArgumentException("Distance cannot be negative");
        }
        if (!Double.isFinite(hourlyRate) || hourlyRate < 0) {
            throw new IllegalArgumentException("Hourly rate cannot be negative");
        }
        if (totalCapacity < 1) {
            throw new IllegalArgumentException("Total capacity must be positive");
        }
        if (occupiedCount < 0 || availableCount < 0 || reservedCount < 0) {
            throw new IllegalArgumentException("Counts cannot be negative");
        }
    }

    /** Returns the occupied percentage of total capacity (0-100). */
    public double getOccupancyRate() {
        if (totalCapacity <= 0) {
            return 0.0;
        }
        return (double) occupiedCount / totalCapacity * 100.0;
    }
}
