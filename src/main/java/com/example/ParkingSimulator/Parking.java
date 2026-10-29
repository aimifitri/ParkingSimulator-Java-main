package com.example.ParkingSimulator;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

public class Parking {
	public enum SpaceStatus {
		AVAILABLE("Available"), OCCUPIED("Occupied"), RESERVED("Reserved");

		private final String displayName;

		SpaceStatus(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}
	}

	public record SpaceState(int slotNumber, SpaceStatus status, String vehicleId,
			List<Reservation> reservations) {
		public SpaceState {
			if (slotNumber < 1) {
				throw new IllegalArgumentException("Parking slot numbers must be positive");
			}
			Objects.requireNonNull(status, "A parking space must have a status");
			reservations = List.copyOf(reservations);
		}
	}

	/** Immutable current-state inventory; each space has one status, even if it has a booking. */
	public record SpaceSnapshot(List<SpaceState> spaces, int waitingCount) {
		public SpaceSnapshot(List<SpaceState> spaces) {
			this(spaces, 0);
		}

		public SpaceSnapshot {
			spaces = List.copyOf(spaces);
			Set<Integer> slotNumbers = new HashSet<>();
			for (SpaceState space : spaces) {
				if (!slotNumbers.add(space.slotNumber())) {
					throw new IllegalArgumentException("A parking snapshot cannot contain duplicate slots");
				}
			}
			if (waitingCount < 0) {
				throw new IllegalArgumentException("Waiting count cannot be negative");
			}
		}

		public int totalCount() {
			return spaces.size();
		}

		public int availableCount() {
			return count(SpaceStatus.AVAILABLE);
		}

		public int occupiedCount() {
			return count(SpaceStatus.OCCUPIED);
		}

		public int reservedCount() {
			// Counts spaces awaiting a reserved arrival, not reservation records or future bookings.
			return count(SpaceStatus.RESERVED);
		}

		private int count(SpaceStatus status) {
			return (int) spaces.stream().filter(space -> space.status() == status).count();
		}
	}

	private static final Logger LOGGER = Logger.getLogger(Parking.class.getName());
	private final Semaphore placesSemaphore;
	private final ReentrantLock placesLock = new ReentrantLock(true);
	private final Condition placeAvailable = placesLock.newCondition();
	private final List<Boolean> placeOccupe = new ArrayList<>();
	private final List<String> occupyingVehicleIds = new ArrayList<>();
	private final Map<Voiture, Long> waitTimes = new HashMap<>();
	private final Set<String> completedReservationIds = new HashSet<>();
	private final IStrategy strategy;
	private final int nbrPlaces;
	private final ParkingListener listener;
	private final List<Reservation> reservations;
	private final long simulationStartedNanos;
	private final Set<String> activeReservationIds = new HashSet<>();
	private final int syncStrategy; // 1 for Semaphore, 2 for Mutex
	private volatile boolean active = true;
	private int waitingCount;
	private int completedExits;
	private long completedWaitTotal;

	public Parking(int nbrPlaces, IStrategy strategy, ParkingListener listener, int syncStrategy) {
		this(nbrPlaces, strategy, listener, syncStrategy, List.of(), System.nanoTime());
	}

	public Parking(int nbrPlaces, IStrategy strategy, ParkingListener listener, int syncStrategy,
			List<Reservation> reservations, long simulationStartedNanos) {
		// Initialize the semaphore and mutex
		this.placesSemaphore = new Semaphore(nbrPlaces, true);
		this.syncStrategy = syncStrategy;
		// Initialize the list of occupied spaces
		for (int i = 0; i < nbrPlaces; i++) {
			placeOccupe.add(false);
			occupyingVehicleIds.add(null);
		}
		// Initialize the strategy
		this.strategy = strategy;
		// Initialize the number of parking spaces
		this.nbrPlaces = nbrPlaces;
		// The listener is optional, allowing the parking model to run without a UI.
		this.listener = listener;
		this.reservations = Collections.unmodifiableList(new ArrayList<>(reservations));
		this.simulationStartedNanos = simulationStartedNanos;
	}

