package com.example.ParkingSimulator;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Screen 5 of the ParkWise redesign: the Smart Prediction &amp; Recommendation
 * engine view.
 *
 * <p>The view is fully decoupled from the backend concurrency model. It reads
 * live data through {@link CarParkRepository} and a {@link Supplier<Parking>}
 * and reports user actions through callbacks. The predicted occupancy curve is
 * computed with {@link OccupancyForecast} and {@link ArrivalSchedule}; the
 * actual curve is recorded from live {@link Parking} model events.
 */
public class PredictionsView extends BorderPane {
	private final CarParkRepository repository;
	private final Supplier<Parking> parkingSupplier;
	private final Supplier<Integer> capacitySupplier;
	private final Supplier<Integer> numCarsSupplier;
	private final Supplier<Integer> arrivalWindowSupplier;
	private final Supplier<Integer> durationSupplier;
	private final Supplier<Integer> seedSupplier;
	private final Supplier<Long> startNanosSupplier;
	private final Consumer<CarPark> onBookNow;

	private LineChart<Number, Number> forecastChart;
	private XYChart.Series<Number, Number> predictedSeries;
	private XYChart.Series<Number, Number> actualSeries;
	private final List<XYChart.Data<Number, Number>> actualPoints = new ArrayList<>();

	private Label predictedPeakValue;
	private Label predictedPeakTimeValue;
	private Label actualCurrentValue;
	private Label availabilityAtPeakValue;

	private VBox recommendationsContainer;

	private ComboBox<String> historicalCarParkSelector;
	private ComboBox<DayOfWeek> historicalDaySelector;
	private ComboBox<LocalTime> historicalTimeSelector;
	private Label historicalPredictionLabel;
	private Label historicalModelScoreLabel;

	private HistoricalOccupancyPredictor historicalPredictor;
	private ComboBox<CarPark> destinationSelector;

	public PredictionsView(CarParkRepository repository,
			Supplier<Parking> parkingSupplier,
			Supplier<Integer> capacitySupplier,
			Supplier<Integer> numCarsSupplier,
			Supplier<Integer> arrivalWindowSupplier,
			Supplier<Integer> durationSupplier,
			Supplier<Integer> seedSupplier,
			Supplier<Long> startNanosSupplier,
			Consumer<CarPark> onBookNow) {
		this.repository = repository;
		this.parkingSupplier = parkingSupplier;
		this.capacitySupplier = capacitySupplier;
		this.numCarsSupplier = numCarsSupplier;
		this.arrivalWindowSupplier = arrivalWindowSupplier;
		this.durationSupplier = durationSupplier;
		this.seedSupplier = seedSupplier;
		this.startNanosSupplier = startNanosSupplier;
		this.onBookNow = onBookNow;
		getStyleClass().add("predictions-view");
		setPadding(new Insets(0));
		setCenter(buildPage());
		CarPark selected = destinationSelector == null ? null : destinationSelector.getValue();
		if (selected != null) {
			generatePrediction(selected.totalCapacity(), numCarsSupplier.get(), arrivalWindowSupplier.get(),
					"2 Hours", seedSupplier.get());
		}
	}

	public void setHistoricalPredictor(HistoricalOccupancyPredictor predictor) {
		this.historicalPredictor = predictor;
		if (historicalPredictionLabel != null) {
			refreshHistoricalPrediction();
		}
	}

	/** Call on every live model event so the actual-occupancy curve advances. */
	public void refresh() {
		Parking parking = parkingSupplier.get();
		if (parking != null && actualSeries != null) {
			int occupied = parking.spaceSnapshot().occupiedCount();
			double elapsedSeconds = elapsedSeconds();
			actualPoints.add(new XYChart.Data<>(elapsedSeconds, occupied));
			actualSeries.setData(javafx.collections.FXCollections.observableArrayList(actualPoints));
			refreshActualCurrentLabel(occupied);
		}
		if (recommendationsContainer != null) {
			renderRecommendations();
		}
	}

	public void resetActualData() {
		actualPoints.clear();
		if (actualSeries != null) {
			actualSeries.getData().clear();
		}
		if (actualCurrentValue != null) {
			actualCurrentValue.setText("\u2014");
		}
	}

