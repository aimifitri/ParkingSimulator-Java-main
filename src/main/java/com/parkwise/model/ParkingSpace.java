package com.parkwise.model;
import com.parkwise.model.*;
import com.parkwise.view.*;
import com.parkwise.service.*;
import com.parkwise.strategy.*;
import com.parkwise.*;

import java.util.Objects;

public record ParkingSpace(int number, Parking.SpaceStatus status, String vehicleId) {
	public ParkingSpace {
		if (number < 1) {
			throw new IllegalArgumentException("Parking space numbers must be positive");
		}
		Objects.requireNonNull(status, "Parking space status is required");
	}
}
