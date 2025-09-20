#!/bin/bash
# Benchmark comparativo para algoritmos de reachability tree
# Compara Java, C y Tina con diferentes configuraciones de hilos

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1" >&2; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1" >&2; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1" >&2; }
log_error() { echo -e "${RED}[ERROR]${NC} $1" >&2; }
log_result() { echo -e "${CYAN}[RESULT]${NC} $1" >&2; }

show_live_results() {
    local network_name="$1" algorithm="$2" threads="$3" places="$4"
    local transitions="$5" states="$6" duration="$7"

    printf "| %-15s | %-4s | %-4s | %-9s | %-6s | %-10s | %-8s |\n" \
           "$network_name" "$places" "$transitions" "$algorithm" "$threads" "$states" "${duration}s"
}

show_help() {
    cat << EOF
🏆 Benchmark Comparativo de Algoritmos S3PR

Uso: $0 <directorio_redes> <hilos> <timeout> [opciones]

Argumentos:
  directorio_redes    Directorio con redes generadas (net_dividida.json)
  hilos              Array de hilos: "[1,4,8]" o "4" para valor único
  timeout            Timeout en segundos por ejecución

Opciones:
  --java-only        Solo ejecutar algoritmo Java
  --c-only           Solo ejecutar algoritmo C
  --tina-only        Solo ejecutar Tina
  --no-java          Omitir Java (útil si es muy lento)
  --no-tina          Omitir Tina (útil si no está instalado)
  --output DIR       Directorio para resultados (default: benchmark_results)
  --max-networks N   Máximo número de redes a procesar (default: ilimitado)
  -h, --help         Mostrar ayuda

Ejemplos:
  $0 redes_s3pr "[1,4,8]" 300
  $0 test_nets "4" 60 --java-only --output resultados_java
  $0 redes_grandes "[1,2,4]" 600 --no-tina --max-networks 5

Algoritmos comparados:
  - Java: Algoritmo paralelo con división de subredes
  - C: Implementación nativa optimizada
  - Tina: Herramienta de referencia (nd)

Output: Tabla comparativa y archivos CSV con resultados detallados
EOF
}

# Función para obtener estadísticas de red
get_network_stats() {
    local json_file="$1"

    if [[ ! -f "$json_file" ]]; then
        echo "0,0"
        return 1
    fi

    local places transitions
    places=$(jq '.M0 | length' "$json_file" 2>/dev/null || echo "0")
    transitions=$(jq '.I_minus[0] | length' "$json_file" 2>/dev/null || echo "0")

    echo "$places,$transitions"
}

# Función para ejecutar Java
run_java_algorithm() {
    local json_file="$1"
    local threads="$2"
    local timeout="$3"

    local start_time end_time duration states
    start_time=$(date +%s)

    local output
    if output=$(timeout "${timeout}s" mvn exec:java \
        -Dexec.mainClass="algorithm.Main" \
        -Dexec.args="--input $json_file --nthreads $threads" \
        -q 2>/dev/null); then

        end_time=$(date +%s)
        duration=$((end_time - start_time))

        # Extraer número de estados del output
        states=$(echo "$output" | grep -o 'Total states found: [0-9]*' | grep -o '[0-9]*' || echo "0")

        echo "$states,$duration,SUCCESS"
    else
        end_time=$(date +%s)
        duration=$((end_time - start_time))
        echo "0,$duration,TIMEOUT"
    fi
}

# Función para ejecutar C
run_c_algorithm() {
    local json_file="$1"
    local threads="$2"
    local timeout="$3"

    local c_binary="convert_to_c/proposal_best/bin/reachability-analyzer"
    if [[ ! -f "$c_binary" ]]; then
        echo "0,0,NOT_FOUND"
        return 1
    fi

    local start_time end_time duration states
    start_time=$(date +%s)

    local output
    if output=$(timeout "${timeout}s" "$c_binary" "$json_file" "$threads" 2>/dev/null); then
        end_time=$(date +%s)
        duration=$((end_time - start_time))

        # Extraer estados del output de C
        states=$(echo "$output" | grep -o 'Total states: [0-9]*' | grep -o '[0-9]*' || echo "0")

        echo "$states,$duration,SUCCESS"
    else
        end_time=$(date +%s)
        duration=$((end_time - start_time))
        echo "0,$duration,TIMEOUT"
    fi
}

