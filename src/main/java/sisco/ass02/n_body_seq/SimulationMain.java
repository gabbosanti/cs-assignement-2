package sisco.ass02.n_body_seq;

import sisco.ass02.n_body_seq.controller.NBodySimulationExecutor;
//import sisco.ass02.n_body_seq.controller.NBodySimulationSequential;

public class SimulationMain {

    public static void main(String[] args) {

		final int nBodies = 5000;
    	NBodySimulationExecutor sim = new NBodySimulationExecutor(nBodies);
	 	//NBodySimulationSequential sim = new NBodySimulationSequential();

    	sim.initRandom(nBodies);
		sim.setupTask(nBodies);

    	int nSteps = 100;

    	long startTime = System.currentTimeMillis();

    	log("Starting simulation with " + sim.getNumBodies() + " bodies - " + nSteps + " steps...");

    	sim.execute(nSteps);

    	long endTime = System.currentTimeMillis();

    	log("Simulation completed in " + (endTime - startTime)/1000 + " seconds.");

    }

    private static void log(String msg) {
    	System.out.println("[" + System.currentTimeMillis() + "] " + msg);
    }


}
