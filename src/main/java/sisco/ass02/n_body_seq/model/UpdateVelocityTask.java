package sisco.ass02.n_body_seq.model;

import java.util.List;
import java.util.concurrent.Callable;

public class UpdateVelocityTask implements Callable<Void> {

    private Body b;
    private List<Body> bodies;
	private double dt;

    public UpdateVelocityTask(Body b, List<Body> bodies,  double dt) {
		this.b = b;
		this.bodies = bodies;
		this.dt = dt;
    }

    @Override
    public Void call() throws Exception {

		/* compute total force on bodies */
		V2d totalForce = computeTotalGForceOnBody(b);

		/* compute instant acceleration */
		V2d acc = new V2d(totalForce).scalarMul(1.0 / b.getMass());

		/* update velocity */
		b.updateVelocity(acc, dt);

		return null;
    }

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
}
