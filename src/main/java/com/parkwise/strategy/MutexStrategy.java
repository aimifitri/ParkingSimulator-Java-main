package com.parkwise.strategy;
import com.parkwise.model.*;
import com.parkwise.view.*;
import com.parkwise.service.*;
import com.parkwise.strategy.*;
import com.parkwise.*;

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
