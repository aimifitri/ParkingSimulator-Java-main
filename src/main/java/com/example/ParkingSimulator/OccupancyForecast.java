package com.example.ParkingSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Predicts occupied spaces from scheduled arrivals, parking duration, and lot capacity. */
final class OccupancyForecast {
	record Point(long timeMillis, int occupied) {
	}

	private OccupancyForecast() {
	}

	static List<Point> predict(List<Integer> arrivalDelaysMillis, int parkingDurationMillis, int capacity) {
		return predict(arrivalDelaysMillis, parkingDurationMillis, capacity, List.of());
	}

	static List<Point> predict(List<Integer> arrivalDelaysMillis, int parkingDurationMillis, int capacity,
			List<Reservation> reservations) {
		if (arrivalDelaysMillis == null || reservations == null) {
			throw new IllegalArgumentException("Arrival delays and reservations must not be null");
		}
		if (parkingDurationMillis < 1) {
			throw new IllegalArgumentException("Parking duration must be positive");
		}
		if (capacity < 1) {
			throw new IllegalArgumentException("Parking capacity must be positive");
		}
		if (arrivalDelaysMillis.stream().anyMatch(delay -> delay < 0)) {
			throw new IllegalArgumentException("Arrival delays cannot be negative");
		}
		if (reservations.stream().anyMatch(reservation -> reservation.slotNumber() > capacity)) {
			throw new IllegalArgumentException("Reservation slot exceeds parking capacity");
		}

		List<Interval> intervals = new ArrayList<>();
		for (Reservation reservation : reservations) {
			long start = reservation.startOffsetSeconds() * 1000L;
			intervals.add(new Interval(reservation.slotNumber() - 1, start,
					start + reservation.durationSeconds() * 1000L));
		}
		List<Integer> arrivals = new ArrayList<>(arrivalDelaysMillis);
		arrivals.sort(Integer::compareTo);
		for (int arrival : arrivals) {
			int selectedSlot = -1;
			long selectedStart = Long.MAX_VALUE;
			for (int slot = 0; slot < capacity; slot++) {
				long candidateStart = earliestFreeStart(slot, arrival, parkingDurationMillis, intervals);
				if (candidateStart < selectedStart) {
					selectedStart = candidateStart;
					selectedSlot = slot;
				}
			}
			intervals.add(new Interval(selectedSlot, selectedStart, selectedStart + parkingDurationMillis));
		}

		TreeMap<Long, Integer> occupancyChanges = new TreeMap<>();
		for (Interval interval : intervals) {
			occupancyChanges.merge(interval.startMillis(), 1, Integer::sum);
			occupancyChanges.merge(interval.endMillis(), -1, Integer::sum);
		}
		List<Point> points = new ArrayList<>();
		points.add(new Point(0, 0));
		int occupied = 0;
		for (var change : occupancyChanges.entrySet()) {
			occupied += change.getValue();
			points.add(new Point(change.getKey(), occupied));
		}
		return List.copyOf(points);
	}

	private static long earliestFreeStart(int slot, long start, long duration, List<Interval> intervals) {
		long candidateStart = start;
		boolean moved;
		do {
			moved = false;
			long candidateEnd = candidateStart + duration;
			long candidateStartForCheck = candidateStart;
			long nextFree = intervals.stream()
					.filter(interval -> interval.slot() == slot
							&& candidateStartForCheck < interval.endMillis()
							&& interval.startMillis() < candidateEnd)
					.mapToLong(Interval::endMillis)
					.min().orElse(Long.MAX_VALUE);
			if (nextFree != Long.MAX_VALUE) {
				candidateStart = nextFree;
				moved = true;
			}
		} while (moved);
		return candidateStart;
	}

	private record Interval(int slot, long startMillis, long endMillis) {
	}
}
