package sisco.ass02.benchmark;

import sisco.ass02.n_body_par.Master;
import sisco.ass02.n_body_seq.controller.NBodySimulation;
import sisco.ass02.n_body_seq.model.Body;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Test di performance dello Step 1 (senza GUI), come richiesto dal README:
 * <ul>
 *   <li>se/quanto la versione concorrente migliora rispetto al sequenziale;</li>
 *   <li>se/come le prestazioni scalano con il numero di core usati.</li>
 * </ul>
 *
 * A parità di N, M e seme esegue il sequenziale (baseline) e la versione a
 * thread con K worker per diversi K; per ogni configurazione riporta il tempo
 * mediano, lo speedup S(K) = T_seq / T_par(K) e l'efficienza E(K) = S(K) / K.
 *
 * Scelte di misura:
 * <ul>
 *   <li>stessi corpi iniziali per tutte le versioni (seme fisso): il carico
 *       di lavoro è identico e i tempi sono confrontabili;</li>
 *   <li>si misura solo la simulazione, non la creazione dei corpi
 *       (per il parallelo: da avvio a join dei worker, vedi Master);</li>
 *   <li>un'esecuzione di riscaldamento non misurata per versione, così il
 *       JIT ha già compilato il codice caldo prima delle misure;</li>
 *   <li>più ripetizioni per configurazione, riportando la mediana: è meno
 *       sensibile della media a esecuzioni anomale (GC, altri processi).</li>
 * </ul>
 *
 * Uso: PerformanceTest [N] [M] [ripetizioni] [lista K]
 *   N           numero di corpi        (default 5000, come il sequenziale)
 *   M           numero di step         (default 100, come il sequenziale)
 *   ripetizioni misure per config.     (default 3)
 *   lista K     worker separati da virgola, es. 1,2,4,8
 *               (default: potenze di 2 fino ai core disponibili, più i core)
 *
 * Esempio: java ... PerformanceTest 5000 100 5 1,2,4,8,12,16,20 > perf.txt
 */
public class PerformanceTest {

    /** Seme fisso: tutte le versioni simulano gli stessi corpi iniziali. */
    private static final long SEED = 42;

    public static void main(String[] args) throws InterruptedException {
        int nBodies = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        long nSteps = args.length > 1 ? Long.parseLong(args[1]) : 100;
        int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        int cores = Runtime.getRuntime().availableProcessors();
        List<Integer> workerCounts = args.length > 3
                ? parseWorkerCounts(args[3])
                : defaultWorkerCounts(cores);

        printHeader(nBodies, nSteps, repetitions, cores, workerCounts);

        // Riscaldamento del JIT su una configurazione ridotta: non misurato.
        warmUp(cores);

        // Baseline sequenziale.
        double seqMillis = median(measureSequential(nBodies, nSteps, repetitions));

        // Versione a thread per ogni K.
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("sequenziale", 1, seqMillis, seqMillis));
        for (int k : workerCounts) {
            double parMillis = median(measureThreads(nBodies, nSteps, k, repetitions));
            rows.add(new Row("thread", k, parMillis, seqMillis));
        }

