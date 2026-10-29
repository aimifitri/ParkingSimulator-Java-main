package com.example.ParkingSimulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Predicts typical parking demand from the CC BY 4.0 Parking Birmingham dataset.
 * The model uses mean occupancy for the same car park, weekday, and half-hour.
 */
final class HistoricalOccupancyPredictor {
	private static final LocalDate VALIDATION_START = LocalDate.of(2016, 12, 5);
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final List<Observation> observations;
	private final Map<DemandKey, Aggregate> weekdayTimeProfiles;
	private final Map<CarParkDay, Aggregate> weekdayProfiles;
	private final Map<String, Aggregate> carParkProfiles;
	private final Map<String, Integer> capacities;
	private final ValidationMetrics validationMetrics;
	private final int sourceRowCount;

	private HistoricalOccupancyPredictor(List<Observation> observations, int sourceRowCount) {
		this.observations = List.copyOf(observations);
		this.sourceRowCount = sourceRowCount;
		this.weekdayTimeProfiles = new HashMap<>();
		this.weekdayProfiles = new HashMap<>();
		this.carParkProfiles = new HashMap<>();
		this.capacities = findTypicalCapacities(observations);
		for (Observation observation : observations) {
			addObservation(weekdayTimeProfiles, keyFor(observation), observation.occupancyRate());
			addObservation(weekdayProfiles,
					new CarParkDay(observation.carParkId(), observation.timestamp().getDayOfWeek()),
					observation.occupancyRate());
			addObservation(carParkProfiles, observation.carParkId(), observation.occupancyRate());
		}
		this.validationMetrics = calculateValidationMetrics(observations);
	}

