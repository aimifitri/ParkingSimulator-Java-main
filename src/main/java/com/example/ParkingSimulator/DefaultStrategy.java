package com.example.ParkingSimulator;
import java.util.List;

public class DefaultStrategy implements IStrategy {
	@Override
	public int trouverPlace(List<Boolean> placeOccupe, int nbrPlaces) {
		for (int place = 0; place < nbrPlaces; place++) {
			if (!placeOccupe.get(place)) {
				return place;
			}
		}
		return -1;
	}
}
