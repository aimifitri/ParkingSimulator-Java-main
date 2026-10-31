package com.parkwise.strategy;
import java.util.List;

public interface ConcurrencyStrategy {
	int findPlace(List<Boolean> occupiedSpaces, int capacity);
}
