package sisco.ass02.n_body_seq.controller;

import sisco.ass02.n_body_seq.model.Body;
import sisco.ass02.n_body_seq.model.P2d;
import sisco.ass02.n_body_seq.model.V2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NBodySimulationExecutor {

	private ExecutorService exec;
    List<Body> bodies;
    private double vt;
	double dt;

	public NBodySimulationExecutor() {
        this.exec = Executors.newFixedThreadPool(
               Runtime.getRuntime().availableProcessors() + 1);
    }

	public void initRandom(int nBodies) {
		Random rand = new Random(System.currentTimeMillis());
		bodies = new ArrayList<Body>();
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

	public void run(long nSteps) {

		/* init virtual time */
		vt = 0;
		dt = 0.001;

		long iter = 0;
		/* simulation loop */

		while (iter < nSteps) {

			/* update bodies velocity */
			for (int i = 0; i < bodies.size(); i++) {
				Body b = bodies.get(i);
                exec.execute(new ComputeElemTask(i,j,matA,matB,matC));
				/* compute total force on bodies */
				V2d totalForce = computeTotalGForceOnBody(b);

				/* compute instant acceleration */
				V2d acc = new V2d(totalForce).scalarMul(1.0 / b.getMass());

				/* update velocity */
				b.updateVelocity(acc, dt);
			}

			/* compute bodies new pos */


			for (Body b : bodies) {
				b.updatePos(dt);
			}

			/* update virtual time */

			vt = vt + dt;
			iter++;


		}

        exec.shutdown();
        exec.awaitTermination(Long.MAX_VALUE,TimeUnit.SECONDS);
	}

	/*
	 * TASK
	 * Compute the total G force exterted on a body by the other bodies
	 *
	 */
	private V2d computeTotalGForceOnBody(Body b) {
		V2d totalForce = new V2d(0, 0);
		for (int j = 0; j < bodies.size(); j++) {
			Body otherBody = bodies.get(j);
			if (!b.equals(otherBody)) {
				try {
					V2d forceByOtherBody = b.computeGForceBy(otherBody);
					totalForce.sum(forceByOtherBody);
				} catch (Exception ex) {
				}
			}
		}
		return totalForce;
	}

	public int getNumBodies() {
		return bodies.size();
	}
}
