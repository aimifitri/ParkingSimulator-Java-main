package com.example.ParkingSimulator;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Clearly marks a shell page whose feature work is scheduled for a later phase. */
final class PlaceholderView extends VBox {
	PlaceholderView(String title, String message) {
		Label marker = new Label("COMING IN A LATER PHASE");
		marker.getStyleClass().add("placeholder-marker");
		Label heading = new Label(title);
		heading.getStyleClass().add("section-title");
		Label description = new Label(message);
		description.getStyleClass().add("section-caption");
		description.setWrapText(true);

		getChildren().addAll(marker, heading, description);
		getStyleClass().addAll("panel", "placeholder-view");
		setAlignment(Pos.CENTER_LEFT);
		setSpacing(10);
		setMaxWidth(Double.MAX_VALUE);
	}
}
