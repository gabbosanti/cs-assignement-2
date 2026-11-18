package sisco.ass02.n_body_jpf;

import sisco.ass02.n_body_par.sync.Barrier;

/**
 * Worker della versione semplificata: stesso ciclo di n_body_par.Worker
 * (fase A, barriera, fase B, barriera), con il calcolo fisico sostituito da
 * aritmetica intera e con asserzioni esplicite sull'ordine delle fasi.
 *
 * Le asserzioni sono "throw new AssertionError" e non "assert": così valgono
 * sempre, senza dipendere dal flag -ea, e JPF le segnala come eccezioni
 * non gestite, mostrando l'interleaving che le ha causate.
 */
public class SimpleWorker extends Thread {

    private final SimpleState state;
    private final int from;
    private final int to;
    private final Barrier forcesComputed;
    private final Barrier stepCompleted;
    private final int nSteps;

    /**
     * Se true il worker salta la barriera di fine fase A. È un errore
     * introdotto apposta, per mostrare che JPF lo trova (vedi run-jpf.sh --bug).
     */
    private final boolean skipPhaseABarrier;

    public SimpleWorker(String name, SimpleState state, int from, int to,
                        Barrier forcesComputed, Barrier stepCompleted,
                        int nSteps, boolean skipPhaseABarrier) {
        super(name);
        this.state = state;
        this.from = from;
        this.to = to;
        this.forcesComputed = forcesComputed;
        this.stepCompleted = stepCompleted;
        this.nSteps = nSteps;
        this.skipPhaseABarrier = skipPhaseABarrier;
    }

    @Override
    public void run() {
        try {
            for (int step = 0; step < nSteps; step++) {
                // Nessuno è ancora passato allo step successivo e vt non è
                // stato avanzato prima che tutti finissero la fase B.
                checkStep(step, "inizio fase A");

                // Fase A: velocità dei propri corpi, leggendo tutte le posizioni.
                for (int i = from; i < to; i++) {
                    state.setVel(i, state.getVel(i) + state.computeForce(i));
                }

                if (!skipPhaseABarrier) {
                    forcesComputed.hitAndWaitAll();
                }

                // vt cambia solo alla barriera di fine step, mai a metà step.
                checkStep(step, "fase B");

                // Fase B: posizioni dei propri corpi.
                for (int i = from; i < to; i++) {
                    state.setPos(i, state.getPos(i) + state.getVel(i));
                }

                // L'ultimo arrivato esegue state.advanceStep() (fase C).
                stepCompleted.hitAndWaitAll();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkStep(int expected, String where) {
        int actual = state.getStep();
        if (actual != expected) {
            throw new AssertionError(getName() + " in " + where + ": step atteso "
                    + expected + ", trovato " + actual);
        }
    }
}
