package com.parkwise.model;
import com.parkwise.model.*;
import com.parkwise.view.*;
import com.parkwise.service.*;
import com.parkwise.strategy.*;
import com.parkwise.*;

/** Receives parking state changes without coupling the parking model to a UI toolkit. */
public interface ParkingListener {
	void carParked(Parking source, int place, String carName, int occupied, int waiting, long waitMillis);

	void carLeft(Parking source, int place, String carName, int occupied, int waiting,
			double averageWaitMillis, int exits);

	void parkingMetrics(Parking source, int occupied, int waiting);
}
