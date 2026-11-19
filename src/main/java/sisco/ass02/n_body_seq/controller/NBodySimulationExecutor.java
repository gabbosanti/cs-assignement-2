package sisco.ass02.n_body_seq.controller;

import sisco.ass02.n_body_seq.model.Body;
import sisco.ass02.n_body_seq.model.ComputeForceTask;
import sisco.ass02.n_body_seq.model.P2d;
import sisco.ass02.n_body_seq.model.V2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class NBodySimulationExecutor {

	private final ExecutorService executor;
    private final List<Callable<V2d>> forceTasks;
    private final List<Callable<Void>> positionTasks;

    private List<Body> bodies;
	private int nBodies;
    private double vt;
	double dt;

	public NBodySimulationExecutor(int nBodies) {
		this.nBodies = nBodies;
        this.executor = Executors.newFixedThreadPool(
               Runtime.getRuntime().availableProcessors() + 1);
        this.forceTasks = new ArrayList<>(nBodies);
        this.positionTasks = new ArrayList<>(nBodies);
    }

	public void initRandom(int nBodies) {
		Random rand = new Random(System.currentTimeMillis());
		bodies = new ArrayList<Body>();

		//Crea randomicamente i corpi
		for (int i = 0; i < nBodies; i++) {
			double x = -1.0 + 2.0*rand.nextDouble();
			double y = -1.0 + 2.0*rand.nextDouble();
			double vx = -0.1 + 0.2*rand.nextDouble();
			double vy = -0.1 + 0.2*rand.nextDouble();
			double m = 1 + rand.nextDouble()*9;
			Body b = new Body(i, new P2d(x, y), new V2d(vx, vy), 0.01, m);
			bodies.add(b);
		}

	}

	public void execute(long nSteps) throws InterruptedException {

		/* init virtual time */
		vt = 0;
		dt = 0.001;
		List<Future<V2d>> totalForce = new ArrayList<>();

		long iter = 0;

        try {
            /* simulation loop */
            while (iter < nSteps) {

              totalForce = executor.invokeAll(forceTasks);

              executor.invokeAll(positionTasks);

			  iter++;
            }
        } finally {
            executor.awaitTermination(Long.MAX_VALUE,TimeUnit.SECONDS);
            executor.shutdown();
        }
	}

	public int getNumBodies() {
		return bodies.size();
	}

    public void setupTask(int nBodies) {
		for (int i = 0; i < nBodies; i++) {

			Body b = this.bodies.get(i);
			ComputeForceTask forceTask = new ComputeForceTask(b, bodies);

		}
    }
}
