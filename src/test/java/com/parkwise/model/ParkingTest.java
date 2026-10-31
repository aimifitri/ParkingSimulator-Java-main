package com.parkwise.model;

import com.parkwise.strategy.SemaphoreStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ParkingTest {
	@Test
	void semaphoreNeverAssignsOneSlotToTwoCarsAndShutdownWakesWaiters() throws Exception {
		assertCapacityAndShutdown(1);
	}

	@Test
	void mutexNeverAssignsOneSlotToTwoCarsAndShutdownWakesWaiters() throws Exception {
		assertCapacityAndShutdown(2);
	}

	private void assertCapacityAndShutdown(int syncStrategy) throws Exception {
		int carCount = 6;
		Parking parking = new Parking(2, new SemaphoreStrategy(), null, syncStrategy);
		ExecutorService workers = Executors.newFixedThreadPool(carCount);
		CountDownLatch started = new CountDownLatch(carCount);
		CountDownLatch parked = new CountDownLatch(2);
		AtomicInteger assignedCount = new AtomicInteger();
		Set<Integer> assignedSlots = java.util.concurrent.ConcurrentHashMap.newKeySet();

		try {
			for (int index = 0; index < carCount; index++) {
				int carNumber = index;
				workers.submit(() -> {
					Car car = new Car("test-car-" + carNumber, parking);
					started.countDown();
					try {
						parking.park(car);
						assignedCount.incrementAndGet();
						assignedSlots.add(car.getPlace());
						parked.countDown();
					} catch (InterruptedException expectedOnShutdown) {
						Thread.currentThread().interrupt();
					} catch (Exception unexpected) {
						throw new AssertionError(unexpected);
					}
				});
			}

			assertTrue(started.await(2, TimeUnit.SECONDS), "all cars should begin their parking attempt");
			assertTrue(parked.await(2, TimeUnit.SECONDS), "the two available spaces should be assigned");
			assertEquals(2, assignedCount.get(), "no more cars than spaces may park");
			assertEquals(2, assignedSlots.size(), "each parked car must have a distinct space");

			parking.shutdown();
			workers.shutdown();
			assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS),
					"shutdown should release cars waiting for a space");
			assertEquals(2, assignedCount.get(), "waiting cars must not be assigned after shutdown");
		} finally {
			parking.shutdown();
			workers.shutdownNow();
		}
	}
}
