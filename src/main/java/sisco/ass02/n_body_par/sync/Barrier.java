package sisco.ass02.n_body_par.sync;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Barriera ciclica (riutilizzabile) implementata come monitor.
 *
 * Sincronizza un numero fissato di thread (nParties): ognuno chiama
 * {@link #hitAndWaitAll()} e resta bloccato finché tutti non sono arrivati;
 * a quel punto vengono rilasciati insieme e la barriera si riarma da sola
 * per il ciclo successivo, senza doverla ricreare.
 *
 * Opzionalmente può eseguire una "azione di barriera": un'operazione svolta
 * una sola volta per ciclo, dall'ultimo thread arrivato, mentre tutti gli
 * altri sono ancora fermi (usata per la fase C, vt += dt).
 */
public class Barrier {

    /** Numero di thread che devono arrivare prima del rilascio. */
    private final int nParties;

    /**
     * Azione eseguita una volta per ciclo dall'ultimo arrivato, prima del rilascio.
     * Può essere null: in quel caso la barriera non esegue nessuna azione.
     */
    private final Runnable barrierAction;

    /** Lock del monitor: garantisce la mutua esclusione sullo stato interno. */
    private final Lock mutex;

    /** Condition su cui attendono i thread arrivati quando mancano ancora altri. */
    private final Condition allArrived;

    /** Thread arrivati nel ciclo corrente (0 .. nParties-1). */
    private int count;

    /** Numero del ciclo corrente: cambia a ogni rilascio. */
    private long generation;


    /**
     * Barriera senza azione: si limita a sincronizzare i thread.
     * L'azione è null e in {@link #hitAndWaitAll()} viene semplicemente saltata.
     */
    public Barrier(int nParties) {
        this(nParties, null);
    }

    public Barrier(int nParties, Runnable barrierAction) {
        if (nParties <= 0) {
            throw new IllegalArgumentException("nParties deve essere > 0");
        }
        this.nParties = nParties;
        this.barrierAction = barrierAction;
        this.mutex = new ReentrantLock();
        this.allArrived = mutex.newCondition();
        this.count = 0;
        this.generation = 0;
    }

    /**
     * Segnala l'arrivo del thread chiamante e lo blocca finché tutti gli
     * nParties thread non hanno chiamato questo metodo nel ciclo corrente.
     *
     * @throws InterruptedException se il thread viene interrotto durante l'attesa
     */
    public void hitAndWaitAll() throws InterruptedException {
        mutex.lock();
        try {
            // Il ciclo a cui questo thread appartiene: serve a capire quando
            // il "proprio" ciclo è stato rilasciato.
            final long myGeneration = generation;
            count++;

            if (count == nParties) {
                // Ultimo arrivato: esegue l'azione (gli altri sono tutti fermi),
                // riarma la barriera e sveglia tutti.
                if (barrierAction != null) {
                    barrierAction.run();
                }
                count = 0;
                generation++;
                allArrived.signalAll();
            } else {
                // Attesa in un while (mai un if): si riparte solo quando il
                // proprio ciclo è stato davvero rilasciato. Protegge sia dai
                // risvegli spuri sia da un thread veloce che rientra subito
                // nella barriera al ciclo successivo e incrementa count.
                while (myGeneration == generation) {
                    allArrived.await();
                }
            }
        } finally {
            mutex.unlock();
        }
    }
}
