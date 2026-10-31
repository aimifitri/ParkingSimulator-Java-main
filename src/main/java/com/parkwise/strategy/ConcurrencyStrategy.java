package com.parkwise.strategy;
import com.parkwise.model.*;
import com.parkwise.view.*;
import com.parkwise.service.*;
import com.parkwise.strategy.*;
import com.parkwise.*;
import java.util.List;

public interface ConcurrencyStrategy {
	int findPlace(List<Boolean> occupiedSpaces, int capacity);
}
