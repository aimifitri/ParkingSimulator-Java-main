package com.parkwise.view;
import com.parkwise.model.*;
import com.parkwise.view.*;
import com.parkwise.service.*;
import com.parkwise.strategy.*;
import com.parkwise.*;

import javafx.scene.layout.BorderPane;
import javafx.scene.Node;

public class ReportsView extends BorderPane {
	public ReportsView() {
		setStyle("-fx-background-color: transparent;");
	}

	public void setContent(Node content) {
		setCenter(content);
	}
}
