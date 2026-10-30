package com.example.ParkingSimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class MainSimulationLaunchTest {
	@Test
	void preparedConfigurationCreatesCapacityScheduleAndWorkingSemaphoreRun() throws Exception {
		assertPreparedRunStartsVehicles(1);
	}

	@Test
	void preparedConfigurationCreatesCapacityScheduleAndWorkingMutexRun() throws Exception {
		assertPreparedRunStartsVehicles(2);
	}

	@Test
	void stopReleasesWaitingVehiclesWithoutParkingThemUnderEitherStrategy() throws Exception {
		assertStopPreventsWaitingVehicleFromParking(1);
		assertStopPreventsWaitingVehicleFromParking(2);
	}

	@Test
	void resetPreparationCreatesANewEmptyParkingSnapshot() {
		Main.PreparedSimulation firstRun = Main.prepareSimulation(3, 4, 2,
				2, 1, 73, List.of(), null, System.nanoTime());
		Main.PreparedSimulation resetRun = Main.prepareSimulation(3, 4, 2,
				2, 1, 73, List.of(), null, System.nanoTime());

		try {
			assertNotSame(firstRun.parking(), resetRun.parking(), "reset must install a fresh Parking instance");
			assertEquals(3, resetRun.parking().spaceSnapshot().totalCount());
			assertEquals(0, resetRun.parking().spaceSnapshot().occupiedCount());
			assertEquals(0, resetRun.parking().spaceSnapshot().waitingCount());
			assertTrue(resetRun.parking().isActive());
		} finally {
			firstRun.parking().shutdown();
			resetRun.parking().shutdown();
		}
	}

	private void assertStopPreventsWaitingVehicleFromParking(int syncStrategy) throws Exception {
		CountDownLatch waiting = new CountDownLatch(1);
		CountDownLatch secondCarParked = new CountDownLatch(1);
		CountDownLatch departureAfterStop = new CountDownLatch(1);
		ParkingListener listener = new ParkingListener() {
			@Override
			public void carParked(Parking source, int place, String carName, int occupied, int waitingCount,
					long waitMillis) {
				if ("second-car".equals(carName)) {
					secondCarParked.countDown();
				}
			}

			@Override
			public void carLeft(Parking source, int place, String carName, int occupied, int waitingCount,
					double averageWaitMillis, int exits) {
				departureAfterStop.countDown();
			}

			@Override
			public void parkingMetrics(Parking source, int occupied, int waitingCount) {
				if (waitingCount > 0) {
					waiting.countDown();
				}
			}
		};
		Parking parking = new Parking(1, new DefaultStrategy(), listener, syncStrategy);
		Voiture firstCar = new Voiture("first-car", parking);
		Voiture waitingCar = new Voiture("second-car", parking);

		try {
			parking.stationner(firstCar);
			waitingCar.start();
			assertTrue(waiting.await(2, TimeUnit.SECONDS), "second vehicle should enter the wait queue");
			parking.shutdown();
			waitingCar.join(1_000);
			assertTrue(!waitingCar.isAlive(), "stop should release the waiting vehicle thread");
			assertEquals(1, parking.spaceSnapshot().occupiedCount(), "the waiting vehicle must not take the occupied slot");
			assertEquals(0, parking.spaceSnapshot().waitingCount(), "stopped waiting threads must leave the queue");
			assertEquals(1, secondCarParked.getCount(), "no post-stop park event should be emitted for the waiting vehicle");
			parking.sortir(firstCar);
			assertEquals(1, departureAfterStop.getCount(), "stop should freeze occupancy without emitting a departure event");
			assertEquals(1, parking.spaceSnapshot().occupiedCount(), "the stopped snapshot should retain its final occupancy");
		} finally {
			parking.shutdown();
			waitingCar.interrupt();
			waitingCar.join(1_000);
			parking.sortir(firstCar);
		}
	}

	private void assertPreparedRunStartsVehicles(int syncStrategy) throws Exception {
		CountDownLatch parked = new CountDownLatch(1);
		CountDownLatch departed = new CountDownLatch(1);
		ParkingListener listener = new ParkingListener() {
			@Override
			public void carParked(Parking source, int place, String carName, int occupied, int waiting,
					long waitMillis) {
				parked.countDown();
			}

			@Override
			public void carLeft(Parking source, int place, String carName, int occupied, int waiting,
					double averageWaitMillis, int exits) {
				departed.countDown();
			}

			@Override
			public void parkingMetrics(Parking source, int occupied, int waiting) {
			}
		};
		Main.PreparedSimulation prepared = Main.prepareSimulation(3, 4, syncStrategy,
				2, 1, 73, List.of(), listener, System.nanoTime());
		Parking parking = prepared.parking();
		Voiture vehicle = new Voiture("launch-flow-test", parking, 100);

		try {
			assertEquals(3, parking.capacity(), "configured parking capacity must reach the active Parking instance");
			assertEquals(4, prepared.arrivalDelays().size(), "configured vehicle count must produce that many arrival delays");
			assertEquals(prepared.arrivalDelays(), ArrivalSchedule.generateDelaysMillis(4, 2, 73),
					"the configured arrival window and seed must be used");
			assertTrue(prepared.forecast().stream().anyMatch(point -> point.occupied() > 0),
					"the configured schedule must initialize the forecast");
			vehicle.start();
			assertTrue(parked.await(2, TimeUnit.SECONDS), "the initialized Parking instance should accept a vehicle thread");
			assertEquals(1, parking.spaceSnapshot().occupiedCount());
			assertTrue(departed.await(2, TimeUnit.SECONDS), "the vehicle thread should complete its parking duration");
			assertEquals(0, parking.spaceSnapshot().occupiedCount());
		} finally {
			parking.shutdown();
			vehicle.interrupt();
			vehicle.join(1_000);
		}
	}
}
