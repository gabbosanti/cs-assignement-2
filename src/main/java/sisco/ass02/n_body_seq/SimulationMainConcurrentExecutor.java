package sisco.ass02.n_body_seq;

import sisco.ass02.n_body_seq.controller.NBodySimulationExecutor;

public class SimulationMainConcurrentExecutor {

    public static void main(String[] args) {

    	NBodySimulationExecutor sim = new NBodySimulationExecutor();

    	sim.initRandom(5000);
    	int nSteps = 100;

    	long startTime = System.currentTimeMillis();

    	log("Starting simulation with " + sim.getNumBodies() + " bodies - " + nSteps + " steps...");

    	sim.run(nSteps);

    	long endTime = System.currentTimeMillis();

    	log("Simulation completed in " + (endTime - startTime)/1000 + " seconds.");

    }

    private static void log(String msg) {
    	System.out.println("[" + System.currentTimeMillis() + "] " + msg);
    }


}
