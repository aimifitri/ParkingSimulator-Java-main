package com.parkwise.strategy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrencyStrategyTest {
    private final SemaphoreStrategy strategy = new SemaphoreStrategy();

    @Test
    void returnsFirstAvailableSlot() {
        List<Boolean> occupied = new ArrayList<>(List.of(true, false, false, false));

        assertEquals(1, strategy.findPlace(occupied, occupied.size()));
    }

    @Test
    void returnsNegativeOneWhenAllSlotsAreOccupied() {
        List<Boolean> occupied = new ArrayList<>(List.of(true, true, true));

        assertEquals(-1, strategy.findPlace(occupied, occupied.size()));
    }

    @Test
    void respectsSlotsOutsideTheGridLayout() {
        List<Boolean> occupied = new ArrayList<>(List.of(true, true, true, false));

        assertEquals(3, strategy.findPlace(occupied, occupied.size()));
    }
}
