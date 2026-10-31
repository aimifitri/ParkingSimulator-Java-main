package com.parkwise.view;

import com.parkwise.model.CarPark;
import com.parkwise.model.Parking;
import com.parkwise.model.Reservation;
import com.parkwise.service.CarParkRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Screens 3 &amp; 4 of the ParkWise redesign: a 3-step reservation wizard and a
 * reservation confirmation view.
 *
 * <p>The view is fully decoupled from the backend concurrency model. It holds
 * wizard state internally, reads live availability through
 * {@link CarParkRepository} and {@link Supplier<Parking>}, and reports the
 * confirmed booking through a {@link Consumer} so {@link Main} owns backend
 * registration (BookingRecord + ReservationManager).
 */
public class ReservationView extends BorderPane {
	public record ConfirmationData(
			String carParkId, String carParkName, String location, double hourlyRate,
			int slotNumber, String slotLabel, LocalDate date, LocalTime startTime,
			int durationHours, double totalCost) {
	}

	private final CarParkRepository repository;
	private final Supplier<Parking> parkingSupplier;
	private final Consumer<ConfirmationData> onConfirm;
	private final Runnable onViewMyReservations;
	private final Runnable onBackToDashboard;

	private CarPark selectedCarPark;
	private int selectedSlot = -1;
	private LocalDate selectedDate;
	private LocalTime selectedStartTime;
	private int selectedDurationHours = 2;

	private int wizardStep = 1; // 1, 2, 3
	private boolean showingConfirmation = false;
	private ConfirmationData pending;

	private VBox wizardContent;
	private VBox confirmationContent;
	private VBox contentPane;
	private VBox step1View;
	private VBox step2View;
	private VBox step3View;
	private Region stepConnector1;
	private Region stepConnector2;

	private Label step1Active, step2Active, step3Active;
	private Label step1Label, step2Label, step3Label;
	private Button step1Next, step2Next;
	private Label step1Feedback, step2Feedback, step3Feedback;
	private TilePane spaceGrid;
	private Label availabilityLegendAvailable, availabilityLegendOccupied, availabilityLegendReserved;
	private Label costSpaceLabel, costRateLabel, costDurationLabel, costTotalLabel;
	private Button confirmPayButton;

	private Label confirmationBookingId, confirmationCarPark, confirmationSpace, confirmationInterval, confirmationCost;
	private VBox qrPane;
	private Label confirmationArrivalNotice;

	public ReservationView(CarParkRepository repository,
			Supplier<Parking> parkingSupplier,
			Consumer<ConfirmationData> onConfirm,
			Runnable onViewMyReservations,
			Runnable onBackToDashboard) {
		this.repository = repository;
		this.parkingSupplier = parkingSupplier;
		this.onConfirm = onConfirm;
		this.onViewMyReservations = onViewMyReservations;
		this.onBackToDashboard = onBackToDashboard;
		getStyleClass().add("reservation-view");
		setPadding(new Insets(0));
		selectedCarPark = repository.getActiveLot();
		if (selectedCarPark == null && !repository.getAllCarParks().isEmpty()) {
			selectedCarPark = repository.getAllCarParks().get(0);
		}
		wizardContent = buildWizard();
		confirmationContent = buildConfirmation();
		setCenter(wizardContent);
	}

	public void refresh() {
		// Rebuild wizard to reflect live availability changes.
		wizardContent = buildWizard();
		if (!showingConfirmation) {
			setCenter(wizardContent);
		}
	}

	private VBox buildWizard() {
		VBox root = new VBox(0);
		root.getStyleClass().add("reservation-wizard-root");
		root.setFillWidth(true);

		root.getChildren().add(buildStepIndicator());
		root.getChildren().add(buildStepContent());
		return root;
	}

	public void goToStep(int step) {
		if (step < 1 || step > 3) {
			return;
		}
		showingConfirmation = false;
		wizardStep = step;
		refresh();
	}

