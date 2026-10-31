package com.parkwise.view;
import com.parkwise.model.*;
import com.parkwise.view.*;
import com.parkwise.service.*;
import com.parkwise.strategy.*;
import com.parkwise.*;

import java.util.function.IntConsumer;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class CustomRectangle extends StackPane {
	private final int slot;
	private final Label slotNumber = new Label();
	private final Pane carIllustration = createCarIllustration();
	private final Label reservationIcon = new Label("◷");
	private final StackPane vehicleVisual = new StackPane(carIllustration, reservationIcon);
	private final Label carName = new Label();
	private final Label status = new Label("AVAILABLE");
	private IntConsumer selectionHandler = ignored -> { };

	public CustomRectangle(int slot) {
		this.slot = slot;
		slotNumber.setText(String.format("SLOT %02d", slot));
		slotNumber.getStyleClass().add("slot-number");
		reservationIcon.getStyleClass().add("reservation-icon");
		carName.getStyleClass().add("car-name");
		status.getStyleClass().add("slot-status");

		vehicleVisual.setMinHeight(30);
		vehicleVisual.setPrefHeight(30);
		vehicleVisual.setMaxHeight(30);
		VBox content = new VBox(4, slotNumber, vehicleVisual, carName, status);
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

	private static Pane createCarIllustration() {
		Pane car = new Pane();
		car.setMinSize(52, 30);
		car.setPrefSize(52, 30);
		car.setMaxSize(52, 30);
		car.getStyleClass().add("vehicle-illustration");

		Rectangle leftWheel = new Rectangle(8, 5, 5, 20);
		Rectangle rightWheel = new Rectangle(39, 5, 5, 20);
		leftWheel.setArcWidth(3);
		leftWheel.setArcHeight(3);
		rightWheel.setArcWidth(3);
		rightWheel.setArcHeight(3);
		leftWheel.getStyleClass().add("vehicle-wheel");
		rightWheel.getStyleClass().add("vehicle-wheel");
		Rectangle body = new Rectangle(5, 6, 42, 18);
		body.setArcWidth(9);
		body.setArcHeight(9);
		body.getStyleClass().add("vehicle-body");
		Rectangle cabin = new Rectangle(14, 8, 24, 14);
		cabin.setArcWidth(6);
		cabin.setArcHeight(6);
		cabin.getStyleClass().add("vehicle-cabin");
		Rectangle windshield = new Rectangle(17, 9, 7, 12);
		windshield.setArcWidth(3);
		windshield.setArcHeight(3);
		windshield.getStyleClass().add("vehicle-window");
		Rectangle rearWindow = new Rectangle(28, 9, 7, 12);
		rearWindow.setArcWidth(3);
		rearWindow.setArcHeight(3);
		rearWindow.getStyleClass().add("vehicle-window");
		Rectangle frontLight = new Rectangle(44, 9, 2, 4);
		Rectangle rearLight = new Rectangle(44, 17, 2, 4);
		frontLight.getStyleClass().add("vehicle-light");
		rearLight.getStyleClass().add("vehicle-light");
		car.getChildren().addAll(body, leftWheel, rightWheel, cabin, windshield, rearWindow, frontLight, rearLight);
		return car;
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
		carIllustration.setVisible(showVehicle);
		carIllustration.setManaged(showVehicle);
		reservationIcon.setVisible(showReservation);
		reservationIcon.setManaged(showReservation);
		vehicleVisual.setVisible(showVehicle || showReservation);
		vehicleVisual.setManaged(showVehicle || showReservation);
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
