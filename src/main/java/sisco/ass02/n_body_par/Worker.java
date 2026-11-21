package sisco.ass02.n_body_par;

import sisco.ass02.n_body_par.sync.Barrier;
import sisco.ass02.n_body_seq.model.Body;
import sisco.ass02.n_body_seq.model.V2d;

import java.util.List;

/**
 * Thread di calcolo: esegue le fasi A e B su una partizione contigua di corpi
 * [from, to) per nSteps step, sincronizzandosi con gli altri worker tramite
 * due barriere per step.
 *
 * Ciclo di ogni step:
 * <pre>
 *   fase A (forze + velocità dei propri corpi)
 *   forcesComputed.hitAndWaitAll()   // nessuno scrive posizioni finché tutti non le hanno lette
 *   fase B (posizioni dei propri corpi)
 *   stepCompleted.hitAndWaitAll()    // nessuno rilegge posizioni finché tutte non sono aggiornate
 * </pre>
 */
public class Worker extends Thread {

    /** Stato condiviso: lettura di tutti i corpi, scrittura solo di [from, to). */
    private final SimulationState state;

    /** Primo indice (incluso) della partizione assegnata. */
    private final int from;

    /** Ultimo indice (escluso) della partizione assegnata. */
    private final int to;

    /** Barriera di fine fase A. */
    private final Barrier forcesComputed;

    /** Barriera di fine fase B, cioè di fine step. */
    private final Barrier stepCompleted;

    /** Numero di step da eseguire (uguale per tutti i worker). */
    private final long nSteps;

    public Worker(String name, SimulationState state, int from, int to,
                  Barrier forcesComputed, Barrier stepCompleted, long nSteps) {
        super(name);
        this.state = state;
        this.from = from;
        this.to = to;
        this.forcesComputed = forcesComputed;
        this.stepCompleted = stepCompleted;
        this.nSteps = nSteps;
    }

    @Override
    public void run() {
        try {
            for (long step = 0; step < nSteps; step++) {
                computeVelocities();            // fase A
                forcesComputed.hitAndWaitAll();
                updatePositions();              // fase B
                stepCompleted.hitAndWaitAll();  // l'ultimo arrivato esegue vt += dt
            }
        } catch (InterruptedException ex) {
            // Nessuno interrompe i worker nello Step 1: se succede, si termina
            // ripristinando il flag di interruzione per chi lo volesse controllare.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Fase A: per ogni corpo della partizione calcola la forza totale esercitata
     * da tutti gli altri corpi, ne ricava l'accelerazione e aggiorna la velocità.
     * Legge le posizioni di tutti i corpi, scrive solo le velocità dei propri.
     */
    private void computeVelocities() {
        final List<Body> bodies = state.getBodies();
        final double dt = state.getDt();
        for (int i = from; i < to; i++) {
            Body b = bodies.get(i);
            V2d totalForce = computeTotalGForceOnBody(b, bodies);
            V2d acc = new V2d(totalForce).scalarMul(1.0 / b.getMass());
            b.updateVelocity(acc, dt);
        }
    }

    /**
     * Fase B: aggiorna la posizione di ogni corpo della partizione
     * a partire dalla velocità calcolata in fase A.
     */
    private void updatePositions() {
        final List<Body> bodies = state.getBodies();
        final double dt = state.getDt();
        for (int i = from; i < to; i++) {
            bodies.get(i).updatePos(dt);
        }
    }

    /**
     * Forza gravitazionale totale su b. Stessa logica (e stesso ordine di somma)
     * di NBodySimulationSequential.computeTotalGForceOnBody del sequenziale, che è privato
     * e quindi non riusabile direttamente: il calcolo fisico resta in Body.
     */
    private static V2d computeTotalGForceOnBody(Body b, List<Body> bodies) {
        V2d totalForce = new V2d(0, 0);
        for (int j = 0; j < bodies.size(); j++) {
            Body other = bodies.get(j);
            if (!b.equals(other)) {
                try {
                    totalForce.sum(b.computeGForceBy(other));
                } catch (Exception ex) {
                    // Come nel sequenziale: corpi sovrapposti (distanza 0)
                    // non contribuiscono alla forza.
                }
            }
        }
        return totalForce;
    }
}
