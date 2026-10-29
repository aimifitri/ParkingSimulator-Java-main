package com.example.ParkingSimulator;

import java.util.function.IntConsumer;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class CustomRectangle extends StackPane {
	private final int slot;
	private final Label slotNumber = new Label();
	private final Label carIcon = new Label("🚘");
	private final Label carName = new Label();
	private final Label status = new Label("AVAILABLE");
	private IntConsumer selectionHandler = ignored -> { };

	public CustomRectangle(int slot) {
		this.slot = slot;
		slotNumber.setText(String.format("SLOT %02d", slot));
		slotNumber.getStyleClass().add("slot-number");
		carIcon.getStyleClass().add("car-icon");
		carName.getStyleClass().add("car-name");
		status.getStyleClass().add("slot-status");

		VBox content = new VBox(4, slotNumber, carIcon, carName, status);
		content.setAlignment(Pos.CENTER);
		getChildren().add(content);
		getStyleClass().add("parking-slot");
		setAccessibleRole(AccessibleRole.BUTTON);
		setFocusTraversable(true);
		setOnMouseClicked(event -> selectionHandler.accept(this.slot));
		setOnKeyPressed(new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
					selectionHandler.accept(CustomRectangle.this.slot);
					event.consume();
				}
			}
		});
		render(Parking.SpaceStatus.AVAILABLE, null, null, false);
	}

	public void setSelectionHandler(IntConsumer selectionHandler) {
		this.selectionHandler = selectionHandler == null ? ignored -> { } : selectionHandler;
	}

	public void render(Parking.SpaceStatus spaceStatus, String vehicleId,
			Reservation reservation, boolean selected) {
		getStyleClass().removeAll("available-slot", "occupied-slot", "reserved-slot", "selected-slot");
		getStyleClass().add(switch (spaceStatus) {
			case AVAILABLE -> "available-slot";
			case OCCUPIED -> "occupied-slot";
			case RESERVED -> "reserved-slot";
		});
		if (selected) {
			getStyleClass().add("selected-slot");
		}

		status.setText(spaceStatus.displayName().toUpperCase(java.util.Locale.ROOT));
		boolean showVehicle = spaceStatus == Parking.SpaceStatus.OCCUPIED && vehicleId != null;
		boolean showReservation = spaceStatus == Parking.SpaceStatus.RESERVED && reservation != null;
		carIcon.setVisible(showVehicle || showReservation);
		carIcon.setManaged(showVehicle || showReservation);
		carIcon.setText(showVehicle ? "🚘" : "◷");
		carName.setText(showVehicle ? vehicleId
				: showReservation ? reservation.vehicleId() + " · " + reservation.startOffsetSeconds() + "s" : "");
		carName.setVisible(showVehicle || showReservation);
		carName.setManaged(showVehicle || showReservation);
		setAccessibleText("Space " + slot + ", " + spaceStatus.displayName()
				+ (vehicleId == null ? "" : ", occupied by " + vehicleId)
				+ (reservation == null ? "" : ", reserved for " + reservation.vehicleId())
				+ (selected ? ", selected" : ""));
	}
}
