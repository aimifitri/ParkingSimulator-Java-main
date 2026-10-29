package com.example.ParkingSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Creates a repeatable set of randomized arrival delays for a simulation run. */
final class ArrivalSchedule {
	private static final int MINIMUM_DELAY_MILLIS = 500;

	private ArrivalSchedule() {
	}

	static List<Integer> generateDelaysMillis(int carCount, int arrivalWindowSeconds, long seed) {
		if (carCount < 0) {
			throw new IllegalArgumentException("Car count cannot be negative");
		}
		if (arrivalWindowSeconds < 1 || arrivalWindowSeconds > 60) {
			throw new IllegalArgumentException("Arrival window must be between 1 and 60 seconds");
		}

		int maximumDelayMillis = arrivalWindowSeconds * 1000;
		int delayRange = maximumDelayMillis - MINIMUM_DELAY_MILLIS + 1;
		Random random = new Random(seed);
		List<Integer> delays = new ArrayList<>(carCount);
		for (int car = 0; car < carCount; car++) {
			delays.add(random.nextInt(delayRange) + MINIMUM_DELAY_MILLIS);
		}
		return List.copyOf(delays);
	}
}
