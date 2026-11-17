package sisco.ass02.n_body_seq.model;

import java.util.concurrent.Callable;

public class ComputeForceTask implements Callable<Void> {

    private Body b;

    public ComputeForceTask(Body b) {
        this.b = b;
    }

    @Override
    public Void call() throws Exception {

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
        return null;
    }
}