	private HBox buildStepIndicator() {
		HBox bar = new HBox(0);
		bar.getStyleClass().add("reservation-step-indicator");
		bar.setPadding(new Insets(18, 26, 10, 26));

		step1Active = stepChip("1", wizardStep == 1);
		step2Active = stepChip("2", wizardStep == 2);
		step3Active = stepChip("3", wizardStep == 3);

		step1Label = stepText("Select Car Park", wizardStep == 1);
		step2Label = stepText("Choose Space", wizardStep == 2);
		step3Label = stepText("Date & Time", wizardStep == 3);

		HBox s1 = stepBlock(step1Active, step1Label);
		HBox s2 = stepBlock(step2Active, step2Label);
		HBox s3 = stepBlock(step3Active, step3Label);

		stepConnector1 = connector(wizardStep >= 2);
		stepConnector2 = connector(wizardStep >= 3);

		bar.getChildren().addAll(s1, stepConnector1, s2, stepConnector2, s3);
		HBox.setHgrow(s1, Priority.ALWAYS);
		HBox.setHgrow(s2, Priority.ALWAYS);
		HBox.setHgrow(s3, Priority.ALWAYS);
		return bar;
	}

	private Label stepChip(String number, boolean active) {
		Label chip = new Label(number);
		chip.getStyleClass().add("res-step-chip");
		chip.getStyleClass().add(active ? "res-step-chip-active" : "res-step-chip-inactive");
		chip.setMinWidth(28);
		chip.setMinHeight(28);
		chip.setMaxWidth(28);
		chip.setMaxHeight(28);
		chip.setAlignment(Pos.CENTER);
		return chip;
	}

	private Label stepText(String text, boolean active) {
		Label label = new Label(text);
		label.getStyleClass().add("res-step-text");
		label.getStyleClass().add(active ? "res-step-text-active" : "res-step-text-inactive");
		return label;
	}

	private HBox stepBlock(Label chip, Label text) {
		HBox block = new HBox(10, chip, text);
		block.setAlignment(Pos.CENTER_LEFT);
		block.getStyleClass().add("res-step-block");
		return block;
	}

	private Region connector(boolean active) {
		Region line = new Region();
		line.getStyleClass().add("res-step-connector");
		if (active) {
			line.getStyleClass().add("res-step-connector-active");
		}
		HBox.setHgrow(line, Priority.ALWAYS);
		return line;
	}

	private VBox buildStepContent() {
		contentPane = new VBox(16);
		contentPane.getStyleClass().add("res-step-content");
		contentPane.setPadding(new Insets(0, 26, 26, 26));

		step1View = buildStep1();
		step2View = buildStep2();
		step3View = buildStep3();

		switch (wizardStep) {
			case 1 -> contentPane.getChildren().setAll(step1View);
			case 2 -> contentPane.getChildren().setAll(step2View);
			case 3 -> contentPane.getChildren().setAll(step3View);
		}
		return contentPane;
	}

	private VBox buildStep1() {
		VBox card = new VBox(14);
		card.getStyleClass().add("res-step-card");

		Label title = new Label("Select Car Park");
		title.getStyleClass().add("res-step-title");
		Label subtitle = new Label("Choose where you would like to park.");
		subtitle.getStyleClass().add("res-step-subtitle");
		subtitle.setWrapText(true);

		VBox list = new VBox(10);
		list.getStyleClass().add("res-carpark-list");
		for (CarPark park : repository.getAllCarParks()) {
			list.getChildren().add(buildCarParkCard(park));
		}

		step1Feedback = new Label();
		step1Feedback.getStyleClass().add("res-feedback");
		step1Feedback.setWrapText(true);

		step1Next = new Button("Next \u2192");
		step1Next.getStyleClass().add("res-primary-button");
		step1Next.setMaxWidth(Double.MAX_VALUE);
		step1Next.setDisable(selectedCarPark == null);
		step1Next.setOnAction(e -> {
			if (selectedCarPark == null) {
				step1Feedback.setText("Please select a car park.");
				step1Feedback.getStyleClass().setAll("res-feedback", "res-feedback-error");
				return;
			}
			step1Feedback.setText("");
			goToStep(2);
		});

		VBox actions = new VBox(10, step1Next);
		actions.setAlignment(Pos.BOTTOM_RIGHT);

		card.getChildren().addAll(title, subtitle, list, step1Feedback, actions);
		return card;
	}

