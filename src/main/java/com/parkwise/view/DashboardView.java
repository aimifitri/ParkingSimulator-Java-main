package com.parkwise.view;

import com.parkwise.model.CarPark;
import com.parkwise.model.Parking;
import com.parkwise.service.CarParkRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/**
 * Redesigned home dashboard view (Screen 1 of the visual mockup).
 *
 * <p>The view owns all presentation for the dashboard and is kept strictly
 * decoupled from the backend simulation model. It reads live data through
 * {@link CarParkRepository} and an optional {@link Parking} supplier, and
 * triggers user actions through callbacks supplied by {@link Main}.
 */
public class DashboardView extends BorderPane {
	private static String toneClass(String tone) {
		return switch (tone.toUpperCase(Locale.ROOT)) {
			case "#10B981", "#22C55E" -> "tone-success";
			case "#EF4444" -> "tone-danger";
			case "#F59E0B" -> "tone-warning";
			default -> "tone-accent";
		};
	}
	public static final String CITY_MALL_IMAGE = "/public/images/city-mall.jpg";
	public static final String CENTRAL_STATION_IMAGE = "/public/images/central-station.jpg";
	public static final String STADIUM_WEST_IMAGE = "/public/images/stadium-west.jpg";
	public static final String SKYLINE_BANNER_IMAGE = "/public/images/skyline-banner.jpg";
	private final CarParkRepository repository;
	private final Supplier<Parking> parkingSupplier;
	private final Supplier<String> statusText;
	private final Supplier<String> strategyText;
	private final List<String> activityEntries;
	private final Runnable onSettings;
	private final Runnable onRestart;
	private final Runnable onViewAllCarParks;
	private final Runnable onFindAvailableSpaces;
	private final java.util.function.Supplier<Double> averageWaitSupplier;

	private Label kpiCarParks;
	private Label kpiTotalSpaces;
	private Label kpiAvailable;
	private Label kpiOccupied;
	private Label kpiReserved;
	private Label kpiAverageWait;
	private VBox overviewCards;

	private TilePane parkingGrid;
	private final Map<Integer, CustomRectangle> slotTiles = new HashMap<>();
	private PieChart occupancyPie;
	private Label occupancySummary;
	private ProgressBar occupancyProgress;
	private VBox activityLog;
	private Label gridOccValue;
	private Label gridAvailValue;
	private Label gridResValue;
	private ComboBox<CarPark> quickReservationCarParkBox;
	public DashboardView(CarParkRepository repository,
			Supplier<Parking> parkingSupplier,
			Supplier<String> statusText,
			Supplier<String> strategyText,
			List<String> activityEntries,
			Runnable onSpawnVehicle,
			Runnable onSettings,
			Runnable onRestart,
			Runnable onViewAllCarParks,
			Runnable onFindAvailableSpaces) {
		this(repository, parkingSupplier, statusText, strategyText, activityEntries, onSpawnVehicle, onSettings,
				onRestart, onViewAllCarParks, onFindAvailableSpaces, () -> 0.0);
	}

	public DashboardView(CarParkRepository repository,
			Supplier<Parking> parkingSupplier,
			Supplier<String> statusText,
			Supplier<String> strategyText,
			List<String> activityEntries,
			Runnable onSpawnVehicle,
			Runnable onSettings,
			Runnable onRestart,
			Runnable onViewAllCarParks,
			Runnable onFindAvailableSpaces,
			Supplier<Double> averageWaitSupplier) {
		this.repository = repository;
		this.parkingSupplier = parkingSupplier;
		this.statusText = statusText;
		this.strategyText = strategyText;
		this.activityEntries = activityEntries;
		this.onSettings = onSettings;
		this.onRestart = onRestart;
		this.onViewAllCarParks = onViewAllCarParks;
		this.onFindAvailableSpaces = onFindAvailableSpaces;
		this.averageWaitSupplier = averageWaitSupplier == null ? () -> 0.0 : averageWaitSupplier;
		getStyleClass().add("dashboard-view");
		setPadding(new Insets(0));
		build();
		refresh();
	}

	public void setParking(Parking parking) {
		// The dashboard reads live state via the supplier; no cached field is required.
	}

	public void setDefaultCarPark(String carParkName) {
		if (quickReservationCarParkBox == null || carParkName == null) {
			return;
		}
		repository.getAllCarParks().stream()
				.filter(park -> park.name().equalsIgnoreCase(carParkName))
				.findFirst().ifPresent(quickReservationCarParkBox::setValue);
	}

