package com.parkwise.service;

import com.parkwise.model.Reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReservationManagerTest {
	@Test
	void overlappingBookingsUseDifferentSpacesAndRejectWhenFull() {
		ReservationManager manager = new ReservationManager(2);

		Reservation first = manager.reserve("CAR-1", 60, 120).orElseThrow();
		Reservation second = manager.reserve("CAR-2", 90, 30).orElseThrow();
		Optional<Reservation> third = manager.reserve("CAR-3", 100, 30);

		assertTrue(first.slotNumber() != second.slotNumber());
		assertTrue(third.isEmpty());
	}

	@Test
	void adjacentBookingsMayReuseTheSameSpaceAndCancellationMakesItAvailableAgain() {
		ReservationManager manager = new ReservationManager(1);
		Reservation first = manager.reserve("CAR-1", 60, 30).orElseThrow();
		Reservation adjacent = manager.reserve("CAR-2", 90, 30).orElseThrow();

		assertEquals(first.slotNumber(), adjacent.slotNumber());
		assertTrue(manager.cancel(first.id()));
		assertEquals(1, manager.all().size());
	}

	@Test
	void cannotReduceCapacityBelowAnAssignedReservationSlot() {
		ReservationManager manager = new ReservationManager(2);
		manager.reserve("CAR-1", 0, 30).orElseThrow();
		manager.reserve("CAR-2", 0, 30).orElseThrow();

		assertFalse(manager.setCapacity(1));
		assertTrue(manager.setCapacity(2));
	}

	@Test
	void selectedSpaceBookingUsesThatSpaceAndRejectsOverlappingConflicts() {
		ReservationManager manager = new ReservationManager(3);
		Reservation first = manager.reserveForSpace("CAR-1", 3, 60, 30).orElseThrow();

		assertEquals(3, first.slotNumber());
		assertTrue(manager.reserveForSpace("CAR-2", 3, 80, 10).isEmpty());
		Reservation adjacent = manager.reserveForSpace("CAR-3", 3, 90, 20).orElseThrow();
		assertEquals(3, adjacent.slotNumber());
		assertTrue(manager.cancel(first.id()));
		assertEquals(1, manager.all().size());
	}
}