	private VBox buildCarParkCard(CarPark park) {
		VBox card = new VBox(10);
		card.getStyleClass().add("res-carpark-card");
		card.setPrefWidth(300);
		card.setMaxWidth(Double.MAX_VALUE);
		boolean selected = selectedCarPark != null && selectedCarPark.id().equals(park.id());

		HBox top = new HBox(10);
		Label name = new Label(park.name());
		name.getStyleClass().add("res-carpark-name");
		Label badge = new Label(park.availableCount() + " available");
		badge.getStyleClass().add("res-availability-badge");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		top.getChildren().addAll(name, sp, badge);

		Label location = new Label(park.location());
		location.getStyleClass().add("res-carpark-location");
		location.setWrapText(true);

		HBox bottom = new HBox(10);
		Label distance = new Label(String.format("%.2f km away", park.distanceKm()));
		distance.getStyleClass().add("res-carpark-distance");
		Region sp2 = new Region();
		HBox.setHgrow(sp2, Priority.ALWAYS);
		Label rate = new Label(String.format("RM %.2f / hr", park.hourlyRate()));
		rate.getStyleClass().add("res-carpark-rate");
		bottom.getChildren().addAll(distance, sp2, rate);

		card.getChildren().addAll(top, location, bottom);
		card.setOnMouseClicked(e -> {
			selectedCarPark = park;
			selectedSlot = -1;
			refresh();
		});
		if (selected) {
			card.getStyleClass().add("res-carpark-card-selected");
		}
		return card;
	}

	private VBox buildStep2() {
		VBox card = new VBox(14);
		card.getStyleClass().add("res-step-card");

		Label title = new Label("Choose Space");
		title.getStyleClass().add("res-step-title");
		Label subtitle = new Label("Select an available space in " + (selectedCarPark == null ? "" : selectedCarPark.name()) + ".");
		subtitle.getStyleClass().add("res-step-subtitle");
		subtitle.setWrapText(true);

		HBox legend = new HBox(18);
		legend.getStyleClass().add("res-legend");
		availabilityLegendAvailable = legendChip("#22C55E", "Available");
		availabilityLegendOccupied = legendChip("#EF4444", "Occupied");
		availabilityLegendReserved = legendChip("#F59E0B", "Reserved");
		legend.getChildren().addAll(availabilityLegendAvailable, availabilityLegendOccupied, availabilityLegendReserved);

		spaceGrid = new TilePane();
		spaceGrid.getStyleClass().add("res-space-grid");
		spaceGrid.setPrefColumns(7);
		spaceGrid.setHgap(10);
		spaceGrid.setVgap(12);
		spaceGrid.setPadding(new Insets(0));
		buildSpaceTiles();

		ScrollPane spaceScrollPane = new ScrollPane(spaceGrid);
		spaceScrollPane.setFitToWidth(true);
		spaceScrollPane.setPrefHeight(400);
		spaceScrollPane.setMaxHeight(400);
		spaceScrollPane.setMinHeight(0);
		spaceScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		spaceScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		spaceScrollPane.getStyleClass().add("res-space-scroll");

		step2Feedback = new Label();
		step2Feedback.getStyleClass().add("res-feedback");
		step2Feedback.setWrapText(true);

		HBox actions = new HBox(10);
		actions.getStyleClass().add("res-step-actions");
		Button back1 = new Button("Back");
		back1.getStyleClass().add("res-secondary-button");
		back1.setOnAction(e -> {
			goToStep(1);
		});
		step2Next = new Button("Continue to Date & Time \u2192");
		step2Next.getStyleClass().add("res-primary-button");
		step2Next.setDisable(selectedSlot == -1);
		step2Next.setOnAction(e -> {
			if (selectedSlot == -1) {
				step2Feedback.setText("Please select an available space.");
				step2Feedback.getStyleClass().setAll("res-feedback", "res-feedback-error");
				return;
			}
			step2Feedback.setText("");
			goToStep(3);
		});
		actions.getChildren().addAll(back1, new Region(), step2Next);
		HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
		actions.setAlignment(Pos.CENTER_RIGHT);

		card.getChildren().addAll(title, subtitle, legend, spaceScrollPane, step2Feedback, actions);
		return card;
	}

