package sisco.ass02.n_body_seq.controller;

import sisco.ass02.n_body_seq.model.Body;
import sisco.ass02.n_body_seq.model.ComputeForceTask;
import sisco.ass02.n_body_seq.model.P2d;
import sisco.ass02.n_body_seq.model.V2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NBodySimulationExecutor {

	private final ExecutorService executor;
    private final List<Callable<Void>> forceTasks;
    private final List<Callable<Void>> positionTasks;

    private List<Body> bodies;
	private int numWorkers;
    private double vt;
	double dt;

	public NBodySimulationExecutor() {
        this.executor = Executors.newFixedThreadPool(
               Runtime.getRuntime().availableProcessors() + 1);
        this.forceTasks = new ArrayList<>();
        this.positionTasks = new ArrayList<>();
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

			this.forceTasks.add(new ComputeForceTask());

		}



	}

	public void execute(long nSteps) throws InterruptedException {

		/* init virtual time */
		vt = 0;
		dt = 0.001;

		long iter = 0;

        try {
            /* simulation loop */
            while (iter < nSteps) {

                executor.invokeAll(forceTasks);

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
}