        printTable(rows);
        printCsv(nBodies, nSteps, rows);
    }

    // ------------------------------------------------------------------
    // Misure
    // ------------------------------------------------------------------

    /** Tempi (ms) di 'repetitions' esecuzioni del sequenziale. */
    private static double[] measureSequential(int nBodies, long nSteps, int repetitions) {
        double[] times = new double[repetitions];
        for (int r = 0; r < repetitions; r++) {
            times[r] = runSequential(nBodies, nSteps);
            logRun("sequenziale", 1, r, times[r]);
        }
        return times;
    }

    /** Tempi (ms) di 'repetitions' esecuzioni della versione a thread con k worker. */
    private static double[] measureThreads(int nBodies, long nSteps, int k, int repetitions)
            throws InterruptedException {
        double[] times = new double[repetitions];
        for (int r = 0; r < repetitions; r++) {
            times[r] = runThreads(nBodies, nSteps, k);
            logRun("thread", k, r, times[r]);
        }
        return times;
    }

    /**
     * Una esecuzione del sequenziale fornito, sugli stessi corpi del parallelo.
     * Si usa init(...) e non initRandom(...), che ha un seme basato sull'ora
     * e renderebbe i carichi diversi tra le versioni.
     */
    private static double runSequential(int nBodies, long nSteps) {
        ArrayList<Body> bodies = Master.createRandomBodies(nBodies, SEED);
        NBodySimulation sim = new NBodySimulation();
        sim.init(bodies);
        // GC prima della misura: riduce la probabilità che una raccolta
        // dovuta alla run precedente cada dentro questa.
        System.gc();
        long start = System.nanoTime();
        sim.execute(nSteps);
        return toMillis(System.nanoTime() - start);
    }

    /** Una esecuzione della versione a thread con k worker. */
    private static double runThreads(int nBodies, long nSteps, int k) throws InterruptedException {
        Master master = new Master(nBodies, nSteps, k, SEED);
        System.gc();
        master.execute();
        return toMillis(master.getElapsedNanos());
    }

    /**
     * Esecuzioni non misurate su un problema piccolo, con 1 worker e con tutti
     * i core: porta a regime il JIT per entrambe le versioni.
     */
    private static void warmUp(int cores) throws InterruptedException {
        System.out.println("Riscaldamento JIT...");
        runSequential(1000, 20);
        runThreads(1000, 20, 1);
        runThreads(1000, 20, cores);
    }

    // ------------------------------------------------------------------
    // Parametri
    // ------------------------------------------------------------------

    /** 1, 2, 4, ... fino ai core disponibili, più il numero esatto di core. */
    private static List<Integer> defaultWorkerCounts(int cores) {
        TreeSet<Integer> ks = new TreeSet<>();
        for (int k = 1; k <= cores; k *= 2) {
            ks.add(k);
        }
        ks.add(cores);
        return new ArrayList<>(ks);
    }

    /** Converte "1,2,4" in [1, 2, 4], ordinato e senza duplicati. */
    private static List<Integer> parseWorkerCounts(String list) {
        TreeSet<Integer> ks = new TreeSet<>();
        for (String s : list.split(",")) {
            int k = Integer.parseInt(s.trim());
            if (k < 1) {
                throw new IllegalArgumentException("K deve essere >= 1: " + k);
            }
            ks.add(k);
        }
        return new ArrayList<>(ks);
    }

    // ------------------------------------------------------------------
    // Statistiche e output
    // ------------------------------------------------------------------

    /** Una riga dei risultati: tempo mediano e metriche rispetto al sequenziale. */
    private static class Row {
        final String version;
        final int workers;
        final double millis;
        final double speedup;
        final double efficiency;

        Row(String version, int workers, double millis, double seqMillis) {
            this.version = version;
            this.workers = workers;
            this.millis = millis;
            this.speedup = seqMillis / millis;
            this.efficiency = speedup / workers;
        }
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    private static double toMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void printHeader(int nBodies, long nSteps, int repetitions,
                                    int cores, List<Integer> workerCounts) {
        System.out.println("=== Test di performance N-Body (Step 1) ===");
        System.out.println("N = " + nBodies + ", M = " + nSteps + ", seme = " + SEED
                + ", ripetizioni = " + repetitions);
        System.out.println("K = " + workerCounts);
        System.out.println("Core disponibili: " + cores
                + ", Java " + System.getProperty("java.version")
                + ", " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println();
    }

    private static void logRun(String version, int k, int rep, double millis) {
        System.out.printf(Locale.ROOT, "  %-12s K=%-3d run %d: %10.1f ms%n",
                version, k, rep + 1, millis);
    }

    private static void printTable(List<Row> rows) {
        System.out.println();
        System.out.println("Risultati (tempo mediano):");
        System.out.printf("%-12s %4s %12s %9s %10s%n",
                "versione", "K", "tempo [ms]", "speedup", "efficienza");
        for (Row row : rows) {
            System.out.printf(Locale.ROOT, "%-12s %4d %12.1f %9.2f %10.2f%n",
                    row.version, row.workers, row.millis, row.speedup, row.efficiency);
        }
    }

    /** Gli stessi dati in CSV, da incollare nel report o in un foglio di calcolo. */
    private static void printCsv(int nBodies, long nSteps, List<Row> rows) {
        System.out.println();
        System.out.println("CSV:");
        System.out.println("versione,N,M,K,tempo_ms,speedup,efficienza");
        for (Row row : rows) {
            System.out.printf(Locale.ROOT, "%s,%d,%d,%d,%.1f,%.3f,%.3f%n",
                    row.version, nBodies, nSteps, row.workers,
                    row.millis, row.speedup, row.efficiency);
        }
    }
}