	private Label legendChip(String color, String label) {
		HBox chip = new HBox(6);
		chip.getStyleClass().add("res-legend-chip");
		Rectangle dot = new Rectangle(10, 10, Color.web(color));
		dot.setArcWidth(4);
		dot.setArcHeight(4);
		Label text = new Label(label);
		text.getStyleClass().add("res-legend-text");
		chip.getChildren().addAll(dot, text);
		return text;
	}

	private void buildSpaceTiles() {
		if (spaceGrid == null) {
			return;
		}
		spaceGrid.getChildren().clear();
		int capacity = selectedCarPark == null ? 5 : selectedCarPark.totalCapacity();
		Parking p = parkingSupplier.get();
		List<Parking.SpaceState> states = p == null ? List.of() : p.spaceStates();
		for (int slot = 1; slot <= capacity; slot++) {
			final int currentSlot = slot;
			Parking.SpaceState state = findState(states, currentSlot);
			boolean available = state == null || state.status() == Parking.SpaceStatus.AVAILABLE;
			CustomRectangle tile = new CustomRectangle(currentSlot);
			tile.getStyleClass().add("res-tile");
			Reservation reservation = state == null || state.reservations().isEmpty() ? null : state.reservations().get(0);
			tile.render(state == null ? Parking.SpaceStatus.AVAILABLE : state.status(),
					state == null ? null : state.vehicleId(), reservation,
					currentSlot == selectedSlot);
			tile.setOnMouseClicked(e -> {
				if (available) {
					selectedSlot = currentSlot;
					if (step2Next != null) {
						step2Next.setDisable(false);
					}
					buildSpaceTiles();
				}
			});
			spaceGrid.getChildren().add(tile);
		}
	}

