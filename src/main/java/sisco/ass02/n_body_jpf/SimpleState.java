package sisco.ass02.n_body_jpf;

/**
 * Stato condiviso della versione semplificata per JPF.
 *
 * Stessa struttura di n_body_par.SimulationState, ma i corpi sono ridotti a
 * due array di interi (posizione e velocità, monodimensionali) e il tempo
 * virtuale a un contatore di step. Gli interi tengono piccolo lo spazio degli
 * stati di JPF (niente oggetti V2d/P2d allocati a ogni calcolo) e rendono
 * il risultato esatto, quindi confrontabile con ==.
 *
 * Come nell'originale NON è sincronizzato: la correttezza degli accessi
 * dipende solo dalle barriere, ed è proprio questo che JPF deve verificare.
 */
public class SimpleState {

    /** Posizione di ogni corpo: letta da tutti in fase A, scritta dal proprietario in fase B. */
    private final int[] pos;

    /** Velocità di ogni corpo: scritta dal proprietario in fase A, letta da lui in fase B. */
    private final int[] vel;

    /** Step completati: è il tempo virtuale vt, avanzato dall'azione della barriera di fine step. */
    private int step;

    /** Corpi in posizioni distinte (pos[i] = i) e fermi. */
    public SimpleState(int nBodies) {
        this.pos = new int[nBodies];
        this.vel = new int[nBodies];
        for (int i = 0; i < nBodies; i++) {
            pos[i] = i;
        }
        this.step = 0;
    }

    public int getNumBodies() {
        return pos.length;
    }

    public int getPos(int i) {
        return pos[i];
    }

    public int getVel(int i) {
        return vel[i];
    }

    public void setPos(int i, int value) {
        pos[i] = value;
    }

    public void setVel(int i, int value) {
        vel[i] = value;
    }

    public int getStep() {
        return step;
    }

    /** Fase C (vt += dt): invocata da un solo thread per step, come azione di barriera. */
    public void advanceStep() {
        step++;
    }

    /**
     * Fase A semplificata per il corpo i: la "forza" è la somma delle distanze
     * da tutti gli altri corpi. Come nella fisica vera, dipende dalle posizioni
     * di TUTTI i corpi: se qualcuno le cambia durante la fase A, il risultato
     * cambia e il controllo finale lo rileva.
     */
    public int computeForce(int i) {
        int force = 0;
        for (int j = 0; j < pos.length; j++) {
            if (j != i) {
                force += pos[j] - pos[i];
            }
        }
        return force;
    }
}
