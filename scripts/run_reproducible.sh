#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NET="data/net_1.json"
THREADS=4
OUT_DIR="$ROOT_DIR/benchmark_results/$(date +%Y%m%d_%H%M%S)"
WITH_GENERATION=false

show_help() {
  cat <<'HELP'
Uso: scripts/run_reproducible.sh [--net FILE] [--threads N] [--output DIR] [--with-generation]

Compila V3 con Maven, ejecuta algorithm.Main sobre una red JSON y guarda logs/resultados.
HELP
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --net) NET="$2"; shift 2 ;;
    --threads) THREADS="$2"; shift 2 ;;
    --output) OUT_DIR="$2"; shift 2 ;;
    --with-generation) WITH_GENERATION=true; shift ;;
    -h|--help) show_help; exit 0 ;;
    *) echo "Argumento desconocido: $1" >&2; show_help; exit 2 ;;
  esac
done

mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.md"
DOT_OUT="$OUT_DIR/tree.dot"
JAR="$ROOT_DIR/target/algorithm-1.0-SNAPSHOT-jar-with-dependencies.jar"

[[ -f "$ROOT_DIR/$NET" || -f "$NET" ]] || { echo "No existe red $NET" >&2; exit 1; }
NET_PATH="$NET"
[[ -f "$NET_PATH" ]] || NET_PATH="$ROOT_DIR/$NET"

(cd "$ROOT_DIR" && mvn -q -Dmaven.test.skip=true package) > "$OUT_DIR/maven.log" 2>&1

START_NS=$(python3 - <<'PY'
import time; print(time.time_ns())
PY
)
java -jar "$JAR" --input "$NET_PATH" --nthreads "$THREADS" --omega --output "$DOT_OUT" > "$OUT_DIR/run.log" 2>&1
END_NS=$(python3 - <<'PY'
import time; print(time.time_ns())
PY
)
ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
STATES=$(grep -Eo 'Total states in reachability tree: [0-9]+' "$OUT_DIR/run.log" | awk '{print $NF}' | tail -1)

{
  echo "# V3 reproducible run"
  echo
  echo "- Fecha: $(date -Iseconds)"
  echo "- Red: $NET_PATH"
  echo "- Threads: $THREADS"
  echo "- Tiempo_ms: $ELAPSED_MS"
  echo "- Estados_reportados: ${STATES:-no_detectado}"
  echo "- DOT: $DOT_OUT"
} > "$SUMMARY"

if [[ "$WITH_GENERATION" == true ]]; then
  "$ROOT_DIR/generate_s3pr_networks.sh" --sizes "micro" --count 1 --output "$OUT_DIR/generated_s3pr" > "$OUT_DIR/generation.log" 2>&1
  echo "- Redes_generadas: $OUT_DIR/generated_s3pr" >> "$SUMMARY"
fi

cat "$SUMMARY"