	public void refresh() {
		refreshKpis();
		refreshLiveGrid();
		refreshActivity();
	}

	private void build() {
		VBox root = new VBox();
		root.getStyleClass().add("dashboard-root");
		root.setFillWidth(true);

		root.getChildren().add(buildHeroBar());
		root.getChildren().add(buildKpiBar());

		HBox columns = new HBox(18);
		columns.getStyleClass().add("dashboard-columns");
		columns.setFillHeight(true);
		HBox.setHgrow(columns, Priority.ALWAYS);

		VBox leftColumn = new VBox(16);
		leftColumn.getStyleClass().add("dashboard-left-column");
		VBox.setVgrow(leftColumn, Priority.ALWAYS);
		leftColumn.getChildren().add(buildCarParkOverview());

		VBox middleColumn = new VBox(16);
		middleColumn.getStyleClass().add("dashboard-middle-column");
		VBox.setVgrow(middleColumn, Priority.ALWAYS);
		middleColumn.getChildren().add(buildCityMapWidget());

		VBox rightColumn = new VBox(16);
		rightColumn.getStyleClass().add("dashboard-right-column");
		rightColumn.setPrefWidth(340);
		rightColumn.setMaxWidth(380);
		rightColumn.getChildren().add(buildQuickReservationPanel());

		HBox.setHgrow(leftColumn, Priority.ALWAYS);
		HBox.setHgrow(middleColumn, Priority.ALWAYS);
		HBox.setHgrow(rightColumn, Priority.ALWAYS);
		columns.getChildren().addAll(leftColumn, middleColumn, rightColumn);
		root.getChildren().add(columns);

		root.getChildren().add(buildLiveGridSection());

		ScrollPane scroll = new ScrollPane(root);
		scroll.setFitToWidth(true);
		scroll.setPannable(true);
		scroll.getStyleClass().addAll("dashboard-scroll", "lot-scroll");
		scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
		scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
		setCenter(scroll);
	}

	private HBox buildHeroBar() {
		HBox bar = new HBox(16);
		bar.getStyleClass().add("hero-banner");
		bar.setPadding(new Insets(24, 28, 22, 28));
		bar.setAlignment(Pos.CENTER_LEFT);
		bar.setMinHeight(118);
		bar.setPrefHeight(118);

		VBox text = new VBox(6);
		Label title = new Label("Smarter Parking for a Smarter City");
		title.getStyleClass().add("hero-title");
		Label subtitle = new Label("Find. Predict. Reserve. Park Smarter.");
		subtitle.getStyleClass().add("hero-subtitle");
		text.getChildren().addAll(title, subtitle);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox controls = new HBox(10);
		controls.getStyleClass().add("hero-controls");
		controls.setAlignment(Pos.CENTER_RIGHT);

		Label statusChip = new Label(statusText.get());
		statusChip.getStyleClass().add("status-chip");
		statusChip.setMinWidth(130);
		statusChip.setAlignment(Pos.CENTER);

		Label strategyBadge = new Label(strategyText.get());
		strategyBadge.getStyleClass().add("strategy-badge");

		Button spawnButton = new Button("Launch Quick Simulation");
		spawnButton.getStyleClass().add("hero-button");
		spawnButton.setOnAction(e -> {
			if (onRestart != null) {
				onRestart.run();
			}
		});

		Button settingsButton = new Button("Settings");
		settingsButton.getStyleClass().add("hero-button");
		settingsButton.setOnAction(e -> onSettings.run());

		Button restartButton = new Button("Restart");
		restartButton.getStyleClass().add("hero-button-primary");
		restartButton.setOnAction(e -> onRestart.run());

		controls.getChildren().addAll(statusChip, strategyBadge, spawnButton, settingsButton, restartButton);
		bar.getChildren().addAll(text, spacer, controls);
		return bar;
	}

	private HBox buildKpiBar() {
		HBox bar = new HBox(12);
		bar.getStyleClass().add("kpi-bar");
		bar.setPadding(new Insets(0, 28, 0, 28));

		kpiCarParks = new Label("3");
		kpiTotalSpaces = new Label("180");
		kpiAvailable = new Label("72");
		kpiOccupied = new Label("88");
		kpiReserved = new Label("20");
		kpiAverageWait = new Label("0.00 s");

		bar.getChildren().addAll(
				kpiCard("Car Parks", kpiCarParks, "+0 today", "\u25A6", "#1D61E7"),
				kpiCard("Total Spaces", kpiTotalSpaces, "Across locations", "\u25A0", "#1D61E7"),
				kpiCard("Available Spaces", kpiAvailable, "Ready for vehicles", "\u2713", "#10B981"),
				kpiCard("Occupied Spaces", kpiOccupied, "In use right now", "\u25CB", "#EF4444"),
				kpiCard("Reserved Spaces", kpiReserved, "Future bookings", "\u25CF", "#F59E0B"));
		return bar;
	}

