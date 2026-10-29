package com.example.ParkingSimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArrivalScheduleTest {
	@Test
	void sameSeedAndSettingsProduceTheSameArrivalDelays() {
		List<Integer> first = ArrivalSchedule.generateDelaysMillis(20, 5, 12345);
		List<Integer> second = ArrivalSchedule.generateDelaysMillis(20, 5, 12345);

		assertEquals(first, second);
	}

	@Test
	void differentSeedsProduceDifferentArrivalDelays() {
		List<Integer> first = ArrivalSchedule.generateDelaysMillis(20, 5, 12345);
		List<Integer> second = ArrivalSchedule.generateDelaysMillis(20, 5, 54321);

		assertNotEquals(first, second);
	}

	@Test
	void delaysStayWithinTheConfiguredArrivalWindow() {
		List<Integer> delays = ArrivalSchedule.generateDelaysMillis(100, 3, 42);

		assertTrue(delays.stream().allMatch(delay -> delay >= 500 && delay <= 3000));
	}
}
