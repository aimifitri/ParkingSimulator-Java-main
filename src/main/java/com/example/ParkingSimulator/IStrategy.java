package com.example.ParkingSimulator;
import java.util.List;

public interface IStrategy {
	int trouverPlace(List<Boolean> placeOccupe, int nbrPlaces);
}
