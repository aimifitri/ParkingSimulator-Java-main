package com.parkwise.service;
import com.parkwise.model.*;
import com.parkwise.view.*;
import com.parkwise.service.*;
import com.parkwise.strategy.*;
import com.parkwise.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Assigns spaces to reservations while preventing overlapping bookings per space. */
public final class ReservationManager {
	private final List<Reservation> reservations = new ArrayList<>();
	private int capacity;

	public ReservationManager(int capacity) {
		if (capacity < 1) {
			throw new IllegalArgumentException("Parking capacity must be positive");
		}
		this.capacity = capacity;
	}

	public synchronized Optional<Reservation> reserve(String vehicleId, int startOffsetSeconds, int durationSeconds) {
		if (vehicleId == null || vehicleId.isBlank()) {
			throw new IllegalArgumentException("Enter a vehicle identifier");
		}
		if (startOffsetSeconds < 0 || durationSeconds < 1) {
			throw new IllegalArgumentException("Reservation times must be positive");
		}

		for (int slotNumber = 1; slotNumber <= capacity; slotNumber++) {
			Optional<Reservation> reservation = reserveForSpace(vehicleId, slotNumber,
					startOffsetSeconds, durationSeconds);
			if (reservation.isPresent()) {
				return reservation;
			}
		}
		return Optional.empty();
	}

	/** Reserves the selected space if its requested interval does not conflict with an existing booking. */
	public synchronized Optional<Reservation> reserveForSpace(String vehicleId, int slotNumber,
			int startOffsetSeconds, int durationSeconds) {
		validateRequest(vehicleId, startOffsetSeconds, durationSeconds);
		if (slotNumber < 1 || slotNumber > capacity) {
			throw new IllegalArgumentException("The selected parking space does not exist");
		}
		boolean overlapsExisting = reservations.stream()
				.filter(existing -> existing.slotNumber() == slotNumber)
				.anyMatch(existing -> overlaps(startOffsetSeconds, durationSeconds,
						existing.startOffsetSeconds(), existing.durationSeconds()));
		if (overlapsExisting) {
			return Optional.empty();
		}
		Reservation reservation = new Reservation(UUID.randomUUID().toString(), vehicleId,
				slotNumber, startOffsetSeconds, durationSeconds);
		reservations.add(reservation);
		reservations.sort(Comparator.comparingInt(Reservation::startOffsetSeconds)
				.thenComparingInt(Reservation::slotNumber));
		return Optional.of(reservation);
	}

	private void validateRequest(String vehicleId, int startOffsetSeconds, int durationSeconds) {
		if (vehicleId == null || vehicleId.isBlank()) {
			throw new IllegalArgumentException("Enter a vehicle identifier");
		}
		if (startOffsetSeconds < 0 || durationSeconds < 1) {
			throw new IllegalArgumentException("Reservation times must be positive");
		}
		if ((long) startOffsetSeconds + durationSeconds > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Reservation interval exceeds the supported time range");
		}
	}

	public synchronized List<Reservation> all() {
		return List.copyOf(reservations);
	}

	public synchronized boolean cancel(String reservationId) {
		if (reservationId == null || reservationId.isBlank()) {
			return false;
		}
		return reservations.removeIf(reservation -> reservation.id().equals(reservationId));
	}

	public synchronized boolean setCapacity(int newCapacity) {
		if (newCapacity < 1 || reservations.stream().anyMatch(reservation -> reservation.slotNumber() > newCapacity)) {
			return false;
		}
		capacity = newCapacity;
		return true;
	}

	private boolean overlaps(int startA, int durationA, int startB, int durationB) {
		long endA = (long) startA + durationA;
		long endB = (long) startB + durationB;
		return startA < endB && startB < endA;
	}
}
