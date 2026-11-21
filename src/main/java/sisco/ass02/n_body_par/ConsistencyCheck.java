package sisco.ass02.n_body_par;

import sisco.ass02.n_body_seq.controller.NBodySimulationSequential;
import sisco.ass02.n_body_seq.model.Body;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifica funzionale (Passo 7): confronta lo stato finale della versione
 * concorrente con quello del sequenziale, a parità di corpi iniziali
 * (stesso seme), dt e numero di step.
 *
 * Il confronto è esatto (==) su posizioni e velocità: il worker somma le
 * forze nello stesso ordine del sequenziale, quindi il risultato deve essere
 * identico bit per bit, non solo "vicino". Qualunque differenza indica
 * stati misti tra fasi, cioè un errore di sincronizzazione.
 *
 * Ogni K viene ripetuto più volte perché l'interleaving dei thread cambia
 * a ogni esecuzione: un errore di sincronizzazione potrebbe non presentarsi
 * sempre.
 *
 * Uso: ConsistencyCheck [N] [M] [seed] [ripetizioni]
 *   default: 1000 corpi, 50 step, seme 42, 3 ripetizioni
 */
public class ConsistencyCheck {

    public static void main(String[] args) throws InterruptedException {
        int nBodies = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        long nSteps = args.length > 1 ? Long.parseLong(args[1]) : 50;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42;
        int repetitions = args.length > 3 ? Integer.parseInt(args[3]) : 3;

        // Riferimento: sequenziale sugli stessi corpi iniziali.
        ArrayList<Body> expected = Master.createRandomBodies(nBodies, seed);
        NBodySimulationSequential seq = new NBodySimulationSequential();
        seq.init(expected);
        seq.execute(nSteps);
        double expectedVt = expectedVirtualTime(nSteps);

        // K = 1 (deve coincidere banalmente), potenze di 2, valori che non
        // dividono N (partizioni di dimensioni diverse) e numero di core.
        int cores = Runtime.getRuntime().availableProcessors();
        Set<Integer> workerCounts = new LinkedHashSet<>(List.of(1, 2, 3, 4, 7, 8, cores));

        System.out.println("Confronto con il sequenziale: " + nBodies + " corpi, "
                + nSteps + " step, seme " + seed + ", " + repetitions + " ripetizioni per K");

        boolean allOk = true;
        for (int k : workerCounts) {
            for (int r = 1; r <= repetitions; r++) {
                Master master = new Master(nBodies, nSteps, k, seed);
                SimulationState state = master.execute();
                int mismatches = countMismatches(expected, state.getBodies());
                boolean vtOk = state.getVirtualTime() == expectedVt;
                boolean ok = mismatches == 0 && vtOk;
                allOk &= ok;
                System.out.printf("K=%-3d run %d: %s (corpi diversi: %d, vt %s)%n",
                        master.getNumWorkers(), r, ok ? "OK" : "ERRORE",
                        mismatches, vtOk ? "corretto" : "ERRATO: " + state.getVirtualTime());
            }
        }

        System.out.println(allOk ? "Verifica superata." : "Verifica FALLITA.");
        if (!allOk) {
            System.exit(1);
        }
    }

    /** Numero di corpi la cui posizione o velocità differisce dal riferimento. */
    private static int countMismatches(List<Body> expected, List<Body> actual) {
        int mismatches = 0;
        for (int i = 0; i < expected.size(); i++) {
            Body e = expected.get(i);
            Body a = actual.get(i);
            boolean samePos = e.getPos().getX() == a.getPos().getX()
                    && e.getPos().getY() == a.getPos().getY();
            boolean sameVel = e.getVel().getX() == a.getVel().getX()
                    && e.getVel().getY() == a.getVel().getY();
            if (!samePos || !sameVel) {
                mismatches++;
            }
        }
        return mismatches;
    }

    /**
     * vt atteso dopo nSteps step: somma ripetuta di dt, nello stesso ordine
     * della simulazione (vt * dt darebbe un arrotondamento diverso).
     * Il tempo virtuale del sequenziale è privato, quindi lo si ricalcola.
     */
    private static double expectedVirtualTime(long nSteps) {
        double vt = 0;
        for (long i = 0; i < nSteps; i++) {
            vt += Master.DT;
        }
        return vt;
    }
}
