package sisco.ass02.n_body_seq.model;

import java.util.concurrent.Callable;

public class UpdatePositionTask implements Callable<Void> {

    private Body body;
    private double dt;

    public UpdatePositionTask(Body body, double dt) {
        this.body = body;
        this.dt = dt;
    }

    @Override
    public Void call() throws Exception {

        body.updatePos(dt);

        return null;
    }
}
