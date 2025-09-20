#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd -P)"
export PYTHONPATH="${ROOT}${PYTHONPATH:+:$PYTHONPATH}"

if [ -x "${ROOT}/.venv/bin/python" ]; then
  PY="${ROOT}/.venv/bin/python"
else
  PY="python3"
fi

OUTDIR="gen_nets"
LIMIT=""
DIVISION_MODE=""
PASSTHRU=()
args=( "$@" )
i=0
while [ $i -lt ${#args[@]} ]; do
  a="${args[$i]}"
  case "$a" in
    -L)
      (( i+1 < ${#args[@]} )) || { echo "Falta valor para -L" >&2; exit 2; }
      LIMIT="${args[$((i+1))]}"; i=$((i+2));;
    -L=*)
      LIMIT="${a#-L=}"; i=$((i+1));;
    --optimized)
      DIVISION_MODE="optimized"; i=$((i+1));;
    --original)
      DIVISION_MODE="original"; i=$((i+1));;
    -o)
      (( i+1 < ${#args[@]} )) || { echo "Falta valor para -o" >&2; exit 2; }
      OUTDIR="${args[$((i+1))]}"
      PASSTHRU+=("$a" "${args[$((i+1))]}")
      i=$((i+2));;
    -o=*)
      OUTDIR="${a#-o=}"
      PASSTHRU+=("$a")
      i=$((i+1));;
    *)
      PASSTHRU+=("$a"); i=$((i+1));;
  esac
done

STAMP="$(mktemp)"; trap 'rm -f "$STAMP"' EXIT
touch "$STAMP"

"$PY" "${ROOT}/auto_gen.py" "${PASSTHRU[@]}"

case "$OUTDIR" in
  /*) ;;
  *) OUTDIR="$(cd -P -- "$OUTDIR" 2>/dev/null || cd -P -- "${PWD%/}/${OUTDIR#./}"; pwd)";;
esac

count=0
find "$OUTDIR" -type f -name "net.pflow" -newer "$STAMP" -print0 \
| while IFS= read -r -d '' PF; do
    DIR="${PF%/*}"
    printf '[*] Procesando: %s\n' "$PF"

    "$PY" "${ROOT}/pflow_to_ndr.py" "$PF" -o "${DIR}/net.ndr"

    PARTITION_CMD=("$PY" "${ROOT}/pflow_partition_s3pr.py" "$PF"
                   -o "${DIR}/net_dividida.json"
                   --dot "${DIR}/subnets.dot"
                   --bin 50 --min-train-places 2 --min-train-trans 2)

    if [ "$DIVISION_MODE" = "optimized" ]; then
        PARTITION_CMD+=(--optimized)
        printf '[*] Usando división optimizada (OptimizedSubnetDivider)\n'
    elif [ "$DIVISION_MODE" = "original" ]; then
        PARTITION_CMD+=(--original)
        printf '[*] Usando división original (clustering geográfico)\n'
    else
        printf '[*] Usando división por defecto (clustering geográfico)\n'
    fi

    "${PARTITION_CMD[@]}"

    DOTPY="${ROOT}/../utils/petri_json_to_dot.py"
    [ -f "$DOTPY" ] || DOTPY="${ROOT}/../utils/petri_json_to_dot.py"
    if [ -f "$DOTPY" ]; then
      "$PY" "$DOTPY" "${DIR}/net_dividida.json" > "${DIR}/net.dot"
      if command -v dot >/dev/null 2>&1; then
        dot -Tsvg "${DIR}/net.dot" -o "${DIR}/net.svg"
      fi
    fi

    printf '[✓] OK: %s | %s | %s | %s\n' "${DIR}/net.ndr" "${DIR}/net_dividida.json" "${DIR}/subnets.dot" "${DIR}/net.dot"

    if [ -n "${LIMIT}" ]; then
      count=$((count+1))
      if [ "$count" -ge "$LIMIT" ]; then exit 0; fi
    fi
  done || true
