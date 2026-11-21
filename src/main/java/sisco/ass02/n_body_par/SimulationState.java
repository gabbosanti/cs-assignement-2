package sisco.ass02.n_body_par;

import sisco.ass02.n_body_seq.model.Body;

import java.util.Collections;
import java.util.List;

/**
 * Stato condiviso della simulazione N-Body concorrente.
 *
 * NON è sincronizzato internamente: la sicurezza degli accessi è garantita
 * dalla disciplina a fasi imposta dalle barriere, non da lock sui corpi.
 * <ul>
 *   <li>Ogni worker scrive solo i corpi della propria partizione
 *       (velocità in fase A, posizione in fase B) e legge tutti i corpi.</li>
 *   <li>Il tempo virtuale viene avanzato da un solo thread (l'ultimo che
 *       arriva alla barriera di fine step) mentre tutti gli altri sono bloccati
 *       su di essa; il lock della barriera fornisce la relazione happens-before
 *       che rende visibile il nuovo valore dopo il rilascio.</li>
 * </ul>
 */
public class SimulationState {

    /** Gli N corpi simulati: l'elenco è fisso, i corpi sono mutabili. */
    private final List<Body> bodies;

    /** Passo temporale: tempo virtuale che trascorre a ogni step. */
    private final double dt;

    /** Tempo virtuale corrente: parte da 0 e cresce di dt a ogni step. */
    private double vt;

    public SimulationState(List<Body> bodies, double dt) {
        // Vista non modificabile: nessun componente può aggiungere o rimuovere
        // corpi durante la simulazione, così la partizione per indici dei
        // worker resta valida per tutti gli step.
        this.bodies = Collections.unmodifiableList(bodies);
        this.dt = dt;
        this.vt = 0;
    }

    /** Tutti i corpi: la lista è fissa, i singoli corpi sono modificabili. */
    public List<Body> getBodies() {
        return bodies;
    }

    public int getNumBodies() {
        return bodies.size();
    }

    public double getDt() {
        return dt;
    }

    public double getVirtualTime() {
        return vt;
    }

    /** Fase C: vt = vt + dt. Va invocato da un solo thread per step. */
    public void advanceTime() {
        vt += dt;
    }
}
