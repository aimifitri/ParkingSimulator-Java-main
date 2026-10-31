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
