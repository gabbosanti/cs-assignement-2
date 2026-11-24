package sisco.ass02.n_body_par;

import sisco.ass02.n_body_seq.view.SimulationView;
import sisco.ass02.n_body_par.sync.Controller;

/**
 * Entry point della versione concorrente a thread (senza GUI).
 *
 * Uso: SimulationMain [N] [M] [K] [seed]
 *   N    numero di corpi            (default 5000, come il sequenziale)
 *   M    numero di step             (default 100, come il sequenziale)
 *   K    numero di worker           (default: core disponibili)
 *   seed seme per i corpi iniziali  (default 42)
 */
public class SimulationMainGUI {

    public static void main(String[] args) throws InterruptedException {
        int nBodies = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        long nSteps = args.length > 1 ? Long.parseLong(args[1]) : 50000;
        int nWorkers = args.length > 2 ? Integer.parseInt(args[2])
                : Runtime.getRuntime().availableProcessors();
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 42;

        Controller controller = new Controller();
        SimulationView view = new SimulationView(620,620, controller);

        Master master = new Master(nBodies, nSteps, nWorkers, seed, view);

        log("Avvio simulazione: " + nBodies + " corpi, " + nSteps + " step, "
                + master.getNumWorkers() + " worker...");

        SimulationState state = master.execute();

        log("Simulazione completata in " + master.getElapsedMillis() + " ms "
                + "(vt finale = " + state.getVirtualTime() + ").");
    }

    private static void log(String msg) {
        System.out.println("[" + System.currentTimeMillis() + "] " + msg);
    }
}