	private double elapsedSeconds() {
		Long startNanos = startNanosSupplier == null ? null : startNanosSupplier.get();
		if (startNanos == null) {
			return 0;
		}
		return (System.nanoTime() - startNanos) / 1_000_000_000.0;
	}

	private Node buildPage() {
		VBox root = new VBox(0);
		root.getStyleClass().add("predictions-page-root");
		root.setFillWidth(true);

		root.getChildren().add(buildControlBar());
		root.getChildren().add(buildForecastSection());
		root.getChildren().add(buildRecommendationsSection());
		root.getChildren().add(buildHistoricalSection());

		VBox scrollWrapper = new VBox(root);
		scrollWrapper.getStyleClass().add("predictions-scroll-wrapper");
		return scrollWrapper;
	}

	private HBox buildControlBar() {
		HBox bar = new HBox(12);
		bar.getStyleClass().add("preds-control-bar");
		bar.setPadding(new Insets(16, 26, 12, 26));
		bar.setAlignment(Pos.CENTER_LEFT);

		List<CarPark> parks = repository.getAllCarParks();
		destinationSelector = new ComboBox<>(
				javafx.collections.FXCollections.observableArrayList(parks));
		destinationSelector.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(CarPark p) {
				return p == null ? "" : p.name();
			}

			@Override
			public CarPark fromString(String s) {
				return null;
			}
		});
		destinationSelector.setValue(parks.isEmpty() ? null : parks.get(0));
		destinationSelector.setPromptText("Destination Car Park");
		destinationSelector.setMaxWidth(240);

		ComboBox<String> startTimeSelector = new ComboBox<>(
				javafx.collections.FXCollections.observableArrayList("08:00 AM", "09:00 AM", "10:30 AM", "12:00 PM",
						"02:00 PM", "03:30 PM", "05:00 PM", "06:30 PM"));
		startTimeSelector.setValue("10:30 AM");
		startTimeSelector.setPromptText("Start Time");
		startTimeSelector.setMaxWidth(150);

		ComboBox<String> durationSelector = new ComboBox<>(
				javafx.collections.FXCollections.observableArrayList("1 Hour", "2 Hours", "3 Hours", "4 Hours",
						"6 Hours", "8 Hours", "12 Hours"));
		durationSelector.setValue("2 Hours");
		durationSelector.setPromptText("Duration");
		durationSelector.setMaxWidth(130);

		Button generateButton = new Button("Generate Prediction");
		generateButton.getStyleClass().add("preds-primary-button");
		generateButton.setMaxWidth(Double.MAX_VALUE);
		destinationSelector.setOnAction(e -> generatePredictionForSelectedPark(durationSelector.getValue()));
		generateButton.setOnAction(e -> generatePredictionForSelectedPark(durationSelector.getValue()));

		bar.getChildren().addAll(new Label("Destination"), destinationSelector, new Label("Start"), startTimeSelector,
				new Label("Duration"), durationSelector, new Region(), generateButton);
		HBox.setHgrow(bar.getChildren().get(bar.getChildren().size() - 2), Priority.ALWAYS);
		return bar;
	}

	private void generatePrediction(int capacity, int numCars, int arrivalWindow, String durationText, int seed) {
		if (predictedSeries == null || actualSeries == null) {
			return;
		}
		int durationMillis = parseDurationHours(durationText) * 1000;

		List<Integer> delays = ArrivalSchedule.generateDelaysMillis(numCars, arrivalWindow, seed);
		List<OccupancyForecast.Point> forecast = OccupancyForecast.predict(delays, durationMillis, capacity,
				List.of());

		predictedSeries.getData().clear();
		for (OccupancyForecast.Point point : forecast) {
			predictedSeries.getData().add(new XYChart.Data<>(point.timeMillis() / 1000.0, point.occupied()));
		}
		installTooltips(predictedSeries, "Predicted · {0}s · {1} occupied");

		XYChart.Data<Number, Number> peak = predictedSeries.getData().stream()
				.max(Comparator.comparingDouble(d -> d.getYValue().doubleValue())).orElse(null);
		if (peak != null) {
			int predictedOccupied = (int) Math.round(peak.getYValue().doubleValue());
			predictedPeakValue.setText(predictedOccupied + " / " + capacity);
			predictedPeakTimeValue.setText("+" + String.format(Locale.ROOT, "%.1f", peak.getXValue().doubleValue())
					+ " sec");
			int available = Math.max(0, capacity - predictedOccupied);
			availabilityAtPeakValue.setText(String.valueOf(available));
		} else {
			predictedPeakValue.setText("0 / " + capacity);
			predictedPeakTimeValue.setText("\u2014");
			availabilityAtPeakValue.setText(String.valueOf(capacity));
		}

		renderRecommendations();
	}

	private void generatePredictionForSelectedPark(String durationText) {
		CarPark selected = destinationSelector == null ? null : destinationSelector.getValue();
		int capacity = selected == null ? capacitySupplier.get() : selected.totalCapacity();
		generatePrediction(capacity, numCarsSupplier.get(), arrivalWindowSupplier.get(),
				durationText, seedSupplier.get());
	}

	private int parseDurationHours(String text) {
		if (text == null || text.isBlank()) {
			return 2;
		}
		String digits = text.replaceAll("[^0-9]", "");
		try {
			int hours = Integer.parseInt(digits);
			return hours < 1 ? 2 : hours;
		} catch (NumberFormatException e) {
			return 2;
		}
	}

	private void renderRecommendations() {
		if (recommendationsContainer == null) {
			return;
		}
		recommendationsContainer.getChildren().clear();

		List<CarPark> parks = new ArrayList<>(repository.getAllCarParks());
		double maxDistance = parks.stream().mapToDouble(CarPark::distanceKm).max().orElse(1.0);
		if (maxDistance <= 0) {
			maxDistance = 1.0;
		}
		double maxRate = parks.stream().mapToDouble(CarPark::hourlyRate).max().orElse(5.0);
		if (maxRate <= 0) {
			maxRate = 5.0;
		}
		int maxAvailable = parks.stream().mapToInt(CarPark::availableCount).max().orElse(0);
		int minDistanceKm = (int) Math.round(parks.stream().mapToDouble(CarPark::distanceKm).min().orElse(0));

		List<RecommendationResult> ranked = new ArrayList<>();
		for (CarPark park : parks) {
			double availabilityRatio = park.availableCount() / (double) park.totalCapacity();
			int availabilityScore = (int) Math.round(availabilityRatio * 40.0);
			int distanceScore = (int) Math.round((1.0 - park.distanceKm() / maxDistance) * 15.0);
			int rateScore = (int) Math.round((1.0 - park.hourlyRate() / maxRate) * 10.0);
			int score = java.lang.Math.clamp(50 + availabilityScore + distanceScore + rateScore, 0, 100);

			String badge;
			if (park.availableCount() == maxAvailable && maxAvailable > 0) {
				badge = "Highest Availability";
			} else if ((int) Math.round(park.distanceKm()) == minDistanceKm) {
				badge = "Closest Distance";
			} else {
				badge = "Good Availability";
			}
			ranked.add(new RecommendationResult(park, score, badge));
		}
		ranked.sort(Comparator.comparingInt(RecommendationResult::matchScore).reversed());
		for (int i = 0; i < ranked.size(); i++) {
			RecommendationResult result = ranked.get(i);
			String rankBadge;
			switch (i) {
				case 0 -> rankBadge = "Best Match";
				case 1 -> rankBadge = "Highest Availability";
				case 2 -> rankBadge = "Closest Distance";
				default -> rankBadge = "Good Availability";
			}
			recommendationsContainer.getChildren()
					.add(buildRecommendationCard(i + 1, result, rankBadge));
		}
	}

	private VBox buildRecommendationCard(int rank, RecommendationResult result, String badgeText) {
		CarPark park = result.carPark();
		VBox card = new VBox(10);
		card.getStyleClass().add("preds-recommendation-card");
		card.setPrefWidth(300);
		card.setMaxWidth(Double.MAX_VALUE);

		HBox header = new HBox(10);
		Label rankBadge = new Label(String.valueOf(rank));
		rankBadge.getStyleClass().add("preds-rank-badge");
		Label name = new Label(park.name());
		name.getStyleClass().add("preds-recommendation-name");
		Label scoreLabel = new Label(String.format(Locale.ROOT, "%d / 100", result.matchScore()));
		scoreLabel.getStyleClass().add("preds-recommendation-score");
		header.getChildren().addAll(rankBadge, name, scoreLabel);
		HBox.setHgrow(name, Priority.ALWAYS);

		Label badge = new Label(badgeText);
		badge.getStyleClass().add("preds-recommendation-badge");

		HBox meta = new HBox(14);
		meta.getStyleClass().add("preds-recommendation-meta");
		Label distance = new Label(String.format("%.2f km", park.distanceKm()));
		distance.getStyleClass().add("preds-recommendation-meta-text");
		Label rate = new Label(String.format(Locale.ROOT, "RM %.2f / hr", park.hourlyRate()));
		rate.getStyleClass().add("preds-recommendation-meta-text");
		Label availability = new Label(park.availableCount() + " available");
		availability.getStyleClass().add("preds-recommendation-meta-text");
		meta.getChildren().addAll(distance, rate, availability);

		ProgressBar progress = new ProgressBar(result.matchScore() / 100.0);
		progress.getStyleClass().add("preds-recommendation-progress");
		progress.setPrefWidth(Double.MAX_VALUE);

		Button bookNow = new Button("Book Now");
		bookNow.getStyleClass().add("preds-book-button");
		bookNow.setMaxWidth(Double.MAX_VALUE);
		bookNow.setOnAction(e -> {
			if (onBookNow != null) {
				onBookNow.accept(park);
			}
		});

		card.getChildren().addAll(header, badge, meta, progress, bookNow);
		return card;
	}

	private VBox buildForecastSection() {
		VBox section = new VBox(12);
		section.getStyleClass().add("preds-forecast-section");
		section.setPadding(new Insets(0, 26, 0, 26));

		Label title = new Label("Predicted Availability");
		title.getStyleClass().add("preds-section-title");

		NumberAxis xAxis = new NumberAxis(0, 60, 10);
		xAxis.setLabel("Time from simulation start (sec)");
		NumberAxis yAxis = new NumberAxis(0, 60, 10);
		yAxis.setLabel("Occupied spaces");

		forecastChart = new LineChart<>(xAxis, yAxis);
		forecastChart.setAnimated(false);
		forecastChart.setCreateSymbols(false);
		forecastChart.setPrefHeight(300);
		forecastChart.setLegendVisible(true);
		forecastChart.getStyleClass().add("preds-forecast-chart");

		predictedSeries = new XYChart.Series<>();
		predictedSeries.setName("Predicted occupancy");
		actualSeries = new XYChart.Series<>();
		actualSeries.setName("Actual occupancy");
		forecastChart.getData().addAll(predictedSeries, actualSeries);

		HBox metrics = new HBox(16);
		metrics.getStyleClass().add("preds-forecast-metrics");
		predictedPeakValue = metricPill("PREDICTED PEAK", "0 / 0", "#0066FF");
		predictedPeakTimeValue = metricPill("PEAK TIME", "\u2014", "#0066FF");
		actualCurrentValue = metricPill("CURRENT OCCUPANCY", "\u2014", "#22C55E");
		availabilityAtPeakValue = metricPill("AVAILABLE AT PEAK", "0", "#F59E0B");
		metrics.getChildren().addAll(predictedPeakValue, predictedPeakTimeValue, actualCurrentValue,
				availabilityAtPeakValue);

		section.getChildren().addAll(title, forecastChart, metrics);
		return section;
	}

	private Label metricPill(String title, String value, String tone) {
		VBox pill = new VBox(6);
		pill.getStyleClass().add("preds-metric-pill");
		Label t = new Label(title);
		t.getStyleClass().add("preds-metric-title");
		Label v = new Label(value);
		v.getStyleClass().add("preds-metric-value");
		v.setStyle("-fx-text-fill: " + tone + "; -fx-font-weight: bold;");
		pill.getChildren().addAll(t, v);
		return v;
	}

	private void refreshActualCurrentLabel(int occupied) {
		if (actualCurrentValue != null) {
			Parking parking = parkingSupplier.get();
			int total = parking == null ? 0 : parking.capacity();
			actualCurrentValue.setText(occupied + " / " + total);
		}
	}

	private VBox buildRecommendationsSection() {
		VBox section = new VBox(12);
		section.getStyleClass().add("preds-recommendations-section");
		section.setPadding(new Insets(0, 26, 0, 26));

		Label title = new Label("Best options based on availability, distance and rate");
		title.getStyleClass().add("preds-section-title");

		recommendationsContainer = new VBox(10);
		recommendationsContainer.getStyleClass().add("preds-recommendations-list");

		section.getChildren().addAll(title, recommendationsContainer);
		return section;
	}

	private VBox buildHistoricalSection() {
		VBox section = new VBox(12);
		section.getStyleClass().add("preds-historical-section");
		section.setPadding(new Insets(0, 26, 26, 26));

		Label title = new Label("Smart City demand benchmark");
		title.getStyleClass().add("preds-section-title");
		Label description = new Label("Typical Birmingham occupancy by car park, weekday, and half-hour "
				+ "(historical benchmark only; not a live city sensor feed).");
		description.getStyleClass().add("preds-section-caption");
		description.setWrapText(true);

		VBox controls = new VBox(8);
		controls.getStyleClass().add("preds-historical-controls");

		historicalCarParkSelector = new ComboBox<>();
		historicalCarParkSelector.setPromptText("Car park ID");
		historicalCarParkSelector.setMaxWidth(Double.MAX_VALUE);
		if (repository != null && !repository.getAllCarParks().isEmpty()) {
			for (CarPark park : repository.getAllCarParks()) {
				historicalCarParkSelector.getItems().add(park.id());
			}
			historicalCarParkSelector.setValue(historicalCarParkSelector.getItems().get(0));
		}

		historicalDaySelector = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(DayOfWeek.values()));
		historicalDaySelector.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(DayOfWeek day) {
				return day == null ? "" : day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
			}

			@Override
			public DayOfWeek fromString(String value) {
				return value == null ? null : DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
			}
		});
		historicalDaySelector.setMaxWidth(Double.MAX_VALUE);
		historicalDaySelector.setValue(DayOfWeek.MONDAY);

		List<LocalTime> availableTimes = new ArrayList<>();
		for (int minutes = 8 * 60; minutes <= 16 * 60 + 30; minutes += 30) {
			availableTimes.add(LocalTime.of(minutes / 60, minutes % 60));
		}
		historicalTimeSelector = new ComboBox<>(
				javafx.collections.FXCollections.observableArrayList(availableTimes));
		historicalTimeSelector.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(LocalTime time) {
				return time == null ? "" : time.format(DateTimeFormatter.ofPattern("HH:mm"));
			}

			@Override
			public LocalTime fromString(String value) {
				return value == null ? null : LocalTime.parse(value);
			}
		});
		historicalTimeSelector.setMaxWidth(Double.MAX_VALUE);
		historicalTimeSelector.setValue(LocalTime.of(10, 0));

		historicalPredictionLabel = new Label();
		historicalPredictionLabel.getStyleClass().add("preds-historical-result");
		historicalPredictionLabel.setWrapText(true);
		historicalModelScoreLabel = new Label();
		historicalModelScoreLabel.getStyleClass().add("preds-historical-score");
		historicalModelScoreLabel.setWrapText(true);

		historicalCarParkSelector.setOnAction(e -> refreshHistoricalPrediction());
		historicalDaySelector.setOnAction(e -> refreshHistoricalPrediction());
		historicalTimeSelector.setOnAction(e -> refreshHistoricalPrediction());

		controls.getChildren().addAll(new Label("Birmingham car park"), historicalCarParkSelector,
				new Label("Target weekday"), historicalDaySelector, new Label("Target time"), historicalTimeSelector,
				historicalPredictionLabel, historicalModelScoreLabel);

		Label note = new Label(
				"Expected occupied/available are rounded counts from the selected occupancy profile. "
						+ "Held-out MAE compares the weekday/time profile with the car-park average; lower is closer to held-out observations.");
		note.getStyleClass().add("preds-historical-note");
		note.setWrapText(true);

		section.getChildren().addAll(title, description, controls, note);
		refreshHistoricalPrediction();
		return section;
	}

	private void refreshHistoricalPrediction() {
		if (historicalCarParkSelector == null || historicalDaySelector == null
				|| historicalTimeSelector == null || historicalPredictionLabel == null
				|| historicalModelScoreLabel == null) {
			return;
		}
		String carPark = historicalCarParkSelector.getValue();
		DayOfWeek day = historicalDaySelector.getValue();
		LocalTime time = historicalTimeSelector.getValue();
		if (carPark == null || day == null || time == null) {
			historicalPredictionLabel.setText("Select a car park, weekday, and time.");
			return;
		}

		HistoricalOccupancyPredictor predictor = historicalPredictor;
		if (predictor != null) {
			HistoricalOccupancyPredictor.Prediction prediction = predictor.predict(carPark, day, time).orElse(null);
			if (prediction == null) {
				historicalPredictionLabel.setText("No historical observations are available for this selection.");
				historicalModelScoreLabel.setText("");
				return;
			}
			String weekday = day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
			historicalPredictionLabel.setText(String.format(Locale.ROOT,
					"Typical for %s %s: %.1f%% occupancy\nAbout %d of %d occupied; %d available",
					weekday, time.format(DateTimeFormatter.ofPattern("HH:mm")),
					prediction.occupancyRate() * 100, prediction.expectedOccupied(), prediction.capacity(),
					prediction.expectedAvailable()));
			HistoricalOccupancyPredictor.ValidationMetrics metrics = predictor.validationMetrics();
			historicalModelScoreLabel.setText(String.format(Locale.ROOT,
					"Held-out MAE: %.1f%% (weekday/time profile) vs %.1f%% (car-park average); %d observations.",
					metrics.profileMaePercent(), metrics.baselineMaePercent(), metrics.observationCount()));
			return;
		}

		CarPark park = repository.findCarParkById(carPark);
		if (park == null) {
			historicalPredictionLabel.setText("No benchmark data for this selection.");
			historicalModelScoreLabel.setText("");
			return;
		}
		double pseudoRate = java.lang.Math.clamp(park.getOccupancyRate() / 100.0, 0.0, 1.0);
		int expectedOccupied = (int) Math.round(pseudoRate * park.totalCapacity());
		historicalPredictionLabel.setText(String.format(Locale.ROOT,
				"Benchmark for %s %s: %.1f%% occupancy\nAbout %d of %d occupied; %d available",
				park.name(), time.format(DateTimeFormatter.ofPattern("HH:mm")), pseudoRate * 100,
				expectedOccupied, park.totalCapacity(), park.totalCapacity() - expectedOccupied));
		historicalModelScoreLabel.setText("Benchmark derived from live lot counts (no Birmingham dataset loaded).");
	}

	private void installTooltips(XYChart.Series<Number, Number> series, String pattern) {
		series.getData().addListener((javafx.beans.InvalidationListener) obs -> {
			for (XYChart.Data<Number, Number> data : series.getData()) {
				if (data.getNode() != null) {
					Tooltip tooltip = new Tooltip(pattern.replace("{0}",
							String.format(Locale.ROOT, "%.1f", data.getXValue().doubleValue()))
							.replace("{1}", String.valueOf(data.getYValue().intValue())));
					Tooltip.install(data.getNode(), tooltip);
				}
			}
		});
		// Install for any nodes already present.
		for (XYChart.Data<Number, Number> data : series.getData()) {
			if (data.getNode() != null) {
				Tooltip tooltip = new Tooltip(pattern.replace("{0}",
						String.format(Locale.ROOT, "%.1f", data.getXValue().doubleValue()))
						.replace("{1}", String.valueOf(data.getYValue().intValue())));
				Tooltip.install(data.getNode(), tooltip);
			}
		}
	}
}
