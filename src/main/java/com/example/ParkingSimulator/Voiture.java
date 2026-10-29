package com.example.ParkingSimulator;
import java.util.logging.Level;
import java.util.logging.Logger;

// The Car class
public class Voiture extends Thread {
	private static final Logger LOGGER = Logger.getLogger(Voiture.class.getName());
	private final String nom;
	private final Parking parking;
	private final long parkingDurationMillis;
	private final Reservation reservation;
	private int place;

	public Voiture(String nom, Parking parking) {
		this(nom, parking, 10_000L, null);
	}

	public Voiture(String nom, Parking parking, long parkingDurationMillis) {
		this(nom, parking, parkingDurationMillis, null);
	}

	public Voiture(String nom, Parking parking, long parkingDurationMillis, Reservation reservation) {
		if (parkingDurationMillis < 1) {
			throw new IllegalArgumentException("Parking duration must be at least 1 millisecond");
		}
		this.nom = nom;
		this.parking = parking;
		this.parkingDurationMillis = parkingDurationMillis;
		this.reservation = reservation;
	}
	public String getNom() {
		return this.nom;
	}

	public void setPlace(int place) {
		this.place = place;
	}

	public int getPlace() {
		return this.place;
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
			parking.stationner(this);
			parked = true;
			Thread.sleep(parkingDurationMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Unable to park " + nom, e);
		} finally {
			if (parked) {
				parking.sortir(this);
			}
		}
	}
}
