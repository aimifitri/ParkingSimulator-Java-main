package com.parkwise.view;

import javafx.scene.layout.BorderPane;
import javafx.scene.Node;

public class ReportsView extends BorderPane {
	public ReportsView() {
		getStyleClass().add("reports-view");
	}

	public void setContent(Node content) {
		setCenter(content);
	}
}
