package com.example.ParkingSimulator;

/** Immutable record of a parking booking made through ParkWise. */
public record BookingRecord(
        String bookingId,
        String carParkId,
        String carParkName,
        String slotLabel,
        String dateStr,
        String startTimeStr,
        String endTimeStr,
        int durationHours,
        double totalCost,
        String status) {

    public BookingRecord {
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("Booking id is required");
        }
        if (carParkId == null || carParkId.isBlank()) {
            throw new IllegalArgumentException("Car park id is required");
        }
        if (carParkName == null || carParkName.isBlank()) {
            throw new IllegalArgumentException("Car park name is required");
        }
        if (slotLabel == null || slotLabel.isBlank()) {
            throw new IllegalArgumentException("Slot label is required");
        }
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("Date is required");
        }
        if (startTimeStr == null || startTimeStr.isBlank()) {
            throw new IllegalArgumentException("Start time is required");
        }
        if (endTimeStr == null || endTimeStr.isBlank()) {
            throw new IllegalArgumentException("End time is required");
        }
        if (durationHours < 1) {
            throw new IllegalArgumentException("Duration must be at least 1 hour");
        }
        if (totalCost < 0) {
            throw new IllegalArgumentException("Total cost cannot be negative");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
    }
}
