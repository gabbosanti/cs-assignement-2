# Risultati: test di performance e verifica con JPF (Step 1, versione a thread)

- Le **misure di performance** sono prodotte da `sisco.ass02.benchmark.PerformanceTest`.
- La **verifica con model-checking** è fatta con Java PathFinder sulla versione semplificata `sisco.ass02.n_body_jpf`, tramite lo script `run-jpf.sh` (vedi la sezione [Verifica con JPF](#verifica-con-java-pathfinder-jpf)).

## Ambiente e metodo

- **Macchina:** Intel Core Ultra 7 255HX con 20 core (8 P-core + 12 E-core, senza hyperthreading), Linux, Java 25.
- **Carico:** stessi corpi iniziali per tutte le versioni (seme 42), `dt = 0.001`.
- **Cosa si misura:** solo la simulazione, non la creazione dei corpi. Per la versione a thread il tempo va dall'avvio al `join()` dei worker.
- **Ripetizioni:** prima delle misure c'è un riscaldamento del JIT non misurato; ogni configurazione viene poi eseguita 3 volte e si riporta il **tempo mediano**.
- **Metriche:** speedup `S(K) = T_seq / T_par(K)`; efficienza `E(K) = S(K) / K`.

Per riprodurre i risultati:

```
java -cp <classes> sisco.ass02.benchmark.PerformanceTest 5000 100 3
java -cp <classes> sisco.ass02.benchmark.PerformanceTest 1500 30 3
```

## N = 5000 corpi, M = 100 step

Sono i parametri del `SimulationMain` sequenziale fornito.

| versione | K | tempo [ms] | speedup | efficienza |
|---|---:|---:|---:|---:|
| sequenziale | 1 | 24332.3 | 1.00 | 1.00 |
| thread | 1 | 24237.2 | 1.00 | 1.00 |
| thread | 2 | 12087.5 | 2.01 | 1.01 |
| thread | 4 | 6097.8 | 3.99 | 1.00 |
| thread | 8 | 3381.4 | 7.20 | 0.90 |
| thread | 16 | 2312.4 | 10.52 | 0.66 |
| thread | 20 | 2265.5 | 10.74 | 0.54 |

## N = 1500 corpi, M = 30 step

| versione | K | tempo [ms] | speedup | efficienza |
|---|---:|---:|---:|---:|
| sequenziale | 1 | 659.5 | 1.00 | 1.00 |
| thread | 1 | 660.3 | 1.00 | 1.00 |
| thread | 2 | 347.6 | 1.90 | 0.95 |
| thread | 4 | 197.1 | 3.35 | 0.84 |
| thread | 8 | 166.3 | 3.97 | 0.50 |
| thread | 16 | 163.6 | 4.03 | 0.25 |
| thread | 20 | 153.9 | 4.29 | 0.21 |

## Osservazioni

- **Miglioramento rispetto al sequenziale.** Con 20 worker e N = 5000 il tempo scende da circa 24.3 s a circa 2.3 s, uno speedup di circa 10.7.
- **Overhead della concorrenza.** Con K = 1 il tempo coincide con il sequenziale, quindi master e barriere hanno un costo trascurabile.
- **Scalabilità con i core.** Fino a 4 worker lo speedup è quasi lineare, e a 8 l'efficienza è ancora al 90%. Oltre cala. La causa più probabile è l'architettura ibrida della CPU: oltre gli 8 P-core i worker girano anche sugli E-core, più lenti. Poiché la partizione dei corpi è statica e a blocchi uguali, a ogni barriera tutti aspettano il worker più lento. È un'ipotesi non verificata.
- **Dimensione del problema.** Con N = 1500 e M = 30 lo speedup si ferma intorno a 4. Il lavoro per step cresce come N², quindi con pochi corpi il calcolo di ogni fase dura poco rispetto al costo fisso delle due barriere per step e del bilanciamento. Il parallelismo rende di più sui problemi grandi.

---

## Verifica con Java PathFinder (JPF)

Come chiede il README, JPF viene applicato a una **versione semplificata** della versione a thread, il package `sisco.ass02.n_body_jpf`.

**Cosa è identico all'originale:**
- l'architettura master-workers;
- la partizione dei corpi a blocchi `k·N/K`;
- il ciclo dei worker: fase A, barriera, fase B, barriera con `vt++`;
- soprattutto la barriera, che è la **stessa classe** `sync.Barrier` del programma vero.

**Cosa è semplificato:** i corpi sono ridotti a interi e la fisica a una "forza" intera, che dipende comunque dalle posizioni di tutti i corpi. Così lo spazio degli stati resta piccolo e il risultato è esatto.

**Proprietà controllate su tutti gli interleaving:**
- **assenza di deadlock**: proprietà di default di JPF;
- **assenza di eccezioni non gestite**: comprende le asserzioni del programma. I worker controllano che `vt` non avanzi prima che tutti abbiano finito lo step; il main controlla che lo stato finale coincida con il calcolo sequenziale e che `vt = M`;
- **assenza di race condition** sui dati condivisi, con il listener `PreciseRaceDetector`.

### Come ottenere i risultati

I risultati si ottengono eseguendo lo script **`run-jpf.sh`** dalla root del progetto:

```
./run-jpf.sh                 # default: N=3 corpi, M=2 step, K=2 worker
./run-jpf.sh 3 3 3           # N M K a scelta
./run-jpf.sh 2 2 2 --bug     # versione con errore introdotto apposta
./run-jpf.sh --no-race       # senza race detector
./run-jpf.sh --help
```

Lo script:
1. compila la versione semplificata e la barriera in `target/jpf-classes`;
2. lancia JPF;
3. salva il log completo in `target/jpf.log`;
4. esce con 0 se JPF non trova errori, 1 se ne trova, 2 se manca il JDK o JPF.

### Risultati

| Configurazione (N, M, K) | Esito | Stati nuovi | Tempo |
|---|---|---:|---:|
| 3, 2, 2 | nessun errore | 16 019 | ~3 s |
| 4, 2, 3 | nessun errore | ~1 076 000 | ~4 min |
| 3, 3, 3 | nessun errore | ~1 256 000 | ~4.5 min |
| 2, 2, 2 con `--bug` | **errore trovato** | — | immediato |

**Il caso con `--bug`.** Con `--bug` i worker saltano la barriera di fine fase A. JPF trova un interleaving in cui un worker aggiorna una posizione mentre un altro la sta ancora leggendo, e lo segnala così:

```
error #1: gov.nasa.jpf.vm.NoUncaughtExceptionsProperty
  "java.lang.AssertionError: corpo 0 diverso dal sequenziale: pos 2 (attesa 1), vel 1 (attesa 0)"
```

Questo mostra che le proprietà non sono verificate "a vuoto": senza la barriera l'errore emerge, con la barriera no.

**Prova sul programma completo.** Lo stesso esperimento è stato fatto sul programma completo (`n_body_par.SimulationMain` con 2 corpi, 2 step e 2 worker, circa 38 000 stati):
- la versione corretta non dà errori;
- senza la barriera di fase A il `PreciseRaceDetector` segnala `race for field ...P2d`, cioè la posizione scritta in fase B mentre un altro worker la legge in fase A.

**Costo.** Lo spazio degli stati cresce molto in fretta con il numero di worker: con 3 worker si supera già il milione di stati. Per verifiche rapide conviene K = 2.

### Versioni di Java: cosa cambiare in `run-jpf.sh`

JPF è una JVM scritta in Java e legge direttamente il bytecode del programma, quindi ha due vincoli:

1. **Va eseguito con il JDK per cui è stato compilato.** Il ramo `master` di `jpf-core` usato qui richiede **Java 11**. Con il JDK di sistema (Java 25) non parte.
2. **Il programma da verificare va compilato a un livello di bytecode che JPF sa leggere.** Il `pom.xml` compila per Java 21, che JPF per Java 11 non legge. Per questo lo script ricompila a parte con `--release 11`.

Le righe da adattare in `run-jpf.sh` sono:

| Riga | Contenuto attuale | Quando cambiarla |
|---|---|---|
| 27 | `JAVA11_HOME="${JAVA11_HOME:-$HOME/.jdks/jdk-11.0.33+10}"` | se il JDK 11 è installato altrove o ha un'altra versione minore (es. `/usr/lib/jvm/java-11-openjdk`) |
| 28 | `JPF_HOME="${JPF_HOME:-/home/KenoG/Uni/Concurrent-Systems/LAB/jpf/jpf-core}"` | se `jpf-core` è in un'altra cartella (deve contenere `build/RunJPF.jar`) |
| 66 | `"$JAVA11_HOME/bin/javac" --release 11 ...` | se si usa un JPF per un'altra versione di Java: `--release` deve essere uguale a quella versione |
| 73–77 | flag `--add-exports` / `--add-opens` | servono da Java 9 in poi; con il ramo `java-8` di JPF vanno tolti |

Invece di modificare il file si possono anche impostare le due variabili d'ambiente:

```
JAVA11_HOME=/usr/lib/jvm/java-11-openjdk JPF_HOME=~/jpf-core ./run-jpf.sh
```

**Per usare un'altra versione di JPF** (per esempio il ramo `java-17` di `jpf-core`):
1. compilarlo con quel JDK (`./gradlew buildJars`);
2. puntare `JAVA11_HOME` a quel JDK (il nome della variabile resta lo stesso);
3. portare `--release` alla stessa versione.

Il codice della versione semplificata compila senza modifiche con qualunque `--release` da 11 in su.

**Installazione usata per questi risultati:**
- JDK Temurin 11.0.33 in `~/.jdks/`;
- `jpf-core` (ramo `master`, compilato con `./gradlew buildJars`) in `LAB/jpf/jpf-core`;
- configurazione in `~/.jpf/site.properties`, con la riga `jpf-core = <percorso di jpf-core>`.
