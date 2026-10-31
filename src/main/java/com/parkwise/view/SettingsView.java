package com.parkwise.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/** Tabbed settings surface for simulation setup and display preferences. */
public class SettingsView extends BorderPane {
	private final TabPane tabs = new TabPane();
	private final VBox pageContent = new VBox(16);
	private final ToggleButton compactMode = new ToggleButton("Compact mode");
	private final Consumer<String> onDefaultLocationChanged;
	private static final String DEFAULT_LOCATION_KEY = "parkwise-default-car-park";

	public SettingsView(Node systemSetup, Node simulationTimings, Node displaySettings,
			Node aboutTeam, Node launchActions) {
		this(systemSetup, simulationTimings, displaySettings, aboutTeam, launchActions, null);
	}

	public SettingsView(Node systemSetup, Node simulationTimings, Node displaySettings,
			Node aboutTeam, Node launchActions, Consumer<String> onDefaultLocationChanged) {
		this.onDefaultLocationChanged = onDefaultLocationChanged;
		build(systemSetup, simulationTimings, displaySettings, aboutTeam, launchActions);
	}

	/** Preserves the original no-argument construction API. */
	public SettingsView() {
		this(null, null, null, null, null, null);
	}

	private void build(Node systemSetup, Node simulationTimings, Node displaySettings,
			Node aboutTeam, Node launchActions) {
		getStyleClass().add("settings-view");
		setPadding(new Insets(0));

		tabs.getStyleClass().add("settings-tabs");
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabs.getTabs().addAll(
				createTab("System Setup & Concurrency", systemSetup),
				createTab("Simulation Timings", simulationTimings),
				createTab("Display & Preferences", buildDisplaySettings(displaySettings)),
				createTab("About & Architecture", aboutTeam));

		pageContent.getStyleClass().add("settings-page-content");
		pageContent.setPadding(new Insets(20, 24, 20, 24));
		pageContent.getChildren().add(tabs);
		VBox.setVgrow(tabs, Priority.ALWAYS);
		if (launchActions != null) {
			HBox actions = new HBox(launchActions);
			actions.getStyleClass().add("settings-actions");
			actions.setAlignment(Pos.CENTER_RIGHT);
			pageContent.getChildren().add(actions);
		}

		ScrollPane scroll = new ScrollPane(pageContent);
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scroll.getStyleClass().add("settings-scroll");
		setCenter(scroll);
	}

	private Tab createTab(String title, Node content) {
		Tab tab = new Tab(title);
		VBox panel = new VBox(16);
		panel.getStyleClass().add("settings-tab-panel");
		panel.setPadding(new Insets(22));
		if (content != null) {
			panel.getChildren().add(content);
		}
		ScrollPane tabScroll = new ScrollPane(panel);
		tabScroll.setFitToWidth(true);
		tabScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		tabScroll.getStyleClass().add("settings-tab-scroll");
		tab.setContent(tabScroll);
		return tab;
	}

	private Node buildDisplaySettings(Node additionalSettings) {
		Label title = new Label("Display preferences");
		title.getStyleClass().add("settings-section-title");
		Label description = new Label("Choose how ParkWise looks on this device.");
		description.getStyleClass().add("settings-description");
		ToggleButton themeToggle = new ToggleButton();
		themeToggle.getStyleClass().add("settings-preference-toggle");
		updateThemeToggle(themeToggle);
		themeToggle.setSelected(ThemeManager.getInstance().getMode() == ThemeManager.Mode.LIGHT);
		themeToggle.setOnAction(event -> ThemeManager.getInstance().setMode(themeToggle.isSelected()
				? ThemeManager.Mode.LIGHT : ThemeManager.Mode.DARK));
		ThemeManager.getInstance().modeProperty().addListener((observable, oldMode, newMode) -> {
			themeToggle.setSelected(newMode == ThemeManager.Mode.LIGHT);
			updateThemeToggle(themeToggle);
		});

		HBox themeRow = preferenceRow("Color theme", "Switch between light and dark appearance.", themeToggle);
		compactMode.getStyleClass().add("settings-preference-toggle");
		compactMode.setOnAction(event -> {
			getStyleClass().remove("compact-mode");
			if (compactMode.isSelected()) {
				getStyleClass().add("compact-mode");
			}
		});
		HBox compactRow = preferenceRow("Compact mode", "Reduce spacing in this settings panel.", compactMode);
		ComboBox<String> defaultLocation = new ComboBox<>(FXCollections.observableArrayList(
				"City Mall", "Central Station", "Stadium West"));
		defaultLocation.setValue(Preferences.userRoot().node("com/parkwise")
				.get(DEFAULT_LOCATION_KEY, "City Mall"));
		defaultLocation.setMaxWidth(220);
		defaultLocation.setOnAction(event -> {
			String selectedLocation = defaultLocation.getValue();
			Preferences.userRoot().node("com/parkwise").put(DEFAULT_LOCATION_KEY, selectedLocation);
			if (onDefaultLocationChanged != null) {
				onDefaultLocationChanged.accept(selectedLocation);
			}
		});
		if (onDefaultLocationChanged != null) {
			onDefaultLocationChanged.accept(defaultLocation.getValue());
		}
		HBox locationRow = preferenceRow("Default car park", "Choose the location to show by default.", defaultLocation);
		VBox content = new VBox(8, title, description, new Separator(), themeRow, compactRow, locationRow);
		content.getStyleClass().add("settings-display-content");
		if (additionalSettings != null) {
			content.getChildren().add(additionalSettings);
		}
		return content;
	}

	private HBox preferenceRow(String title, String description, Node control) {
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("settings-preference-title");
		Label descriptionLabel = new Label(description);
		descriptionLabel.getStyleClass().add("settings-description");
		VBox labels = new VBox(4, titleLabel, descriptionLabel);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox row = new HBox(16, labels, spacer, control);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("settings-preference-row");
		return row;
	}

	private void updateThemeToggle(ToggleButton toggle) {
		toggle.setText(ThemeManager.getInstance().getMode() == ThemeManager.Mode.LIGHT ? "Light" : "Dark");
	}
}
