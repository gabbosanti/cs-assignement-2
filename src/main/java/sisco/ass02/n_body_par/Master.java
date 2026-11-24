package sisco.ass02.n_body_par;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import sisco.ass02.n_body_par.sync.Barrier;
import sisco.ass02.n_body_par.sync.Controller;
import sisco.ass02.n_body_seq.model.Body;
import sisco.ass02.n_body_seq.model.P2d;
import sisco.ass02.n_body_seq.model.V2d;
import sisco.ass02.n_body_seq.view.SimulationView;

/**
 * Coordinatore della simulazione: inizializza lo stato, divide i corpi tra
 * i worker, crea le barriere, avvia i worker e ne attende la terminazione.
 *
 * Non partecipa al calcolo né alle barriere: viene eseguito dal thread che
 * chiama {@link #execute()} (tipicamente il main).
 */
public class Master {

    /** Passo temporale: stesso valore del sequenziale, per poter confrontare i risultati. */
    public static final double DT = 0.001;

    private final int nBodies;
    private final long nSteps;
    private final int nWorkers;
    private final long seed;

    private SimulationView view;

    private SimulationState state;
    private long elapsedNanos;

    /**
     * @param nBodies  numero di corpi N (>= 1)
     * @param nSteps   numero di step M (>= 0)
     * @param nWorkers numero di worker K richiesti (>= 1); se K > N si usano N worker
     * @param seed     seme del generatore casuale: stesso seme, stessi corpi iniziali
     * @param view     vista per la GUI
     */
    public Master(int nBodies, long nSteps, int nWorkers, long seed) {
        if (nBodies < 1 || nSteps < 0 || nWorkers < 1) {
            throw new IllegalArgumentException("parametri non validi: N >= 1, M >= 0, K >= 1");
        }
        this.nBodies = nBodies;
        this.nSteps = nSteps;
        // Un worker senza corpi passerebbe solo per le barriere: sarebbe
        // overhead senza lavoro utile, quindi non lo si crea.
        this.nWorkers = Math.min(nWorkers, nBodies);
        this.seed = seed;
    }

    public Master(int nBodies, long nSteps, int nWorkers, long seed, SimulationView view) {
        if (nBodies < 1 || nSteps < 0 || nWorkers < 1) {
            throw new IllegalArgumentException("parametri non validi: N >= 1, M >= 0, K >= 1");
        }
        this.nBodies = nBodies;
        this.nSteps = nSteps;
        // Un worker senza corpi passerebbe solo per le barriere: sarebbe
        // overhead senza lavoro utile, quindi non lo si crea.
        this.nWorkers = Math.min(nWorkers, nBodies);
        this.seed = seed;
        this.view = view;
    }

    /**
     * Esegue l'intera simulazione e ritorna lo stato finale.
     * Il tempo misurato comprende avvio, calcolo e terminazione dei worker,
     * non la creazione dei corpi (come nel SimulationMain sequenziale).
     */
    public SimulationState execute() throws InterruptedException {
        // 1. Inizializzazione dello stato condiviso.
        state = new SimulationState(createRandomBodies(nBodies, seed), DT);
        if (view != null) {
            view.init();
        }

        // 2-3. Barriere dimensionate su K; solo quella di fine step avanza vt.
        //aggiunta del controller per la GUI
        Barrier forcesComputed = new Barrier(nWorkers);
        Controller controller = null;
        Barrier stepStarted = null;

        if (view != null) {
            controller = view.getController();
        }
        if (controller != null) {
            stepStarted = new Barrier(nWorkers, controller::awaitNextStep);
        }
        
        Barrier stepCompleted = new Barrier(nWorkers, new Runnable() {
            private long iteration = 0;

            @Override
            public void run() {
                state.advanceTime();
                iteration++;

                if (view != null) {
                    // Copia indipendente dei corpi, creata mentre
                    // tutti gli altri worker sono ancora fermi.
                    List<Body> snapshot = copyBodies(state.getBodies());

                    view.display(snapshot, state.getVirtualTime(), iteration);
                }
            }

            private List<Body> copyBodies(List<Body> bodies) {
                List<Body> snapshot = new ArrayList<>(bodies.size());
                for (Body body : bodies) {
                    P2d position = body.getPos();
                    snapshot.add(new Body(
                            body.getId(),
                            new P2d(position.getX(), position.getY()),
                            new V2d(body.getVel()),
                            body.getRadius(),
                            body.getMass()));
                }
                return snapshot;
            }
        });
        long start = System.nanoTime();

        // 4. Partizione a blocchi contigui e avvio dei worker.
        List<Worker> workers = new ArrayList<>(nWorkers);
        for (int k = 0; k < nWorkers; k++) {
            Worker w = new Worker("worker-" + k, state,
                    partitionStart(k), partitionStart(k + 1),
                    forcesComputed, stepCompleted, nSteps, stepStarted, controller);
            workers.add(w);
            w.start();
        }

        // 5. Attesa della terminazione di tutti i worker.
        for (Worker w : workers) {
            w.join();
        }

        elapsedNanos = System.nanoTime() - start;
        return state;
    }

    /**
     * Primo indice della partizione k: k * N / K.
     * I blocchi [start(k), start(k+1)) coprono tutti i corpi senza
     * sovrapposizioni e differiscono in dimensione al massimo di 1
     * (il resto di N / K viene distribuito tra i worker).
     */
    private int partitionStart(int k) {
        return (int) ((long) k * nBodies / nWorkers);
    }

    /**
     * Crea n corpi casuali con la stessa distribuzione di
     * NBodySimulationSequential.initRandom del sequenziale, ma con seme esplicito
     * (il sequenziale usa l'ora corrente, quindi non è riproducibile).
     */
    public static ArrayList<Body> createRandomBodies(int n, long seed) {
        Random rand = new Random(seed);
        ArrayList<Body> bodies = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double x = -1.0 + 2.0 * rand.nextDouble();
            double y = -1.0 + 2.0 * rand.nextDouble();
            double vx = -0.1 + 0.2 * rand.nextDouble();
            double vy = -0.1 + 0.2 * rand.nextDouble();
            double m = 1 + rand.nextDouble() * 9;
            bodies.add(new Body(i, new P2d(x, y), new V2d(vx, vy), 0.01, m));
        }
        return bodies;
    }

    public int getNumWorkers() {
        return nWorkers;
    }

    /** Durata dell'ultima esecuzione in millisecondi. */
    public long getElapsedMillis() {
        return elapsedNanos / 1_000_000;
    }

    /**
     * Durata dell'ultima esecuzione in nanosecondi: usata dai test di
     * performance, dove i millisecondi interi sarebbero troppo grossolani
     * per le configurazioni piccole.
     */
    public long getElapsedNanos() {
        return elapsedNanos;
    }
}
