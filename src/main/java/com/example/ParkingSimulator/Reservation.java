package com.example.ParkingSimulator;

/** A parking-space booking relative to the start of a simulation run. */
public record Reservation(
		String id,
		String vehicleId,
		int slotNumber,
		int startOffsetSeconds,
		int durationSeconds) {

	public Reservation {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Reservation id is required");
		}
		if (vehicleId == null || vehicleId.isBlank()) {
			throw new IllegalArgumentException("Vehicle identifier is required");
		}
		vehicleId = vehicleId.trim();
		if (slotNumber < 1 || startOffsetSeconds < 0 || durationSeconds < 1) {
			throw new IllegalArgumentException("Invalid reservation slot or time interval");
		}
	}

	public int endOffsetSeconds() {
		return startOffsetSeconds + durationSeconds;
	}
}
