package com.parkwise.view;

import com.parkwise.model.CarPark;
import com.parkwise.model.Parking;
import com.parkwise.model.Reservation;
import com.parkwise.service.CarParkRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/**
 * Screen 2 of the ParkWise redesign: an interactive parking-lots inspector.
 *
 * <p>The view is fully decoupled from the backend concurrency model. It reads
 * live data through {@link CarParkRepository} and a {@link Supplier<Parking>}
 * and reports user actions through callbacks. All slot rendering reuses the
 * existing {@link CustomRectangle} component.
 */
public class ParkingLotsView extends BorderPane {
	public static final String SPACE_A05_IMAGE = "/public/images/space-a05.jpg";
	private final CarParkRepository repository;
	private final Supplier<Parking> parkingSupplier;
	private final IntConsumer onReserveSelected;
	private final Consumer<CarPark> onCarParkChange;

	private CarPark selectedCarPark;
	private int selectedSlotNumber = -1;
	private String filter = "All"; // All | Available | Occupied | Reserved

	private ComboBox<CarPark> carParkSelector;
	private Label addressInfoLabel;
	private Label totalValue, availableValue, occupiedValue, reservedValue;
	private Label hoursLabel, rateLabel;
	private ToggleGroup filterGroup;
	private ToggleButton allFilter, availableFilter, occupiedFilter, reservedFilter;
	private TilePane grid;
	private final Map<Integer, CustomRectangle> slotTiles = new TreeMap<>();
	private Label detailsTitle;
	private Label detailsSpaceId;
	private Label detailsStatus;
	private VBox detailsFloor;
	private VBox detailsType;
	private VBox detailsRate;
	private VBox detailsVehicle;
	private VBox detailsBooking;
	private VBox detailsCapacity;
	private VBox detailsOccupancy;
	private VBox detailsLocation;
	private VBox detailsCoordinates;
	private Button reserveButton;

	public ParkingLotsView(CarParkRepository repository,
			Supplier<Parking> parkingSupplier,
			IntConsumer onReserveSelected,
			Consumer<CarPark> onCarParkChange) {
		this.repository = repository;
		this.parkingSupplier = parkingSupplier;
		this.onReserveSelected = onReserveSelected;
		this.onCarParkChange = onCarParkChange;
		getStyleClass().add("parking-lots-view");
		setPadding(new Insets(0));
		selectedCarPark = repository.getActiveLot();
		if (selectedCarPark == null) {
			selectedCarPark = repository.getAllCarParks().isEmpty() ? null : repository.getAllCarParks().get(0);
		}
		build();
		refresh();
	}

	public void refresh() {
		refreshHeader();
		refreshGrid();
		refreshDetails();
	}

	public int getSelectedSlotNumber() {
		return selectedSlotNumber;
	}

	private void build() {
		VBox root = new VBox();
		root.getStyleClass().add("parking-lots-root");
		root.setFillWidth(true);

		root.getChildren().add(buildTopHeaderBar());
		root.getChildren().add(buildCenter());

		BorderPane scrollWrapper = new BorderPane();
		scrollWrapper.setCenter(root);
		setCenter(scrollWrapper);
	}

