#!/usr/bin/env bash
#
# Verifica con Java PathFinder della versione semplificata della simulazione
# (package sisco.ass02.n_body_jpf), che usa la vera barriera sync.Barrier.
#
# Uso: ./run-jpf.sh [N] [M] [K] [--bug] [--no-race]
#   N          numero di corpi    (default 3)
#   M          numero di step     (default 2)
#   K          numero di worker   (default 2)
#   --bug      toglie la barriera di fine fase A: JPF deve trovare l'errore
#   --no-race  disattiva PreciseRaceDetector (esplorazione un po' più veloce)
#
# Esempi:
#   ./run-jpf.sh                # configurazione di default
#   ./run-jpf.sh 3 3 3          # 3 corpi, 3 step, 3 worker
#   ./run-jpf.sh 2 2 2 --bug    # versione con errore introdotto apposta
#
# Variabili d'ambiente (opzionali), per usare installazioni diverse:
#   JAVA11_HOME  JDK 11 con cui eseguire JPF
#   JPF_HOME     cartella di jpf-core già compilato (contiene build/RunJPF.jar)
#
# Esito: codice di uscita 0 se JPF non trova errori, 1 se ne trova,
# 2 per errori di configurazione. Il log completo va in target/jpf.log.

set -euo pipefail

JAVA11_HOME="${JAVA11_HOME:-$HOME/.jdks/jdk-11.0.33+10}"
JPF_HOME="${JPF_HOME:-/home/KenoG/Uni/Concurrent-Systems/LAB/jpf/jpf-core}"

# --- argomenti ----------------------------------------------------------
N=3; M=2; K=2
BUG=""
RACE=1
positional=()
for arg in "$@"; do
    case "$arg" in
        --bug)     BUG="bug" ;;
        --no-race) RACE=0 ;;
        -h|--help) sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        -*)        echo "Opzione sconosciuta: $arg" >&2; exit 2 ;;
        *)         positional+=("$arg") ;;
    esac
done
if [ ${#positional[@]} -ge 1 ]; then N="${positional[0]}"; fi
if [ ${#positional[@]} -ge 2 ]; then M="${positional[1]}"; fi
if [ ${#positional[@]} -ge 3 ]; then K="${positional[2]}"; fi

# --- controlli sull'ambiente --------------------------------------------
if [ ! -x "$JAVA11_HOME/bin/java" ]; then
    echo "JDK 11 non trovato in $JAVA11_HOME (imposta JAVA11_HOME)" >&2; exit 2
fi
if [ ! -f "$JPF_HOME/build/RunJPF.jar" ]; then
    echo "jpf-core non trovato in $JPF_HOME (imposta JPF_HOME)" >&2; exit 2
fi

# Lo script può essere lanciato da qualunque cartella: si lavora nella root del progetto.
cd "$(dirname "$0")"

# --- compilazione -------------------------------------------------------
# JPF (versione per Java 11) legge solo bytecode fino a Java 11, mentre il
# pom compila per Java 21: per questo si compila a parte con --release 11.
# Servono solo la versione semplificata e la barriera.
CLASSES=target/jpf-classes
rm -rf "$CLASSES"
mkdir -p "$CLASSES"
"$JAVA11_HOME/bin/javac" --release 11 -d "$CLASSES" \
    src/main/java/sisco/ass02/n_body_jpf/*.java \
    src/main/java/sisco/ass02/n_body_par/sync/Barrier.java

# --- esecuzione di JPF --------------------------------------------------
# I flag --add-exports/--add-opens servono ai "native peer" di JPF che, su
# Java 11, accedono a classi interne del JDK (stessi flag del build di jpf-core).
JVM_FLAGS=(-Xmx2g -ea
    --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
    --add-opens   java.base/jdk.internal.misc=ALL-UNNAMED
    --add-opens   java.base/java.lang=ALL-UNNAMED
    --add-opens   java.base/java.util=ALL-UNNAMED)

JPF_OPTS=(+classpath="$CLASSES"
          # Si ferma al primo errore e stampa la traccia che lo produce.
          +search.multiple_errors=false
          +report.console.property_violation=error,trace)
if [ "$RACE" -eq 1 ]; then
    JPF_OPTS+=(+listener=gov.nasa.jpf.listener.PreciseRaceDetector)
fi

echo "JPF su SimpleSimulationMain: N=$N M=$M K=$K${BUG:+ (con bug: senza barriera di fase A)}$([ "$RACE" -eq 1 ] && echo ', race detector attivo')"

LOG=target/jpf.log
# set +e: si vuole leggere l'esito dal log anche se JPF termina con errore.
set +e
"$JAVA11_HOME/bin/java" "${JVM_FLAGS[@]}" -jar "$JPF_HOME/build/RunJPF.jar" \
    "${JPF_OPTS[@]}" sisco.ass02.n_body_jpf.SimpleSimulationMain "$N" "$M" "$K" $BUG \
    2>&1 | tee "$LOG"
set -e

# RunJPF non distingue sempre l'esito nel codice di uscita: lo si ricava dal log.
if grep -q "no errors detected" "$LOG"; then
    echo ">>> JPF: nessun errore trovato"
    exit 0
else
    echo ">>> JPF: errore trovato (dettagli in $LOG)"
    exit 1
fi
