# Generative AI Documentation

## AI assistance

Generative AI assistance was used to prepare the JavaFX interface update and this documentation. The requested update replaces startup dialogs with an in-app configuration screen, adds dashboard actions for settings and manually spawning a vehicle, and reinitializes the wait-time chart series when the analytics view is rebuilt.

## Project context

The application is the Smart Parking Prediction & Reservation System, built as a JavaFX parking simulator. `Main` owns the UI and starts one `Voiture` worker thread per vehicle. `Parking` allocates slots using either a fair `Semaphore` or a fair `ReentrantLock` and `Condition`. Worker threads report state changes to `Main`, which dispatches UI updates to the JavaFX application thread.

## AI-assisted changes in this update

- Replaced modal startup prompts with an embedded configuration view using strategy radio buttons, numeric spinners, and presets.
- Added dashboard controls to open settings, restart the simulation, toggle analytics, and spawn an extra vehicle.
- Recreated the wait chart series when configuring the analytics panel and guarded chart updates when the series is not yet available.
- Added lifecycle checks around slot updates and stopped simulations before returning to configuration.
- Added this file and `TECHNICAL_REPORT.md` to document the implementation and its concurrency model.

## Later AI-assisted development

Subsequent AI-assisted work in this project added synchronization and lifecycle tests, changed semaphore shutdown behavior, updated dashboard occupancy and queue-wait labels, moved worker cleanup off the JavaFX application thread, added editable arrival and parking-duration settings, changed log-file location and rotation, removed unused Maven dependencies, introduced a `ParkingListener` interface, configured Java 21 release compilation, added seeded arrival schedules, a simulation forecast, and a simulation-relative reservation workflow. Reserved vehicles use assigned spaces; unreserved parking intervals avoid overlapping bookings. The project now also includes the UCI Parking Birmingham historical dataset and an interpretable weekday/time occupancy profile with a chronological holdout comparison against a per-car-park average baseline. This benchmark uses historical Birmingham data, not live sensor data. The team should review these changes, understand and verify them, and record the actual tools, prompts, versions, and evidence required by the course policy. This summary does not replace any required prompt transcript or screenshot evidence.

## Review guidance

The earlier application version passed the Maven test suite and JavaFX runtime-image build. The historical predictor addition compiled and packaged successfully, with tests skipped for that build; the bundled dataset also loaded when the app launched. Run the full suite again before submission. Exercise configuration, presets, both synchronization strategies, manual vehicle spawning, the historical prediction controls, reservation flows, restart/settings transitions, and window shutdown. Confirm that workers terminate as expected under larger simulation loads.