	private VBox kpiCard(String title, Label value, String caption, String icon, String tone) {
		VBox card = new VBox(8);
		card.getStyleClass().add("kpi-card");
		card.setPrefWidth(190);
		card.setMinWidth(125);
		HBox.setHgrow(card, Priority.ALWAYS);

		HBox top = new HBox(8);
		Label iconLabel = new Label(icon);
		iconLabel.getStyleClass().add("kpi-icon");
		iconLabel.getStyleClass().add(toneClass(tone));
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("kpi-label");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		top.getChildren().addAll(iconLabel, titleLabel, sp);

		value.getStyleClass().add("kpi-value");
		Label captionLabel = new Label(caption);
		captionLabel.getStyleClass().add("kpi-caption");
		card.getChildren().addAll(top, value, captionLabel);
		return card;
	}

	private void refreshKpis() {
		List<CarPark> parks = repository.getAllCarParks();
		int totalSpaces = 0, totalOccupied = 0, totalAvailable = 0, totalReserved = 0;
		for (CarPark p : parks) {
			totalSpaces += p.totalCapacity();
			totalOccupied += p.occupiedCount();
			totalAvailable += p.availableCount();
			totalReserved += p.reservedCount();
		}
		kpiCarParks.setText(String.valueOf(parks.size()));
		kpiTotalSpaces.setText(String.valueOf(totalSpaces));
		kpiAvailable.setText(String.valueOf(totalAvailable));
		kpiOccupied.setText(String.valueOf(totalOccupied));
		kpiReserved.setText(String.valueOf(totalReserved));
		double averageWaitMillis = averageWaitSupplier.get();
		kpiAverageWait.setText(String.format(java.util.Locale.ROOT, "%.2f s", averageWaitMillis / 1000.0));
		refreshOverviewCards();
	}

	private void refreshOverviewCards() {
		if (overviewCards == null) {
			return;
		}
		overviewCards.getChildren().clear();
		for (CarPark park : repository.getAllCarParks()) {
			overviewCards.getChildren().add(buildOverviewCard(park));
		}
	}

	private VBox buildCarParkOverview() {
		VBox section = new VBox(12);
		section.getStyleClass().add("overview-section");

		HBox header = new HBox(10);
		Label title = new Label("Car Park Overview");
		title.getStyleClass().add("section-title");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Button viewAll = new Button("View All");
		viewAll.getStyleClass().add("view-all-link");
		viewAll.setOnAction(e -> onViewAllCarParks.run());
		header.getChildren().addAll(title, sp, viewAll);

		VBox cards = overviewCards = new VBox(10);
		cards.getStyleClass().add("overview-cards");
		for (CarPark park : repository.getAllCarParks()) {
			cards.getChildren().add(buildOverviewCard(park));
		}

		section.getChildren().addAll(header, cards);
		return section;
	}