	public void stationner(Voiture voiture) throws Exception {
		long startAttemptTime = System.currentTimeMillis();
		LOGGER.info("Car " + voiture.getNom() + " is trying to park");
		boolean permitAcquired = false;
		boolean assigned = false;
		int place = -1;
		long parkingWaitTime;
		boolean reservedCar = voiture.getReservation() != null;
		try {
			if (syncStrategy == 1 && !reservedCar) {
				acquireSemaphorePermit();
				permitAcquired = true;
			}

			placesLock.lockInterruptibly();
			try {
				while (active && (place = findPlace(voiture)) < 0) {
					waitingCount++;
					publishMetrics();
					try {
						long nextReservationEnd = nanosUntilNextReservationEnd(voiture);
						if (nextReservationEnd > 0) {
							placeAvailable.awaitNanos(nextReservationEnd);
						} else {
							placeAvailable.await();
						}
					} finally {
						waitingCount--;
						publishMetrics();
					}
				}
				if (!active) {
					throw new InterruptedException("Parking simulation has been stopped");
				}
				place = findPlace(voiture);
				if (place < 0 || place >= nbrPlaces) {
					throw new IllegalStateException("No available parking slot was found after synchronization");
				}
				placeOccupe.set(place, true);
				occupyingVehicleIds.set(place, voiture.getNom());
				if (voiture.getReservation() != null) {
					activeReservationIds.add(voiture.getReservation().id());
				}
				voiture.setPlace(place);
				parkingWaitTime = System.currentTimeMillis() - startAttemptTime;
				waitTimes.put(voiture, parkingWaitTime);
				assigned = true;
				if (listener != null) {
					listener.carParked(this, place, voiture.getNom(), occupiedCount(), waitingCount, parkingWaitTime);
				}
			} finally {
				placesLock.unlock();
			}
		} finally {
			if (syncStrategy == 1 && permitAcquired && !assigned) {
				placesSemaphore.release();
			}
		}

		LOGGER.info("Car " + voiture.getNom() + " has parked!");
		LOGGER.info("Wait time for car " + voiture.getNom() + ": " + parkingWaitTime + " milliseconds");
	}

	public void sortir(Voiture voiture) {
		LOGGER.info("Car " + voiture.getNom() + " is trying to leave");
		int place = voiture.getPlace();
		int occupied;
		int waiting;
		int exits;
		double averageWait;
		boolean releasePermit = false;
		placesLock.lock();
		try {
			if (place < 0 || place >= nbrPlaces || !placeOccupe.get(place)) {
				return;
			}
			placeOccupe.set(place, false);
			occupyingVehicleIds.set(place, null);
			if (voiture.getReservation() != null) {
				activeReservationIds.remove(voiture.getReservation().id());
				completedReservationIds.add(voiture.getReservation().id());
			}
			releasePermit = syncStrategy == 1 && voiture.getReservation() == null;
			Long waitTime = waitTimes.remove(voiture);
			if (waitTime != null) {
				completedWaitTotal += waitTime;
				completedExits++;
			}
			occupied = occupiedCount();
			waiting = waitingCount;
			exits = completedExits;
			averageWait = completedExits == 0 ? 0 : (double) completedWaitTotal / completedExits;
			placeAvailable.signalAll();
			if (listener != null) {
				listener.carLeft(this, place, voiture.getNom(), occupied, waiting, averageWait, exits);
			}
		} finally {
			placesLock.unlock();
			if (releasePermit) {
				placesSemaphore.release();
			}
		}
		LOGGER.info("Car " + voiture.getNom() + " has left!");
	}

	private void acquireSemaphorePermit() throws InterruptedException {
		boolean acquired = false;
		boolean waiting = false;
		placesLock.lockInterruptibly();
		try {
			acquired = placesSemaphore.tryAcquire();
			if (!acquired) {
				waitingCount++;
				waiting = true;
				publishMetrics();
			}
		} finally {
			placesLock.unlock();
		}
		if (!acquired) {
			try {
				while (active && !(acquired = placesSemaphore.tryAcquire(100, java.util.concurrent.TimeUnit.MILLISECONDS))) {
					// Periodically recheck active so shutdown also releases semaphore waiters.
				}
				if (!active) {
					if (acquired) {
						placesSemaphore.release();
					}
					throw new InterruptedException("Parking simulation has been stopped");
				}
			} finally {
				if (waiting) {
					placesLock.lock();
					try {
						waitingCount--;
						publishMetrics();
					} finally {
						placesLock.unlock();
					}
				}
			}
		}
	}

	private int occupiedCount() {
		int occupied = 0;
		for (boolean slotOccupied : placeOccupe) {
			if (slotOccupied) {
				occupied++;
			}
		}
		return occupied;
	}

	/** Returns a consistent occupancy and reservation snapshot for the current simulation time. */
	public SpaceSnapshot spaceSnapshot() {
		placesLock.lock();
		try {
			return new SpaceSnapshot(buildSpaceStates(placeOccupe, occupyingVehicleIds, reservations,
					completedReservationIds, activeReservationIds), waitingCount);
		} finally {
			placesLock.unlock();
		}
	}

	public List<SpaceState> spaceStates() {
		return spaceSnapshot().spaces();
	}

	public int capacity() {
		return nbrPlaces;
	}

	public boolean isActive() {
		return active;
	}

	/** Returns the planned inventory before a simulation has started, with every space unoccupied. */
	static SpaceSnapshot plannedSpaceSnapshot(int capacity, List<Reservation> reservations) {
		if (capacity < 1) {
			throw new IllegalArgumentException("Parking capacity must be positive");
		}
		List<Boolean> emptySpaces = new ArrayList<>(Collections.nCopies(capacity, false));
		List<String> emptyVehicleIds = new ArrayList<>(Collections.nCopies(capacity, null));
		return new SpaceSnapshot(buildSpaceStates(emptySpaces, emptyVehicleIds, reservations, Set.of(), Set.of()));
	}

