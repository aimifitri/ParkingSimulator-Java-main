package com.example.ParkingSimulator;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Persistent ParkWise navigation and page header around the existing application views. */
final class AppShell extends BorderPane {
	 enum Page {
		DASHBOARD("⌂", "Dashboard", "Set up and view the active parking simulation."),
		PARKING_LOTS("▦", "Parking Lots", "Inspect spaces in the current simulated parking lot."),
		RESERVATIONS("▣", "Reservations", "Manage simulation reservations and their assigned spaces."),
		PREDICTIONS("⌁", "Predictions", "Explore simulation forecasts and historical Birmingham demand."),
		SIMULATION_MONITOR("◉", "Simulation Monitor", "Simulation lifecycle and worker monitoring."),
		REPORTS("▤", "Reports", "Occupancy and reservation reporting."),
		SETTINGS("⚙", "Settings", "Configure the next simulation run.");

		final String icon;
		final String title;
		final String description;

		Page(String icon, String title, String description) {
			this.icon = icon;
			this.title = title;
			this.description = description;
		}
	}

	private final BorderPane workspace = new BorderPane();
	private final Map<Page, Button> navigationButtons = new EnumMap<>(Page.class);
	private final Consumer<Page> navigationHandler;

	AppShell(Consumer<Page> navigationHandler) {
		this.navigationHandler = navigationHandler;
		getStyleClass().add("app-shell");
		setLeft(createSidebar());
		setCenter(workspace);
	}

	void showPage(Page page, Node content) {
		showPage(page, page.title, page.description, content);
	}

	void showPage(Page page, String title, String description, Node content) {
		for (Map.Entry<Page, Button> entry : navigationButtons.entrySet()) {
			Button button = entry.getValue();
			button.getStyleClass().remove("nav-button-active");
			if (entry.getKey() == page) {
				button.getStyleClass().add("nav-button-active");
			}
		}

		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("shell-page-title");
		Label descriptionLabel = new Label(description);
		descriptionLabel.getStyleClass().add("shell-page-description");
		VBox heading = new VBox(4, titleLabel, descriptionLabel);
		HBox header = new HBox(heading);
		header.setAlignment(Pos.CENTER_LEFT);
		header.getStyleClass().add("shell-header");
		workspace.setTop(header);
		workspace.setCenter(content);
		BorderPane.setMargin(content, new Insets(0, 28, 26, 28));
	}

	private VBox createSidebar() {
		VBox sidebar = new VBox(0);
		sidebar.getStyleClass().add("sidebar");
		sidebar.setPrefWidth(232);
		sidebar.setMinWidth(232);
		sidebar.setMaxWidth(232);

		Label brandMark = new Label("P");
		brandMark.getStyleClass().add("brand-mark");
		Label brandName = new Label("ParkWise");
		brandName.getStyleClass().add("brand-name");
		Label brandSubtitle = new Label("Smart Parking");
		brandSubtitle.getStyleClass().add("brand-subtitle");
		VBox brandText = new VBox(2, brandName, brandSubtitle);
		HBox brand = new HBox(11, brandMark, brandText);
		brand.setAlignment(Pos.CENTER_LEFT);
		brand.getStyleClass().add("sidebar-brand");

		Label sectionLabel = new Label("WORKSPACE");
		sectionLabel.getStyleClass().add("sidebar-section-label");
		VBox navigation = new VBox(5);
		navigation.getStyleClass().add("sidebar-navigation");
		for (Page page : Page.values()) {
			Button button = new Button(page.icon + "    " + page.title);
			button.getStyleClass().add("nav-button");
			button.setMaxWidth(Double.MAX_VALUE);
			button.setAlignment(Pos.CENTER_LEFT);
			button.setOnAction(event -> navigationHandler.accept(page));
			navigationButtons.put(page, button);
			navigation.getChildren().add(button);
		}

		Region spacer = new Region();
		VBox.setVgrow(spacer, Priority.ALWAYS);
		Label footer = new Label("PARKING OPERATIONS");
		footer.getStyleClass().add("sidebar-footer");
		sidebar.getChildren().addAll(brand, sectionLabel, navigation, spacer, footer);
		return sidebar;
	}
}
