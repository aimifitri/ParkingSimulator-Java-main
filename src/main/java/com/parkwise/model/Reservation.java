package com.parkwise.model;

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
		id = id.trim();
		if (vehicleId == null || vehicleId.isBlank()) {
			throw new IllegalArgumentException("Vehicle identifier is required");
		}
		vehicleId = vehicleId.trim();
		if (slotNumber < 1 || startOffsetSeconds < 0 || durationSeconds < 1) {
			throw new IllegalArgumentException("Invalid reservation slot or time interval");
		}
		if ((long) startOffsetSeconds + durationSeconds > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Reservation interval exceeds the supported time range");
		}
	}

	public long endOffsetSeconds() {
		return (long) startOffsetSeconds + durationSeconds;
	}
}
