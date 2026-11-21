# Analisi del problema — Simulazione N-Body concorrente (Step 1, thread puri)

## 1. Il problema

La simulazione N-Body predice il moto di `N` corpi che si attraggono gravitazionalmente. La soluzione è iterativa: lo stato del sistema evolve a **step temporali discreti** di ampiezza `dt`, per `M` step complessivi. La logica fisica (legge di gravitazione, `F = m·a`, integrazione di velocità e posizione) è fornita dall'implementazione sequenziale e viene riusata invariata: l'oggetto di questa analisi è **l'orchestrazione dell'esecuzione concorrente**, non il calcolo fisico.

## 2. Struttura di un singolo step

Ogni step si scompone in tre sotto-fasi, in ordine logico obbligato:

- **Fase A — forze e velocità.** Per ogni corpo `i`, si somma la forza gravitazionale esercitata da tutti gli altri corpi `j ≠ i`; da questa si ricava l'accelerazione (`F/m`) e si aggiorna la velocità (`v + a·dt`).
- **Fase B — posizioni.** Per ogni corpo si aggiorna la posizione a partire dalla velocità appena calcolata (`p + v·dt`).
- **Fase C — avanzamento del tempo.** `vt = vt + dt`.

La sequenza A → B → C si ripete per `M` step.

## 3. Profili di accesso ai dati

Lo stato condiviso è l'insieme degli `N` corpi, ciascuno con posizione, velocità e massa. Le fasi accedono a questo stato in modo diverso, ed è da qui che emerge tutta la concorrenza del problema:

| Fase | Legge | Scrive |
|------|-------|--------|
| A | posizione e massa di **tutti** i corpi | solo la **propria** velocità `v[i]` |
| B | la propria velocità `v[i]` | solo la **propria** posizione `p[i]` |

Punto cruciale: nel calcolo del corpo `i`, gli altri corpi `j` sono usati **solo in lettura**. L'unico input condiviso della fase A sono le posizioni, che all'inizio dello step sono **già tutte disponibili** (quelle dello step precedente, o quelle iniziali al primo step). Nessun corpo dipende dalla forza o dalla velocità di un altro corpo *dentro lo stesso step*: ciascuno parte dalle posizioni comuni e produce il proprio risultato.

## 4. Dove nasce il parallelismo

Dentro ciascuna fase i corpi sono **indipendenti in scrittura**: in fase A il corpo `i` scrive solo `v[i]`, il corpo `j` solo `v[j]`, senza conflitti; idem per le posizioni in fase B. Gli `N` corpi possono quindi essere distribuiti tra più esecutori che lavorano simultaneamente (data parallelism). L'ordine di calcolo dei corpi dentro una fase è irrilevante — proprio ciò che consente di suddividerli tra worker.

## 5. Dove nasce il pericolo

Il rischio **non** è dentro una fase, ma **al confine tra fasi**:

- Se un esecutore entra in fase B (riscrive `p[j]`) mentre un altro è ancora in fase A (legge `p[j]` per calcolare la propria forza), quest'ultimo legge una posizione "del futuro" → stato incoerente, risultato fisicamente errato.
- Lo stesso problema si presenta tra la fine di uno step e l'inizio del successivo.

## 6. Il vincolo di sincronizzazione

Ne deriva l'unico vincolo forte del problema: **separazione temporale netta tra le fasi**. Nessun esecutore entra in fase B finché *tutti* non hanno completato la fase A; nessuno entra nello step successivo finché *tutti* non hanno chiuso lo step corrente. Durante ogni fase le posizioni restano "congelate" e tutti leggono lo stesso stato coerente.

Questo vincolo si realizza con una **barriera** (sincronizzazione a grana grossa), non con mutua esclusione fine-grained sui singoli corpi: poiché gli accessi concorrenti alla stessa variabile non avvengono mai *dentro* una fase, lock per-corpo durante il calcolo sarebbero inutili e dannosi per le performance (sovra-sincronizzazione).

## 7. Implicazioni architetturali

Il problema è quindi una sequenza di `M` step, ciascuno con lavoro parallelo (fasi A e B divise per corpo) separato da punti di coordinamento (barriere). Questa forma richiede un'entità di **coordinamento** distinta dagli esecutori di **calcolo**, cioè un'architettura **master-workers**:

