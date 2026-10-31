package com.parkwise.view;

import java.util.prefs.Preferences;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Scene;

public final class ThemeManager {
	public enum Mode {
		DARK, LIGHT
	}

	private static final String PREFERENCE_KEY = "parkwise-theme";
	private static final ThemeManager INSTANCE = new ThemeManager();

	private final Preferences preferences = Preferences.userRoot().node("com/parkwise");
	private final ReadOnlyObjectWrapper<Mode> mode = new ReadOnlyObjectWrapper<>();
	private Scene scene;

	private ThemeManager() {
		String saved = preferences.get(PREFERENCE_KEY, "dark");
		mode.set("light".equalsIgnoreCase(saved) ? Mode.LIGHT : Mode.DARK);
	}

	public static ThemeManager getInstance() {
		return INSTANCE;
	}

	public ReadOnlyObjectProperty<Mode> modeProperty() {
		return mode.getReadOnlyProperty();
	}

	public Mode getMode() {
		return mode.get();
	}

	public void setScene(Scene scene) {
		this.scene = scene;
		applyMode(getMode());
	}

	public void toggle() {
		setMode(getMode() == Mode.DARK ? Mode.LIGHT : Mode.DARK);
	}

	public void setMode(Mode newMode) {
		if (newMode == null) {
			newMode = Mode.DARK;
		}
		mode.set(newMode);
		preferences.put(PREFERENCE_KEY, newMode == Mode.LIGHT ? "light" : "dark");
		applyMode(newMode);
	}

	private void applyMode(Mode newMode) {
		if (scene == null) {
			return;
		}
		String lightStylesheet = ThemeManager.class.getResource("/com/parkwise/theme-light.css").toExternalForm();
		scene.getStylesheets().remove(lightStylesheet);
		if (scene.getRoot() != null) {
			scene.getRoot().getStyleClass().removeAll("theme-dark", "theme-light");
			scene.getRoot().getStyleClass().add(newMode == Mode.LIGHT ? "theme-light" : "theme-dark");
		}
		if (newMode == Mode.LIGHT) {
			scene.getStylesheets().add(lightStylesheet);
		}
	}
}
