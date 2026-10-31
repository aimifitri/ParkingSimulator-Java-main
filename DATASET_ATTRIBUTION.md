# Dataset attribution

The application bundles `src/main/resources/com/parkwise/parking-birmingham.csv` for offline historical occupancy prediction.

- **Dataset:** Parking Birmingham, donated by Daniel Stolfi to the UCI Machine Learning Repository.
- **Source:** [UCI dataset page](https://archive.ics.uci.edu/dataset/482/parking%2Bbirmingham); [DOI 10.24432/C51K5Z](https://doi.org/10.24432/C51K5Z).
- **Original data provider noted by UCI:** Birmingham City Council / NCP car parks.
- **License stated by UCI:** Creative Commons Attribution 4.0 International (CC BY 4.0).
- **Dataset period and fields:** observations from October 4 to December 19, 2016, with car park ID, capacity, occupancy, and timestamp.
- **Changes made for this project:** bundled the CSV unchanged. The Java loader excludes malformed rows and occupancy observations below zero or greater than capacity before building the prediction profile. Prediction groups valid values by car park, weekday, and nearest half-hour.
- **Use limitations:** this is historical Birmingham data. It is not a live sensor feed and does not represent current occupancy in the user's city.

Suggested attribution: “Parking Birmingham dataset by Daniel Stolfi, via the UCI Machine Learning Repository, DOI: 10.24432/C51K5Z, CC BY 4.0.”

# Project Attribution & Foundation Baseline

## Legacy Specification Reference
- **Source File:** `Mini Projet Simulateur parking.pdf`
- **Role:** Original starter project specification ("Simulation du Problème de Parking") serving as the initial problem formulation and concept baseline.
- **Inherited Core OS Concepts:**
  - Vehicle modeling via concurrent Java threads (`Runnable`).
  - Shared resource allocation for finite parking space availability.
  - Synchronization mechanisms (Mutexes, Semaphores, and Deadlock prevention).

## Refactored Architecture (ParkWise Team Expansion)
- **Package Migration:** Transitioned legacy structure into `com.parkwise` standard Java 21 packages (`model`, `view`, `service`, `strategy`).
- **Domain Standardization:** Fully translated French variable and class names to English standard domain models.
- **UI Engine:** Replaced placeholder UI elements with a complete 7-screen JavaFX dashboard leveraging `Platform.runLater()` for UI thread safety.
- **Quality Assurance:** Built a 29-test JUnit 5 verification suite to ensure zero regressions during concurrency runs.