	private VBox buildTopHeaderBar() {
		HBox bar = new HBox(16);
		bar.getStyleClass().add("lots-top-header");
		bar.setPadding(new Insets(18, 26, 14, 26));
		bar.setAlignment(Pos.CENTER_LEFT);

		carParkSelector = new ComboBox<>();
		carParkSelector.getItems().addAll(repository.getAllCarParks());
		carParkSelector.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(CarPark p) {
				return p == null ? "" : p.name();
			}

			@Override
			public CarPark fromString(String s) {
				return null;
			}
		});
		carParkSelector.setValue(selectedCarPark);
		carParkSelector.setPromptText("Select Car Park");
		carParkSelector.setMaxWidth(220);
		carParkSelector.setOnAction(e -> {
			CarPark picked = carParkSelector.getValue();
			if (picked != null && !picked.equals(selectedCarPark)) {
				selectedCarPark = picked;
				selectedSlotNumber = -1;
				filter = "All";
				if (onCarParkChange != null) {
					onCarParkChange.accept(picked);
				}
				refresh();
			}
		});

		addressInfoLabel = new Label();
		addressInfoLabel.getStyleClass().add("lots-address-info");

		Region leftSpacer = new Region();
		HBox.setHgrow(leftSpacer, Priority.ALWAYS);

		HBox pills = new HBox(10);
		pills.getStyleClass().add("lots-metric-pills");
		pills.setAlignment(Pos.CENTER_RIGHT);
		totalValue = pillCard("TOTAL CAPACITY", "0");
		availableValue = pillCard("AVAILABLE", "0");
		occupiedValue = pillCard("OCCUPIED", "0");
		reservedValue = pillCard("RESERVED", "0");
		pills.getChildren().addAll(totalValue, availableValue, occupiedValue, reservedValue);

		bar.getChildren().addAll(carParkSelector, addressInfoLabel, leftSpacer, pills);

		HBox subInfo = new HBox(20);
		subInfo.getStyleClass().add("lots-sub-info");
		subInfo.setPadding(new Insets(10, 26, 14, 26));
		subInfo.setAlignment(Pos.CENTER_LEFT);
		hoursLabel = new Label("07:00 AM - 12:00 AM");
		hoursLabel.getStyleClass().add("lots-sub-info-text");
		rateLabel = new Label("RM5.00 / hour");
		rateLabel.getStyleClass().add("lots-sub-info-rate");
		subInfo.getChildren().addAll(hoursLabel, rateLabel);

		VBox header = new VBox(0, bar, subInfo);
		header.getStyleClass().add("lots-header");
		return header;
	}

	private Label pillCard(String title, String value) {
		VBox card = new VBox(6);
		card.getStyleClass().add("lots-pill-card");
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("lots-pill-title");
		Label valueLabel = new Label(value);
		valueLabel.getStyleClass().add("lots-pill-value");
		card.getChildren().addAll(titleLabel, valueLabel);
		return valueLabel;
	}

	private VBox buildCenter() {
		VBox center = new VBox(16);
		center.getStyleClass().add("lots-center");
		center.setPadding(new Insets(0, 26, 0, 26));
		VBox.setVgrow(center, Priority.ALWAYS);

		center.getChildren().add(buildFilterBar());
		center.getChildren().add(buildGridAndDetails());
		return center;
	}

	private HBox buildFilterBar() {
		HBox bar = new HBox(10);
		bar.getStyleClass().add("lots-filter-bar");
		bar.setAlignment(Pos.CENTER_LEFT);
		bar.setPadding(new Insets(0, 0, 8, 0));

		Label filterTitle = new Label("Space status filter");
		filterTitle.getStyleClass().add("lots-filter-title");

		filterGroup = new ToggleGroup();
		allFilter = filterChip("All (35)", true);
		availableFilter = filterChip("Available (12)", false);
		occupiedFilter = filterChip("Occupied (18)", false);
		reservedFilter = filterChip("Reserved (5)", false);

		bar.getChildren().addAll(filterTitle, allFilter, availableFilter, occupiedFilter, reservedFilter);
		return bar;
	}

	private ToggleButton filterChip(String text, boolean selected) {
		ToggleButton chip = new ToggleButton(text);
		chip.getStyleClass().add("lots-filter-chip");
		chip.setToggleGroup(filterGroup);
		chip.setSelected(selected);
		chip.setUserData(text.split(" \\(")[0]);
		chip.setOnAction(e -> {
			filter = (String) chip.getUserData();
			refreshGrid();
		});
		return chip;
	}

	private HBox buildGridAndDetails() {
		HBox row = new HBox(16);
		row.getStyleClass().add("lots-grid-and-details");
		row.setAlignment(Pos.TOP_LEFT);

		VBox left = new VBox(12);
		left.getStyleClass().add("lots-left-column");
		VBox.setVgrow(left, Priority.ALWAYS);

		Label gridTitle = new Label("Parking spaces \u2014 " + (selectedCarPark == null ? "" : selectedCarPark.name()));
		gridTitle.getStyleClass().add("lots-grid-title");
		Label gridCaption = new Label("Tap a space card to inspect it. Colours show live occupancy status.");
		gridCaption.getStyleClass().add("lots-grid-caption");
		gridCaption.setWrapText(true);

		grid = new TilePane();
		grid.getStyleClass().add("lots-space-grid");
		grid.setPrefColumns(6);
		grid.setHgap(12);
		grid.setVgap(14);
		grid.setPadding(new Insets(0));

		left.getChildren().addAll(gridTitle, gridCaption, grid);

		VBox right = new VBox(0);
		right.getStyleClass().add("lots-right-column");
		right.setPrefWidth(320);
		right.setMaxWidth(360);
		right.getChildren().add(buildDetailsInspector());

		row.getChildren().addAll(left, right);
		HBox.setHgrow(left, Priority.ALWAYS);
		return row;
	}

	private VBox buildDetailsInspector() {
		VBox card = new VBox(14);
		card.getStyleClass().add("lots-details-card");
		card.setPadding(new Insets(18));

		detailsTitle = new Label("Parking Space Details");
		detailsTitle.getStyleClass().add("lots-details-title");

		HBox headerRow = new HBox(10);
		headerRow.getStyleClass().add("lots-details-header");
		detailsSpaceId = new Label(selectedSlotNumber == -1 ? "\u2014" : String.format("A%02d", selectedSlotNumber));
		detailsSpaceId.getStyleClass().add("lots-details-space-id");
		detailsStatus = new Label("Select a space");
		detailsStatus.getStyleClass().add("lots-details-status-pill");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		headerRow.getChildren().addAll(detailsSpaceId, sp, detailsStatus);

		VBox gridFields = new VBox(10);
		gridFields.getStyleClass().add("lots-details-fields");
		detailsFloor = detailRow("Floor level", "Level A");
		detailsType = detailRow("Type", "Standard");
		detailsRate = detailRow("Hourly rate", "RM5.00/hr");
		detailsVehicle = detailRow("Current vehicle", "\u2014");
		detailsBooking = detailRow("Booking status", "None");
		detailsCapacity = detailRow("Capacity", "0 spaces");
		detailsOccupancy = detailRow("Occupancy", "0%");
		detailsLocation = detailRow("Location", "\u2014");
		detailsCoordinates = detailRow("Coordinates", "\u2014");
		gridFields.getChildren().addAll(detailsFloor, detailsType, detailsRate, detailsVehicle, detailsBooking,
				detailsCapacity, detailsOccupancy, detailsLocation, detailsCoordinates);

		Region illustration = new Region();
		illustration.getStyleClass().add("lots-details-illustration");
		illustration.setUserData(SPACE_A05_IMAGE);
		illustration.setStyle("-fx-background-image: url(\"" + SPACE_A05_IMAGE + "\");"
				+ "-fx-background-size: cover;");
		illustration.setMinHeight(160);
		illustration.setPrefHeight(160);
		illustration.setMaxWidth(Double.MAX_VALUE);

		reserveButton = new Button("Reserve Selected Space");
		reserveButton.getStyleClass().add("lots-reserve-button");
		reserveButton.setMaxWidth(Double.MAX_VALUE);
		reserveButton.setDisable(true);
		reserveButton.setOnAction(e -> {
			if (onReserveSelected != null && selectedSlotNumber != -1) {
				onReserveSelected.accept(selectedSlotNumber);
			}
		});

		card.getChildren().addAll(detailsTitle, headerRow, gridFields, illustration, reserveButton);
		return card;
	}

	private VBox detailRow(String label, String value) {
		VBox row = new VBox(4);
		row.getStyleClass().add("lots-detail-row");
		Label l = new Label(label);
		l.getStyleClass().add("lots-detail-label");
		Label v = new Label(value);
		v.getStyleClass().add("lots-detail-value");
		row.getChildren().addAll(l, v);
		return row;
	}

	private void refreshHeader() {
		if (selectedCarPark == null) {
			return;
		}
		addressInfoLabel.setText(selectedCarPark.location() + " \u2022 " + String.format("%.2f km",
				selectedCarPark.distanceKm()));
		hoursLabel.setText("07:00 AM - 12:00 AM");
		rateLabel.setText(String.format("RM %.2f / hour", selectedCarPark.hourlyRate()));

		totalValue.setText(String.valueOf(selectedCarPark.totalCapacity()));
		availableValue.setText(String.valueOf(selectedCarPark.availableCount()));
		occupiedValue.setText(String.valueOf(selectedCarPark.occupiedCount()));
		reservedValue.setText(String.valueOf(selectedCarPark.reservedCount()));

		allFilter.setText("All (" + selectedCarPark.totalCapacity() + ")");
		availableFilter.setText("Available (" + selectedCarPark.availableCount() + ")");
		occupiedFilter.setText("Occupied (" + selectedCarPark.occupiedCount() + ")");
		reservedFilter.setText("Reserved (" + selectedCarPark.reservedCount() + ")");
	}

	private void refreshGrid() {
		if (grid == null) {
			return;
		}
		grid.getChildren().clear();
		slotTiles.clear();

		Parking p = parkingSupplier.get();
		List<Parking.SpaceState> states = new ArrayList<>();
		int total = selectedCarPark == null ? 0 : selectedCarPark.totalCapacity();
		if (p != null) {
			states = p.spaceStates();
		}

		int capacity = total > 0 ? total : (states.isEmpty() ? 5 : states.size());
		for (int slot = 1; slot <= capacity; slot++) {
			final int currentSlot = slot;
			Parking.SpaceState state = findState(states, currentSlot);
			boolean show = matchesFilter(state);
			CustomRectangle tile = new CustomRectangle(currentSlot);
			tile.getStyleClass().add("lots-tile");
			Reservation reservation = state == null || state.reservations().isEmpty()
					? null : state.reservations().get(0);
			tile.render(state == null ? Parking.SpaceStatus.AVAILABLE : state.status(),
					state == null ? null : state.vehicleId(), reservation,
					currentSlot == selectedSlotNumber);
			tile.setOnMouseClicked(e -> {
				selectedSlotNumber = currentSlot;
				refreshGrid();
				refreshDetails();
			});
			slotTiles.put(currentSlot, tile);
			if (show) {
				grid.getChildren().add(tile);
			}
		}
	}

	private boolean matchesFilter(Parking.SpaceState state) {
		if ("All".equals(filter)) {
			return true;
		}
		if (state == null) {
			return "Available".equals(filter);
		}
		return switch (filter) {
			case "Available" -> state.status() == Parking.SpaceStatus.AVAILABLE;
			case "Occupied" -> state.status() == Parking.SpaceStatus.OCCUPIED;
			case "Reserved" -> state.status() == Parking.SpaceStatus.RESERVED;
			default -> true;
		};
	}

	private void refreshDetails() {
		if (detailsTitle == null) {
			return;
		}
		refreshLotInspector();
		if (selectedSlotNumber == -1) {
			detailsTitle.setText("Parking Space Details");
			detailsSpaceId.setText("\u2014");
			detailsStatus.setText("Select a space");
			detailsStatus.getStyleClass().setAll("lots-details-status-pill");
			setDetailValue(detailsFloor, "\u2014");
			setDetailValue(detailsType, "\u2014");
			setDetailValue(detailsRate, "\u2014");
			setDetailValue(detailsVehicle, "\u2014");
			setDetailValue(detailsBooking, "None");
			reserveButton.setDisable(true);
			return;
		}

		Parking p = parkingSupplier.get();
		Parking.SpaceState state = p == null ? null : findState(p.spaceStates(), selectedSlotNumber);

		detailsSpaceId.setText(String.format("A%02d", selectedSlotNumber));

		if (state == null) {
			detailsStatus.setText("Available");
			detailsStatus.getStyleClass().setAll("lots-details-status-pill", "lots-status-available");
			setDetailValue(detailsFloor, "Level A");
			setDetailValue(detailsType, "Standard");
			setDetailValue(detailsRate, String.format("RM %.2f / hr", selectedCarPark == null ? 5 : selectedCarPark.hourlyRate()));
			setDetailValue(detailsVehicle, "\u2014");
			setDetailValue(detailsBooking, "None");
			reserveButton.setDisable(false);
			return;
		}

		detailsStatus.setText(state.status().displayName());
		detailsStatus.getStyleClass().setAll("lots-details-status-pill", "lots-status-"
				+ state.status().name().toLowerCase());
		setDetailValue(detailsFloor, "Level A");
		setDetailValue(detailsType, "Standard");
		setDetailValue(detailsRate, String.format("RM %.2f / hr",
				selectedCarPark == null ? 5.0 : selectedCarPark.hourlyRate()));
		setDetailValue(detailsVehicle, state.vehicleId() == null ? "\u2014" : state.vehicleId());
		if (state.reservations().isEmpty()) {
			setDetailValue(detailsBooking, "None");
		} else {
			Reservation r = state.reservations().get(0);
			setDetailValue(detailsBooking, "Booked \u2014 " + r.vehicleId());
		}
		reserveButton.setDisable(state.status() != Parking.SpaceStatus.AVAILABLE);
	}

	private void refreshLotInspector() {
		if (selectedCarPark == null || detailsCapacity == null) {
			return;
		}
		setDetailValue(detailsCapacity, selectedCarPark.totalCapacity() + " spaces");
		setDetailValue(detailsOccupancy, String.format(java.util.Locale.ROOT, "%.1f%%",
				selectedCarPark.getOccupancyRate()));
		setDetailValue(detailsLocation, selectedCarPark.location());
		int hash = Math.abs(selectedCarPark.id().hashCode() % 1000);
		setDetailValue(detailsCoordinates, String.format(java.util.Locale.ROOT, "3.%03d° N, 101.%03d° E",
				hash / 2, hash * 7 % 1000));
	}

	private Parking.SpaceState findState(List<Parking.SpaceState> states, int slot) {
		for (Parking.SpaceState s : states) {
			if (s.slotNumber() == slot) {
				return s;
			}
		}
		return null;
	}

	private void setDetailValue(VBox row, String value) {
		Label label = (Label) row.getChildren().get(1);
		label.setText(value);
	}

	/** Small non-JavaFX-free placeholder illustration box for a space. */
	private static final class PlaceholderNode extends Region {
		PlaceholderNode(String text, double w, double h) {
			setPrefSize(w, h);
			setMinSize(w, h);
			setMaxSize(w, h);
			Label label = new Label(text);
			label.getStyleClass().add("lots-illustration-label");
			VBox box = new VBox(8, label);
			box.setAlignment(Pos.CENTER);
			box.setPrefSize(w, h);
			getChildren().add(box);
		}
	}
}