# Función para ejecutar Tina
run_tina_algorithm() {
    local ndr_file="$1"
    local timeout="$2"

    if [[ ! -f "$ndr_file" ]] || ! command -v tina >/dev/null 2>&1; then
        echo "0,0,NOT_AVAILABLE"
        return 1
    fi

    local start_time end_time duration states
    start_time=$(date +%s)

    local temp_output
    temp_output=$(mktemp)

    if timeout "${timeout}s" tina -R "$ndr_file" "$temp_output" >/dev/null 2>&1; then
        end_time=$(date +%s)
        duration=$((end_time - start_time))

        # Contar estados en archivo de salida
        states=$(wc -l < "$temp_output" 2>/dev/null || echo "0")

        rm -f "$temp_output"
        echo "$states,$duration,SUCCESS"
    else
        end_time=$(date +%s)
        duration=$((end_time - start_time))
        rm -f "$temp_output"
        echo "0,$duration,TIMEOUT"
    fi
}

# Parsear argumentos
if [[ $# -lt 3 ]]; then
    show_help
    exit 1
fi

NETWORKS_DIR="$1"
THREADS_INPUT="$2"
TIMEOUT="$3"
shift 3

# Opciones por defecto
RUN_JAVA=true
RUN_C=true
RUN_TINA=true
OUTPUT_DIR="benchmark_results"
MAX_NETWORKS=""

# Parsear opciones
while [[ $# -gt 0 ]]; do
    case $1 in
        --java-only) RUN_C=false; RUN_TINA=false; shift ;;
        --c-only) RUN_JAVA=false; RUN_TINA=false; shift ;;
        --tina-only) RUN_JAVA=false; RUN_C=false; shift ;;
        --no-java) RUN_JAVA=false; shift ;;
        --no-tina) RUN_TINA=false; shift ;;
        --output) OUTPUT_DIR="$2"; shift 2 ;;
        --max-networks) MAX_NETWORKS="$2"; shift 2 ;;
        -h|--help) show_help; exit 0 ;;
        *) log_error "Opción desconocida: $1"; exit 1 ;;
    esac
done

# Validar directorio de redes
if [[ ! -d "$NETWORKS_DIR" ]]; then
    log_error "Directorio de redes no encontrado: $NETWORKS_DIR"
    exit 1
fi

# Parsear threads
if [[ "$THREADS_INPUT" =~ ^\[.*\]$ ]]; then
    # Array format: [1,4,8]
    THREADS_ARRAY=($(echo "$THREADS_INPUT" | tr -d '[]' | tr ',' ' '))
else
    # Single value: 4
    THREADS_ARRAY=("$THREADS_INPUT")
fi

# Crear directorio de resultados
mkdir -p "$OUTPUT_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_CSV="$OUTPUT_DIR/benchmark_${TIMESTAMP}.csv"

log_info "🏆 Benchmark Comparativo S3PR"
log_info "📁 Redes: $NETWORKS_DIR"
log_info "🧵 Threads: ${THREADS_ARRAY[*]}"
log_info "⏱️  Timeout: ${TIMEOUT}s"
log_info "📊 Resultados: $RESULTS_CSV"

# Compilar Java si es necesario
if [[ "$RUN_JAVA" == "true" ]]; then
    log_info "🔨 Compilando Java..."
    mvn compile -q
fi

# Compilar C si es necesario
if [[ "$RUN_C" == "true" ]]; then
    C_MAKEFILE="convert_to_c/proposal_best/Makefile"
    if [[ -f "$C_MAKEFILE" ]]; then
        log_info "🔨 Compilando C..."
        make -C convert_to_c/proposal_best -s >/dev/null 2>&1 || log_warning "Error compilando C"
    fi
fi

# Crear CSV header
echo "Network,Places,Transitions,Algorithm,Threads,States,Duration_s,Status" > "$RESULTS_CSV"

# Mostrar header de tabla
echo
log_info "🚀 Iniciando benchmark..."
echo "+------------------+------+------+-----------+--------+------------+----------+"
echo "| Network          | P    | T    | Algorithm | Threads| States     | Time (s) |"
echo "+------------------+------+------+-----------+--------+------------+----------+"

