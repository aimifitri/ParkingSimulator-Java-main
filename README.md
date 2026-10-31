# Smart Parking Prediction & Reservation System

### A Java-based Smart Parking Prediction & Reservation System using JavaFX. It forecasts occupancy from scheduled arrivals and reservations, and simulates parking with configurable synchronization strategies (Semaphore or Mutex).

## Requirements

* JDK 21 (LTS)
* Maven 3.x, or use the included Maven wrapper

## Features:

* Live Parking Lot Dashboard: Displays numbered parking slots, a marked driving lane, and animated vehicle cards showing each car ID.
* Synchronization Strategies: Implements different synchronization strategies (Semaphore and Mutex) through the IStrategy interface and DefaultStrategy class.
* Real-Time Statistics: Shows the active synchronization strategy, total/occupied/available slots, waiting cars, and average wait time.
* Analytics and Activity: Includes live occupancy, a predicted-versus-actual occupancy chart, an average queue-wait chart, and an event log.
* Smart City Demand Prediction: Estimates typical occupancy for a selected Birmingham car park, weekday, and half-hour from a real historical dataset. The dashboard reports a held-out MAE against a simple per-car-park average baseline.
* Reservations: Book a numbered space for a simulation-relative time window, view bookings, and cancel them. Overlapping bookings cannot claim the same space.
* Controls: Restart the simulation or toggle the analytics panel without closing the application.
* Logging: Logs parking attempts and waiting times to a file.
* Simulation and Animation: Configure the randomized arrival window and parking duration, with animated slot state changes and vehicle representations.
* Thread Management: Program oriented to thread management, where a critical section (parking) is managed using Mutex for synchronization.

## Project Structure:

* CustomRectangle.java: A custom JavaFX component representing a parking spot.
* DefaultStrategy.java: Implements the default parking strategy to find the next available spot.
* IStrategy.java: Interface for different parking strategies.
* Main.java: Main application class that sets up the JavaFX interface and starts the simulation.
* Parking.java: Manages parking spots and synchronization strategies.
* ParkingListener.java: Reports parking events without tying the parking model to JavaFX.
* Voiture.java: Represents a car trying to park in the parking lot.


## Thread Management and Critical Section
The application is designed to manage multiple threads representing cars trying to park simultaneously. The critical section (parking operation) is protected using Mutex or Semaphore to ensure thread safety and prevent race conditions. This will avoid conflicts and ensure consistent state updates.

## Graphical User Interface (GUI):
* Lot View: Numbered parking slots are arranged around a marked center lane. Available slots are green; occupied slots are blue and show a vehicle icon and car ID.
* User Prompts: On startup, the configuration screen lets the user choose a synchronization strategy, number of cars and parking spots, randomized arrival window, parking duration, and arrival schedule seed.
* Dashboard: Live counters and the activity feed update as cars queue, park, and leave. Average wait time is calculated from completed departures.
* Analytics: Use **View analytics** to show or hide occupancy and average-wait charts. Use **Restart simulation** to reset the lot and replay the configured cars.

## Automated tests

Run the JUnit suite with the Maven wrapper:

```bash
./mvnw test
```

## How to Run and Package:
You can directly use your IDE or:

Clone the repository:
```
git clone https://github.com/boussas/ParkingSimulator-Java.git
cd ParkingSimulator-Java
```
Build and run the project using Make:
```
make all
make run
```

Create a standalone Java runtime image and executable:

```bash
make package-app
./target/SmartParkingSystem/bin/SmartParkingSystem
```

Or run the platform-independent Maven goals directly:

```bash
./mvnw package
./mvnw javafx:jlink
```

## Usage:
Upon running the application, configure:

  1. Select a synchronization strategy (Semaphore or Mutex).
  2. Enter the number of cars.
  3. Enter the number of parking spots.
  4. Set the randomized arrival window and each car's parking duration (1–60 seconds).
  5. Set an arrival schedule seed to reproduce the same randomized car arrival delays.

The dashboard then displays the live parking lot as cars arrive according to the generated schedule. The same seed and arrival window produce the same delays, although OS thread scheduling can still change the order in which cars park. Use the analytics and restart controls in the header as needed.

The occupancy forecast schedules cars into predicted free spaces using the configured arrival delays, parking duration, reservations, and lot capacity. It excludes manually spawned cars and is an estimate; operating-system thread timing can cause actual occupancy to differ.