	private VBox buildOverviewCard(CarPark park) {
		VBox card = new VBox(10);
		card.getStyleClass().add("overview-card");
		card.setPrefWidth(300);
		card.setMaxWidth(Double.MAX_VALUE);

		Region thumbnail = new Region();
		thumbnail.getStyleClass().add("overview-card-thumbnail");
		String parkName = park.name().toLowerCase(java.util.Locale.ROOT);
		String imagePath = parkName.contains("central") ? CENTRAL_STATION_IMAGE
				: parkName.contains("stadium") ? STADIUM_WEST_IMAGE : CITY_MALL_IMAGE;
		thumbnail.setUserData(imagePath);
		thumbnail.setStyle("-fx-background-image: url(\"" + imagePath + "\");"
				+ "-fx-background-size: cover;");
		thumbnail.setMinHeight(96);
		thumbnail.setPrefHeight(96);
		thumbnail.setMaxWidth(Double.MAX_VALUE);

		HBox top = new HBox(8);
		Label name = new Label(park.name());
		name.getStyleClass().add("overview-name");
		Label distance = new Label(String.format("%.2f km", park.distanceKm()));
		distance.getStyleClass().add("distance-chip");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		top.getChildren().addAll(name, sp, distance);

		ProgressBar progress = new ProgressBar(park.getOccupancyRate() / 100.0);
		progress.getStyleClass().add("overview-progress");
		progress.setPrefWidth(Double.MAX_VALUE);
		progress.setMaxWidth(Double.MAX_VALUE);

		HBox counts = new HBox(14);
		counts.getStyleClass().add("overview-counts");
		counts.getChildren().addAll(countChip("Available", park.availableCount(), "#22C55E"),
				countChip("Occupied", park.occupiedCount(), "#EF4444"),
				countChip("Reserved", park.reservedCount(), "#F59E0B"));

		HBox rateRow = new HBox(10);
		Label rate = new Label(String.format("RM %.2f / hr", park.hourlyRate()));
		rate.getStyleClass().add("overview-rate");
		Region r2 = new Region();
		HBox.setHgrow(r2, Priority.ALWAYS);
		Button details = new Button("View Details \u2192");
		details.getStyleClass().add("details-button");
		details.setOnAction(e -> onViewAllCarParks.run());
		rateRow.getChildren().addAll(rate, r2, details);

		card.getChildren().addAll(thumbnail, top, progress, counts, rateRow);
		return card;
	}

	private VBox countChip(String label, int count, String tone) {
		VBox chip = new VBox(4);
		chip.getStyleClass().add("count-chip");
		Label value = new Label(String.valueOf(count));
		value.getStyleClass().add("count-value");
		value.getStyleClass().add(toneClass(tone));
		Label text = new Label(label);
		text.getStyleClass().add("count-label");
		chip.getChildren().addAll(value, text);
		return chip;
	}

	private VBox buildCityMapWidget() {
		VBox section = new VBox(12);
		section.getStyleClass().add("map-section");

		HBox header = new HBox(10);
		Label title = new Label("City Map & Locations");
		title.getStyleClass().add("section-title");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		header.getChildren().addAll(title, sp);

		Pane map = new Pane();
		map.getStyleClass().add("city-map");
		map.setPrefHeight(210);
		map.setMaxWidth(Double.MAX_VALUE);

		int x = 0;
		for (CarPark park : repository.getAllCarParks()) {
			map.getChildren().add(buildMapPin(map, park, x));
			x += 1;
		}
		map.widthProperty().addListener((observable, oldWidth, newWidth) -> layoutMapPins(map));
		layoutMapPins(map);

		section.getChildren().addAll(header, map);
		return section;
	}

	private Node buildMapPin(Pane map, CarPark park, int index) {
		VBox pin = new VBox(2);
		pin.getStyleClass().add("map-pin");
		pin.setAlignment(Pos.CENTER);
		pin.setPrefWidth(220);
		pin.setMaxWidth(220);
		Label dot = new Label("\u25CF");
		dot.getStyleClass().add("map-dot");
		dot.getStyleClass().add(park.availableCount() > 0 ? "map-dot-open" : "map-dot-full");
		Label label = new Label(park.name() + " (" + park.availableCount() + "/" + park.totalCapacity() + ")");
		label.getStyleClass().add("map-label");
		label.setWrapText(true);
		label.setMaxWidth(190);
		pin.getChildren().addAll(dot, label);
		pin.boundsInLocalProperty().addListener((observable, oldBounds, newBounds) -> layoutMapPins(map));
		return pin;
	}

	private void layoutMapPins(Pane map) {
		int count = map.getChildren().size();
		if (count == 0 || map.getWidth() <= 0) {
			return;
		}
		double segmentWidth = map.getWidth() / count;
		for (int index = 0; index < count; index++) {
			Node pin = map.getChildren().get(index);
			double pinWidth = Math.max(110, Math.min(220, segmentWidth - 16));
			if (pin instanceof javafx.scene.layout.Region region) {
				region.setMinWidth(0);
				region.setPrefWidth(pinWidth);
				region.setMaxWidth(pinWidth);
			}
			pin.setLayoutX(segmentWidth * index + (segmentWidth - pinWidth) / 2);
			pin.setLayoutY(map.getHeight() * 0.43);
		}
	}

