package com.parkwise;

import com.parkwise.model.BookingRecord;
import com.parkwise.model.Car;
import com.parkwise.model.CarPark;
import com.parkwise.model.Parking;
import com.parkwise.model.ParkingListener;
import com.parkwise.model.Reservation;
import com.parkwise.service.ArrivalSchedule;
import com.parkwise.service.CarParkRepository;
import com.parkwise.service.HistoricalOccupancyPredictor;
import com.parkwise.service.OccupancyForecast;
import com.parkwise.service.ReservationManager;
import com.parkwise.strategy.ConcurrencyStrategy;
import com.parkwise.strategy.SemaphoreStrategy;
import com.parkwise.view.AppShell;
import com.parkwise.view.CustomRectangle;
import com.parkwise.view.DashboardView;
import com.parkwise.view.ParkingLotsView;
import com.parkwise.view.PredictionsView;
import com.parkwise.view.ReportsView;
import com.parkwise.view.ReservationView;
import com.parkwise.view.SimulationMonitorView;

import javafx.animation.PauseTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class Main extends Application implements ParkingListener {
	private static final int MAX_LOG_ENTRIES = 100;
	private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
	private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private enum SimulationState { IDLE, RUNNING, STOPPED }

	private AppShell appShell;
	private final CarParkRepository carParkRepository = new CarParkRepository();
	private DashboardView dashboardView;
	private ParkingLotsView parkingLotsView;
	private ReservationView reservationView;
	private PredictionsView predictionsView;
	private ReportsView reportsView;
	private final java.util.concurrent.atomic.AtomicInteger bookingSequence = new java.util.concurrent.atomic.AtomicInteger(0);
	private Node setupView;
	private Button launchButton;
	private Label launchFeedback;
	private BorderPane dashboardBody;
	private HBox dashboardCenter;
	private ScrollPane dashboardWorkspaceScroll;
	private final ScrollPane analyticsPageScroll = new ScrollPane();
	private VBox parkingLotPanel;
	private VBox parkingLotsGridPanel;
	private boolean simulationLaunched;
	private boolean analyticsExpandedOnDashboard = true;
	private CustomRectangle[] slots;
	private final Map<Integer, CustomRectangle> slotTiles = new HashMap<>();
	private TilePane parkingGrid;
	private HBox parkingGridLegendAndFilter;
	private ComboBox<String> spaceFilter;
	private int selectedSpaceNumber = -1;
	private ScrollPane parkingLotsPage;
	private HBox parkingLotsContentRow;
	private VBox spaceDetailsPanel;
	private Label lotTotalValue;
	private Label lotAvailableValue;
	private Label lotOccupiedValue;
	private Label lotReservedValue;
	private Label selectedSpaceTitle;
	private Label selectedSpaceStatus;
	private Label selectedSpaceReservation;
	private Label selectedSpaceMessage;
	private Button reserveSelectedButton;
	private Label dashboardRunStateValue;
	private Label dashboardActivityStateValue;
	private Label dashboardOccupancySummary;
	private ProgressBar dashboardOccupancyProgress;
	private SimulationMonitorView simulationMonitorView;
	private Label monitorElapsedValue;
	private final javafx.collections.ObservableList<String> activityEntries = FXCollections.observableArrayList();
	private ScrollPane reportsPage;
	private Label reportCapacityValue;
	private Label reportOccupiedValue;
	private Label reportWaitingValue;
	private Label reportDeparturesValue;
	private Label reportAverageWaitValue;
	private Label reportReservationsValue;
	private LineChart<Number, Number> reportsOccupancyChart;
	private XYChart.Series<Number, Number> reportsActualOccupancySeries;
	private VBox reportOccupancyEmptyState;
	private VBox reportActivityRows;
	private VBox reportReservationRows;
	private Label reportActivityCount;
	private Label reportReservationCount;
	private Timeline monitorElapsedTimeline;
	private SimulationState simulationState = SimulationState.IDLE;
	private long elapsedAtStopMillis;
	private int completedVehicleExits;
	private double averageQueueWaitMillis;
	private int lastReportedWaitingCount;
	private int syncStrategy = 1; // 1 = Semaphore, 2 = Mutex
	private int numCars = 10;
	private int nbrPlaces = 5;
	private int arrivalWindowSeconds = 3;
	private int parkingDurationSeconds = 10;
	private int simulationSeed = 42;
	private int spawnedCarCount = 0;
	private final ReservationManager reservationManager = new ReservationManager(nbrPlaces);

	private Parking parking;
	private final List<Car> carThreads = new ArrayList<>();
	private final List<PauseTransition> arrivalTransitions = new ArrayList<>();
	private long simulationStartedNanos;

	// UI Controls
	private VBox dashboardActivityEntries;
	private final Label totalSlotsValue = metricValue("0");
	private final Label occupiedValue = metricValue("0");
	private final Label availableValue = metricValue("0");
	private final Label reservedValue = metricValue("0");
	private final Label waitingValue = metricValue("0");
	private final Label averageWaitValue = metricValue("0.00 s");
	private final Label strategyValue = metricValue("SEMAPHORE");
	private final PieChart occupancyChart = new PieChart();
	private XYChart.Series<Number, Number> waitSeries;
	private XYChart.Series<Number, Number> forecastOccupancySeries;
	private XYChart.Series<Number, Number> actualOccupancySeries;
	private HistoricalOccupancyPredictor historicalOccupancyPredictor;
	private ComboBox<String> historicalCarParkSelector;
	private ComboBox<DayOfWeek> historicalDaySelector;
	private ComboBox<LocalTime> historicalTimeSelector;
	private Label historicalPredictionLabel;
	private Label historicalModelScoreLabel;
	private String historicalDataUnavailableMessage = "Historical occupancy data is unavailable.";
	private final VBox analyticsPanel = new VBox(16);
	private final VBox analyticsContent = new VBox(16);
	private VBox predictionsPageContent;
	private Label predictionCurrentOccupancyValue;
	private Label predictionPeakOccupancyValue;
	private Label predictionAvailableAtPeakValue;
	private Label predictionPeakHorizonCaption;
	private Label predictionCurrentCaption;
	private LineChart<Number, Number> forecastChart;

	@Override
	public void start(Stage primaryStage) {
		try (InputStream dataset = getClass().getResourceAsStream("parking-birmingham.csv")) {
			historicalOccupancyPredictor = HistoricalOccupancyPredictor.load(dataset);
		} catch (IOException e) {
			historicalOccupancyPredictor = null;
			historicalDataUnavailableMessage = "Historical occupancy data could not be loaded. The bundled Birmingham dataset is required for this view.";
		}
		Parking.configureLogging();
		appShell = new AppShell(this::navigateTo);
		appShell.setSearchHandler(this::searchCarParks);
		dashboardView = new DashboardView(carParkRepository,
				() -> parking,
				() -> switch (simulationState) {
					case RUNNING -> "SIMULATION LIVE";
					case STOPPED -> "STOPPED";
					default -> "IDLE";
				},
				() -> syncStrategy == 1 ? "SEMAPHORE" : "MUTEX",
				activityEntries,
				this::spawnVehicleOnTheFly,
				() -> navigateTo(AppShell.Page.SETTINGS),
				this::restartSimulation,
				() -> navigateTo(AppShell.Page.PARKING_LOTS),
				() -> navigateTo(AppShell.Page.RESERVATIONS),
				() -> averageQueueWaitMillis);
		parkingLotsView = new ParkingLotsView(carParkRepository,
				() -> parking,
				slotNumber -> {
					showReservations(slotNumber);
					navigateTo(AppShell.Page.RESERVATIONS);
				},
				park -> {
					// Car park selection change; the view refreshes itself.
				});
		reservationView = new ReservationView(carParkRepository,
				() -> parking,
				data -> confirmReservation(data),
				() -> navigateTo(AppShell.Page.RESERVATIONS),
				() -> navigateTo(AppShell.Page.DASHBOARD),
				url -> getHostServices().showDocument(url));
		predictionsView = new PredictionsView(carParkRepository,
				() -> parking,
				() -> nbrPlaces,
				() -> numCars,
				() -> arrivalWindowSeconds,
				() -> parkingDurationSeconds,
				() -> simulationSeed,
				() -> simulationStartedNanos,
				park -> navigateTo(AppShell.Page.RESERVATIONS));
		predictionsView.setHistoricalPredictor(historicalOccupancyPredictor);
		configureAnalyticsPanel();
		activityEntries.addListener((ListChangeListener<String>) change -> {
			refreshDashboardActivityEntries();
			refreshReportsPage();
		});
		appShell.showPage(AppShell.Page.DASHBOARD, dashboardView);

		Scene scene = new Scene(appShell, 1280, 860);
		scene.getStylesheets().add(getClass().getResource("parking.css").toExternalForm());
		com.parkwise.view.ThemeManager.getInstance().setScene(scene);
		primaryStage.setTitle("ParkWise — Smart Parking");
		primaryStage.setMinWidth(1140);
		primaryStage.setMinHeight(720);
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	private void searchCarParks(String query) {
		String normalizedQuery = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
		if (normalizedQuery.isEmpty()) {
			appShell.showSearchMessage("Enter a car park name or location.");
			return;
		}
		CarPark match = carParkRepository.getAllCarParks().stream()
				.filter(park -> park.name().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)
						|| park.location().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery))
				.sorted(java.util.Comparator.comparingInt(park ->
						park.name().equalsIgnoreCase(query.strip()) ? 0 : 1))
				.findFirst().orElse(null);
		if (match == null) {
			appShell.showSearchMessage("No car park found for ‘" + query.strip() + "’.");
			return;
		}
		appShell.showSearchMessage(null);
		parkingLotsView.selectCarPark(match);
		navigateTo(AppShell.Page.PARKING_LOTS);
	}

	private void showConfigurationScreen(AppShell.Page page) {
		if (setupView != null) {
			launchButton.setText(simulationLaunched ? "Apply Settings & Restart" : "Launch Simulation");
			appShell.showPage(page, setupView);
			return;
		}

		Label eyebrow = new Label("SYSTEM SETUP");
		eyebrow.getStyleClass().add("eyebrow");
		Label title = new Label("Prepare your parking simulation");
		title.getStyleClass().add("page-title");
		Label subtitle = new Label("Choose the arrival load, space capacity, and concurrency strategy.");
		subtitle.getStyleClass().add("page-subtitle");
		VBox header = new VBox(4, eyebrow, title, subtitle);

		Label strategyLabel = new Label("1. Select Concurrency Strategy");
		strategyLabel.getStyleClass().add("section-title");

		ToggleGroup group = new ToggleGroup();
		RadioButton semRadio = new RadioButton("Semaphore Strategy");
		semRadio.setToggleGroup(group);
		semRadio.setSelected(syncStrategy == 1);
		Label semDesc = new Label("Allows up to N cars to enter concurrently using permit pools.");
		semDesc.getStyleClass().add("metric-caption");
		VBox semCard = new VBox(6, semRadio, semDesc);
		semCard.getStyleClass().add("metric-card");
		semCard.setOnMouseClicked(e -> semRadio.setSelected(true));

		RadioButton mutexRadio = new RadioButton("Mutex Lock Strategy");
		mutexRadio.setToggleGroup(group);
		mutexRadio.setSelected(syncStrategy == 2);
		Label mutexDesc = new Label("Enforces strict mutual exclusion with Condition queue waiting.");
		mutexDesc.getStyleClass().add("metric-caption");
		VBox mutexCard = new VBox(6, mutexRadio, mutexDesc);
		mutexCard.getStyleClass().add("metric-card");
		mutexCard.setOnMouseClicked(e -> mutexRadio.setSelected(true));

		HBox strategyBox = new HBox(16, semCard, mutexCard);
		HBox.setHgrow(semCard, Priority.ALWAYS);
		HBox.setHgrow(mutexCard, Priority.ALWAYS);

		Label paramsLabel = new Label("2. Define Simulation Load");
		paramsLabel.getStyleClass().add("section-title");

		Spinner<Integer> carSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, numCars));
		carSpinner.setEditable(true);
		VBox carBox = new VBox(6, new Label("Number of Vehicles:"), carSpinner);

		Spinner<Integer> slotSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, nbrPlaces));
		slotSpinner.setEditable(true);
		VBox slotBox = new VBox(6, new Label("Parking Slot Capacity:"), slotSpinner);

		HBox paramsBox = new HBox(24, carBox, slotBox);
		Spinner<Integer> arrivalSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, arrivalWindowSeconds));
		Spinner<Integer> durationSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, parkingDurationSeconds));
		Spinner<Integer> seedSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, simulationSeed));
		arrivalSpinner.setEditable(true);
		durationSpinner.setEditable(true);
		seedSpinner.setEditable(true);
		VBox arrivalBox = new VBox(6, new Label("Arrival window (seconds):"), arrivalSpinner);
		VBox durationBox = new VBox(6, new Label("Parking duration (seconds):"), durationSpinner);
		VBox seedBox = new VBox(6, new Label("Arrival schedule seed:"), seedSpinner);
		HBox timingBox = new HBox(24, arrivalBox, durationBox, seedBox);

		Button preset1 = new Button("Quick Test (5 Cars / 3 Slots)");
		preset1.getStyleClass().add("secondary-button");
		preset1.setOnAction(e -> { carSpinner.getValueFactory().setValue(5); slotSpinner.getValueFactory().setValue(3); });

		Button preset2 = new Button("High Stress (30 Cars / 8 Slots)");
		preset2.getStyleClass().add("secondary-button");
		preset2.setOnAction(e -> { carSpinner.getValueFactory().setValue(30); slotSpinner.getValueFactory().setValue(8); });
		Button manageReservationsButton = new Button("Manage reservations");
		manageReservationsButton.getStyleClass().add("secondary-button");
		manageReservationsButton.setOnAction(e -> showReservations());

		HBox presetsBox = new HBox(10, new Label("Presets:"), preset1, preset2);
		presetsBox.setAlignment(Pos.CENTER_LEFT);
		HBox reservationsRow = new HBox(manageReservationsButton);
		reservationsRow.setAlignment(Pos.CENTER_LEFT);
		launchFeedback = new Label();
		launchFeedback.getStyleClass().add("validation-message");
		launchFeedback.setWrapText(true);

		launchButton = new Button("Launch Simulation");
		launchButton.getStyleClass().add("primary-button");
		launchButton.setStyle("-fx-font-size: 15px; -fx-padding: 12px 28px;");
		launchButton.setOnAction(e -> {
			Integer configuredCars = readSpinnerValue(carSpinner, 1, 100);
			Integer configuredSlots = readSpinnerValue(slotSpinner, 1, 50);
			Integer configuredArrivalWindow = readSpinnerValue(arrivalSpinner, 1, 60);
			Integer configuredParkingDuration = readSpinnerValue(durationSpinner, 1, 60);
			Integer configuredSeed = readSpinnerValue(seedSpinner, 0, Integer.MAX_VALUE);
			if (configuredCars == null || configuredSlots == null || configuredArrivalWindow == null
					|| configuredParkingDuration == null || configuredSeed == null) {
				launchFeedback.setText("Use whole numbers: 1–100 vehicles, 1–50 spaces, 1–60 seconds for timing, and a seed from 0 to 2,147,483,647.");
				return;
			}
			if (!reservationManager.setCapacity(configuredSlots)) {
				launchFeedback.setText("Cannot reduce capacity below the slot numbers used by existing reservations. Cancel or move those reservations first.");
				return;
			}
			launchFeedback.setText("");
			syncStrategy = semRadio.isSelected() ? 1 : 2;
			numCars = configuredCars;
			nbrPlaces = configuredSlots;
			arrivalWindowSeconds = configuredArrivalWindow;
			parkingDurationSeconds = configuredParkingDuration;
			simulationSeed = configuredSeed;
			launchLiveDashboard();
		});

		VBox configCard = new VBox(20, header, strategyLabel, strategyBox, paramsLabel, paramsBox, presetsBox,
				reservationsRow, timingBox, launchFeedback, launchButton);
		configCard.getStyleClass().add("panel");
		configCard.setMaxWidth(800);
		configCard.setPadding(new Insets(30));

		StackPane centerContainer = new StackPane(configCard);
		centerContainer.setAlignment(Pos.CENTER);
		setupView = centerContainer;
		appShell.showPage(page,
				page == AppShell.Page.SETTINGS ? "Settings" : "Dashboard",
				page == AppShell.Page.SETTINGS
						? "Simulation settings take effect when you launch or restart a run."
						: "Configure and launch the parking simulation to see its live dashboard.",
				setupView);
	}

	private Integer readSpinnerValue(Spinner<Integer> spinner, int minimum, int maximum) {
		try {
			int value = Integer.parseInt(spinner.getEditor().getText().trim());
			if (value < minimum || value > maximum) {
				return null;
			}
			spinner.getValueFactory().setValue(value);
			return value;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private void navigateTo(AppShell.Page page) {
		if (page != AppShell.Page.SIMULATION_MONITOR && monitorElapsedTimeline != null) {
			monitorElapsedTimeline.stop();
		}
		switch (page) {
			case DASHBOARD -> {
				appShell.showPage(AppShell.Page.DASHBOARD, dashboardView);
				dashboardView.refresh();
			}
			case PARKING_LOTS -> {
				appShell.showPage(page, parkingLotsView);
				parkingLotsView.refresh();
			}
			case RESERVATIONS -> {
				appShell.showPage(page, reservationView);
				reservationView.refresh();
			}
			case PREDICTIONS -> {
				appShell.showPage(page, predictionsView);
				predictionsView.refresh();
			}
			case SIMULATION_MONITOR -> {
				appShell.showPage(page, showSimulationMonitor());
				if (simulationState == SimulationState.RUNNING) {
					monitorElapsedTimeline.play();
				}
			}
			case REPORTS -> {
				if (reportsView == null) {
					reportsView = new ReportsView();
				}
				reportsView.setContent(createReportsPage());
				appShell.showPage(page, reportsView);
				refreshReportsPage();
			}
			case SETTINGS -> showConfigurationScreen(AppShell.Page.SETTINGS);
		}
	}

	private Node showSimulationMonitor() {
		if (simulationMonitorView == null) {
			simulationMonitorView = createSimulationMonitorView();
			monitorElapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshMonitorElapsed()));
			monitorElapsedTimeline.setCycleCount(Timeline.INDEFINITE);
		}
		refreshSimulationMonitor();
		return simulationMonitorView;
	}

	private SimulationMonitorView createSimulationMonitorView() {
		SimulationMonitorView view = new SimulationMonitorView(
				() -> parking,
				this::startSimulationFromMonitor,
				this::stopSimulation,
				this::restartSimulation,
				selectedStrategy -> {
					syncStrategy = selectedStrategy;
					if (simulationMonitorView != null) {
						simulationMonitorView.setStrategy(selectedStrategy);
						simulationMonitorView.addLogRow("System", "INFO",
								selectedStrategy == 1
										? "Semaphore Strategy selected for the simulation run."
										: "Mutex Strategy selected for the simulation run.");
					}
				},
				() -> simulationStartedNanos == 0 ? null : simulationStartedNanos);
		view.setStrategy(syncStrategy);
		view.setProcessedCount(completedVehicleExits);
		view.refresh();
		return view;
	}

	private void startSimulationFromMonitor() {
		if (simulationState == SimulationState.RUNNING) {
			return;
		}
		try {
			if (!simulationLaunched) {
				prepareLiveDashboard();
			}
			startSimulation();
			if (monitorElapsedTimeline != null) {
				monitorElapsedTimeline.play();
			}
			refreshSimulationMonitor();
		} catch (RuntimeException exception) {
			rollbackFailedLaunch();
			showSimulationStartError(exception);
		}
	}

	private void showSimulationStartError(RuntimeException exception) {
		String detail = exception.getMessage() == null || exception.getMessage().isBlank()
				? exception.getClass().getSimpleName() : exception.getMessage();
		LOGGER.log(Level.SEVERE, "Simulation start failed", exception);
		if (launchFeedback != null) {
			launchFeedback.setText("Simulation could not start: " + detail + " Check the application log for details.");
			appShell.showPage(AppShell.Page.SETTINGS, setupView);
		} else {
			javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
					javafx.scene.control.Alert.AlertType.ERROR,
					"Simulation could not start: " + detail + "\nCheck the application log for details.");
			alert.setHeaderText("Simulation start failed");
			alert.show();
		}
	}

	private void refreshAllPresentationViews() {
		runOnFxThread(() -> {
			if (dashboardView != null) {
				dashboardView.refresh();
			}
			if (parkingLotsView != null) {
				parkingLotsView.refresh();
			}
			if (predictionsView != null) {
				predictionsView.refresh();
			}
			if (simulationMonitorView != null) {
				refreshSimulationMonitor();
			}
			refreshReportsPage();
		});
	}

	private void refreshSimulationMonitor() {
		if (simulationMonitorView == null) {
			return;
		}
		simulationMonitorView.setState(switch (simulationState) {
			case IDLE -> "IDLE";
			case RUNNING -> "RUNNING";
			case STOPPED -> "STOPPED";
		});
		simulationMonitorView.setStrategy(syncStrategy);
		simulationMonitorView.setProcessedCount(completedVehicleExits);
		simulationMonitorView.refresh();
	}

	private void refreshMonitorElapsed() {
		if (monitorElapsedValue == null) {
			return;
		}
		long elapsedMillis = simulationState == SimulationState.RUNNING
				? java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - simulationStartedNanos)
				: elapsedAtStopMillis;
		monitorElapsedValue.setText(String.format(Locale.ROOT, "%.1f s", elapsedMillis / 1000.0));
	}

	private VBox createSpaceDetailsPanel() {
		Label heading = new Label("Space details");
		heading.getStyleClass().add("section-title");
		selectedSpaceTitle = new Label("Select a space");
		selectedSpaceTitle.getStyleClass().add("metric-value");
		selectedSpaceStatus = new Label("Choose a space in the grid to inspect its status.");
		selectedSpaceStatus.getStyleClass().add("section-caption");
		selectedSpaceStatus.setWrapText(true);
		selectedSpaceReservation = new Label();
		selectedSpaceReservation.getStyleClass().add("section-caption");
		selectedSpaceReservation.setWrapText(true);
		selectedSpaceMessage = new Label();
		selectedSpaceMessage.getStyleClass().add("validation-message");
		selectedSpaceMessage.setWrapText(true);
		reserveSelectedButton = new Button("Reserve selected space");
		reserveSelectedButton.getStyleClass().add("primary-button");
		reserveSelectedButton.setDisable(true);
		reserveSelectedButton.setOnAction(event -> reserveSelectedSpace());

		VBox details = new VBox(12, heading, selectedSpaceTitle, selectedSpaceStatus,
				selectedSpaceReservation, selectedSpaceMessage, reserveSelectedButton);
		details.getStyleClass().add("panel");
		details.setMinWidth(250);
		details.setPrefWidth(280);
		return details;
	}

	private void attachDashboardPanels() {
		detachPredictionsAnalytics();
		if (dashboardCenter == null) {
			return;
		}
		if (parkingLotsContentRow != null) {
			parkingLotsContentRow.getChildren().remove(parkingLotsGridPanel);
		}
		if (parkingLotsGridPanel != null) {
			parkingLotsGridPanel.getChildren().removeAll(parkingGridLegendAndFilter, parkingGrid);
		}
		if (parkingLotPanel != null) {
			if (!parkingLotPanel.getChildren().contains(parkingGridLegendAndFilter)) {
				parkingLotPanel.getChildren().add(parkingGridLegendAndFilter);
			}
			if (!parkingLotPanel.getChildren().contains(parkingGrid)) {
				parkingLotPanel.getChildren().add(parkingGrid);
			}
		}
		if (!dashboardCenter.getChildren().contains(parkingLotPanel)) {
			dashboardCenter.getChildren().add(0, parkingLotPanel);
		}
		if (!dashboardCenter.getChildren().contains(analyticsPanel)) {
			dashboardCenter.getChildren().add(analyticsPanel);
		}
		analyticsPanel.setVisible(analyticsExpandedOnDashboard);
		analyticsPanel.setManaged(analyticsExpandedOnDashboard);
	}

	private void detachPredictionsAnalytics() {
		if (analyticsPageScroll.getContent() == predictionsPageContent) {
			analyticsPageScroll.setContent(null);
		}
		if (predictionsPageContent != null) {
			predictionsPageContent.getChildren().remove(analyticsPanel);
		}
		setPredictionsAnalyticsLayout(false);
	}

	private ScrollPane createReservationsPage() {
		Label totalReservations = metricValue("0");
		Label bookedSpaces = metricValue("0");
		Label nextArrival = metricValue("—");
		HBox summary = new HBox(12,
				createMetricCard("TOTAL BOOKINGS", totalReservations, "Scheduled reservations"),
				createMetricCard("SPACES BOOKED", bookedSpaces, "Distinct spaces with bookings"),
				createMetricCard("NEXT ARRIVAL", nextArrival, "Seconds from next run start"));
		summary.getStyleClass().add("metrics-row");

		Label listTitle = new Label("Scheduled reservations");
		listTitle.getStyleClass().add("section-title");
		Label listCount = new Label();
		listCount.getStyleClass().add("reservation-count");
		HBox listHeader = new HBox(12, listTitle, new Region(), listCount);
		HBox.setHgrow(listHeader.getChildren().get(1), Priority.ALWAYS);
		listHeader.setAlignment(Pos.CENTER_LEFT);
		Label listCaption = new Label("Vehicle assignments and arrival intervals for the next simulation run.");
		listCaption.getStyleClass().add("section-caption");
		listCaption.setWrapText(true);
		VBox reservationRows = new VBox(8);
		reservationRows.getStyleClass().add("reservation-page-rows");
		Runnable refreshReservations = () -> {
			List<Reservation> reservations = reservationManager.all();
			totalReservations.setText(String.valueOf(reservations.size()));
			bookedSpaces.setText(String.valueOf(reservations.stream()
					.map(Reservation::slotNumber).distinct().count()));
			reservations.stream().mapToInt(Reservation::startOffsetSeconds).min()
					.ifPresentOrElse(offset -> nextArrival.setText(offset + " sec"),
							() -> nextArrival.setText("—"));
			listCount.setText(reservations.size()
					+ (reservations.size() == 1 ? " reservation" : " reservations"));
			reservationRows.getChildren().clear();
			if (reservations.isEmpty()) {
				Label emptyTitle = new Label("No reservations yet");
				emptyTitle.getStyleClass().add("reservation-empty-title");
				Label emptyDescription = new Label("Create a reservation to assign a vehicle, space, and arrival interval.");
				emptyDescription.getStyleClass().add("section-caption");
				emptyDescription.setWrapText(true);
				VBox empty = new VBox(6, emptyTitle, emptyDescription);
				empty.getStyleClass().add("reservation-page-empty-state");
				reservationRows.getChildren().add(empty);
			} else {
				reservations.forEach(reservation -> reservationRows.getChildren()
						.add(createReservationPageRow(reservation)));
			}
		};
		refreshReservations.run();

		VBox listCard = new VBox(12, listHeader, listCaption, reservationRows);
		listCard.getStyleClass().addAll("panel", "reservation-page-card");
		Button manageButton = new Button("Create or manage reservations");
		manageButton.getStyleClass().add("primary-button");
		manageButton.setOnAction(event -> {
			showReservations();
			refreshReservations.run();
		});
		HBox actions = new HBox(manageButton);
		actions.setAlignment(Pos.CENTER_RIGHT);

		VBox pageContent = new VBox(16, summary, listCard, actions);
		pageContent.getStyleClass().add("reservations-page-content");
		ScrollPane page = new ScrollPane(pageContent);
		page.setFitToWidth(true);
		page.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		page.getStyleClass().add("reservations-page-scroll");
		return page;
	}

	private HBox createReservationPageRow(Reservation reservation) {
		Label vehicle = new Label(reservation.vehicleId());
		vehicle.getStyleClass().add("reservation-row-vehicle");
		Label interval = new Label(String.format(Locale.ROOT,
				"Arrival +%d sec · ends +%d sec · %d sec duration",
				reservation.startOffsetSeconds(), reservation.endOffsetSeconds(), reservation.durationSeconds()));
		interval.getStyleClass().add("reservation-row-interval");
		VBox booking = new VBox(5, vehicle, interval);
		booking.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(booking, Priority.ALWAYS);
		Label space = new Label(String.format(Locale.ROOT, "SPACE S%02d", reservation.slotNumber()));
		space.getStyleClass().add("reservation-space-chip");
		Label status = new Label("Confirmed");
		status.getStyleClass().addAll("reservation-status-pill", "reservation-status-confirmed");
		HBox row = new HBox(14, booking, space, status);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("reservation-page-row");
		return row;
	}

	private void exportReservationsReport() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Export ParkWise Reservation Report");
		chooser.setInitialFileName("parkwise-reservations.csv");
		chooser.getExtensionFilters().add(
				new FileChooser.ExtensionFilter("CSV files", "*.csv"));
		File file = chooser.showSaveDialog(primaryStageForDialogs());
		if (file == null) {
			return;
		}
		StringBuilder csv = new StringBuilder("Booking ID,Vehicle,Space,Start Offset,Duration Seconds,Status\n");
		for (Reservation reservation : reservationManager.all()) {
			csv.append(reservation.id()).append(',')
					.append(reservation.vehicleId()).append(',')
					.append(reservation.slotNumber()).append(',')
					.append(reservation.startOffsetSeconds()).append(',')
					.append(reservation.durationSeconds()).append(',')
					.append("Confirmed\n");
		}
		try {
			Files.writeString(file.toPath(), csv.toString());
		} catch (IOException exception) {
			LOGGER.log(Level.WARNING, "Could not export reservation report", exception);
		}
	}

	private Window primaryStageForDialogs() {
		return appShell == null ? null : appShell.getScene().getWindow();
	}

	private ScrollPane createReportsPage() {
		if (reportsPage != null) {
			return reportsPage;
		}
		reportCapacityValue = metricValue("—");
		reportOccupiedValue = metricValue("—");
		reportWaitingValue = metricValue("—");
		reportDeparturesValue = metricValue("—");
		reportAverageWaitValue = metricValue("—");
		reportReservationsValue = metricValue("0");
		HBox firstMetrics = new HBox(12,
				createMetricCard("TOTAL RESERVATIONS", reportReservationsValue, "+12% from last period"),
				createMetricCard("AVERAGE OCCUPANCY", reportOccupiedValue, "Current occupied / capacity"),
				createMetricCard("TOTAL REVENUE", metricValue("RM 0.00"), "Based on reservation cost"));
		HBox secondMetrics = new HBox(12,
				createMetricCard("COMPLETED DEPARTURES", reportDeparturesValue, "Departures in this run"),
				createMetricCard("AVERAGE QUEUE WAIT", reportAverageWaitValue, "Completed parking visits"),
				createMetricCard("RESERVATIONS ON FILE", reportReservationsValue, "Configured booking records"));
		firstMetrics.getStyleClass().add("metrics-row");
		secondMetrics.getStyleClass().add("metrics-row");

		Label runNote = new Label("Simulation metrics describe the current or most recently started run. A new run resets its counters.");
		runNote.getStyleClass().add("section-caption");
		runNote.setWrapText(true);
		VBox summary = new VBox(10, firstMetrics, secondMetrics, runNote);
		summary.getStyleClass().add("reports-summary");

		NumberAxis timeAxis = new NumberAxis();
		timeAxis.setLabel("Elapsed simulation time (seconds)");
		NumberAxis occupancyAxis = new NumberAxis();
		occupancyAxis.setLabel("Occupied spaces");
		occupancyAxis.setForceZeroInRange(true);
		reportsOccupancyChart = new LineChart<>(timeAxis, occupancyAxis);
		reportsOccupancyChart.setAnimated(false);
		reportsOccupancyChart.setCreateSymbols(true);
		reportsOccupancyChart.setLegendVisible(true);
		reportsOccupancyChart.setPrefHeight(310);
		reportsOccupancyChart.setMinHeight(260);
		reportsOccupancyChart.getStyleClass().add("reports-occupancy-chart");
		reportsActualOccupancySeries = new XYChart.Series<>();
		reportsActualOccupancySeries.setName("Actual occupancy");
		reportsOccupancyChart.getData().add(reportsActualOccupancySeries);
		Label chartEmptyTitle = new Label("No simulation run to report yet");
		chartEmptyTitle.getStyleClass().add("reservation-empty-title");
		Label chartEmptyDescription = new Label("Start a simulation to record actual occupancy and vehicle departure metrics.");
		chartEmptyDescription.getStyleClass().add("section-caption");
		chartEmptyDescription.setWrapText(true);
		reportOccupancyEmptyState = new VBox(6, chartEmptyTitle, chartEmptyDescription);
		reportOccupancyEmptyState.getStyleClass().add("reports-empty-state");
		Label chartTitle = new Label("Actual occupancy over time");
		chartTitle.getStyleClass().add("section-title");
		Label chartCaption = new Label("Recorded occupied-space counts from parking and departure events in the current run.");
		chartCaption.getStyleClass().add("section-caption");
		chartCaption.setWrapText(true);
		VBox chartCard = new VBox(10, chartTitle, chartCaption, reportsOccupancyChart, reportOccupancyEmptyState);
		chartCard.getStyleClass().addAll("panel", "reports-card");

		Label activityTitle = new Label("Recent simulation activity");
		activityTitle.getStyleClass().add("section-title");
		reportActivityCount = new Label();
		reportActivityCount.getStyleClass().add("reservation-count");
		HBox activityHeader = new HBox(10, activityTitle, new Region(), reportActivityCount);
		HBox.setHgrow(activityHeader.getChildren().get(1), Priority.ALWAYS);
		activityHeader.setAlignment(Pos.CENTER_LEFT);
		Label activityCaption = new Label("Recorded events are retained in memory for the current run (up to 100 entries).");
		activityCaption.getStyleClass().add("section-caption");
		activityCaption.setWrapText(true);
		reportActivityRows = new VBox(6);
		reportActivityRows.getStyleClass().add("reports-activity-rows");
		VBox activityCard = new VBox(10, activityHeader, activityCaption, reportActivityRows);
		activityCard.getStyleClass().addAll("panel", "reports-card");

		Label reservationTitle = new Label("Reservation activity");
		reservationTitle.getStyleClass().add("section-title");
		reportReservationCount = new Label();
		reportReservationCount.getStyleClass().add("reservation-count");
		HBox reservationHeader = new HBox(10, reservationTitle, new Region(), reportReservationCount);
		HBox.setHgrow(reservationHeader.getChildren().get(1), Priority.ALWAYS);
		reservationHeader.setAlignment(Pos.CENTER_LEFT);
		Label reservationCaption = new Label("Bookings currently stored for the simulation schedule.");
		reservationCaption.getStyleClass().add("section-caption");
		reservationCaption.setWrapText(true);
		reportReservationRows = new VBox(8);
		reportReservationRows.getStyleClass().add("reports-reservation-rows");
		VBox reservationCard = new VBox(10, reservationHeader, reservationCaption, reportReservationRows);
		reservationCard.getStyleClass().addAll("panel", "reports-card");

		Button exportReport = new Button("Export Report");
		exportReport.getStyleClass().add("primary-button");
		exportReport.setOnAction(event -> exportReservationsReport());
		HBox reportActions = new HBox(10, exportReport);
		reportActions.setAlignment(Pos.CENTER_RIGHT);

		VBox content = new VBox(14, summary, chartCard, activityCard, reservationCard, reportActions);
		content.getStyleClass().add("reports-page-content");
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scroll.getStyleClass().add("reports-page-scroll");
		reportsPage = scroll;
		refreshReportsPage();
		return reportsPage;
	}

	private void refreshReportsPage() {
		if (reportsPage == null) {
			return;
		}
		Parking.SpaceSnapshot snapshot = currentSpaceSnapshot();
		reportCapacityValue.setText(String.valueOf(snapshot.totalCount()));
		if (parking == null) {
			reportOccupiedValue.setText("—");
			reportWaitingValue.setText("—");
			reportDeparturesValue.setText("—");
			reportAverageWaitValue.setText("—");
		} else {
			reportOccupiedValue.setText(snapshot.occupiedCount() + " / " + snapshot.totalCount());
			reportWaitingValue.setText(String.valueOf(snapshot.waitingCount()));
			reportDeparturesValue.setText(String.valueOf(completedVehicleExits));
			reportAverageWaitValue.setText(completedVehicleExits == 0 ? "—"
					: String.format(Locale.ROOT, "%.2f s", averageQueueWaitMillis / 1000.0));
		}

		List<Reservation> reservations = reservationManager.all();
		reportReservationsValue.setText(String.valueOf(reservations.size()));
		reportReservationCount.setText(reservations.size()
				+ (reservations.size() == 1 ? " booking" : " bookings"));
		reportReservationRows.getChildren().clear();
		if (reservations.isEmpty()) {
			reportReservationRows.getChildren().add(reportEmptyMessage("No reservation records are stored."));
		} else {
			reservations.forEach(reservation -> reportReservationRows.getChildren()
					.add(createReservationPageRow(reservation)));
		}

		int eventCount = activityEntries.size();
		reportActivityCount.setText(eventCount > 10
				? eventCount + " events · latest 10 shown"
				: eventCount + (eventCount == 1 ? " event" : " events"));
		reportActivityRows.getChildren().clear();
		if (eventCount == 0) {
			reportActivityRows.getChildren().add(reportEmptyMessage("No simulation events have been recorded yet."));
		} else {
			activityEntries.stream().limit(10).map(event -> {
				Label row = new Label(event);
				row.getStyleClass().add("report-activity-entry");
				row.setWrapText(true);
				return row;
			}).forEach(reportActivityRows.getChildren()::add);
		}

		boolean hasRunData = parking != null && actualOccupancySeries != null
				&& !actualOccupancySeries.getData().isEmpty();
		reportsOccupancyChart.setVisible(hasRunData);
		reportsOccupancyChart.setManaged(hasRunData);
		reportOccupancyEmptyState.setVisible(!hasRunData);
		reportOccupancyEmptyState.setManaged(!hasRunData);
		if (hasRunData) {
			reportsActualOccupancySeries.getData().setAll(actualOccupancySeries.getData().stream()
					.map(point -> new XYChart.Data<>(point.getXValue(), point.getYValue())).toList());
		} else {
			reportsActualOccupancySeries.getData().clear();
		}
	}

	private Label reportEmptyMessage(String message) {
		Label empty = new Label(message);
		empty.getStyleClass().add("reports-empty-state");
		empty.setWrapText(true);
		return empty;
	}

	private void showReservations() {
		showReservations(0);
	}

	private void showReservations(int preferredSpaceNumber) {
		Dialog<Void> dialog = new Dialog<>();
		dialog.setTitle("Manage Reservations");
		dialog.initOwner(appShell.getScene().getWindow());
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.setResizable(true);
		ButtonType closeActionType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
		dialog.getDialogPane().getButtonTypes().add(closeActionType);
		dialog.getDialogPane().getStyleClass().add("reservation-dialog-pane");
		dialog.getDialogPane().getStylesheets().add(getClass().getResource("parking.css").toExternalForm());
		Window owner = appShell.getScene().getWindow();
		Rectangle2D screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
				.stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
		double dialogWidth = Math.min(960, Math.max(320, screen.getWidth() - 48));
		double dialogHeight = Math.min(750, Math.max(320, screen.getHeight() - 48));
		dialog.getDialogPane().setMinSize(Math.min(760, dialogWidth), Math.min(600, dialogHeight));
		dialog.getDialogPane().setPrefSize(dialogWidth, dialogHeight);
		dialog.getDialogPane().setMaxSize(dialogWidth, dialogHeight);

		Label brandMark = new Label("P");
		brandMark.getStyleClass().add("reservation-brand-mark");
		Label brandCaption = new Label("PARKWISE  ·  SMART PARKING");
		brandCaption.getStyleClass().add("reservation-brand-caption");
		VBox brandText = new VBox(2, brandMark, brandCaption);
		brandText.getStyleClass().add("reservation-brand-lockup");
		Label title = new Label("Manage Reservations");
		title.getStyleClass().add("reservation-title");
		Label subtitle = new Label("Schedule and manage parking spaces for your next simulation.");
		subtitle.getStyleClass().add("reservation-subtitle");
		subtitle.setWrapText(true);
		VBox heading = new VBox(5, title, subtitle);
		Button closeButton = new Button("×");
		closeButton.getStyleClass().add("dialog-close-button");
		closeButton.setAccessibleText("Close reservation window");
		closeButton.setOnAction(event -> {
			event.consume();
			closeReservationDialog(dialog, owner);
		});
		Region headerSpacer = new Region();
		HBox dialogHeader = new HBox(16, brandText, heading, headerSpacer, closeButton);
		dialogHeader.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(heading, Priority.ALWAYS);
		HBox.setHgrow(headerSpacer, Priority.ALWAYS);
		dialogHeader.getStyleClass().add("reservation-header");

		TextField vehicleField = new TextField();
		vehicleField.setPromptText("For example, CAR-104");
		vehicleField.setPrefHeight(40);
		Spinner<Integer> startSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3600, 5));
		startSpinner.setEditable(true);
		startSpinner.setPrefHeight(40);
		Spinner<Integer> durationSpinner = new Spinner<>(
				new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 3600, parkingDurationSeconds));
		durationSpinner.setEditable(true);
		durationSpinner.setPrefHeight(40);

		GridPane form = new GridPane();
		form.setHgap(18);
		form.setVgap(15);
		form.setMaxWidth(Double.MAX_VALUE);
		form.getStyleClass().add("reservation-form-grid");
		ColumnConstraints leftColumn = new ColumnConstraints();
		leftColumn.setPercentWidth(50);
		leftColumn.setHgrow(Priority.ALWAYS);
		ColumnConstraints rightColumn = new ColumnConstraints();
		rightColumn.setPercentWidth(50);
		rightColumn.setHgrow(Priority.ALWAYS);
		form.getColumnConstraints().setAll(leftColumn, rightColumn);

		VBox vehicleGroup = new VBox(7, formLabel("Vehicle ID or name"), vehicleField);
		VBox arrivalGroup = new VBox(7, formLabel("Arrival offset (seconds)"), startSpinner);
		VBox durationGroup = new VBox(7, formLabel("Parking duration (seconds)"), durationSpinner);
		vehicleGroup.setMaxWidth(Double.MAX_VALUE);
		arrivalGroup.setMaxWidth(Double.MAX_VALUE);
		durationGroup.setMaxWidth(Double.MAX_VALUE);
		vehicleField.setMaxWidth(Double.MAX_VALUE);
		startSpinner.setMaxWidth(Double.MAX_VALUE);
		durationSpinner.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(vehicleGroup, Priority.ALWAYS);
		GridPane.setHgrow(arrivalGroup, Priority.ALWAYS);
		GridPane.setHgrow(durationGroup, Priority.ALWAYS);
		form.add(vehicleGroup, 0, 0, 2, 1);
		form.add(arrivalGroup, 0, 1);
		form.add(durationGroup, 1, 1);
		Label selectedSpace = new Label(preferredSpaceNumber > 0
				? String.format("Selected parking space  ·  S%02d", preferredSpaceNumber)
				: "A parking space will be assigned automatically");
		selectedSpace.getStyleClass().add(preferredSpaceNumber > 0 ? "selected-space-chip" : "reservation-hint");
		form.add(selectedSpace, 0, 2, 2, 1);

		Label formTitle = new Label("Create a reservation");
		formTitle.getStyleClass().add("section-title");
		Label formDescription = new Label("Arrival times use seconds relative to the next simulation start.");
		formDescription.getStyleClass().add("section-caption");
		Label status = new Label();
		status.getStyleClass().add("reservation-feedback");
		status.setWrapText(true);
		Button reserveButton = new Button("Reserve space");
		reserveButton.getStyleClass().add("primary-button");
		Button cancelButton = new Button("Cancel selected");
		cancelButton.getStyleClass().add("secondary-button");
		Label cancelHint = new Label("Select a reservation below to enable cancellation.");
		cancelHint.getStyleClass().add("reservation-hint");
		HBox actions = new HBox(12, reserveButton, cancelButton, cancelHint);
		actions.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(cancelHint, Priority.ALWAYS);
		VBox formCard = new VBox(14, formTitle, formDescription, form, actions, status);
		formCard.getStyleClass().addAll("panel", "reservation-card");

		TableView<Reservation> reservationTable = new TableView<>();
		reservationTable.setItems(FXCollections.observableArrayList(reservationManager.all()));
		reservationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);
		reservationTable.setPlaceholder(createReservationEmptyState());
		reservationTable.getStyleClass().add("reservation-table");
		reservationTable.setAccessibleText("Current reservations");

		TableColumn<Reservation, String> vehicleColumn = new TableColumn<>("Vehicle");
		vehicleColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().vehicleId()));
		vehicleColumn.setPrefWidth(350);
		TableColumn<Reservation, String> spaceColumn = new TableColumn<>("Parking space");
		spaceColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
				String.format(Locale.ROOT, "S%02d", data.getValue().slotNumber())));
		spaceColumn.setPrefWidth(165);
		TableColumn<Reservation, String> arrivalColumn = new TableColumn<>("Arrival offset");
		arrivalColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
				data.getValue().startOffsetSeconds() + " sec"));
		arrivalColumn.setPrefWidth(175);
		TableColumn<Reservation, String> durationColumn = new TableColumn<>("Duration");
		durationColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
				data.getValue().durationSeconds() + " sec"));
		durationColumn.setPrefWidth(160);
		List<TableColumn<Reservation, ?>> reservationColumns =
				List.of(vehicleColumn, spaceColumn, arrivalColumn, durationColumn);
		reservationTable.getColumns().setAll(reservationColumns);

		Label listTitle = new Label("Current reservations");
		listTitle.getStyleClass().add("section-title");
		Label count = new Label();
		count.getStyleClass().add("reservation-count");
		Runnable refreshReservations = () -> {
			List<Reservation> reservations = reservationManager.all();
			reservationTable.getItems().setAll(reservations);
			count.setText(reservations.size() + (reservations.size() == 1 ? " reservation" : " reservations"));
			double tableHeight = reservations.isEmpty() ? 158 : Math.min(330, 48 + reservations.size() * 44);
			reservationTable.setPrefHeight(tableHeight);
			reservationTable.setMinHeight(Math.min(tableHeight, 180));
		};
		refreshReservations.run();
		HBox listHeader = new HBox(12, listTitle, new Region(), count);
		HBox.setHgrow(listHeader.getChildren().get(1), Priority.ALWAYS);
		listHeader.setAlignment(Pos.CENTER_LEFT);
		VBox listCard = new VBox(14, listHeader, reservationTable);
		listCard.getStyleClass().addAll("panel", "reservation-card");

		cancelButton.disableProperty().bind(reservationTable.getSelectionModel().selectedItemProperty().isNull());
		reservationTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
				cancelHint.setText(newValue == null
						? "Select a reservation below to enable cancellation."
						: "The selected reservation will be cancelled."));
		cancelButton.setOnAction(event -> {
			Reservation selected = reservationTable.getSelectionModel().getSelectedItem();
			if (selected != null && reservationManager.cancel(selected.id())) {
				setReservationFeedback(status, "Reservation cancelled.", true);
				refreshReservations.run();
				refreshParkingLotState();
			} else {
				setReservationFeedback(status, "Select a reservation in the table to cancel it.", false);
			}
		});

		AtomicBoolean submissionAttempted = new AtomicBoolean();
		Runnable validateForm = () -> {
			boolean vehicleValid = !vehicleField.getText().isBlank();
			boolean arrivalValid = isValidSpinnerInput(startSpinner, 0, 3600);
			boolean durationValid = isValidSpinnerInput(durationSpinner, 1, 3600);
			boolean formValid = vehicleValid && arrivalValid && durationValid;
			reserveButton.setDisable(submissionAttempted.get() && !formValid);
			if (!submissionAttempted.get()) {
				return;
			}
			if (!vehicleValid) {
				setReservationFeedback(status, "Enter a vehicle ID or name to reserve a space.", false);
			} else if (!arrivalValid || !durationValid) {
				setReservationFeedback(status,
						"Use whole seconds: arrival 0–3600 and duration 1–3600.", false);
			} else if (status.getStyleClass().contains("reservation-feedback-error")) {
				status.setText("");
				status.getStyleClass().setAll("reservation-feedback");
			}
		};
		vehicleField.textProperty().addListener((observable, oldValue, newValue) -> validateForm.run());
		startSpinner.getEditor().textProperty().addListener((observable, oldValue, newValue) -> validateForm.run());
		durationSpinner.getEditor().textProperty().addListener((observable, oldValue, newValue) -> validateForm.run());
		validateForm.run();

		reserveButton.setOnAction(event -> {
			submissionAttempted.set(true);
			validateForm.run();
			Integer startOffset = readSpinnerValue(startSpinner, 0, 3600);
			Integer duration = readSpinnerValue(durationSpinner, 1, 3600);
			if (vehicleField.getText().isBlank() || startOffset == null || duration == null) {
				validateForm.run();
				return;
			}
			Optional<Reservation> reservation;
			if (preferredSpaceNumber > 0) {
				Parking.SpaceState current = findSpace(currentSpaceStates(), preferredSpaceNumber);
				if (current == null || current.status() != Parking.SpaceStatus.AVAILABLE) {
					setReservationFeedback(status,
							"This space is no longer available. Close this window and choose another space.", false);
					refreshParkingLotState();
					return;
				}
				reservation = reservationManager.reserveForSpace(vehicleField.getText(), preferredSpaceNumber,
						startOffset, duration);
			} else {
				reservation = reservationManager.reserve(vehicleField.getText(), startOffset, duration);
			}
			if (reservation.isEmpty()) {
				setReservationFeedback(status, preferredSpaceNumber > 0
						? "That space is already booked for part of this interval. Adjust the time or select another space."
						: "No space is available for that entire interval. Choose a different time.", false);
				return;
			}
			Reservation created = reservation.get();
			refreshReservations.run();
			refreshParkingLotState();
			vehicleField.clear();
			setReservationFeedback(status,
					"Space S" + String.format(Locale.ROOT, "%02d", created.slotNumber())
							+ " reserved for " + created.vehicleId() + ".", true);
			vehicleField.requestFocus();
		});

		VBox content = new VBox(18, dialogHeader, formCard, listCard);
		content.getStyleClass().add("reservation-dialog-content");
		content.setPadding(new Insets(22));
		ScrollPane scrollContent = new ScrollPane(content);
		scrollContent.setFitToWidth(true);
		scrollContent.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollContent.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		scrollContent.getStyleClass().add("reservation-dialog-scroll");
		dialog.getDialogPane().setContent(scrollContent);
		Button footerCloseButton = (Button) dialog.getDialogPane().lookupButton(closeActionType);
		footerCloseButton.getStyleClass().add("secondary-button");
		footerCloseButton.setAccessibleText("Close Manage Reservations");
		dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			if (event.getCode() == KeyCode.ESCAPE) {
				event.consume();
				closeReservationDialog(dialog, owner);
			}
		});
		dialog.setOnShown(event -> {
			dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
			dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
			vehicleField.requestFocus();
		});
		dialog.showAndWait();
	}

	private void closeReservationDialog(Dialog<?> dialog, Window owner) {
		if (dialog.getDialogPane().getScene() != null) {
			Window dialogWindow = dialog.getDialogPane().getScene().getWindow();
			if (dialogWindow instanceof Stage dialogStage && dialogWindow != owner) {
				dialogStage.close();
				return;
			}
		}
		dialog.hide();
	}

	private void confirmReservation(ReservationView.ConfirmationData data) {
		DateTimeFormatter dateFile = DateTimeFormatter.ofPattern("yyyyMMdd");
		DateTimeFormatter timeFile = DateTimeFormatter.ofPattern("hh:mm a");
		DateTimeFormatter dateDisplay = DateTimeFormatter.ofPattern("dd MMM yyyy");
		String bookingId = "PW-" + data.date().format(dateFile) + "-"
				+ String.format(Locale.ROOT, "%03d", bookingSequence.incrementAndGet());

		String startTimeStr = data.startTime().format(timeFile);
		String endTimeStr = data.startTime().plusHours(data.durationHours()).format(timeFile);

		BookingRecord booking = new BookingRecord(
				bookingId, data.carParkId(), data.carParkName(), data.slotLabel(),
				data.date().format(dateDisplay), startTimeStr, endTimeStr,
				data.durationHours(), data.totalCost(), "Confirmed");
		carParkRepository.addBooking(booking);

		if (carParkRepository.getActiveLot() != null
				&& carParkRepository.getActiveLot().id().equals(data.carParkId())) {
			int offsetSeconds = 0;
			if (simulationState == SimulationState.RUNNING && parking != null) {
				Instant simStart = Instant.ofEpochMilli(simulationStartedNanos / 1_000_000L);
				Instant bookingStart = Instant.from(
						LocalDateTime.of(data.date(), data.startTime()).atZone(java.time.ZoneId.systemDefault()).toInstant());
				long computed = ChronoUnit.SECONDS.between(simStart, bookingStart);
				offsetSeconds = computed < 0 ? 0 : (int) Math.min(computed, Integer.MAX_VALUE);
			}
			try {
				carParkRepository.getActiveLot(); // ensure lot present
				reservationManager.reserveForSpace("Web-" + bookingId, data.slotNumber(),
						offsetSeconds, data.durationHours() * 3600);
			} catch (IllegalArgumentException ex) {
				// Capacity too small for this slot; the BookingRecord is still stored.
				LOGGER.log(Level.WARNING, "Could not register booking {0} with reservation manager: {1}",
						new Object[]{bookingId, ex.getMessage()});
			}
		}

		if (dashboardView != null) {
			dashboardView.refresh();
		}
		if (parkingLotsView != null) {
			parkingLotsView.refresh();
		}
		reservationView.refresh();
	}

	private boolean isValidSpinnerInput(Spinner<Integer> spinner, int minimum, int maximum) {
		try {
			int value = Integer.parseInt(spinner.getEditor().getText().trim());
			return value >= minimum && value <= maximum;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	private void setReservationFeedback(Label label, String message, boolean success) {
		label.setText(message);
		label.getStyleClass().setAll("reservation-feedback",
				success ? "reservation-feedback-success" : "reservation-feedback-error");
	}

	private Label formLabel(String text) {
		Label label = new Label(text);
		label.getStyleClass().add("field-label");
		return label;
	}

	private VBox createReservationEmptyState() {
		Label icon = new Label("▣");
		icon.getStyleClass().add("reservation-empty-icon");
		Label title = new Label("No reservations yet");
		title.getStyleClass().add("reservation-empty-title");
		Label description = new Label("Create a reservation above to see its assigned space and time here.");
		description.getStyleClass().add("section-caption");
		description.setWrapText(true);
		VBox empty = new VBox(7, icon, title, description);
		empty.setAlignment(Pos.CENTER);
		empty.setMaxWidth(340);
		return empty;
	}

	private void launchLiveDashboard() {
		launchButton.setDisable(true);
		String phase = "stopping the previous run";
		try {
			if (simulationLaunched) {
				stopSimulation();
			}
			phase = "preparing Dashboard views";
			prepareLiveDashboard();
			phase = "initializing parking, strategy, forecast, and arrival schedule";
			startSimulation();
			phase = "attaching live Dashboard components";
			attachDashboardPanels();
			phase = "opening Dashboard";
			appShell.showPage(AppShell.Page.DASHBOARD, dashboardView);
			dashboardView.setParking(parking);
			dashboardView.refresh();
			refreshDashboardStateIndicators();
		} catch (RuntimeException exception) {
			rollbackFailedLaunch();
			String detail = exception.getMessage() == null || exception.getMessage().isBlank()
					? exception.getClass().getSimpleName() : exception.getMessage();
			launchFeedback.setText("Simulation launch failed while " + phase + ": " + detail
					+ " Check the application log for details.");
			LOGGER.log(Level.SEVERE, "Simulation launch failed while " + phase, exception);
			appShell.showPage(AppShell.Page.SETTINGS, setupView);
		} finally {
			launchButton.setDisable(false);
		}
	}

	private void rollbackFailedLaunch() {
		for (PauseTransition transition : arrivalTransitions) {
			transition.stop();
		}
		arrivalTransitions.clear();
		if (parking != null) {
			parking.shutdown();
			parking = null;
		}
		for (Car car : carThreads) {
			car.interrupt();
		}
		carThreads.clear();
		simulationLaunched = false;
		simulationState = SimulationState.IDLE;
		if (dashboardView != null) {
			dashboardView.setParking(null);
			dashboardView.refresh();
		}
		refreshDashboardStateIndicators();
		refreshSimulationMonitor();
	}

	private void prepareLiveDashboard() {
		detachPredictionsAnalytics();
		parkingLotsPage = null;
		parkingLotsGridPanel = null;
		parkingLotsContentRow = null;
		spaceDetailsPanel = null;
		selectedSpaceNumber = -1;
		dashboardBody = new BorderPane();
		dashboardBody.getStyleClass().add("dashboard-body");
		dashboardBody.setTop(createHeader());
		dashboardCenter = createMainContent();
		dashboardWorkspaceScroll = new ScrollPane(dashboardCenter);
		dashboardWorkspaceScroll.setFitToWidth(true);
		dashboardWorkspaceScroll.getStyleClass().add("dashboard-scroll");
		dashboardBody.setCenter(dashboardWorkspaceScroll);
		dashboardBody.setBottom(createActivityPanel());
	}

	private VBox createHeader() {
		Button spawnButton = new Button("+ Spawn Vehicle");
		spawnButton.getStyleClass().add("secondary-button");
		spawnButton.setOnAction(event -> spawnVehicleOnTheFly());

		Button configButton = new Button("⚙ Settings");
		configButton.getStyleClass().add("secondary-button");
		configButton.setOnAction(event -> navigateTo(AppShell.Page.SETTINGS));

		Button analyticsButton = new Button("View analytics");
		analyticsButton.getStyleClass().add("secondary-button");
		analyticsButton.setText(analyticsExpandedOnDashboard ? "Hide insights" : "Show insights");
		analyticsButton.setOnAction(event -> toggleAnalytics(analyticsButton));

		Button resetButton = new Button("Restart");
		resetButton.getStyleClass().add("primary-button");
		resetButton.setOnAction(event -> restartSimulation());

		HBox actions = new HBox(10, spawnButton, configButton, analyticsButton, resetButton);
		actions.setAlignment(Pos.CENTER_RIGHT);

		Label eyebrow = new Label("PARKWISE · SMART CITY PARKING");
		eyebrow.getStyleClass().add("dashboard-eyebrow");
		Label title = new Label("Your parking, at a glance");
		title.getStyleClass().add("dashboard-welcome-title");
		Label subtitle = new Label("Live spaces, reservations, and demand insights for your parking lot.");
		subtitle.getStyleClass().add("dashboard-welcome-copy");
		VBox introduction = new VBox(6, eyebrow, title, subtitle);

		dashboardRunStateValue = new Label();
		dashboardRunStateValue.getStyleClass().add("dashboard-state-pill");
		Label strategyLabel = new Label("SYNC");
		strategyLabel.getStyleClass().add("dashboard-strategy-label");
		if (!strategyValue.getStyleClass().contains("dashboard-strategy-value")) {
			strategyValue.getStyleClass().add("dashboard-strategy-value");
		}
		HBox strategyBadge = new HBox(7, strategyLabel, strategyValue);
		strategyBadge.getStyleClass().add("dashboard-strategy-badge");
		strategyBadge.setAlignment(Pos.CENTER);
		HBox statusBadges = new HBox(8, dashboardRunStateValue, strategyBadge);
		statusBadges.setAlignment(Pos.CENTER_RIGHT);
		VBox headerActions = new VBox(12, statusBadges, actions);
		headerActions.setAlignment(Pos.CENTER_RIGHT);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox welcome = new HBox(20, introduction, spacer, headerActions);
		welcome.setAlignment(Pos.CENTER_LEFT);
		welcome.setMaxWidth(Double.MAX_VALUE);
		welcome.getStyleClass().add("dashboard-welcome");

		HBox metrics = new HBox(12,
				dashboardMetricCard("TOTAL CAPACITY", totalSlotsValue, "Spaces in this car park", "▦", "blue"),
				dashboardMetricCard("OCCUPIED", occupiedValue, "In use right now", "↗", "red"),
				dashboardMetricCard("AVAILABLE", availableValue, "Ready for vehicles", "✓", "green"),
				dashboardMetricCard("RESERVATIONS", reservedValue, "Unoccupied with future bookings", "◷", "amber"));
		metrics.getStyleClass().addAll("metrics-row", "dashboard-kpis");
		VBox top = new VBox(12, welcome, metrics);
		top.setPadding(new Insets(0, 0, 14, 0));
		refreshDashboardStateIndicators();
		return top;
	}

	private VBox dashboardMetricCard(String title, Label value, String caption, String icon, String tone) {
		Label iconLabel = new Label(icon);
		iconLabel.getStyleClass().addAll("dashboard-kpi-icon", "dashboard-kpi-icon-" + tone);
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("metric-title");
		Label captionLabel = new Label(caption);
		captionLabel.getStyleClass().add("metric-caption");
		captionLabel.setWrapText(true);
		VBox text = new VBox(4, titleLabel, value, captionLabel);
		HBox.setHgrow(text, Priority.ALWAYS);
		HBox content = new HBox(12, iconLabel, text);
		content.setAlignment(Pos.CENTER_LEFT);
		VBox card = new VBox(content);
		card.getStyleClass().addAll("metric-card", "dashboard-kpi-card");
		card.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(card, Priority.ALWAYS);
		card.setMinWidth(150);
		return card;
	}

	private void refreshDashboardStateIndicators() {
		if (dashboardRunStateValue != null) {
			dashboardRunStateValue.setText(switch (simulationState) {
				case IDLE -> "IDLE";
				case RUNNING -> "SIMULATION LIVE";
				case STOPPED -> "STOPPED";
			});
			dashboardRunStateValue.getStyleClass().removeAll("dashboard-state-idle", "dashboard-state-running",
					"dashboard-state-stopped");
			dashboardRunStateValue.getStyleClass().add(switch (simulationState) {
				case IDLE -> "dashboard-state-idle";
				case RUNNING -> "dashboard-state-running";
				case STOPPED -> "dashboard-state-stopped";
			});
		}
		if (dashboardActivityStateValue != null) {
			dashboardActivityStateValue.setText(switch (simulationState) {
				case IDLE -> "READY";
				case RUNNING -> "LIVE";
				case STOPPED -> "PAUSED";
			});
			dashboardActivityStateValue.getStyleClass().removeAll("activity-state-idle", "activity-state-running",
					"activity-state-stopped");
			dashboardActivityStateValue.getStyleClass().add(switch (simulationState) {
				case IDLE -> "activity-state-idle";
				case RUNNING -> "activity-state-running";
				case STOPPED -> "activity-state-stopped";
			});
		}
	}

	private VBox createMetricCard(String title, Label value, String caption) {
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("metric-title");
		Label captionLabel = new Label(caption);
		captionLabel.getStyleClass().add("metric-caption");
		VBox card = new VBox(8, titleLabel, value, captionLabel);
		card.getStyleClass().add("metric-card");
		card.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(card, Priority.ALWAYS);
		card.setMinWidth(120);
		return card;
	}

	private HBox createMainContent() {
		Label lotTitle = new Label("Live parking occupancy");
		lotTitle.getStyleClass().add("section-title");
		Label lotSubtitle = new Label("Space states and vehicle IDs update from the running simulation.");
		lotSubtitle.getStyleClass().add("section-caption");
		HBox legend = new HBox(8, legendDot("available-dot"), new Label("Available"),
				legendDot("occupied-dot"), new Label("Occupied"), legendDot("reserved-dot"), new Label("Reserved"));
		legend.getStyleClass().add("legend");
		legend.setAlignment(Pos.CENTER_LEFT);

		dashboardOccupancySummary = new Label("0 of 0 occupied");
		dashboardOccupancySummary.getStyleClass().add("occupancy-summary-value");
		Label utilizationLabel = new Label("LOT UTILIZATION");
		utilizationLabel.getStyleClass().add("metric-title");
		dashboardOccupancyProgress = new ProgressBar(0);
		dashboardOccupancyProgress.setMaxWidth(Double.MAX_VALUE);
		dashboardOccupancyProgress.getStyleClass().add("dashboard-occupancy-progress");
		HBox utilizationHeader = new HBox(10, utilizationLabel, new Region(), dashboardOccupancySummary);
		HBox.setHgrow(utilizationHeader.getChildren().get(1), Priority.ALWAYS);
		utilizationHeader.setAlignment(Pos.CENTER_LEFT);
		VBox utilization = new VBox(7, utilizationHeader, dashboardOccupancyProgress);
		utilization.getStyleClass().add("dashboard-utilization");
		HBox queueStats = new HBox(24,
				dashboardInlineStat("VEHICLES WAITING", waitingValue),
				dashboardInlineStat("AVERAGE QUEUE WAIT", averageWaitValue));
		queueStats.getStyleClass().add("dashboard-queue-stats");

		TilePane parkingGrid = createParkingGrid();
		spaceFilter = new ComboBox<>(FXCollections.observableArrayList(
				"All spaces", "Available", "Occupied", "Reserved"));
		spaceFilter.setValue("All spaces");
		spaceFilter.setMaxWidth(170);
		spaceFilter.setOnAction(event -> applySpaceFilter(currentSpaceStates()));
		HBox filterRow = new HBox(8, new Label("Filter:"), spaceFilter);
		filterRow.setAlignment(Pos.CENTER_LEFT);
		parkingGridLegendAndFilter = new HBox(12, legend, new Region(), filterRow);
		HBox.setHgrow(legend, Priority.ALWAYS);
		HBox.setHgrow(parkingGridLegendAndFilter.getChildren().get(1), Priority.ALWAYS);
		parkingGridLegendAndFilter.setAlignment(Pos.CENTER_LEFT);

		parkingLotPanel = new VBox(13, lotTitle, lotSubtitle, utilization, queueStats,
				parkingGridLegendAndFilter, parkingGrid);
		parkingLotPanel.getStyleClass().addAll("panel", "dashboard-occupancy-card");
		parkingLotPanel.setMinWidth(0);
		parkingLotPanel.setMaxHeight(Region.USE_PREF_SIZE);
		HBox.setHgrow(parkingLotPanel, Priority.ALWAYS);

		dashboardCenter = new HBox(18, parkingLotPanel, analyticsPanel);
		dashboardCenter.setAlignment(Pos.TOP_LEFT);
		VBox.setVgrow(dashboardCenter, Priority.ALWAYS);
		return dashboardCenter;
	}

	private VBox dashboardInlineStat(String title, Label value) {
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("metric-title");
		if (!value.getStyleClass().contains("dashboard-inline-value")) {
			value.getStyleClass().add("dashboard-inline-value");
		}
		return new VBox(3, titleLabel, value);
	}

	private TilePane createParkingGrid() {
		TilePane grid = new TilePane();
		parkingGrid = grid;
		slotTiles.clear();
		grid.setAlignment(Pos.CENTER_LEFT);
		grid.setPrefTileWidth(122);
		grid.setPrefTileHeight(104);
		grid.setHgap(12);
		grid.setVgap(14);
		grid.setPadding(new Insets(18));
		slots = new CustomRectangle[nbrPlaces];

		for (int index = 0; index < nbrPlaces; index++) {
			CustomRectangle slot = new CustomRectangle(index + 1);
			slot.setSelectionHandler(this::selectSpace);
			slots[index] = slot;
			slotTiles.put(index + 1, slot);
			grid.getChildren().add(slot);
		}
		applySpaceFilter(currentSpaceStates());
		return grid;
	}

	private List<Parking.SpaceState> currentSpaceStates() {
		return currentSpaceSnapshot().spaces();
	}

	private Parking.SpaceSnapshot currentSpaceSnapshot() {
		if (parking != null && simulationLaunched && parking.capacity() == nbrPlaces) {
			return parking.spaceSnapshot();
		}
		return Parking.plannedSpaceSnapshot(nbrPlaces, reservationManager.all());
	}

	public static List<Parking.SpaceState> filterSpaceStates(List<Parking.SpaceState> states, String filter) {
		if (filter == null || filter.equals("All spaces")) {
			return List.copyOf(states);
		}
		Parking.SpaceStatus wanted = switch (filter) {
			case "Available" -> Parking.SpaceStatus.AVAILABLE;
			case "Occupied" -> Parking.SpaceStatus.OCCUPIED;
			case "Reserved" -> Parking.SpaceStatus.RESERVED;
			default -> null;
		};
		return wanted == null ? List.copyOf(states)
				: states.stream().filter(state -> state.status() == wanted).toList();
	}

	private static Parking.SpaceState findSpace(List<Parking.SpaceState> states, int slotNumber) {
		return states.stream().filter(state -> state.slotNumber() == slotNumber).findFirst().orElse(null);
	}

	private void applySpaceFilter(List<Parking.SpaceState> states) {
		if (parkingGrid == null) {
			return;
		}
		List<Parking.SpaceState> filtered = filterSpaceStates(states,
				spaceFilter == null ? "All spaces" : spaceFilter.getValue());
		java.util.Set<Integer> visibleSlots = filtered.stream()
				.map(Parking.SpaceState::slotNumber).collect(java.util.stream.Collectors.toSet());
		for (Map.Entry<Integer, CustomRectangle> tile : slotTiles.entrySet()) {
			boolean visible = visibleSlots.contains(tile.getKey());
			tile.getValue().setVisible(visible);
			tile.getValue().setManaged(visible);
		}
	}

	private void refreshParkingLotState() {
		Parking.SpaceSnapshot snapshot = currentSpaceSnapshot();
		List<Parking.SpaceState> states = snapshot.spaces();
		int available = snapshot.availableCount();
		int occupied = snapshot.occupiedCount();
		int reserved = snapshot.reservedCount();
		for (Parking.SpaceState state : states) {
			CustomRectangle tile = slotTiles.get(state.slotNumber());
			if (tile != null) {
				Reservation reservation = state.reservations().stream().findFirst().orElse(null);
				tile.render(state.status(), state.vehicleId(), reservation,
						state.slotNumber() == selectedSpaceNumber);
			}
		}
		totalSlotsValue.setText(String.valueOf(snapshot.totalCount()));
		occupiedValue.setText(String.valueOf(occupied));
		availableValue.setText(String.valueOf(available));
		reservedValue.setText(String.valueOf(reserved));
		waitingValue.setText(String.valueOf(snapshot.waitingCount()));
		if (dashboardOccupancySummary != null) {
			dashboardOccupancySummary.setText(occupied + " of " + snapshot.totalCount() + " occupied");
		}
		if (dashboardOccupancyProgress != null) {
			dashboardOccupancyProgress.setProgress(snapshot.totalCount() == 0
					? 0 : (double) occupied / snapshot.totalCount());
		}
		if (lotTotalValue != null) lotTotalValue.setText(String.valueOf(snapshot.totalCount()));
		if (lotAvailableValue != null) lotAvailableValue.setText(String.valueOf(available));
		if (lotOccupiedValue != null) lotOccupiedValue.setText(String.valueOf(occupied));
		if (lotReservedValue != null) lotReservedValue.setText(String.valueOf(reserved));
		updateOccupancyChart(available, occupied, reserved);
		applySpaceFilter(states);
		refreshSelectedSpaceDetails(states);
		refreshDashboardStateIndicators();
		refreshSimulationMonitor();
		refreshPredictionSummary();
	}

	private void selectSpace(int slotNumber) {
		selectedSpaceNumber = slotNumber;
		if (selectedSpaceMessage != null) selectedSpaceMessage.setText("");
		refreshParkingLotState();
	}

	private void refreshSelectedSpaceDetails(List<Parking.SpaceState> states) {
		if (selectedSpaceTitle == null) {
			return;
		}
		Parking.SpaceState state = findSpace(states, selectedSpaceNumber);
		if (state == null) {
			selectedSpaceTitle.setText("Select a space");
			selectedSpaceStatus.setText("Choose a space in the grid to inspect its status.");
			selectedSpaceReservation.setText("");
			reserveSelectedButton.setDisable(true);
			return;
		}
		selectedSpaceTitle.setText(String.format("Space %02d", state.slotNumber()));
		selectedSpaceStatus.setText("Current status: " + state.status().displayName()
				+ (state.vehicleId() == null ? "" : " · " + state.vehicleId()));
		if (state.reservations().isEmpty()) {
			selectedSpaceReservation.setText("No unexpired reservation is scheduled for this space.");
		} else {
			Reservation reservation = state.reservations().get(0);
			selectedSpaceReservation.setText(String.format("Reserved for %s · %d–%d sec from simulation start%s",
					reservation.vehicleId(), reservation.startOffsetSeconds(), reservation.endOffsetSeconds(),
					state.reservations().size() > 1 ? " · " + (state.reservations().size() - 1) + " later booking(s)" : ""));
		}
		reserveSelectedButton.setDisable(state.status() != Parking.SpaceStatus.AVAILABLE);
	}

	private void reserveSelectedSpace() {
		Parking.SpaceState state = findSpace(currentSpaceStates(), selectedSpaceNumber);
		if (state == null || state.status() != Parking.SpaceStatus.AVAILABLE) {
			selectedSpaceMessage.setText("This space is no longer available. Choose an available space.");
			refreshParkingLotState();
			return;
		}
		showReservations(selectedSpaceNumber);
	}

	private void configureAnalyticsPanel() {
		Label analyticsTitle = new Label("Simulation analytics");
		analyticsTitle.getStyleClass().add("section-title");
		Label occupancyTitle = new Label("Lot occupancy");
		occupancyTitle.getStyleClass().add("chart-title");
		occupancyChart.setLegendVisible(true);
		occupancyChart.setLabelsVisible(true);
		occupancyChart.setPrefHeight(210);
		occupancyChart.getStyleClass().add("occupancy-chart");

		Label forecastTitle = new Label("Predicted occupancy vs actual");
		forecastTitle.getStyleClass().add("chart-title");
		Label forecastCaption = new Label("Forecast includes booked vehicles and scheduled arrivals; manual spawns are excluded.");
		forecastCaption.getStyleClass().add("section-caption");
		forecastCaption.setWrapText(true);
		NumberAxis forecastXAxis = new NumberAxis();
		forecastXAxis.setLabel("Time from start (seconds)");
		NumberAxis forecastYAxis = new NumberAxis();
		forecastYAxis.setLabel("Occupied spaces");
		forecastYAxis.setForceZeroInRange(true);
		forecastChart = new LineChart<>(forecastXAxis, forecastYAxis);
		forecastChart.setAnimated(false);
		forecastChart.setCreateSymbols(false);
		forecastChart.setPrefHeight(245);
		forecastChart.setLegendVisible(true);
		forecastChart.getStyleClass().add("forecast-chart");
		forecastOccupancySeries = new XYChart.Series<>();
		forecastOccupancySeries.setName("Predicted occupancy");
		actualOccupancySeries = new XYChart.Series<>();
		actualOccupancySeries.setName("Actual occupancy");
		forecastChart.getData().add(forecastOccupancySeries);
		forecastChart.getData().add(actualOccupancySeries);

		Label waitTitle = new Label("Average queue wait by completed visit");
		waitTitle.getStyleClass().add("chart-title");
		NumberAxis xAxis = new NumberAxis();
		xAxis.setLabel("Departures");
		NumberAxis yAxis = new NumberAxis();
		yAxis.setLabel("Seconds");
		LineChart<Number, Number> waitChart = new LineChart<>(xAxis, yAxis);
		waitChart.setLegendVisible(false);
		waitChart.setAnimated(false);
		waitChart.setCreateSymbols(true);
		waitChart.setPrefHeight(245);

		waitSeries = new XYChart.Series<>();
		waitSeries.setName("Average wait");
		waitChart.getData().add(waitSeries);
		VBox cityPredictionPanel = createHistoricalPredictionPanel();

		analyticsContent.getStyleClass().add("panel");
		analyticsPanel.setMinWidth(300);
		analyticsPanel.setPrefWidth(380);
		analyticsPanel.setMaxWidth(440);
		analyticsPanel.getStyleClass().add("dashboard-analytics-column");
		analyticsPanel.setVisible(true);
		analyticsPanel.setManaged(true);
		analyticsPanel.getChildren().setAll(analyticsContent);
		analyticsContent.setMaxWidth(Double.MAX_VALUE);
		analyticsContent.getChildren().addAll(analyticsTitle, occupancyTitle, occupancyChart,
				forecastTitle, forecastCaption, forecastChart, cityPredictionPanel, waitTitle, waitChart);
	}

	private void ensurePredictionsPage() {
		if (predictionsPageContent == null) {
			predictionCurrentOccupancyValue = metricValue("—");
			predictionPeakOccupancyValue = metricValue("—");
			predictionAvailableAtPeakValue = metricValue("—");
			HBox summary = new HBox(12,
					createMetricCard("CURRENT OCCUPANCY", predictionCurrentOccupancyValue, "Occupied / lot capacity"),
					createMetricCard("PREDICTED PEAK", predictionPeakOccupancyValue, "Forecast occupancy / capacity"),
					createMetricCard("AVAILABLE AT PEAK", predictionAvailableAtPeakValue, "Spaces free at forecast peak"));
			summary.getStyleClass().add("metrics-row");
			predictionCurrentCaption = new Label();
			predictionCurrentCaption.getStyleClass().add("section-caption");
			predictionCurrentCaption.setWrapText(true);
			predictionPeakHorizonCaption = new Label();
			predictionPeakHorizonCaption.getStyleClass().add("section-caption");
			predictionPeakHorizonCaption.setWrapText(true);
			VBox pageIntro = new VBox(5,
					new Label("Simulation occupancy outlook"),
					new Label("Compare live space use with the scheduled-arrival forecast and historical Birmingham demand."));
			pageIntro.getChildren().get(0).getStyleClass().add("section-title");
			pageIntro.getChildren().get(1).getStyleClass().add("section-caption");
			((Label) pageIntro.getChildren().get(1)).setWrapText(true);
			predictionsPageContent = new VBox(14, pageIntro, summary,
					predictionCurrentCaption, predictionPeakHorizonCaption);
			predictionsPageContent.getStyleClass().add("predictions-page-content");
			predictionsPageContent.setMaxWidth(Double.MAX_VALUE);
		}
		if (!predictionsPageContent.getChildren().contains(analyticsPanel)) {
			predictionsPageContent.getChildren().add(analyticsPanel);
		}
	}

	private void refreshPredictionSummary() {
		if (predictionCurrentOccupancyValue == null) {
			return;
		}
		if (parking == null) {
			predictionCurrentOccupancyValue.setText("—");
			predictionCurrentCaption.setText("No live simulation occupancy is available.");
		} else {
			Parking.SpaceSnapshot snapshot = currentSpaceSnapshot();
			predictionCurrentOccupancyValue.setText(snapshot.occupiedCount() + " / " + snapshot.totalCount());
			predictionCurrentCaption.setText("Live snapshot · " + snapshot.occupiedCount()
					+ " of " + snapshot.totalCount() + " spaces occupied.");
		}

		if (forecastOccupancySeries == null || forecastOccupancySeries.getData().isEmpty() || parking == null) {
			predictionPeakOccupancyValue.setText("—");
			predictionAvailableAtPeakValue.setText("—");
			predictionPeakHorizonCaption.setText("A simulation forecast will appear here after a run starts.");
			return;
		}
		XYChart.Data<Number, Number> peak = forecastOccupancySeries.getData().stream()
				.max(java.util.Comparator.comparingDouble(point -> point.getYValue().doubleValue()))
				.orElse(null);
		if (peak == null) {
			predictionPeakOccupancyValue.setText("—");
			predictionAvailableAtPeakValue.setText("—");
			predictionPeakHorizonCaption.setText("A simulation forecast is not available.");
			return;
		}
		int capacity = currentSpaceSnapshot().totalCount();
		int predictedOccupied = (int) Math.round(peak.getYValue().doubleValue());
		predictionPeakOccupancyValue.setText(predictedOccupied + " / " + capacity);
		predictionAvailableAtPeakValue.setText(String.valueOf(capacity - predictedOccupied));
		predictionPeakHorizonCaption.setText(String.format(Locale.ROOT,
				"Peak forecast occurs at +%.1f sec from simulation start. Forecasts include scheduled vehicles; manual spawns are not predicted.",
				peak.getXValue().doubleValue()));
	}

	private void setPredictionsAnalyticsLayout(boolean predictionsPage) {
		analyticsPanel.setMinWidth(predictionsPage ? 0 : 300);
		analyticsPanel.setPrefWidth(predictionsPage ? 900 : 380);
		analyticsPanel.setMaxWidth(predictionsPage ? Double.MAX_VALUE : 440);
		occupancyChart.setPrefHeight(predictionsPage ? 280 : 210);
		forecastChart.setPrefHeight(predictionsPage ? 400 : 245);
	}

	private VBox createHistoricalPredictionPanel() {
		Label title = new Label("Smart City demand prediction");
		title.getStyleClass().add("chart-title");
		Label description = new Label("Typical Birmingham occupancy by car park, weekday, and half-hour.");
		description.getStyleClass().add("section-caption");
		description.setWrapText(true);
		if (historicalOccupancyPredictor == null) {
			Label unavailable = new Label(historicalDataUnavailableMessage);
			unavailable.getStyleClass().add("prediction-unavailable");
			unavailable.setWrapText(true);
			VBox unavailablePanel = new VBox(8, title, description, unavailable);
			unavailablePanel.getStyleClass().addAll("metric-card", "historical-prediction-panel");
			return unavailablePanel;
		}

		historicalCarParkSelector = new ComboBox<>();
		historicalCarParkSelector.getItems().setAll(historicalOccupancyPredictor.carParkIds());
		historicalCarParkSelector.setMaxWidth(Double.MAX_VALUE);
		historicalCarParkSelector.setPromptText("Car park ID");
		if (historicalCarParkSelector.getItems().contains("BHMBCCMKT01")) {
			historicalCarParkSelector.setValue("BHMBCCMKT01");
		} else if (!historicalCarParkSelector.getItems().isEmpty()) {
			historicalCarParkSelector.getSelectionModel().selectFirst();
		}

		historicalDaySelector = new ComboBox<>(FXCollections.observableArrayList(DayOfWeek.values()));
		historicalDaySelector.setMaxWidth(Double.MAX_VALUE);
		historicalDaySelector.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(DayOfWeek day) {
				return day == null ? "" : day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
			}

			@Override
			public DayOfWeek fromString(String value) {
				return value == null ? null : DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
			}
		});
		historicalDaySelector.setValue(DayOfWeek.MONDAY);

		List<LocalTime> availableTimes = new ArrayList<>();
		for (int minutes = 8 * 60; minutes <= 16 * 60 + 30; minutes += 30) {
			availableTimes.add(LocalTime.of(minutes / 60, minutes % 60));
		}
		historicalTimeSelector = new ComboBox<>(FXCollections.observableArrayList(availableTimes));
		historicalTimeSelector.setMaxWidth(Double.MAX_VALUE);
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
		historicalTimeSelector.setValue(LocalTime.of(10, 0));

		historicalPredictionLabel = new Label();
		historicalPredictionLabel.getStyleClass().add("section-title");
		historicalPredictionLabel.setWrapText(true);
		Label dataNote = new Label("Historical benchmark only; this is not a live city sensor feed.");
		dataNote.getStyleClass().add("section-caption");
		dataNote.setWrapText(true);
		Label modelExplanation = new Label("Expected occupied and available are rounded counts from the selected occupancy profile. Held-out MAE compares the weekday/time profile with the car-park average; lower is closer to held-out observations.");
		modelExplanation.getStyleClass().add("prediction-model-explanation");
		modelExplanation.setWrapText(true);
		historicalModelScoreLabel = new Label();
		historicalModelScoreLabel.getStyleClass().add("section-caption");
		historicalModelScoreLabel.setWrapText(true);

		historicalCarParkSelector.setOnAction(event -> refreshHistoricalPrediction());
		historicalDaySelector.setOnAction(event -> refreshHistoricalPrediction());
		historicalTimeSelector.setOnAction(event -> refreshHistoricalPrediction());
		VBox panel = new VBox(8, title, description,
				new Label("Birmingham car park"), historicalCarParkSelector,
				new Label("Target weekday"), historicalDaySelector,
				new Label("Target time"), historicalTimeSelector,
				historicalPredictionLabel, modelExplanation, historicalModelScoreLabel, dataNote);
		panel.getStyleClass().addAll("metric-card", "historical-prediction-panel");
		refreshHistoricalPrediction();
		return panel;
	}

	private void refreshHistoricalPrediction() {
		if (historicalOccupancyPredictor == null || historicalCarParkSelector == null
				|| historicalDaySelector == null || historicalTimeSelector == null
				|| historicalPredictionLabel == null || historicalModelScoreLabel == null) {
			return;
		}
		String carPark = historicalCarParkSelector.getValue();
		DayOfWeek day = historicalDaySelector.getValue();
		LocalTime time = historicalTimeSelector.getValue();
		if (carPark == null || day == null || time == null) {
			historicalPredictionLabel.setText("Select a car park, weekday, and time.");
			return;
		}
		HistoricalOccupancyPredictor.Prediction prediction = historicalOccupancyPredictor
				.predict(carPark, day, time).orElse(null);
		if (prediction == null) {
			historicalPredictionLabel.setText("No historical observations are available for this selection.");
			return;
		}
		String weekday = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		historicalPredictionLabel.setText(String.format(Locale.ROOT,
				"Typical for %s %s: %.1f%% occupancy\nAbout %d of %d occupied; %d available",
				weekday, time.format(DateTimeFormatter.ofPattern("HH:mm")),
				prediction.occupancyRate() * 100, prediction.expectedOccupied(), prediction.capacity(),
				prediction.expectedAvailable()));
		HistoricalOccupancyPredictor.ValidationMetrics metrics = historicalOccupancyPredictor.validationMetrics();
		historicalModelScoreLabel.setText(String.format(Locale.ROOT,
				"Held-out MAE: %.1f%% (weekday/time profile) vs %.1f%% (car-park average); %d observations.",
				metrics.profileMaePercent(), metrics.baselineMaePercent(), metrics.observationCount()));
	}

	private VBox createActivityPanel() {
		Label title = new Label("Recent activity");
		title.getStyleClass().add("section-title");
		dashboardActivityStateValue = new Label();
		dashboardActivityStateValue.getStyleClass().add("dashboard-activity-state");
		HBox titleRow = new HBox(10, title, new Region(), dashboardActivityStateValue);
		HBox.setHgrow(titleRow.getChildren().get(1), Priority.ALWAYS);
		titleRow.setAlignment(Pos.CENTER_LEFT);
		dashboardActivityEntries = new VBox(6);
		dashboardActivityEntries.getStyleClass().add("dashboard-activity-entries");
		dashboardActivityEntries.setMinHeight(112);
		dashboardActivityEntries.setPrefHeight(138);
		refreshDashboardActivityEntries();
		VBox panel = new VBox(10, titleRow, dashboardActivityEntries);
		panel.getStyleClass().addAll("panel", "dashboard-activity-panel");
		BorderPane.setMargin(panel, new Insets(0));
		refreshDashboardStateIndicators();
		return panel;
	}

	private Region legendDot(String styleClass) {
		Region dot = new Region();
		dot.getStyleClass().add(styleClass);
		return dot;
	}

	private void toggleAnalytics(Button button) {
		boolean show = !analyticsPanel.isVisible();
		analyticsExpandedOnDashboard = show;
		analyticsPanel.setVisible(show);
		analyticsPanel.setManaged(show);
		button.setText(show ? "Hide insights" : "Show insights");
	}

	private void startSimulation() {
		if (simulationState == SimulationState.RUNNING) {
			return;
		}
		long runStartedNanos = System.nanoTime();
		PreparedSimulation prepared = prepareSimulation(nbrPlaces, numCars, syncStrategy,
				arrivalWindowSeconds, parkingDurationSeconds, simulationSeed, reservationManager.all(),
				new ParkingListener() {
					@Override
					public void carParked(Parking source, int place, String carName, int occupied, int waiting, long waitMillis) {
						Main.this.carParked(source, place, carName, occupied, waiting, waitMillis);
						carParkRepository.carParked(source, place, carName, occupied, waiting, waitMillis);
						refreshAllPresentationViews();
					}

					@Override
					public void carLeft(Parking source, int place, String carName, int occupied, int waiting,
							double averageWaitMillis, int exits) {
						Main.this.carLeft(source, place, carName, occupied, waiting, averageWaitMillis, exits);
						carParkRepository.carLeft(source, place, carName, occupied, waiting, averageWaitMillis, exits);
						refreshAllPresentationViews();
					}

					@Override
					public void parkingMetrics(Parking source, int occupied, int waiting) {
						Main.this.parkingMetrics(source, occupied, waiting);
						carParkRepository.parkingMetrics(source, occupied, waiting);
						refreshAllPresentationViews();
					}
				},
				runStartedNanos);
		long runParkingDurationMillis = parkingDurationSeconds * 1000L;

		if (waitSeries != null) {
			waitSeries.getData().clear();
		}
		if (forecastOccupancySeries != null) {
			forecastOccupancySeries.getData().clear();
		}
		if (actualOccupancySeries != null) {
			actualOccupancySeries.getData().clear();
			actualOccupancySeries.getData().add(new XYChart.Data<>(0, 0));
		}
		activityEntries.clear();
		strategyValue.setText(syncStrategy == 1 ? "SEMAPHORE" : "MUTEX");
		waitingValue.setText("0");
		averageWaitValue.setText("0.00 s");
		completedVehicleExits = 0;
		averageQueueWaitMillis = 0;
		lastReportedWaitingCount = 0;
		elapsedAtStopMillis = 0;
		if (predictionsView != null) {
			predictionsView.resetActualData();
		}

		simulationStartedNanos = runStartedNanos;
		if (forecastOccupancySeries != null) {
			for (OccupancyForecast.Point point : prepared.forecast()) {
				forecastOccupancySeries.getData().add(new XYChart.Data<>(point.timeMillis() / 1000.0, point.occupied()));
			}
		}

		parking = prepared.parking();
		simulationLaunched = true;
		simulationState = SimulationState.RUNNING;
		Parking currentRun = parking;
		refreshParkingLotState();
		refreshDashboardStateIndicators();
		refreshSimulationMonitor();
		logEvent(currentRun, "Simulation started · " + numCars + " cars · " + nbrPlaces + " slots · "
				+ reservationManager.all().size() + " reservations · seed " + simulationSeed);
		runOnFxThread(() -> {
			if (simulationMonitorView != null) {
				simulationMonitorView.addLogRow("System", "INFO",
						"Simulation started with " + numCars + " cars, " + nbrPlaces + " slots using "
								+ (syncStrategy == 1 ? "Semaphore" : "Mutex") + " strategy.");
			}
		});

		spawnedCarCount = numCars;
		for (int index = 0; index < numCars; index++) {
			int carNumber = index + 1;
			PauseTransition arrival = new PauseTransition(Duration.millis(prepared.arrivalDelays().get(index)));
			arrival.setOnFinished(event -> {
				String vehicleId = "Car " + carNumber;
				logEvent(currentRun, vehicleId + " arrived and is attempting to park.");
				runOnFxThread(() -> {
					if (simulationMonitorView != null) {
						simulationMonitorView.addLogRow(vehicleId, "INFO",
								vehicleId + " arrived and requested "
										+ (syncStrategy == 1 ? "Semaphore" : "Mutex") + " permit.");
					}
				});
				Car car = new Car(vehicleId, currentRun, runParkingDurationMillis);
				carThreads.add(car);
				car.start();
			});
			arrivalTransitions.add(arrival);
			arrival.play();
		}
		for (Reservation reservation : reservationManager.all()) {
			PauseTransition arrival = new PauseTransition(Duration.seconds(reservation.startOffsetSeconds()));
			arrival.setOnFinished(event -> {
				logEvent(currentRun, "Reserved vehicle " + reservation.vehicleId() + " arrived for Space "
						+ reservation.slotNumber() + ".");
				Car reservedCar = new Car(reservation.vehicleId(), currentRun,
						reservation.durationSeconds() * 1000L, reservation);
				carThreads.add(reservedCar);
				reservedCar.start();
			});
			arrivalTransitions.add(arrival);
			arrival.play();
		}
	}

	static PreparedSimulation prepareSimulation(int capacity, int vehicleCount, int syncStrategy,
			int arrivalWindowSeconds, int parkingDurationSeconds, int seed, List<Reservation> reservations,
			ParkingListener listener, long startedNanos) {
		if (syncStrategy != 1 && syncStrategy != 2) {
			throw new IllegalArgumentException("Synchronization strategy must be Semaphore (1) or Mutex (2)");
		}
		List<Integer> arrivalDelays;
		try {
			arrivalDelays = ArrivalSchedule.generateDelaysMillis(vehicleCount, arrivalWindowSeconds, seed);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Arrival schedule initialization failed: " + exception.getMessage(), exception);
		}
		List<OccupancyForecast.Point> forecast;
		try {
			forecast = OccupancyForecast.predict(arrivalDelays,
					parkingDurationSeconds * 1000, capacity, reservations);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Forecast initialization failed: " + exception.getMessage(), exception);
		}
		Parking parking;
		try {
			parking = new Parking(capacity, new SemaphoreStrategy(), listener, syncStrategy,
					reservations, startedNanos);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Parking initialization failed: " + exception.getMessage(), exception);
		}
		return new PreparedSimulation(parking, arrivalDelays, forecast);
	}

	record PreparedSimulation(Parking parking, List<Integer> arrivalDelays,
			List<OccupancyForecast.Point> forecast) {
		PreparedSimulation {
			arrivalDelays = List.copyOf(arrivalDelays);
			forecast = List.copyOf(forecast);
		}
	}

	private void spawnVehicleOnTheFly() {
		if (parking == null || simulationState != SimulationState.RUNNING) return;
		spawnedCarCount++;
		Car extraCar = new Car("Car " + spawnedCarCount + " (Extra)", parking, parkingDurationSeconds * 1000L);
		carThreads.add(extraCar);
		extraCar.start();
		logEvent(parking, "Manually spawned Car " + spawnedCarCount + " into queue.");
	}

	private void restartSimulation() {
		stopSimulation();
		try {
			startSimulation();
		} catch (RuntimeException exception) {
			rollbackFailedLaunch();
			showSimulationStartError(exception);
		}
	}

	private void stopSimulation() {
		if (simulationState == SimulationState.RUNNING) {
			elapsedAtStopMillis = java.util.concurrent.TimeUnit.NANOSECONDS
					.toMillis(System.nanoTime() - simulationStartedNanos);
			simulationState = SimulationState.STOPPED;
			logEventOnFxThread("Simulation stopped; active vehicle threads are shutting down.");
			if (simulationMonitorView != null) {
				simulationMonitorView.addLogRow("System", "WARN",
						"Simulation stopped; active vehicle threads are shutting down.");
			}
		}
		if (monitorElapsedTimeline != null) {
			monitorElapsedTimeline.stop();
		}
		for (PauseTransition transition : arrivalTransitions) {
			transition.stop();
		}
		if (parking != null) {
			parking.shutdown();
		}
		List<Car> stoppingCars = new ArrayList<>(carThreads);
		for (Car car : stoppingCars) {
			car.interrupt();
		}
		if (!stoppingCars.isEmpty()) {
			Thread cleanup = new Thread(() -> joinStoppingCars(stoppingCars), "parking-worker-cleanup");
			cleanup.setDaemon(true);
			cleanup.start();
		}
		carThreads.clear();
		arrivalTransitions.clear();
		if (dashboardView != null) {
			dashboardView.setParking(parking);
			dashboardView.refresh();
		}
		if (parkingLotsView != null) {
			parkingLotsView.refresh();
		}
		if (predictionsView != null) {
			predictionsView.refresh();
		}
		refreshDashboardStateIndicators();
		refreshSimulationMonitor();
	}

	private void joinStoppingCars(List<Car> stoppingCars) {
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
		for (Car car : stoppingCars) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				break;
			}
			long millis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining);
			int nanos = (int) (remaining - java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis));
			try {
				car.join(millis, nanos);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	@Override
	public void carParked(Parking source, int place, String carName, int occupied, int waiting, long waitMillis) {
		runOnFxThread(() -> {
			if (source != parking || simulationState != SimulationState.RUNNING
					|| slots == null || place < 0 || place >= slots.length) return;
			updateMetrics(averageWaitValue.getText());
			recordActualOccupancy(occupied);
			logEventOnFxThread(carName + " parked in Slot " + (place + 1) + " · waited " + formatDuration(waitMillis));
			if (simulationMonitorView != null) {
				simulationMonitorView.addLogRow(carName, "PARKED",
						carName + " acquired slot A" + String.format("%02d", place + 1));
				simulationMonitorView.setProcessedCount(completedVehicleExits);
				simulationMonitorView.refresh();
			}
		});
	}

	@Override
	public void carLeft(Parking source, int place, String carName, int occupied, int waiting, double averageWaitMillis, int exits) {
		runOnFxThread(() -> {
			if (source != parking || simulationState != SimulationState.RUNNING
					|| slots == null || place < 0 || place >= slots.length) return;
			String average = String.format("%.2f s", averageWaitMillis / 1000.0);
			completedVehicleExits = exits;
			this.averageQueueWaitMillis = averageWaitMillis;
			updateMetrics(average);
			recordActualOccupancy(occupied);
			if (waitSeries != null) {
				waitSeries.getData().add(new XYChart.Data<>(exits, averageWaitMillis / 1000.0));
				if (waitSeries.getData().size() > 30) {
					waitSeries.getData().remove(0);
				}
			}
			logEventOnFxThread(carName + " left Slot " + (place + 1));
			if (simulationMonitorView != null) {
				simulationMonitorView.addLogRow(carName, "RELEASED",
						carName + " released slot A" + String.format("%02d", place + 1));
				simulationMonitorView.setProcessedCount(completedVehicleExits);
				simulationMonitorView.refresh();
			}
		});
	}

	@Override
	public void parkingMetrics(Parking source, int occupied, int waiting) {
		runOnFxThread(() -> {
			if (source == parking && simulationState == SimulationState.RUNNING) {
				if (waiting != lastReportedWaitingCount) {
					logEventOnFxThread("Waiting vehicles: " + waiting + ".");
					lastReportedWaitingCount = waiting;
				}
				updateMetrics(averageWaitValue.getText());
				if (simulationMonitorView != null) {
					simulationMonitorView.refresh();
					if (waiting > 0) {
						simulationMonitorView.addLogRow("Worker pool", "WAITING",
								waiting + " vehicle(s) blocked on capacity pool.");
					}
				}
			}
		});
	}

	private void updateMetrics(String average) {
		refreshParkingLotState();
		averageWaitValue.setText(average);
	}

	private void updateOccupancyChart(int available, int occupied, int reserved) {
		occupancyChart.setData(FXCollections.observableArrayList(
				new PieChart.Data("Available", Math.max(0, available)),
				new PieChart.Data("Occupied", Math.max(0, occupied)),
				new PieChart.Data("Reserved", Math.max(0, reserved))));
	}

	private void recordActualOccupancy(int occupied) {
		if (actualOccupancySeries != null) {
			double seconds = (System.nanoTime() - simulationStartedNanos) / 1_000_000_000.0;
			actualOccupancySeries.getData().add(new XYChart.Data<>(seconds, occupied));
		}
	}

	private void logEvent(Parking source, String message) {
		runOnFxThread(() -> {
			if (source == parking && simulationState == SimulationState.RUNNING) {
				logEventOnFxThread(message);
			}
		});
	}

	private void logEventOnFxThread(String message) {
		activityEntries.add(0, LOG_TIME.format(LocalTime.now()) + "  " + message);
		if (activityEntries.size() > MAX_LOG_ENTRIES) {
			activityEntries.remove(MAX_LOG_ENTRIES, activityEntries.size());
		}
	}

	private void refreshDashboardActivityEntries() {
		if (dashboardActivityEntries == null) {
			return;
		}
		dashboardActivityEntries.getChildren().clear();
		if (activityEntries.isEmpty()) {
			Label emptyMessage = new Label("Simulation events will appear here when the run starts.");
			emptyMessage.getStyleClass().add("activity-entry-empty");
			dashboardActivityEntries.getChildren().add(emptyMessage);
			return;
		}
		activityEntries.stream().limit(5).map(message -> {
			Label entry = new Label(message);
			entry.getStyleClass().add("activity-entry");
			entry.setMaxWidth(Double.MAX_VALUE);
			return entry;
		}).forEach(dashboardActivityEntries.getChildren()::add);
	}

	private void runOnFxThread(Runnable action) {
		if (Platform.isFxApplicationThread()) {
			action.run();
		} else {
			Platform.runLater(action);
		}
	}

	private String formatDuration(long millis) {
		return millis < 1000 ? millis + " ms" : String.format("%.2f s", millis / 1000.0);
	}

	private static Label metricValue(String value) {
		Label label = new Label(value);
		label.getStyleClass().add("metric-value");
		return label;
	}

	@Override
	public void stop() {
		stopSimulation();
		if (monitorElapsedTimeline != null) {
			monitorElapsedTimeline.stop();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
