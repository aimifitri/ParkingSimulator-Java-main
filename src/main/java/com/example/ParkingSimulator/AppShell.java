package com.example.ParkingSimulator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Persistent ParkWise navigation shell: top header bar, sidebar, and page workspace. */
final class AppShell extends BorderPane {
	enum Page {
		DASHBOARD("⌂", "Dashboard", "Set up and view the active parking simulation."),
		PARKING_LOTS("▦", "Parking Lots", "Inspect spaces in the current simulated parking lot."),
		RESERVATIONS("▣", "Reservations", "Manage simulation reservations and their assigned spaces."),
		PREDICTIONS("⌁", "Predictions & Recommendations", "Explore simulation forecasts and historical Birmingham demand."),
		SIMULATION_MONITOR("◉", "Simulation & Thread Monitor", "Simulation lifecycle and worker monitoring."),
		REPORTS("▤", "Reports & Analytics", "Occupancy and reservation reporting."),
		SETTINGS("⚙", "Settings", "Configure the next simulation run.");

		final String icon;
		final String title;
		final String description;

		Page(String icon, String title, String description) {
			this.icon = icon;
			this.title = title;
			this.description = description;
		}

		String breadcrumb() {
			return switch (this) {
				case DASHBOARD -> title + " (Home)";
				default -> title;
			};
		}
	}

	private final BorderPane workspace = new BorderPane();
	private final BorderPane mainLayout = new BorderPane();
	private final Map<Page, Button> navigationButtons = new EnumMap<>(Page.class);
	private final Consumer<Page> navigationHandler;

	private final Label breadcrumbLabel = new Label();
	private final TextField searchField = new TextField();
	private final Label clockLabel = new Label();
	private final Timeline clock;

	AppShell(Consumer<Page> navigationHandler) {
		this.navigationHandler = navigationHandler;
		getStyleClass().add("app-shell");
		setLeft(createSidebar());
		setCenter(mainLayout);

		DateTimeFormatter clockFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy | hh:mm a");
		clockLabel.setText(clockFormatter.format(LocalDateTime.now()));
		clock = new Timeline(new KeyFrame(Duration.seconds(1), event ->
				clockLabel.setText(clockFormatter.format(LocalDateTime.now()))));
		clock.setCycleCount(Timeline.INDEFINITE);

		mainLayout.setTop(createTopHeaderBar());
		mainLayout.setCenter(workspace);

		clock.play();
	}

	void showPage(Page page, Node content) {
		showPage(page, page.title, page.description, content);
	}

	void showPage(Page page, String title, String description, Node content) {
		breadcrumbLabel.setText(page.breadcrumb());
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

	private HBox createTopHeaderBar() {
		HBox bar = new HBox(16);
		bar.getStyleClass().add("top-header-bar");
		bar.setPadding(new Insets(10, 22, 10, 22));
		bar.setAlignment(Pos.CENTER_LEFT);

		breadcrumbLabel.getStyleClass().add("top-header-breadcrumb");

		searchField.setPromptText("Search location, car park...");
		searchField.getStyleClass().add("top-header-search");
		searchField.setPrefWidth(280);
		searchField.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(searchField, Priority.ALWAYS);

		Label weatherChip = new Label("28°C  Kuala Lumpur");
		weatherChip.getStyleClass().add("top-header-weather");

		Label userPill = new Label("Driver Online");
		userPill.getStyleClass().add("top-header-user");

		Region rightSpacer = new Region();
		HBox.setHgrow(rightSpacer, Priority.ALWAYS);

		HBox right = new HBox(12, clockLabel, weatherChip, userPill);
		right.setAlignment(Pos.CENTER_RIGHT);

		bar.getChildren().addAll(breadcrumbLabel, searchField, rightSpacer, right);
		return bar;
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