	static HistoricalOccupancyPredictor load(InputStream source) throws IOException {
		if (source == null) {
			throw new IOException("Bundled Birmingham parking dataset was not found");
		}
		List<Observation> observations = new ArrayList<>();
		int rowCount = 0;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
			String row = reader.readLine(); // CSV header
			while ((row = reader.readLine()) != null) {
				rowCount++;
				String[] fields = row.split(",", -1);
				if (fields.length != 4) {
					continue;
				}
				try {
					String carParkId = fields[0].trim();
					int capacity = Integer.parseInt(fields[1].trim());
					int occupancy = Integer.parseInt(fields[2].trim());
					LocalDateTime timestamp = LocalDateTime.parse(fields[3].trim(), TIMESTAMP);
					if (!carParkId.isEmpty() && capacity > 0 && occupancy >= 0 && occupancy <= capacity) {
						observations.add(new Observation(carParkId, timestamp, capacity, occupancy));
					}
				} catch (NumberFormatException | java.time.format.DateTimeParseException ignored) {
					// Ignore malformed or missing observations; the source contains missing values.
				}
			}
		}
		if (observations.isEmpty()) {
			throw new IOException("Birmingham parking dataset contains no valid occupancy observations");
		}
		observations.sort(Comparator.comparing(Observation::timestamp));
		return new HistoricalOccupancyPredictor(observations, rowCount);
	}

	List<String> carParkIds() {
		return List.copyOf(new TreeSet<>(capacities.keySet()));
	}

	Optional<Prediction> predict(String carParkId, DayOfWeek day, LocalTime time) {
		Integer capacity = capacities.get(carParkId);
		if (capacity == null || day == null || time == null) {
			return Optional.empty();
		}
		Aggregate profile = weekdayTimeProfiles.get(new DemandKey(carParkId, day, halfHourBucket(time)));
		if (profile == null) {
			profile = weekdayProfiles.get(new CarParkDay(carParkId, day));
		}
		if (profile == null) {
			profile = carParkProfiles.get(carParkId);
		}
		if (profile == null) {
			return Optional.empty();
		}
		double occupancyRate = Math.clamp(profile.mean(), 0.0, 1.0);
		int expectedOccupied = (int) Math.round(occupancyRate * capacity);
		return Optional.of(new Prediction(carParkId, capacity, occupancyRate, expectedOccupied,
				capacity - expectedOccupied, profile.count()));
	}

	ValidationMetrics validationMetrics() {
		return validationMetrics;
	}

	int sourceRowCount() {
		return sourceRowCount;
	}

	int validObservationCount() {
		return observations.size();
	}

	private static Map<String, Integer> findTypicalCapacities(List<Observation> observations) {
		Map<String, Map<Integer, Integer>> counts = new HashMap<>();
		for (Observation observation : observations) {
			counts.computeIfAbsent(observation.carParkId(), ignored -> new HashMap<>())
					.merge(observation.capacity(), 1, Integer::sum);
		}
		Map<String, Integer> typical = new HashMap<>();
		for (var entry : counts.entrySet()) {
			int capacity = entry.getValue().entrySet().stream()
					.max(Map.Entry.comparingByValue())
					.orElseThrow().getKey();
			typical.put(entry.getKey(), capacity);
		}
		return Map.copyOf(typical);
	}

	private static ValidationMetrics calculateValidationMetrics(List<Observation> observations) {
		Map<DemandKey, Aggregate> trainingProfiles = new HashMap<>();
		Map<String, Aggregate> trainingCarParkAverages = new HashMap<>();
		for (Observation observation : observations) {
			if (observation.timestamp().toLocalDate().isBefore(VALIDATION_START)) {
				addObservation(trainingProfiles, keyFor(observation), observation.occupancyRate());
				addObservation(trainingCarParkAverages, observation.carParkId(), observation.occupancyRate());
			}
		}
		int evaluated = 0;
		double profileAbsoluteError = 0;
		double baselineAbsoluteError = 0;
		for (Observation observation : observations) {
			if (!observation.timestamp().toLocalDate().isBefore(VALIDATION_START)) {
				Aggregate profile = trainingProfiles.get(keyFor(observation));
				Aggregate baseline = trainingCarParkAverages.get(observation.carParkId());
				if (profile != null && baseline != null) {
					double actual = observation.occupancyRate();
					profileAbsoluteError += Math.abs(profile.mean() - actual);
					baselineAbsoluteError += Math.abs(baseline.mean() - actual);
					evaluated++;
				}
			}
		}
		if (evaluated == 0) {
			return new ValidationMetrics(VALIDATION_START, 0, Double.NaN, Double.NaN);
		}
		return new ValidationMetrics(VALIDATION_START, evaluated,
				100.0 * profileAbsoluteError / evaluated,
				100.0 * baselineAbsoluteError / evaluated);
	}

	private static DemandKey keyFor(Observation observation) {
		return new DemandKey(observation.carParkId(), observation.timestamp().getDayOfWeek(),
				halfHourBucket(observation.timestamp().toLocalTime()));
	}

	private static int halfHourBucket(LocalTime time) {
		int totalMinutes = time.getHour() * 60 + time.getMinute();
		return ((totalMinutes + 15) / 30) * 30;
	}

	private static <K> void addObservation(Map<K, Aggregate> profiles, K key, double occupancyRate) {
		profiles.computeIfAbsent(key, ignored -> new Aggregate()).add(occupancyRate);
	}

	private record Observation(String carParkId, LocalDateTime timestamp, int capacity, int occupancy) {
		double occupancyRate() {
			return (double) occupancy / capacity;
		}
	}

	private record DemandKey(String carParkId, DayOfWeek day, int halfHourBucket) {
	}

	private record CarParkDay(String carParkId, DayOfWeek day) {
	}

	record Prediction(String carParkId, int capacity, double occupancyRate, int expectedOccupied,
			int expectedAvailable, int historicalSampleCount) {
	}

	record ValidationMetrics(LocalDate testStart, int observationCount, double profileMaePercent,
			double baselineMaePercent) {
	}

	private static final class Aggregate {
		private double sum;
		private int count;

		void add(double value) {
			sum += value;
			count++;
		}

		double mean() {
			return sum / count;
		}

		int count() {
			return count;
		}
	}
}
