package com.parkwise.model;

import com.parkwise.strategy.SemaphoreStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CarTest {
	@Test
	void carLeavesAfterItsConfiguredParkingDuration() throws Exception {
		CountDownLatch parked = new CountDownLatch(1);
		CountDownLatch left = new CountDownLatch(1);
		ParkingListener listener = new TestListener() {
			@Override
			public void carParked(Parking source, int place, String carName, int occupied, int waiting, long waitMillis) {
				parked.countDown();
			}

			@Override
			public void carLeft(Parking source, int place, String carName, int occupied, int waiting,
					double averageWaitMillis, int exits) {
				left.countDown();
			}
		};
		Parking parking = new Parking(1, new SemaphoreStrategy(), listener, 1);
		long startedAt = System.nanoTime();
		Car car = new Car("duration-test", parking, 300);

		try {
			car.start();
			assertTrue(parked.await(2, TimeUnit.SECONDS), "car should park before its dwell timer starts");
			assertTrue(left.await(3, TimeUnit.SECONDS), "car should leave after its configured duration");
			long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
			assertTrue(elapsedMillis >= 250, "car must remain parked for approximately the configured duration");
			assertTrue(elapsedMillis < 3_000, "car should not remain parked beyond the timeout");
		} finally {
			car.interrupt();
			car.join(1_000);
			parking.shutdown();
		}
	}

	@Test
	void semaphorePermitIsReusableAfterDeparture() throws Exception {
		assertWaitingCarParksAfterDeparture(1);
	}

	@Test
	void mutexSlotIsReusableAfterDeparture() throws Exception {
		assertWaitingCarParksAfterDeparture(2);
	}

	@Test
	void unreservedCarAvoidsASpaceDuringItsBookedWindow() throws Exception {
		Reservation reservation = new Reservation("r1", "BOOKED", 1, 10, 10);
		Parking parking = new Parking(2, new SemaphoreStrategy(), null, 1, java.util.List.of(reservation), System.nanoTime());
		Car unreservedCar = new Car("unreserved", parking, 15_000);

		try {
			parking.park(unreservedCar);
			assertEquals(1, unreservedCar.getPlace(), "unreserved demand must leave the booked space free");
		} finally {
			parking.leave(unreservedCar);
			parking.shutdown();
		}
	}

	private void assertWaitingCarParksAfterDeparture(int syncStrategy) throws Exception {
		CountDownLatch waiting = new CountDownLatch(1);
		CountDownLatch secondCarParked = new CountDownLatch(1);
		ParkingListener listener = new TestListener() {
			@Override
			public void carParked(Parking source, int place, String carName, int occupied, int waitingCount, long waitMillis) {
				if ("second-car".equals(carName)) {
					secondCarParked.countDown();
				}
			}

			@Override
			public void parkingMetrics(Parking source, int occupied, int waitingCount) {
				if (waitingCount > 0) {
					waiting.countDown();
				}
			}
		};
		Parking parking = new Parking(1, new SemaphoreStrategy(), listener, syncStrategy);
		ExecutorService worker = Executors.newSingleThreadExecutor();
		Car firstCar = new Car("first-car", parking);
		Car secondCar = new Car("second-car", parking);

		try {
			parking.park(firstCar);
			Future<?> secondAttempt = worker.submit(() -> {
				parking.park(secondCar);
				return null;
			});
			assertTrue(waiting.await(2, TimeUnit.SECONDS), "second car should wait while the only slot is occupied");
			Parking.SpaceSnapshot snapshot = parking.spaceSnapshot();
			assertEquals(1, snapshot.waitingCount(), "the wait queue is reported separately from space states");
			assertEquals(1, snapshot.totalCount());
			assertEquals(snapshot.totalCount(), snapshot.availableCount() + snapshot.occupiedCount()
					+ snapshot.reservedCount(), "waiting cars do not add to parking capacity");

			parking.leave(firstCar);
			assertTrue(secondCarParked.await(2, TimeUnit.SECONDS), "departure should release the slot to the waiting car");
			secondAttempt.get(2, TimeUnit.SECONDS);
			assertEquals(0, secondCar.getPlace());
			parking.leave(secondCar);
		} finally {
			parking.shutdown();
			worker.shutdownNow();
			assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	private static class TestListener implements ParkingListener {
		@Override
		public void carParked(Parking source, int place, String carName, int occupied, int waiting, long waitMillis) {
		}

		@Override
		public void carLeft(Parking source, int place, String carName, int occupied, int waiting,
				double averageWaitMillis, int exits) {
		}

		@Override
		public void parkingMetrics(Parking source, int occupied, int waiting) {
		}
	}
}
