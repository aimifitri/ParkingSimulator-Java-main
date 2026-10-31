package com.example.ParkingSimulator;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * Screen 6 of the ParkWise redesign: the Concurrency &amp; Thread Monitor view.
 *
 * <p>The view is fully decoupled from the backend concurrency model. It reads
 * live data through a {@link Supplier<Parking>} and reports user actions
 * (start / stop / reset / strategy change) through callbacks. Structured
 * activity rows are pushed into the {@link TableView} log from the
 * {@link ParkingListener} callbacks via {@link #addLogRow}.
 */
public class SimulationMonitorView extends BorderPane {
	public record LogRow(String time, String threadId, String level, String message) {
	}

	private final Supplier<Parking> parkingSupplier;
	private final Runnable onStart;
	private final Runnable onStop;
	private final Runnable onReset;
	private final Consumer<Integer> onStrategyChange;
	private final Supplier<Long> startNanosSupplier;

	private Label stateValue;
	private Label elapsedValue;
	private Label capacityValue;
	private Label occupiedValue;
	private Label availableValue;
	private Label reservedValue;
	private Label waitingValue;
	private Label exitsValue;
	private Label threadsValue;
	private Label rateValue;
	private Label strategyValue;

	private Button startButton;
	private Button stopButton;
	private Button resetButton;

	private TableView<LogRow> logTable;
	private final ObservableList<LogRow> logRows = FXCollections.observableArrayList();
	private TextField logFilter;
	private int processedCount = 0;
	private long lastProcessedNanos = System.nanoTime();

	private Timeline elapsedTimeline;

	public SimulationMonitorView(Supplier<Parking> parkingSupplier,
			Runnable onStart,
			Runnable onStop,
			Runnable onReset,
			Consumer<Integer> onStrategyChange,
			Supplier<Long> startNanosSupplier) {
		this.parkingSupplier = parkingSupplier;
		this.onStart = onStart;
		this.onStop = onStop;
		this.onReset = onReset;
		this.onStrategyChange = onStrategyChange;
		this.startNanosSupplier = startNanosSupplier;
		getStyleClass().add("monitor-view");
		setPadding(new Insets(0));
		setCenter(buildPage());
		elapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refresh()));
		elapsedTimeline.setCycleCount(Timeline.INDEFINITE);
	}

	/** Refresh KPIs and space tiles from the live Parking model. Call on model events. */
	public void refresh() {
		Parking parking = parkingSupplier.get();
		if (parking == null) {
			stateValue.setText("IDLE");
			stateValue.getStyleClass().setAll("monitor-state-pill", "monitor-state-idle");
			capacityValue.setText("0");
			occupiedValue.setText("0");
			availableValue.setText("0");
			reservedValue.setText("0");
			waitingValue.setText("0");
			exitsValue.setText(String.valueOf(processedCount));
			threadsValue.setText("0");
			rateValue.setText("0 /s");
			strategyValue.setText("SEMAPHORE");
			return;
		}
		Parking.SpaceSnapshot snapshot = parking.spaceSnapshot();
		capacityValue.setText(String.valueOf(snapshot.totalCount()));
		occupiedValue.setText(String.valueOf(snapshot.occupiedCount()));
		availableValue.setText(String.valueOf(snapshot.availableCount()));
		reservedValue.setText(String.valueOf(snapshot.reservedCount()));
		waitingValue.setText(String.valueOf(snapshot.waitingCount()));
		int activeThreads = snapshot.occupiedCount() + snapshot.waitingCount();
		threadsValue.setText(String.valueOf(activeThreads));
		exitsValue.setText(String.valueOf(processedCount));
		double elapsed = elapsedSeconds();
		double rate = elapsed > 0 ? processedCount / elapsed : 0.0;
		rateValue.setText(String.format("%.1f /s", rate));
		elapsedValue.setText(String.format("%.1f s", elapsed));
	}

	public void setState(String state) {
		if (stateValue == null) {
			return;
		}
		stateValue.setText(state);
		stateValue.getStyleClass().removeAll("monitor-state-idle", "monitor-state-running", "monitor-state-stopped");
		String style = switch (state) {
			case "RUNNING" -> "monitor-state-running";
			case "STOPPED" -> "monitor-state-stopped";
			default -> "monitor-state-idle";
		};
		stateValue.getStyleClass().add(style);
		if ("RUNNING".equals(state)) {
			playElapsed();
		} else if ("STOPPED".equals(state) || "IDLE".equals(state)) {
			stopElapsed();
		}
	}

	/** Push a structured row into the activity log table (FX thread). */
	public void addLogRow(String threadId, String level, String message) {
		String time = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now());
		LogRow row = new LogRow(time, threadId, level, message);
		logRows.add(0, row);
		if (logRows.size() > 500) {
			logRows.remove(logRows.size() - 1);
		}
		applyLogFilter();
	}

	public void playElapsed() {
		if (elapsedTimeline != null) {
			elapsedTimeline.play();
		}
	}

	public void stopElapsed() {
		if (elapsedTimeline != null) {
			elapsedTimeline.stop();
		}
	}

	public void setStrategy(int syncStrategy) {
		if (strategyValue != null) {
			strategyValue.setText(syncStrategy == 1 ? "SEMAPHORE" : "MUTEX");
		}
	}

	public void setProcessedCount(int count) {
		this.processedCount = count;
	}

	private Node buildPage() {
		VBox root = new VBox(0);
		root.getStyleClass().add("monitor-root");
		root.setFillWidth(true);

		root.getChildren().add(buildControlHeader());
		root.getChildren().add(buildKpiRow());
		root.getChildren().add(buildHealthChips());
		root.getChildren().add(buildLogSection());

		VBox scrollWrapper = new VBox(root);
		scrollWrapper.getStyleClass().add("monitor-scroll-wrapper");
		return scrollWrapper;
	}

	private HBox buildControlHeader() {
		HBox bar = new HBox(12);
		bar.getStyleClass().add("monitor-control-bar");
		bar.setPadding(new Insets(16, 26, 12, 26));
		bar.setAlignment(Pos.CENTER_LEFT);

		ComboBox<String> presetSelector = new ComboBox<>(
				FXCollections.observableArrayList("Peak Hour - High Demand", "Normal Hours - Standard Demand",
						"Off-Peak Night"));
		presetSelector.setValue("Normal Hours - Standard Demand");
		presetSelector.setPromptText("Mode preset");
		presetSelector.setMaxWidth(260);

		startButton = new Button("Start Simulation");
		startButton.getStyleClass().add("monitor-primary-button");
		startButton.setMaxWidth(Double.MAX_VALUE);
		startButton.setOnAction(e -> {
			if (onStart != null) {
				onStart.run();
			}
			playElapsed();
		});

		stopButton = new Button("Stop Simulation");
		stopButton.getStyleClass().add("monitor-secondary-button");
		stopButton.setMaxWidth(Double.MAX_VALUE);
		stopButton.setOnAction(e -> {
			if (onStop != null) {
				onStop.run();
			}
			stopElapsed();
		});

		resetButton = new Button("Clear / Reset Run");
		resetButton.getStyleClass().add("monitor-secondary-button");
		resetButton.setMaxWidth(Double.MAX_VALUE);
		resetButton.setOnAction(e -> {
			if (onReset != null) {
				onReset.run();
			}
			logRows.clear();
			processedCount = 0;
			lastProcessedNanos = System.nanoTime();
		});

		Label strategyLabel = new Label("Concurrency strategy");
		strategyLabel.getStyleClass().add("monitor-control-label");
		ToggleGroup strategyGroup = new ToggleGroup();
		ToggleButton semaphoreToggle = new ToggleButton("Semaphore Strategy");
		ToggleButton mutexToggle = new ToggleButton("Mutex Strategy");
		semaphoreToggle.getStyleClass().addAll("monitor-strategy-toggle", "monitor-strategy-active");
		mutexToggle.getStyleClass().add("monitor-strategy-toggle");
		semaphoreToggle.setToggleGroup(strategyGroup);
		mutexToggle.setToggleGroup(strategyGroup);
		semaphoreToggle.setSelected(true);
		strategyGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
			if (selected == semaphoreToggle && onStrategyChange != null) {
				onStrategyChange.accept(1);
			} else if (selected == mutexToggle && onStrategyChange != null) {
				onStrategyChange.accept(2);
			}
		});

		HBox actions = new HBox(8, startButton, stopButton, resetButton);
		actions.setAlignment(Pos.CENTER_LEFT);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox strategyBox = new HBox(8, semaphoreToggle, mutexToggle);
		strategyBox.setAlignment(Pos.CENTER_LEFT);
		strategyBox.getStyleClass().add("monitor-strategy-box");

		bar.getChildren().addAll(new Label("Preset"), presetSelector, actions, spacer, strategyLabel, strategyBox);
		return bar;
	}

	private VBox buildKpiRow() {
		VBox root = new VBox(12);
		root.getStyleClass().add("monitor-kpi-row");
		root.setPadding(new Insets(0, 26, 0, 26));

		Label title = new Label("Live system status");
		title.getStyleClass().add("monitor-kpi-title");

		GridPane metrics = new GridPane();
		metrics.setHgap(12);
		metrics.setVgap(10);
		metrics.add(kpiCard("ACTIVE THREADS", threadsValue = metricLabel("0"), "Workers acquiring permits"), 0, 0);
		metrics.add(kpiCard("COMPLETED TASKS", exitsValue = metricLabel("0"), "Parked and exited vehicles"), 1, 0);
		metrics.add(kpiCard("QUEUE / DENIED", waitingValue = metricLabel("0"), "Waiting or capacity-limited"), 2, 0);
		metrics.add(kpiCard("PROCESSING RATE", rateValue = metricLabel("0 /s"), "Throughput"), 3, 0);
		metrics.add(kpiCard("CAPACITY", capacityValue = metricLabel("0"), "Configured spaces"), 0, 1);
		metrics.add(kpiCard("OCCUPIED", occupiedValue = metricLabel("0"), "Currently parked"), 1, 1);
		metrics.add(kpiCard("AVAILABLE", availableValue = metricLabel("0"), "Ready to use"), 2, 1);
		metrics.add(kpiCard("RESERVED", reservedValue = metricLabel("0"), "Future bookings"), 3, 1);
		for (int column = 0; column < 4; column++) {
			ColumnConstraints constraints = new ColumnConstraints();
			constraints.setPercentWidth(25);
			constraints.setHgrow(Priority.ALWAYS);
			metrics.getColumnConstraints().add(constraints);
		}

		stateValue = new Label("IDLE");
		stateValue.getStyleClass().addAll("monitor-state-pill", "monitor-state-idle");
		elapsedValue = new Label("0.0 s");
		elapsedValue.getStyleClass().add("monitor-elapsed");
		strategyValue = new Label("SEMAPHORE");
		strategyValue.getStyleClass().add("monitor-strategy-pill");

		HBox statusRow = new HBox(16,
				kpiCard("STATE", stateValue, "Simulation lifecycle"),
				kpiCard("ELAPSED", elapsedValue, "Run time"),
				kpiCard("STRATEGY", strategyValue, "Concurrency control"));
		statusRow.setAlignment(Pos.CENTER_LEFT);

		root.getChildren().addAll(title, metrics, statusRow);
		return root;
	}

	private VBox buildHealthChips() {
		VBox root = new VBox(12);
		root.getStyleClass().add("monitor-health-row");
		root.setPadding(new Insets(0, 26, 0, 26));

		Label title = new Label("Task health");
		title.getStyleClass().add("monitor-kpi-title");

		HBox chips = new HBox(12);
		chips.getStyleClass().add("monitor-health-chips");
		chips.getChildren().addAll(
				healthChip("Sensor Simulation", true),
				healthChip("Prediction Engine", true),
				healthChip("Reservation Processor", true),
				healthChip("Recommendation Service", true));

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		chips.getChildren().add(spacer);

		root.getChildren().addAll(title, chips);
		return root;
	}

	private VBox healthChip(String name, boolean operational) {
		VBox chip = new VBox(6);
		chip.getStyleClass().add("monitor-health-chip");
		Label nameLabel = new Label(name);
		nameLabel.getStyleClass().add("monitor-health-name");
		Label status = new Label(operational ? "OPERATIONAL" : "DEGRADED");
		status.getStyleClass().add("monitor-health-status");
		status.getStyleClass().add(operational ? "monitor-health-ok" : "monitor-health-warn");
		chip.getChildren().addAll(nameLabel, status);
		return chip;
	}

	private VBox buildLogSection() {
		VBox section = new VBox(12);
		section.getStyleClass().add("monitor-log-section");
		section.setPadding(new Insets(0, 26, 26, 26));

		Label title = new Label("Thread activity log");
		title.getStyleClass().add("monitor-section-title");

		HBox controls = new HBox(10);
		controls.getStyleClass().add("monitor-log-controls");
		Button clearLog = new Button("Clear Log");
		clearLog.getStyleClass().add("monitor-secondary-button");
		clearLog.setOnAction(e -> logRows.clear());
		logFilter = new TextField();
		logFilter.setPromptText("Filter by thread / search...");
		logFilter.setMaxWidth(260);
		logFilter.textProperty().addListener((obs, old, neu) -> applyLogFilter());
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		controls.getChildren().addAll(clearLog, new Label("Filter"), logFilter, spacer);

		logTable = new TableView<>(logRows);
		logTable.getStyleClass().add("monitor-log-table");
		logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<LogRow, String> timeCol = new TableColumn<>("Time");
		timeCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().time()));
		timeCol.setPrefWidth(100);

		TableColumn<LogRow, String> threadCol = new TableColumn<>("Thread ID");
		threadCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().threadId()));
		threadCol.setPrefWidth(120);

		TableColumn<LogRow, String> levelCol = new TableColumn<>("Level");
		levelCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().level()));
		levelCol.setPrefWidth(100);
		levelCol.setCellFactory(col -> new TableCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					setStyle("");
				} else {
					setText(item);
					String style = switch (item) {
						case "PARKED" -> "-fx-text-fill: #22C55E; -fx-font-weight: bold;";
						case "RELEASED" -> "-fx-text-fill: #0066FF; -fx-font-weight: bold;";
						case "WAITING" -> "-fx-text-fill: #F59E0B; -fx-font-weight: bold;";
						case "WARN" -> "-fx-text-fill: #EF4444; -fx-font-weight: bold;";
						default -> "-fx-text-fill: #475467;";
					};
					setStyle(style);
				}
			}
		});

		TableColumn<LogRow, String> messageCol = new TableColumn<>("Message");
		messageCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().message()));
		messageCol.setPrefWidth(Region.USE_COMPUTED_SIZE);

		logTable.getColumns().setAll(timeCol, threadCol, levelCol, messageCol);
		logTable.setPrefHeight(340);
		logTable.setMinHeight(240);
		VBox.setVgrow(logTable, Priority.ALWAYS);

		section.getChildren().addAll(title, controls, logTable);
		return section;
	}

	private void applyLogFilter() {
		String term = logFilter == null ? "" : logFilter.getText().toLowerCase();
		logTable.setItems(term.isBlank() ? logRows : FXCollections.observableArrayList(
				logRows.stream().filter(row -> row.message().toLowerCase().contains(term)
						|| row.threadId().toLowerCase().contains(term)
						|| row.level().toLowerCase().contains(term)).toList()));
	}

	private GridPane kpiCard(String title, Label value, String caption) {
		GridPane card = new GridPane();
		card.getStyleClass().add("monitor-kpi-card");
		Label t = new Label(title);
		t.getStyleClass().add("monitor-kpi-title-small");
		Label c = new Label(caption);
		c.getStyleClass().add("monitor-kpi-caption");
		value.getStyleClass().add("monitor-kpi-value");
		GridPane.setConstraints(t, 0, 0);
		GridPane.setConstraints(value, 0, 1);
		GridPane.setConstraints(c, 0, 2);
		GridPane.setHgrow(value, Priority.ALWAYS);
		GridPane.setHgrow(t, Priority.ALWAYS);
		GridPane.setHgrow(c, Priority.ALWAYS);
		card.getChildren().addAll(t, value, c);
		return card;
	}

	private Label metricLabel(String value) {
		Label label = new Label(value);
		label.getStyleClass().add("monitor-kpi-value");
		return label;
	}

	private double elapsedSeconds() {
		Long startNanos = startNanosSupplier == null ? null : startNanosSupplier.get();
		if (startNanos == null) {
			return 0;
		}
		return (System.nanoTime() - startNanos) / 1_000_000_000.0;
	}
}
