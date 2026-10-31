package com.parkwise.model;

import com.parkwise.Main;
import com.parkwise.service.ReservationManager;
import com.parkwise.strategy.SemaphoreStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ParkingSpaceTest {
	@Test
	void inventoryCountsStayConsistentAsReservedVehicleArrivesAndLeaves() throws Exception {
		ReservationManager manager = new ReservationManager(2);
		Reservation served = manager.reserveForSpace("SERVED", 1, 0, 5).orElseThrow();
		Reservation future = manager.reserveForSpace("FUTURE", 1, 20, 5).orElseThrow();
		Reservation other = manager.reserveForSpace("OTHER", 2, 30, 5).orElseThrow();
		Parking parking = new Parking(2, new SemaphoreStrategy(), null, 2, manager.all(), System.nanoTime());
		Car reservedVehicle = new Car("SERVED", parking, 1_000, served);

		try {
			assertInventoryCounts(parking.spaceSnapshot(), 2, 0, 0, 2);

			parking.park(reservedVehicle);
			Parking.SpaceSnapshot whileParked = parking.spaceSnapshot();
			assertInventoryCounts(whileParked, 2, 0, 1, 1);
			assertEquals(Parking.SpaceStatus.OCCUPIED, whileParked.spaces().get(0).status());
			assertEquals("SERVED", whileParked.spaces().get(0).vehicleId());
			assertEquals(List.of(future.id()), whileParked.spaces().get(0).reservations().stream()
					.map(Reservation::id).toList(),
					"the active booking is represented by occupancy while later bookings remain visible");

			parking.leave(reservedVehicle);
			Parking.SpaceSnapshot afterDeparture = parking.spaceSnapshot();
			assertInventoryCounts(afterDeparture, 2, 0, 0, 2);
			assertEquals(Parking.SpaceStatus.RESERVED, afterDeparture.spaces().get(0).status());
			assertEquals(List.of(future.id()), afterDeparture.spaces().get(0).reservations().stream()
					.map(Reservation::id).toList());
			assertEquals(List.of(other.id()), afterDeparture.spaces().get(1).reservations().stream()
					.map(Reservation::id).toList());
			assertEquals(3, manager.all().size(), "departure must not delete scheduled reservations from the manager");
		} finally {
			parking.shutdown();
		}
	}

	private void assertInventoryCounts(Parking.SpaceSnapshot snapshot, int total, int available,
			int occupied, int reserved) {
		assertEquals(total, snapshot.totalCount());
		assertEquals(available, snapshot.availableCount());
		assertEquals(occupied, snapshot.occupiedCount());
		assertEquals(reserved, snapshot.reservedCount());
		assertEquals(snapshot.totalCount(), snapshot.availableCount() + snapshot.occupiedCount()
				+ snapshot.reservedCount(), "space-state KPIs must sum to total capacity");
	}

	@Test
	void unoccupiedBookedSpaceIsReservedAndOtherSpaceIsAvailable() {
		Reservation booking = new Reservation("r1", "BOOKED", 1, 30, 20);
		Parking parking = new Parking(2, new SemaphoreStrategy(), null, 2,
				List.of(booking), System.nanoTime());

		try {
			Parking.SpaceSnapshot snapshot = parking.spaceSnapshot();
			List<Parking.SpaceState> spaces = snapshot.spaces();
			assertEquals(Parking.SpaceStatus.RESERVED, spaces.get(0).status());
			assertEquals("BOOKED", spaces.get(0).reservations().get(0).vehicleId());
			assertEquals(Parking.SpaceStatus.AVAILABLE, spaces.get(1).status());
			assertInventoryCounts(snapshot, 2, 1, 0, 1);
		} finally {
			parking.shutdown();
		}
	}

	@Test
	void occupiedStatusTakesPrecedenceOverItsReservationAndDepartureRestoresReservedStatus() throws Exception {
		Reservation booking = new Reservation("r1", "BOOKED", 1, 0, 30);
		Parking parking = new Parking(1, new SemaphoreStrategy(), null, 2,
				List.of(booking), System.nanoTime());
		Car reservedVehicle = new Car("BOOKED", parking, 1_000, booking);

		try {
			parking.park(reservedVehicle);
			assertEquals(Parking.SpaceStatus.OCCUPIED, parking.spaceStates().get(0).status());
			assertEquals("BOOKED", parking.spaceStates().get(0).vehicleId());

			parking.leave(reservedVehicle);
			assertEquals(Parking.SpaceStatus.AVAILABLE, parking.spaceStates().get(0).status());
		} finally {
			parking.shutdown();
		}
	}

	@Test
	void overdueBookingRemainsReservedUntilItsScheduledVehicleHasArrivedAndLeft() {
		Reservation expired = new Reservation("r1", "BOOKED", 1, 0, 1);
		Parking parking = new Parking(1, new SemaphoreStrategy(), null, 2, List.of(expired),
				System.nanoTime() - 1_100_000_000L);

		try {
			assertEquals(Parking.SpaceStatus.RESERVED, parking.spaceStates().get(0).status());
		} finally {
			parking.shutdown();
		}
	}

	@Test
	void cancellingBookingReturnsPlannedSpaceToAvailable() {
		ReservationManager manager = new ReservationManager(1);
		Reservation booking = manager.reserveForSpace("BOOKED", 1, 20, 15).orElseThrow();

		assertEquals(Parking.SpaceStatus.RESERVED,
				Parking.plannedSpaceStates(1, manager.all()).get(0).status());
		manager.cancel(booking.id());
		assertEquals(Parking.SpaceStatus.AVAILABLE,
				Parking.plannedSpaceStates(1, manager.all()).get(0).status());
	}

	@Test
	void statusFilterReturnsMatchingSpacesWithoutMutatingInventory() {
		List<Parking.SpaceState> inventory = List.of(
				new Parking.SpaceState(1, Parking.SpaceStatus.AVAILABLE, null, List.of()),
				new Parking.SpaceState(2, Parking.SpaceStatus.OCCUPIED, "CAR-2", List.of()),
				new Parking.SpaceState(3, Parking.SpaceStatus.RESERVED, null,
						List.of(new Reservation("r3", "CAR-3", 3, 30, 10))));

		List<Parking.SpaceState> reserved = Main.filterSpaceStates(inventory, "Reserved");
		assertEquals(List.of(3), reserved.stream().map(Parking.SpaceState::slotNumber).toList());
		assertEquals(3, inventory.size());
		assertEquals(Parking.SpaceStatus.AVAILABLE, inventory.get(0).status());
	}
}
