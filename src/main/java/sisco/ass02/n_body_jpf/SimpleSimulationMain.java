package sisco.ass02.n_body_jpf;

import sisco.ass02.n_body_par.sync.Barrier;

/**
 * Versione semplificata della simulazione concorrente, da verificare con
 * Java PathFinder (README: "JPF applied with a simplified version of the program").
 *
 * Cosa resta uguale a n_body_par:
 * <ul>
 *   <li>architettura master-workers: questo main fa da Master (stato,
 *       partizione a blocchi k*N/K, barriere, start e join);</li>
 *   <li>la barriera è la VERA sync.Barrier, non una copia: è il monitor
 *       che si vuole verificare;</li>
 *   <li>ciclo dei worker: fase A, barriera, fase B, barriera con vt++.</li>
 * </ul>
 * Cosa è semplificato: corpi ridotti a interi, nessuna fisica, nessuna
 * misura di tempo né stampa durante il calcolo (I/O e double farebbero
 * crescere lo spazio degli stati senza aggiungere nulla alla concorrenza).
 *
 * Proprietà verificate da JPF su tutti gli interleaving:
 * <ul>
 *   <li>assenza di deadlock (proprietà di default di JPF);</li>
 *   <li>assenza di eccezioni non gestite, quindi delle AssertionError:
 *       <ul>
 *         <li>i worker controllano che vt non avanzi prima del tempo;</li>
 *         <li>il main confronta lo stato finale con il calcolo sequenziale
 *             (qualsiasi sovrapposizione tra fasi lo renderebbe diverso)
 *             e controlla che vt = M;</li>
 *       </ul></li>
 *   <li>assenza di race condition sui dati condivisi
 *       (con il listener PreciseRaceDetector, attivato da run-jpf.sh).</li>
 * </ul>
 *
 * Uso: SimpleSimulationMain [N] [M] [K] [bug]
 *   default N = 3 corpi, M = 2 step, K = 2 worker;
 *   "bug" come quarto argomento toglie la barriera di fine fase A.
 */
public class SimpleSimulationMain {

    public static void main(String[] args) throws InterruptedException {
        int nBodies = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int nSteps = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        int requestedWorkers = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        boolean bug = args.length > 3 && args[3].equals("bug");

        // Come in Master: mai più worker che corpi.
        int nWorkers = Math.min(requestedWorkers, nBodies);

        SimpleState state = new SimpleState(nBodies);
        Barrier forcesComputed = new Barrier(nWorkers);
        // Azione della barriera di fine step: vt++ eseguito dall'ultimo arrivato.
        Barrier stepCompleted = new Barrier(nWorkers, state::advanceStep);

        SimpleWorker[] workers = new SimpleWorker[nWorkers];
        for (int k = 0; k < nWorkers; k++) {
            workers[k] = new SimpleWorker("worker-" + k, state,
                    partitionStart(k, nBodies, nWorkers),
                    partitionStart(k + 1, nBodies, nWorkers),
                    forcesComputed, stepCompleted, nSteps, bug);
            workers[k].start();
        }
        for (SimpleWorker w : workers) {
            w.join();
        }

        checkFinalState(state, nBodies, nSteps);
    }

    /** Stessa partizione a blocchi contigui di Master.partitionStart. */
    private static int partitionStart(int k, int nBodies, int nWorkers) {
        return k * nBodies / nWorkers;
    }

    /**
     * Confronta lo stato finale con lo stesso calcolo eseguito in modo
     * sequenziale: con la disciplina a fasi corretta devono coincidere
     * qualunque sia l'interleaving.
     */
    private static void checkFinalState(SimpleState state, int nBodies, int nSteps) {
        SimpleState expected = new SimpleState(nBodies);
        for (int step = 0; step < nSteps; step++) {
            // Fase A per tutti i corpi, poi fase B per tutti: come il sequenziale.
            int[] newVel = new int[nBodies];
            for (int i = 0; i < nBodies; i++) {
                newVel[i] = expected.getVel(i) + expected.computeForce(i);
            }
            for (int i = 0; i < nBodies; i++) {
                expected.setVel(i, newVel[i]);
                expected.setPos(i, expected.getPos(i) + newVel[i]);
            }
        }

        if (state.getStep() != nSteps) {
            throw new AssertionError("vt finale " + state.getStep() + ", atteso " + nSteps);
        }
        for (int i = 0; i < nBodies; i++) {
            if (state.getPos(i) != expected.getPos(i) || state.getVel(i) != expected.getVel(i)) {
                throw new AssertionError("corpo " + i + " diverso dal sequenziale: pos "
                        + state.getPos(i) + " (attesa " + expected.getPos(i) + "), vel "
                        + state.getVel(i) + " (attesa " + expected.getVel(i) + ")");
            }
        }
    }
}
