package com.parkwise.model;
import com.parkwise.model.*;
import com.parkwise.view.*;
import com.parkwise.service.*;
import com.parkwise.strategy.*;
import com.parkwise.*;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

// The Car class
public class Car extends Thread {
	private static final Logger LOGGER = Logger.getLogger(Car.class.getName());
	private final String name;
	private final Parking parking;
	private final long parkingDurationMillis;
	private final Reservation reservation;
	private int parkingSpaceNumber = -1;

	public Car(String name, Parking parking) {
		this(name, parking, 10_000L, null);
	}

	public Car(String name, Parking parking, long parkingDurationMillis) {
		this(name, parking, parkingDurationMillis, null);
	}

	public Car(String name, Parking parking, long parkingDurationMillis, Reservation reservation) {
		super(name);
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Vehicle identifier is required");
		}
		if (parkingDurationMillis < 1) {
			throw new IllegalArgumentException("Parking duration must be at least 1 millisecond");
		}
		this.name = name.trim();
		this.parking = Objects.requireNonNull(parking, "Parking is required");
		this.parkingDurationMillis = parkingDurationMillis;
		this.reservation = reservation;
	}
	public String getCarName() {
		return this.name;
	}

	public void setPlace(int parkingSpaceNumber) {
		this.parkingSpaceNumber = parkingSpaceNumber;
	}

	public int getPlace() {
		return this.parkingSpaceNumber;
	}

	public void setParkingSpaceNumber(int parkingSpaceNumber) {
		this.parkingSpaceNumber = parkingSpaceNumber;
	}

	public int getParkingSpaceNumber() {
		return parkingSpaceNumber;
	}

	public long getParkingDurationMillis() {
		return parkingDurationMillis;
	}

	public Reservation getReservation() {
		return reservation;
	}

	@Override
	public void run() {
		boolean parked = false;
		try {
			parking.park(this);
			parked = true;
			Thread.sleep(parkingDurationMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Unable to park " + name, e);
		} finally {
			if (parked) {
				parking.leave(this);
			}
		}
	}
}
