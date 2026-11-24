package sisco.ass02.n_body_par.sync;

public class Controller{

    private boolean paused;
    private boolean stopped;
    private long pendingSteps;

    public Controller(){
        this.paused = false;
        this.stopped = false;
        this.pendingSteps = 0;
    }

    // Decisione dell'iterazione corrente: stop() non deve cambiarla
    // mentre alcuni worker stanno ancora uscendo dalla barriera.
    private boolean stepAllowed;

    public synchronized void togglePause(){
        if (stopped) return;
        paused = !paused;
        pendingSteps = 0;
        notifyAll();
    }

    public synchronized boolean getPaused(){
        return this.paused;
    }

    public synchronized void step() {
        if (stopped) return;
        paused = true;
        pendingSteps++;
        notifyAll();
    }

    public synchronized void stop() {
        stopped = true;
        notifyAll();
    }

    /** Chiamato solo dall'azione della barriera di inizio step. */
    public synchronized void awaitNextStep() {
        while (paused && pendingSteps == 0 && !stopped) {
            try {
                wait();
            } catch (InterruptedException ex) {
                stopped = true;
                Thread.currentThread().interrupt();
            }
        }
        stepAllowed = !stopped;
        if (stepAllowed && paused) pendingSteps--;
    }

    /** Tutti i worker leggono la stessa decisione dopo stepStarted. */
    public synchronized boolean isStepAllowed() {
        return stepAllowed;
    }
}