- i **worker** (pool di thread persistenti, creati una sola volta) calcolano le fasi A e B sui corpi loro assegnati;
- il **master** inizializza i corpi, suddivide il lavoro, scandisce gli step e gestisce la terminazione;
- una **barriera ciclica**, implementata come **monitor** con proprio codice (non costrutti low-level), separa le fasi e gli step.

Il riuso dei thread tra gli step (invece di crearli/distruggerli a ogni iterazione) evita un overhead proporzionale a `N·M` ed è ciò che permette allo speedup di non essere eroso dal costo di gestione dei thread.

## 8. Progetto della soluzione (master-workers + monitor)

Sintesi delle scelte di progetto, implementate nel package `sisco.ass02.n_body_par`. La logica fisica di `Body` (forza, velocità, posizione) è riusata senza modifiche.

**Stato condiviso — `SimulationState`.** Contiene la lista fissa degli `N` corpi, `dt` e il tempo virtuale `vt`. Non è sincronizzato: ogni worker legge tutti i corpi ma scrive solo quelli della propria partizione, e la disciplina a fasi delle barriere rende sicuri gli accessi.

**Barriera ciclica — `sync.Barrier`.** È un monitor (`ReentrantLock` + una `Condition`) con un solo metodo, `hitAndWaitAll()`:
- ogni thread incrementa `count` e si ricorda la `generation` corrente;
- l'ultimo arrivato esegue l'eventuale *azione di barriera*, azzera `count`, incrementa `generation` e sveglia tutti (`signalAll`);
- gli altri attendono in un `while (myGeneration == generation)`. Il `while` protegge dai risvegli spuri, e la generazione impedisce a un thread veloce, già rientrato nel ciclo successivo, di confondere il conteggio.

La barriera si riarma da sola, quindi la stessa istanza serve per tutti gli `M` step.

**Worker — `Worker`.** Thread persistente con una partizione contigua `[from, to)`. Per ognuno degli `M` step esegue:

```
Fase A: forze + velocità dei propri corpi
   ▼
[ forcesComputed ]  ← nessuno scrive posizioni finché tutti non le hanno lette
   ▼
Fase B: posizioni dei propri corpi
   ▼
[ stepCompleted ]   ← l'ultimo arrivato esegue vt += dt (fase C)
   ▼
step successivo
```

**Master — `Master`.** Gira sul thread chiamante e non partecipa alle barriere. Nell'ordine:
1. crea i corpi con un seme esplicito, per avere esecuzioni riproducibili;
2. divide i corpi in `K` blocchi contigui `[k·N/K, (k+1)·N/K)`, che differiscono al massimo di un corpo; se `K > N` usa solo `N` worker;
3. crea le due barriere dimensionate su `K`;
4. avvia i worker e ne fa il `join()`.

**Scelte di progetto principali.**
- *Due barriere per step* invece di una sola con double-buffering delle posizioni: lo stato resta semplice e il modello FSP lineare.
- *Fase C come azione della barriera di fine step*: `vt += dt` viene eseguito da un solo thread, mentre tutti gli altri sono fermi, quindi non c'è corsa.
- *Terminazione bilanciata*: ogni worker chiama `hitAndWaitAll()` esattamente `2·M` volte, quindi nessuno resta bloccato su una barriera dopo l'ultimo step.

**Verifica.**
- `ConsistencyCheck` confronta posizioni e velocità finali con il sequenziale, a parità di seme, `dt` e `M`, per diversi valori di `K`. Il confronto è esatto (`==`), perché le forze vengono sommate nello stesso ordine.
- Il modello FSP in [`n_body_par.lts`](n_body_par.lts) astrae master, worker e barriera.

## 9. Proprietà di correttezza rilevanti

Dal modello discendono alcune proprietà da verificare (via FSP e model-checking con JPF):

- **Assenza di deadlock**: tutti i worker e il master progrediscono sempre verso lo step successivo.
- **Coerenza di fase (safety)**: nessuna scrittura di posizioni avviene mentre una lettura di posizioni della stessa fase è in corso — garantita dalla barriera.
- **Sincronizzazione di step**: tutti i worker completano lo step `k` prima che chiunque inizi lo step `k+1`.
- **Terminazione**: dopo esattamente `M` step la simulazione termina e tutti i thread vengono chiusi ordinatamente.
