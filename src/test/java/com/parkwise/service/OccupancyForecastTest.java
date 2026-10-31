package com.parkwise.service;

import com.parkwise.model.Reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OccupancyForecastTest {
	@Test
	void forecastRespectsCapacityAndReturnsToEmpty() {
		List<OccupancyForecast.Point> forecast = OccupancyForecast.predict(List.of(500, 700, 1200), 1_000, 2);

		assertEquals(new OccupancyForecast.Point(0, 0), forecast.get(0));
		assertEquals(new OccupancyForecast.Point(2_500, 0), forecast.get(forecast.size() - 1));
		assertTrue(forecast.stream().allMatch(point -> point.occupied() >= 0 && point.occupied() <= 2));
		assertEquals(2, forecast.stream().mapToInt(OccupancyForecast.Point::occupied).max().orElseThrow());
	}

	@Test
	void forecastModelsWaitingWhenCarsArriveFasterThanSpacesFreeUp() {
		List<OccupancyForecast.Point> forecast = OccupancyForecast.predict(List.of(500, 600, 700), 1_000, 1);

		assertEquals(new OccupancyForecast.Point(3_500, 0), forecast.get(forecast.size() - 1));
		assertEquals(1, forecast.stream().mapToInt(OccupancyForecast.Point::occupied).max().orElseThrow());
	}

	@Test
	void forecastIncludesReservationsAndMovesRandomDemandPastTheBookedWindow() {
		Reservation reservation = new Reservation("r1", "BOOKED", 1, 5, 5);
		List<OccupancyForecast.Point> forecast = OccupancyForecast.predict(List.of(1_000), 6_000, 1, List.of(reservation));

		assertEquals(new OccupancyForecast.Point(5_000, 1), forecast.get(1));
		assertEquals(new OccupancyForecast.Point(10_000, 1), forecast.get(2));
		assertEquals(new OccupancyForecast.Point(16_000, 0), forecast.get(forecast.size() - 1));
	}
}