	private VBox buildQuickReservationPanel() {
		VBox panel = new VBox(12);
		panel.getStyleClass().add("reservation-panel");
		panel.setPrefWidth(340);
		panel.setMaxWidth(Double.MAX_VALUE);

		HBox header = new HBox(10);
		Label title = new Label("Quick Reservation");
		title.getStyleClass().add("section-title");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		header.getChildren().addAll(title, sp);

		quickReservationCarParkBox = new ComboBox<>(FXCollections.observableArrayList(repository.getAllCarParks()));
		quickReservationCarParkBox.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(CarPark p) {
				return p == null ? "" : p.name();
			}

			@Override
			public CarPark fromString(String s) {
				return null;
			}
		});
		quickReservationCarParkBox.setValue(repository.getActiveLot());
		quickReservationCarParkBox.setPromptText("Select Car Park");
		quickReservationCarParkBox.setMaxWidth(Double.MAX_VALUE);

		DatePicker datePicker = new DatePicker(LocalDate.now());
		datePicker.setPromptText("Date");
		datePicker.setMaxWidth(Double.MAX_VALUE);

		ComboBox<String> timeBox = new ComboBox<>(FXCollections.observableArrayList(
				"08:00 AM", "09:30 AM", "11:00 AM", "12:30 PM", "02:00 PM", "03:30 PM", "05:00 PM"));
		timeBox.setValue("10:30 AM");
		timeBox.setPromptText("Start Time");
		timeBox.setMaxWidth(Double.MAX_VALUE);

		ComboBox<String> durationBox = new ComboBox<>(FXCollections.observableArrayList(
				"1 Hour", "2 Hours", "3 Hours", "4 Hours"));
		durationBox.setValue("2 Hours");
		durationBox.setPromptText("Duration");
		durationBox.setMaxWidth(Double.MAX_VALUE);

		Button findButton = new Button("Find Available Spaces");
		findButton.getStyleClass().add("find-button");
		findButton.setMaxWidth(Double.MAX_VALUE);
		findButton.setOnAction(e -> onFindAvailableSpaces.run());

		panel.getChildren().addAll(header,
				new Label("Select Car Park"), quickReservationCarParkBox,
				new Label("Date"), datePicker,
				new Label("Start Time"), timeBox,
				new Label("Duration"), durationBox,
				new Separator(), findButton);
		return panel;
	}

	private VBox buildLiveGridSection() {
		VBox section = new VBox(14);
		section.getStyleClass().add("live-grid-section");
		section.setPadding(new Insets(0, 28, 26, 28));

		HBox header = new HBox(10);
		Label title = new Label("Live Parking Lot \u2014 City Mall");
		title.getStyleClass().add("section-title");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		header.getChildren().addAll(title, sp);

		VBox gridCard = new VBox(10);
		gridCard.getStyleClass().add("grid-card");

		HBox stats = new HBox(16);
		stats.getStyleClass().add("grid-stats");
		Label occLabel = new Label("Occupied");
		occLabel.getStyleClass().add("grid-stat-label");
		Label occValue = new Label("0");
		occValue.getStyleClass().add("grid-stat-value");
		gridOccValue = occValue;
		Label availLabel = new Label("Available");
		availLabel.getStyleClass().add("grid-stat-label");
		Label availValue = new Label("0");
		availValue.getStyleClass().add("grid-stat-value");
		gridAvailValue = availValue;
		Label resLabel = new Label("Reserved");
		resLabel.getStyleClass().add("grid-stat-label");
		Label resValue = new Label("0");
		resValue.getStyleClass().add("grid-stat-value");
		gridResValue = resValue;
		stats.getChildren().addAll(statPair(occLabel, occValue), statPair(availLabel, availValue),
				statPair(resLabel, resValue));

		parkingGrid = new TilePane();
		parkingGrid.getStyleClass().add("live-parking-grid");
		parkingGrid.setAlignment(Pos.CENTER_LEFT);
		parkingGrid.setPrefTileWidth(110);
		parkingGrid.setPrefTileHeight(96);
		parkingGrid.setHgap(10);
		parkingGrid.setVgap(12);
		parkingGrid.setPadding(new Insets(14));
		buildGridTiles();

		HBox chartRow = new HBox(14);
		chartRow.getStyleClass().add("grid-chart-row");
		chartRow.setAlignment(Pos.CENTER_LEFT);

		occupancyPie = new PieChart();
		occupancyPie.getStyleClass().add("occupancy-pie");
		occupancyPie.setPrefWidth(210);
		occupancyPie.setPrefHeight(210);
		occupancyPie.setLegendVisible(true);
		occupancyPie.setClockwise(false);
		chartRow.getChildren().add(occupancyPie);

		VBox summary = new VBox(8);
		summary.getStyleClass().add("grid-summary");
		occupancySummary = new Label("0 of 0 occupied");
		occupancySummary.getStyleClass().add("grid-summary-value");
		occupancyProgress = new ProgressBar(0);
		occupancyProgress.getStyleClass().add("grid-occupancy-progress");
		occupancyProgress.setPrefWidth(Double.MAX_VALUE);
		summary.getChildren().addAll(occupancySummary, occupancyProgress);
		HBox.setHgrow(summary, Priority.ALWAYS);
		chartRow.getChildren().add(summary);

		VBox activitySection = new VBox(10);
		activitySection.getStyleClass().add("activity-section");
		Label activityTitle = new Label("Recent Activity");
		activityTitle.getStyleClass().add("section-title-small");
		activityLog = new VBox(6);
		activityLog.getStyleClass().add("activity-log");
		activitySection.getChildren().addAll(activityTitle, activityLog);

		gridCard.getChildren().addAll(stats, parkingGrid, chartRow, activitySection);
		section.getChildren().addAll(header, gridCard);
		return section;
	}

	private VBox statPair(Label label, Label value) {
		VBox pair = new VBox(4);
		pair.getStyleClass().add("stat-pair");
		label.getStyleClass().add("stat-pair-label");
		value.getStyleClass().add("stat-pair-value");
		pair.getChildren().addAll(label, value);
		return pair;
	}

	private void buildGridTiles() {
		parkingGrid.getChildren().clear();
		slotTiles.clear();
		Parking p = parkingSupplier.get();
		int capacity = p != null ? p.capacity() : 5;
		for (int index = 0; index < capacity; index++) {
			CustomRectangle slot = new CustomRectangle(index + 1);
			slotTiles.put(index + 1, slot);
			parkingGrid.getChildren().add(slot);
		}
	}

	private void refreshLiveGrid() {
		if (parkingGrid == null) {
			return;
		}
		Parking p = parkingSupplier.get();
		int occupied = 0, available = 0, reserved = 0;
		if (p != null) {
			List<Parking.SpaceState> states = p.spaceStates();
			for (Parking.SpaceState state : states) {
				CustomRectangle tile = slotTiles.get(state.slotNumber());
				if (tile != null) {
					tile.render(state.status(), state.vehicleId(), null,
							false);
				}
				switch (state.status()) {
					case OCCUPIED -> occupied++;
					case RESERVED -> reserved++;
					default -> available++;
				}
			}
		} else {
			available = parkingGrid.getChildren().size();
		}
		for (Object child : parkingGrid.getChildren()) {
			if (child instanceof CustomRectangle slot) {
				slot.setVisible(true);
			}
		}
		gridOccValue.setText(String.valueOf(occupied));
		gridAvailValue.setText(String.valueOf(available));
		gridResValue.setText(String.valueOf(reserved));
		if (occupancySummary != null) {
			occupancySummary.setText(occupied + " of " + (occupied + available + reserved) + " occupied");
		}
		if (occupancyProgress != null) {
			int total = occupied + available + reserved;
			occupancyProgress.setProgress(total == 0 ? 0 : (double) occupied / total);
		}
		if (occupancyPie != null) {
			occupancyPie.getData().clear();
			if (occupied > 0) {
				occupancyPie.getData().add(new PieChart.Data("Occupied", occupied));
			}
			if (available > 0) {
				occupancyPie.getData().add(new PieChart.Data("Available", available));
			}
			if (reserved > 0) {
				occupancyPie.getData().add(new PieChart.Data("Reserved", reserved));
			}
			if (occupied == 0 && available == 0 && reserved == 0) {
				occupancyPie.getData().add(new PieChart.Data("Empty", 1));
			}
		}
	}

	private void refreshActivity() {
		if (activityLog == null) {
			return;
		}
		activityLog.getChildren().clear();
		if (activityEntries.isEmpty()) {
			Label empty = new Label("No recent activity.");
			empty.getStyleClass().add("activity-empty");
			activityLog.getChildren().add(empty);
			return;
		}
		int limit = Math.min(5, activityEntries.size());
		for (int i = activityEntries.size() - limit; i < activityEntries.size(); i++) {
			Label entry = new Label(activityEntries.get(i));
			entry.getStyleClass().add("activity-entry");
			entry.setMaxWidth(Double.MAX_VALUE);
			activityLog.getChildren().add(entry);
		}
	}
}
