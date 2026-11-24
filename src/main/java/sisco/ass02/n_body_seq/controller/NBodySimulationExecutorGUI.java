package sisco.ass02.n_body_seq.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import sisco.ass02.n_body_seq.model.Body;
import sisco.ass02.n_body_seq.model.P2d;
import sisco.ass02.n_body_seq.model.UpdatePositionTask;
import sisco.ass02.n_body_seq.model.UpdateVelocityTask;
import sisco.ass02.n_body_seq.model.V2d;
import sisco.ass02.n_body_seq.view.SimulationView;


public class NBodySimulationExecutorGUI {

	private final ExecutorService executor;
    private final List<Callable<Void>> velocityTask;
    private final List<Callable<Void>> positionTasks;

    private List<Body> bodies;
    private double vt;
	private double dt;

	private Optional<SimulationView> view;

	public NBodySimulationExecutorGUI(int nBodies, SimulationView view) {
        this.executor = Executors.newFixedThreadPool(
               Runtime.getRuntime().availableProcessors() + 1);
        this.velocityTask = new ArrayList<>(nBodies);
        this.positionTasks = new ArrayList<>(nBodies);
		this.view = Optional.of(view);
    }

	public void initRandom(int nBodies) {
		Random rand = new Random(System.currentTimeMillis());
		bodies = new ArrayList<>();

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

	public void setupTask(int nBodies) {
		for (int i = 0; i < nBodies; i++) {

			Body b = bodies.get(i);

			velocityTask.add(new UpdateVelocityTask(b, bodies, dt));
			positionTasks.add(new UpdatePositionTask(b, dt));

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

			  //Execute all UpdateVelocityTask in parallel and  waits for termination
              executor.invokeAll(velocityTask);

			  //Execute all UpdateVelocityTask in parallel and  waits for termination
			  executor.invokeAll(positionTasks);

			  vt += dt;
			  iter++;
			  view.get().display(bodies, vt, iter);
            }
        } finally {
            executor.shutdown();
        }
	}

	public int getNumBodies() {
		return bodies.size();
	}

}
