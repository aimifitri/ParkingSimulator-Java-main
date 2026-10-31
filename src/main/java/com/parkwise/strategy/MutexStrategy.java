package com.parkwise.strategy;

import java.util.List;

public class MutexStrategy implements ConcurrencyStrategy {
	@Override
	public int findPlace(List<Boolean> occupiedSpaces, int capacity) {
		for (int spaceIndex = 0; spaceIndex < capacity; spaceIndex++) {
			if (!occupiedSpaces.get(spaceIndex)) {
				return spaceIndex;
			}
		}
		return -1;
	}
}