# Buscar redes
network_files=($(find "$NETWORKS_DIR" -name "net_dividida.json" | sort))

if [[ ${#network_files[@]} -eq 0 ]]; then
    log_error "No se encontraron redes (net_dividida.json) en $NETWORKS_DIR"
    exit 1
fi

# Limitar número de redes si se especifica
if [[ -n "$MAX_NETWORKS" ]] && [[ ${#network_files[@]} -gt $MAX_NETWORKS ]]; then
    network_files=("${network_files[@]:0:$MAX_NETWORKS}")
    log_info "📊 Limitando a $MAX_NETWORKS redes"
fi

log_info "📊 Procesando ${#network_files[@]} redes"

# Ejecutar benchmarks
for json_file in "${network_files[@]}"; do
    network_name=$(basename "$(dirname "$json_file")")
    network_dir=$(dirname "$json_file")
    ndr_file="$network_dir/net.ndr"

    # Obtener estadísticas de la red
    IFS=',' read -r places transitions <<< "$(get_network_stats "$json_file")"

    # Ejecutar para cada configuración de threads
    for threads in "${THREADS_ARRAY[@]}"; do

        # Java
        if [[ "$RUN_JAVA" == "true" ]]; then
            IFS=',' read -r states duration status <<< "$(run_java_algorithm "$json_file" "$threads" "$TIMEOUT")"
            show_live_results "$network_name" "Java" "$threads" "$places" "$transitions" "$states" "$duration"
            echo "$network_name,$places,$transitions,Java,$threads,$states,$duration,$status" >> "$RESULTS_CSV"
        fi

        # C (solo thread principal para comparación justa)
        if [[ "$RUN_C" == "true" ]] && [[ "$threads" == "${THREADS_ARRAY[0]}" ]]; then
            IFS=',' read -r states duration status <<< "$(run_c_algorithm "$json_file" "$threads" "$TIMEOUT")"
            show_live_results "$network_name" "C" "$threads" "$places" "$transitions" "$states" "$duration"
            echo "$network_name,$places,$transitions,C,$threads,$states,$duration,$status" >> "$RESULTS_CSV"
        fi

        # Tina (no paralelo, solo una vez)
        if [[ "$RUN_TINA" == "true" ]] && [[ "$threads" == "${THREADS_ARRAY[0]}" ]]; then
            IFS=',' read -r states duration status <<< "$(run_tina_algorithm "$ndr_file" "$TIMEOUT")"
            show_live_results "$network_name" "Tina" "1" "$places" "$transitions" "$states" "$duration"
            echo "$network_name,$places,$transitions,Tina,1,$states,$duration,$status" >> "$RESULTS_CSV"
        fi
    done
done

echo "+------------------+------+------+-----------+--------+------------+----------+"

# Resumen final
total_runs=$(wc -l < "$RESULTS_CSV")
total_runs=$((total_runs - 1))  # Excluir header
successful_runs=$(grep -c "SUCCESS" "$RESULTS_CSV" || echo "0")

log_success "🏁 Benchmark completado"
log_result "📊 Total ejecuciones: $total_runs"
log_result "✅ Exitosas: $successful_runs"
log_result "📁 Resultados: $RESULTS_CSV"

if [[ $successful_runs -gt 0 ]]; then
    echo
    log_info "🎯 Análisis rápido:"

    # Mostrar estadísticas por algoritmo si hay resultados
    for algo in Java C Tina; do
        if grep -q "$algo" "$RESULTS_CSV"; then
            avg_time=$(awk -F, -v algo="$algo" '$4==algo && $8=="SUCCESS" {sum+=$7; count++} END {if(count>0) printf "%.1f", sum/count; else print "N/A"}' "$RESULTS_CSV")
            total_states=$(awk -F, -v algo="$algo" '$4==algo && $8=="SUCCESS" {sum+=$6; count++} END {if(count>0) printf "%.0f", sum/count; else print "N/A"}' "$RESULTS_CSV")
            log_result "   $algo: ${avg_time}s promedio, $total_states estados promedio"
        fi
    done
fi

log_success "📊 Para análisis detallado: open $RESULTS_CSV"