	private VBox buildStep3() {
		VBox card = new VBox(14);
		card.getStyleClass().add("res-step-card");

		Label title = new Label("Date & Time");
		title.getStyleClass().add("res-step-title");
		Label subtitle = new Label("Pick when you would like to park and for how long.");
		subtitle.getStyleClass().add("res-step-subtitle");
		subtitle.setWrapText(true);

		selectedDate = selectedDate == null ? LocalDate.now() : selectedDate;
		DatePicker datePicker = new DatePicker(selectedDate);
		datePicker.setPromptText("Date");
		datePicker.setMaxWidth(Double.MAX_VALUE);
		datePicker.setOnAction(e -> selectedDate = datePicker.getValue());

		DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
		selectedStartTime = selectedStartTime == null ? LocalTime.of(10, 30) : selectedStartTime;
		ComboBox<String> timeBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
				"08:00 AM", "09:00 AM", "10:30 AM", "12:00 PM", "02:00 PM", "03:30 PM", "05:00 PM", "06:30 PM", "08:00 PM"));
		timeBox.setValue(selectedStartTime.format(timeFormatter));
		timeBox.setPromptText("Start Time");
		timeBox.setMaxWidth(Double.MAX_VALUE);
		timeBox.setOnAction(e -> {
			String v = timeBox.getValue();
			if (v != null) {
				selectedStartTime = LocalTime.parse(v, timeFormatter);
			}
		});

		ComboBox<String> durationBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
				"1 Hour", "2 Hours", "3 Hours", "4 Hours", "6 Hours", "8 Hours", "12 Hours"));
		durationBox.setValue(selectedDurationHours + " Hours");
		durationBox.setPromptText("Duration");
		durationBox.setMaxWidth(Double.MAX_VALUE);
		durationBox.setOnAction(e -> {
			String v = durationBox.getValue();
			if (v != null) {
				selectedDurationHours = Integer.parseInt(v.replace(" Hours", ""));
				updateCostSummary();
			}
		});

		VBox costCard = buildCostCard();

		step3Feedback = new Label();
		step3Feedback.getStyleClass().add("res-feedback");
		step3Feedback.setWrapText(true);

		confirmPayButton = new Button("Confirm & Pay");
		confirmPayButton.getStyleClass().add("res-primary-button");
		confirmPayButton.setMaxWidth(Double.MAX_VALUE);
		confirmPayButton.setOnAction(e -> {
			if (selectedCarPark == null || selectedSlot == -1 || selectedDate == null || selectedStartTime == null) {
				step3Feedback.setText("Please complete all fields.");
				step3Feedback.getStyleClass().setAll("res-feedback", "res-feedback-error");
				return;
			}
			if (selectedDate.isBefore(LocalDate.now())) {
				step3Feedback.setText("Reservation date cannot be in the past.");
				step3Feedback.getStyleClass().setAll("res-feedback", "res-feedback-error");
				return;
			}
			if (selectedDate.equals(LocalDate.now()) && selectedStartTime.isBefore(LocalTime.now())) {
				step3Feedback.setText("Reservation start time cannot be in the past.");
				step3Feedback.getStyleClass().setAll("res-feedback", "res-feedback-error");
				return;
			}
			step3Feedback.setText("");
			double total = selectedCarPark.hourlyRate() * selectedDurationHours;
			pending = new ConfirmationData(
					selectedCarPark.id(), selectedCarPark.name(), selectedCarPark.location(),
					selectedCarPark.hourlyRate(), selectedSlot,
					String.format("A%02d - Level A", selectedSlot),
					selectedDate, selectedStartTime, selectedDurationHours, total);
			if (onConfirm != null) {
				onConfirm.accept(pending);
			}
			showConfirmation();
		});

		HBox actions = new HBox(10);
		actions.getStyleClass().add("res-step-actions");
		Button back2 = new Button("\u2190 Back");
		back2.getStyleClass().add("res-secondary-button");
		back2.setOnAction(e -> {
			goToStep(2);
		});
		actions.getChildren().addAll(back2, new Region(), confirmPayButton);
		HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);

		GridPane form = new GridPane();
		form.setHgap(18);
		form.setVgap(15);
		form.setMaxWidth(Double.MAX_VALUE);
		ColumnConstraints c1 = new ColumnConstraints();
		c1.setPercentWidth(50);
		c1.setHgrow(Priority.ALWAYS);
		ColumnConstraints c2 = new ColumnConstraints();
		c2.setPercentWidth(50);
		c2.setHgrow(Priority.ALWAYS);
		form.getColumnConstraints().setAll(c1, c2);

		VBox dateGroup = new VBox(7, new Label("Date"), datePicker);
		dateGroup.setMaxWidth(Double.MAX_VALUE);
		VBox timeGroup = new VBox(7, new Label("Start Time"), timeBox);
		timeGroup.setMaxWidth(Double.MAX_VALUE);
		VBox durGroup = new VBox(7, new Label("Duration"), durationBox);
		durGroup.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(dateGroup, Priority.ALWAYS);
		GridPane.setHgrow(timeGroup, Priority.ALWAYS);
		GridPane.setHgrow(durGroup, Priority.ALWAYS);
		form.add(dateGroup, 0, 0);
		form.add(timeGroup, 1, 0);
		form.add(durGroup, 0, 1, 2, 1);

		card.getChildren().addAll(title, subtitle, form, costCard, step3Feedback, actions);
		return card;
	}

	private VBox buildCostCard() {
		VBox card = new VBox(10);
		card.getStyleClass().add("res-cost-card");
		card.setPadding(new Insets(14));

		Label title = new Label("Cost Summary");
		title.getStyleClass().add("res-cost-title");

		costSpaceLabel = new Label("Space: \u2014");
		costSpaceLabel.getStyleClass().add("res-cost-line");
		costRateLabel = new Label("Rate: \u2014");
		costRateLabel.getStyleClass().add("res-cost-line");
		costDurationLabel = new Label("Duration: \u2014");
		costDurationLabel.getStyleClass().add("res-cost-line");
		costTotalLabel = new Label("Total: RM 0.00");
		costTotalLabel.getStyleClass().add("res-cost-total");

		updateCostSummary();

		card.getChildren().addAll(title, costSpaceLabel, costRateLabel, costDurationLabel, costTotalLabel);
		return card;
	}

	private void updateCostSummary() {
		if (costSpaceLabel == null) {
			return;
		}
		String slotText = selectedSlot == -1 ? "\u2014" : String.format("A%02d - Level A", selectedSlot);
		costSpaceLabel.setText("Space: " + slotText);
		costRateLabel.setText("Rate: RM " + String.format("%.2f", selectedCarPark == null ? 5.0 : selectedCarPark.hourlyRate()) + " / hr");
		costDurationLabel.setText("Duration: " + selectedDurationHours + " hour" + (selectedDurationHours == 1 ? "" : "s"));
		double total = (selectedCarPark == null ? 5.0 : selectedCarPark.hourlyRate()) * selectedDurationHours;
		costTotalLabel.setText("Total: RM " + String.format("%.2f", total));
	}

	private void showConfirmation() {
		showingConfirmation = true;
		setCenter(confirmationContent);
		buildConfirmationData();
	}

	private void buildConfirmationData() {
		if (pending == null) {
			return;
		}
		DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
		DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
		String bookingId = "PW-" + pending.date().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-0001";

		confirmationBookingId.setText(bookingId);
		confirmationCarPark.setText(pending.carParkName() + " \u2022 " + pending.location());
		confirmationSpace.setText(pending.slotLabel());
		confirmationInterval.setText(pending.date().format(dateFmt) + " | "
				+ pending.startTime().format(timeFmt) + " - "
				+ pending.startTime().plusHours(pending.durationHours()).format(timeFmt));
		confirmationCost.setText("RM " + String.format("%.2f", pending.totalCost()));
	}

	private VBox buildConfirmation() {
		VBox root = new VBox(0);
		root.getStyleClass().add("reservation-confirmation-root");
		root.setFillWidth(true);

		VBox banner = new VBox(8);
		banner.getStyleClass().add("res-confirmation-banner");
		banner.setPadding(new Insets(22, 26, 18, 26));
		Label check = new Label("\u2714");
		check.getStyleClass().add("res-check-badge");
		Label successTitle = new Label("Reservation Successful!");
		successTitle.getStyleClass().add("res-confirmation-title");
		Label successSub = new Label("Your parking space has been reserved.");
		successSub.getStyleClass().add("res-confirmation-subtitle");
		banner.getChildren().addAll(check, successTitle, successSub);

		VBox card = new VBox(14);
		card.getStyleClass().add("res-confirmation-card");
		card.setPadding(new Insets(20));

		Label summaryTitle = new Label("Booking Summary");
		summaryTitle.getStyleClass().add("res-section-title");

		confirmationBookingId = new Label("PW-\u2014");
		confirmationBookingId.getStyleClass().add("res-meta-value");
		confirmationCarPark = new Label("\u2014");
		confirmationCarPark.getStyleClass().add("res-meta-value");
		confirmationSpace = new Label("\u2014");
		confirmationSpace.getStyleClass().add("res-meta-value");
		confirmationInterval = new Label("\u2014");
		confirmationInterval.getStyleClass().add("res-meta-value");
		confirmationCost = new Label("RM 0.00");
		confirmationCost.getStyleClass().add("res-meta-total");

		card.getChildren().addAll(summaryTitle,
				metaRow("Booking ID", confirmationBookingId),
				metaRow("Car Park & Location", confirmationCarPark),
				metaRow("Reserved Space", confirmationSpace),
				metaRow("Reserved Interval", confirmationInterval),
				metaRow("Total Cost", confirmationCost));

		VBox meta = new VBox(12);
		meta.getStyleClass().add("res-meta-section");
		Label metaTitle = new Label("Booking Details");
		metaTitle.getStyleClass().add("res-section-title");
		qrPane = buildQrPane("PW-0001");
		confirmationArrivalNotice = new Label("Please arrive on time. The reservation will be held for 15 minutes after the start time.");
		confirmationArrivalNotice.getStyleClass().add("res-arrival-notice");
		confirmationArrivalNotice.setWrapText(true);
		meta.getChildren().addAll(metaTitle, qrPane, confirmationArrivalNotice);

		VBox actions = new VBox(10);
		actions.getStyleClass().add("res-confirmation-actions");
		Button calendar = new Button("Add to Calendar");
		calendar.getStyleClass().add("res-secondary-button");
		calendar.setMaxWidth(Double.MAX_VALUE);
		Button viewReservations = new Button("View My Reservations");
		viewReservations.getStyleClass().add("res-primary-button");
		viewReservations.setMaxWidth(Double.MAX_VALUE);
		viewReservations.setOnAction(e -> {
			if (onViewMyReservations != null) {
				onViewMyReservations.run();
			}
		});
		Button backDashboard = new Button("Back to Dashboard");
		backDashboard.getStyleClass().add("res-secondary-button");
		backDashboard.setMaxWidth(Double.MAX_VALUE);
		backDashboard.setOnAction(e -> {
			if (onBackToDashboard != null) {
				onBackToDashboard.run();
			}
		});
		actions.getChildren().addAll(calendar, viewReservations, backDashboard);

		root.getChildren().addAll(banner, card, meta, actions);
		VBox.setVgrow(card, Priority.ALWAYS);
		VBox.setVgrow(meta, Priority.ALWAYS);
		return root;
	}

	private VBox metaRow(String label, Node value) {
		VBox row = new VBox(4);
		row.getStyleClass().add("res-meta-row");
		Label l = new Label(label);
		l.getStyleClass().add("res-meta-label");
		value.getStyleClass().add("res-meta-value-node");
		row.getChildren().addAll(l, value);
		return row;
	}

	private VBox buildQrPane(String text) {
		VBox box = new VBox(8);
		box.getStyleClass().add("res-qr-box");
		Label qrLabel = new Label("QR Code");
		qrLabel.getStyleClass().add("res-qr-title");
		Pane qr = new Pane();
		qr.getStyleClass().add("res-qr-canvas");
		qr.setPrefSize(160, 160);
		qr.setMinSize(160, 160);
		qr.setMaxSize(160, 160);
		drawQr(qr, text);
		box.getChildren().addAll(qrLabel, qr);
		box.setAlignment(Pos.CENTER);
		return box;
	}

	private void drawQr(Pane canvas, String text) {
		int size = 9;
		double cell = 160.0 / size;
		int seed = text.hashCode();
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean fill = ((seed >> (x + y * size)) & 1) == 1 || (x == 0 && y == 0) || (x == size - 1 && y == 0)
						|| (x == 0 && y == size - 1);
				Rectangle cellRect = new Rectangle(cell, cell, fill ? Color.web("#0B132B") : Color.web("#E2E8F0"));
				cellRect.setX(x * cell);
				cellRect.setY(y * cell);
				canvas.getChildren().add(cellRect);
			}
		}
	}

	private Parking.SpaceState findState(List<Parking.SpaceState> states, int slot) {
		for (Parking.SpaceState s : states) {
			if (s.slotNumber() == slot) {
				return s;
			}
		}
		return null;
	}
}
