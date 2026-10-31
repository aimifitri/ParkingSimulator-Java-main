package com.example.ParkingSimulator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe repository of car parks and bookings.
 *
 * <p>Holds the multi-lot seed data and keeps the live metrics for the active
 * simulation lot (City Mall) synchronized with {@link Parking} model events by
 * implementing {@link ParkingListener}. The repository contains no JavaFX types,
 * keeping the backend model strictly decoupled from the presentation layer.
 */
public final class CarParkRepository implements ParkingListener {
    public static final String ACTIVE_LOT_ID = "city-mall";

    private final Map<String, CarPark> carParks = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BookingRecord> bookings = new CopyOnWriteArrayList<>();

    public CarParkRepository() {
        seedData();
        seedBookings();
    }

    private void seedData() {
        put(new CarPark("city-mall", "City Mall", "Jalan Ampang, Kuala Lumpur",
                0.12, 5.00, 35, 18, 12, 5));
        put(new CarPark("central-station", "Central Station",
                "Jalan Sultan Ismail, Kuala Lumpur", 0.18, 5.00, 60, 22, 28, 10));
        put(new CarPark("stadium-west", "Stadium West",
                "Jalan Tun Razak, Kuala Lumpur", 0.33, 5.00, 80, 40, 32, 8));
    }

    private void seedBookings() {
        bookings.add(new BookingRecord("PW-20250623-0001", "city-mall", "City Mall",
                "A05 (Level A)", "23 Jun 2025", "09:00 AM", "11:00 AM", 2, 10.00, "Confirmed"));
        bookings.add(new BookingRecord("PW-20250623-0002", "central-station", "Central Station",
                "B12 (Level B)", "23 Jun 2025", "10:30 AM", "01:30 PM", 3, 15.00, "Confirmed"));
        bookings.add(new BookingRecord("PW-20250623-0003", "stadium-west", "Stadium West",
                "C07 (Level C)", "23 Jun 2025", "02:00 PM", "05:00 PM", 3, 15.00, "Completed"));
        bookings.add(new BookingRecord("PW-20250623-0004", "city-mall", "City Mall",
                "A12 (Level A)", "24 Jun 2025", "08:00 AM", "10:00 AM", 2, 10.00, "Cancelled"));
    }

    private void put(CarPark carPark) {
        carParks.put(carPark.id(), carPark);
    }

    /** Returns an immutable copy of all known car parks. */
    public List<CarPark> getAllCarParks() {
        return List.copyOf(carParks.values());
    }

    /** Finds a car park by its id, or {@code null} if not present. */
    public CarPark findCarParkById(String id) {
        return carParks.get(id);
    }

    /** Returns the active simulation lot (City Mall), or {@code null} if absent. */
    public CarPark getActiveLot() {
        return carParks.get(ACTIVE_LOT_ID);
    }

    /** Returns an immutable copy of all stored bookings. */
    public List<BookingRecord> getBookings() {
        return List.copyOf(bookings);
    }

    /** Returns bookings for the given car park id, in insertion order. */
    public List<BookingRecord> getBookingsByCarParkId(String carParkId) {
        if (carParkId == null || carParkId.isBlank()) {
            return List.of();
        }
        return bookings.stream()
                .filter(booking -> carParkId.equals(booking.carParkId()))
                .toList();
    }

    /**
     * Stores a new booking. Returns {@code false} if the booking is null, has a blank id,
     * or duplicates an existing booking id.
     */
    public synchronized boolean addBooking(BookingRecord booking) {
        if (booking == null || booking.bookingId() == null || booking.bookingId().isBlank()) {
            return false;
        }
        if (bookings.stream().anyMatch(existing -> existing.bookingId().equals(booking.bookingId()))) {
            return false;
        }
        bookings.add(booking);
        return true;
    }

    /** Replaces the live counts for the active simulation lot (City Mall). */
    public synchronized void updateCityMallCounts(int occupiedCount, int availableCount, int reservedCount) {
        CarPark current = carParks.get(ACTIVE_LOT_ID);
        if (current == null) {
            return;
        }
        carParks.put(ACTIVE_LOT_ID, new CarPark(
                current.id(), current.name(), current.location(), current.distanceKm(),
                current.hourlyRate(), current.totalCapacity(),
                occupiedCount, availableCount, reservedCount));
    }

    /**
     * Synchronizes the active lot counts from a live {@link Parking} snapshot.
     * Safe to invoke while {@code parking} holds its internal lock because
     * {@link Parking.SpaceSnapshot#spaceSnapshot()} re-acquires the same
     * re-entrant lock on the calling thread.
     */
    public void syncCityMall(Parking parking) {
        if (parking == null) {
            return;
        }
        Parking.SpaceSnapshot snapshot = parking.spaceSnapshot();
        updateCityMallCounts(snapshot.occupiedCount(), snapshot.availableCount(),
                snapshot.reservedCount());
    }

    @Override
    public void carParked(Parking source, int place, String carName, int occupied, int waiting, long waitMillis) {
        syncCityMall(source);
    }

    @Override
    public void carLeft(Parking source, int place, String carName, int occupied, int waiting,
            double averageWaitMillis, int exits) {
        syncCityMall(source);
    }

    @Override
    public void parkingMetrics(Parking source, int occupied, int waiting) {
        syncCityMall(source);
    }
}
