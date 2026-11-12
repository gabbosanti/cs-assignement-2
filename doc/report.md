## 1. Problem Analysis

### 1.1 Overview of the Requirements
The goal of this assignment is to design and implement a concurrent version of a 2D **N-Body gravitational simulation**, starting from an existing sequential implementation. The problem is structured into two main steps:
1. **Performance-oriented simulation (Step 1):** Developing two headless concurrent versions—one using Java platform threads with custom high-level synchronization constructs (Monitors), and another adopting a task-based approach via the Java Executor Framework—to simulate $N$ bodies over $M$ discrete time steps.
2. **Interactive GUI simulation (Step 2):** Extending the system with a graphical user interface capable of rendering each simulation step while remaining responsive to user commands (*start*, *stop*, *suspend*, and *step-by-step* execution).

### 1.2 Concurrency Aspects and Challenges
Analyzing the simulation algorithm from a concurrency perspective highlights several key characteristics and challenges:

* **System Classification:**
  * **Internal vs. External Concurrency:** In Step 1, concurrency is purely *internal* to the system, introduced to decompose a CPU-bound workload across multiple processing cores. In Step 2, *environment-driven* concurrency is also introduced, as the application must handle asynchronous user interactions through the GUI alongside the ongoing computation.
  * **Shared-Memory & Data-Oriented Architecture:** All active components operate within a single node, sharing access to the collection of bodies. Because the computation for each body follows the exact same logic independently, the problem naturally lends itself to a **data-parallel** (domain decomposition) strategy, where disjoint subsets of bodies are assigned to different workers.

* **Data Dependencies and Read-Write Hazards:**
  Each simulation step consists of two sequential computational phases:
  1. **Force and Velocity Update:** Computing the net gravitational force acting on each body $b_i$ by reading the current positions of all other bodies $b_j$ ($j \neq i$), deriving its acceleration, and updating its velocity.
  2. **Position Update:** Updating the spatial coordinates of each body $b_i$ using its newly computed velocity.

  Due to the non-deterministic interleaving of concurrent threads, allowing workers to proceed independently across these two phases would introduce a critical **race condition** (read-write hazard). Specifically, if a faster thread begins updating a body's position in Phase 2 while a slower thread is still reading positions to compute forces in Phase 1, the simulation state becomes physically inconsistent.

* **Barrier Synchronization and Partitioning:**
  * Because each worker writes exclusively to its own assigned partition of bodies, **write-write conflicts are avoided by design**, eliminating the need for fine-grained mutual exclusion locks on individual body objects.
  * However, the strict dependency between Phase 1 and Phase 2 requires **barrier synchronization**. All workers must complete the force and velocity updates before any worker can begin updating positions. Similarly, all position updates must finish before advancing the virtual time (`vt = vt + dt`) and starting the next iteration.

* **GUI Responsiveness and Consistent Rendering (Step 2):**
  In the interactive version, running the simulation loop directly on the UI event thread would freeze the interface. Therefore, the computational engine must be decoupled from the rendering thread. Furthermore, the GUI must only render consistent snapshots of the system (i.e., at the end of a complete simulation step) and react promptly to state transitions triggered by the user (such as pausing or stepping).