The Smart City prediction panel uses the UCI Parking Birmingham dataset (35,717 source rows from 30 car parks, October–December 2016; CC BY 4.0). It predicts typical occupancy from historical records for the selected car park, weekday, and half-hour. On a chronological holdout of 6,958 observations, the weekday/time profile achieved 6.77% MAE versus 17.45% for a simple per-car-park average baseline. Invalid rows are excluded. This is a historical benchmark, not live sensor data or a forecast for the user's local city. See `DATASET_ATTRIBUTION.md` for data credit and license details.

Use **Manage reservations** on the configuration screen to reserve a numbered space for a time window measured in seconds after a simulation starts. Bookings persist while the app is open, can be cancelled, and prevent reducing the configured lot capacity below a space number already reserved. Reserved vehicles join the live simulation at their booked offsets; random and manually spawned cars avoid spaces whose booked intervals overlap their parking duration. Manage reservations in Settings before the next launch; changes apply to the next run.

## Logging:
The application writes rotating logs to `~/.parkflow/parking.log` by default (with up to three 1 MB log files). Set the Java system property `-Dparkflow.log=/path/to/parking.log` to choose another location. The dashboard calculates average queue wait from completed parking visits and displays it in the statistics panel and analytics chart.

# Synchronization Strategies:

## Semaphore:

A Semaphore is a more generalized synchronization primitive that can control access to a resource pool with multiple permits. It allows more than one thread to access a resource concurrently, up to a specified limit.
### Benefits:
  * Scalability: Semaphores can manage access to multiple resources simultaneously, making them suitable for scenarios where limited concurrency is desired.
  * Flexibility: They can be used to control access to a pool of resources, not just a single resource.
  * Fairness: Semaphores can be configured to ensure fair access to resources, preventing thread starvation.
  * Non-Binary: Unlike mutexes, semaphores can have more than two states (locked/unlocked), allowing for more nuanced control over resource access.

### Trade-offs:

  * Complexity: Managing semaphores can be more complex compared to mutexes, especially when dealing with multiple permits and ensuring correct usage patterns.

## Mutex

A Mutex (short for mutual exclusion) is a locking mechanism used to enforce exclusive access to a resource. It ensures that only one thread can access a critical section of code at any given time.
### Benefits

  * Simplicity: Mutexes provide a straightforward mechanism for ensuring exclusive access to resources, making them easy to implement and understand.
  * Efficiency: They typically have lower overhead compared to semaphores since they only need to manage two states (locked/unlocked).
  * Deterministic: Mutexes ensure that only one thread can enter the critical section, providing deterministic behavior for critical resource access.
  * Deadlock Prevention: Properly used mutexes can prevent deadlocks by ensuring that a thread holds only one lock at a time.

### Trade-offs:

  * Limited Concurrency: Mutexes enforce strict mutual exclusion, which can limit concurrency in scenarios where limited parallel access might be acceptable.
  * Potential for Deadlock: If not used correctly (e.g., if a thread tries to acquire a mutex it already holds), mutexes can lead to deadlocks.
  * Starvation: Without proper handling, some threads might starve if they are constantly preempted by other threads acquiring the mutex.

## Use Case in Parking Simulator:

### Semaphore in Parking Simulator:

Using a semaphore in the parking simulator could allow multiple cars to attempt parking simultaneously, up to the number of available parking spots. Each car would acquire a permit before parking and release it upon leaving.

#### Pros:

  * Higher Throughput: Multiple cars can park concurrently, potentially increasing the overall throughput of the parking lot.
  * Resource Utilization: Better utilization of available parking spots, as cars can park as long as there are permits available.

#### Cons:

   * Complexity in Management: Managing the correct acquisition and release of permits can be more complex.
   * Race Conditions: Increased risk of race conditions if the semaphore is not managed correctly.

### Mutex in Parking Simulator:

Using a mutex would ensure that only one car can attempt to park at any given time, simplifying the critical section management.
#### Pros:

   * Simplicity: Easier to implement and understand, reducing the risk of concurrency issues.
   * Deterministic Access: Ensures that only one car can park at a time, making state management straightforward.

#### Cons:

   * Limited Concurrency: Only one car can park at a time, potentially reducing throughput.
   * Potential for Bottlenecks: Can create a bottleneck if many cars are trying to park simultaneously, leading to increased waiting times.

## Conclusion:

The choice between Semaphore and Mutex in the parking simulator depends on your specific requirements and constraints:

  * Use Semaphore if you need to allow multiple cars to park concurrently and can manage the complexity of permits.
  * Use Mutex if you prefer a simpler, deterministic approach with exclusive access to the parking process, even at the cost of limited concurrency.

Each approach has its own set of trade-offs, and the best choice will depend on the desired balance between concurrency, simplicity, and resource management.

Classes Path: src/main/java/com/parkwise