	static List<SpaceState> plannedSpaceStates(int capacity, List<Reservation> reservations) {
		return plannedSpaceSnapshot(capacity, reservations).spaces();
	}

	private static List<SpaceState> buildSpaceStates(List<Boolean> occupied,
			List<String> vehicleIds, List<Reservation> reservations, Set<String> completedReservations,
			Set<String> activeReservations) {
		List<SpaceState> states = new ArrayList<>(occupied.size());
		for (int slot = 0; slot < occupied.size(); slot++) {
			final int slotNumber = slot + 1;
			// Future bookings claim a slot until arrival. Once a reserved vehicle parks, its booking
			// is represented by the occupied vehicle state, then removed after departure.
			List<Reservation> upcoming = reservations.stream()
					.filter(reservation -> reservation.slotNumber() == slotNumber)
					.filter(reservation -> !completedReservations.contains(reservation.id()))
					.filter(reservation -> !activeReservations.contains(reservation.id()))
					.toList();
			SpaceStatus status = occupied.get(slot) ? SpaceStatus.OCCUPIED
					: upcoming.isEmpty() ? SpaceStatus.AVAILABLE : SpaceStatus.RESERVED;
			states.add(new SpaceState(slotNumber, status, vehicleIds.get(slot), upcoming));
		}
		return List.copyOf(states);
	}

	private int findPlace(Voiture voiture) {
		Reservation reservation = voiture.getReservation();
		if (reservation != null) {
			int reservedIndex = reservation.slotNumber() - 1;
			return reservedIndex >= 0 && reservedIndex < nbrPlaces && !placeOccupe.get(reservedIndex)
					? reservedIndex : -1;
		}

		long nowMillis = Math.max(0, (System.nanoTime() - simulationStartedNanos) / 1_000_000L);
		long parkingEndsAt = nowMillis + voiture.getParkingDurationMillis();
		List<Boolean> unavailable = new ArrayList<>(nbrPlaces);
		for (int slot = 0; slot < nbrPlaces; slot++) {
			boolean blocked = placeOccupe.get(slot);
			if (!blocked) {
				for (Reservation booking : reservations) {
					if (booking.slotNumber() == slot + 1
							&& !completedReservationIds.contains(booking.id())) {
						long reservedStart = booking.startOffsetSeconds() * 1000L;
						long reservedEnd = (booking.startOffsetSeconds() + (long) booking.durationSeconds()) * 1000L;
						boolean overlapsWindow = nowMillis < reservedEnd && reservedStart < parkingEndsAt;
						boolean overdueUnservedBooking = nowMillis >= reservedStart;
						if (overlapsWindow || overdueUnservedBooking) {
							blocked = true;
							break;
						}
					}
				}
			}
			unavailable.add(blocked);
		}
		return strategy.trouverPlace(unavailable, nbrPlaces);
	}

	private long nanosUntilNextReservationEnd(Voiture voiture) {
		if (voiture.getReservation() != null) {
			return 0;
		}
		long nowMillis = Math.max(0, (System.nanoTime() - simulationStartedNanos) / 1_000_000L);
		long parkingEndsAt = nowMillis + voiture.getParkingDurationMillis();
		long nextEnd = Long.MAX_VALUE;
		for (Reservation booking : reservations) {
			if (completedReservationIds.contains(booking.id())) {
				continue;
			}
			long reservedStart = booking.startOffsetSeconds() * 1000L;
			long reservedEnd = (booking.startOffsetSeconds() + (long) booking.durationSeconds()) * 1000L;
			if (nowMillis < reservedEnd && reservedStart < parkingEndsAt) {
				nextEnd = Math.min(nextEnd, reservedEnd);
			}
		}
		return nextEnd == Long.MAX_VALUE ? 0
				: java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(1, nextEnd - nowMillis));
	}

	private void publishMetrics() {
		if (listener != null) {
			listener.parkingMetrics(this, occupiedCount(), waitingCount);
		}
	}

	public void shutdown() {
		placesLock.lock();
		try {
			active = false;
			placeAvailable.signalAll();
		} finally {
			placesLock.unlock();
		}
	}

	static synchronized void configureLogging() {
		if (LOGGER.getHandlers().length > 0) {
			return;
		}
		try {
			String configuredPath = System.getProperty("parkflow.log");
			Path logPath = configuredPath == null || configuredPath.isBlank()
					? Path.of(System.getProperty("user.home"), ".parkflow", "parking.log")
					: Path.of(configuredPath).toAbsolutePath();
			Path logDirectory = logPath.getParent();
			if (logDirectory != null) {
				Files.createDirectories(logDirectory);
			}
			FileHandler fileHandler = new FileHandler(logPath.toString(), 1_000_000, 3, true);
			fileHandler.setFormatter(new SimpleFormatter());
			LOGGER.addHandler(fileHandler);
			LOGGER.info("Parking simulator logging configured");
		} catch (IOException | SecurityException e) {
			LOGGER.warning("Could not configure the parking log file: " + e.getMessage());
		}
	}
}
