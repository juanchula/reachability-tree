#!/bin/bash

# Script de benchmark comparativo para algoritmos de reachability tree
# Compara Java, C y Tina con diferentes configuraciones de hilos

set -euo pipefail

# Colores para logging
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" >&2
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

log_result() {
    echo -e "${CYAN}[RESULT]${NC} $1" >&2
}

# Función para mostrar tabla en tiempo real
show_live_results() {
    local network_name="$1"
    local algorithm="$2" 
    local threads="$3"
    local places="$4"
    local transitions="$5"
    local states="$6"
    local duration="$7"
    local status="$8"
    
    printf "| %-15s | %-4s | %-4s | %-9s | %-6s | %-10s | %-8s |\n" \
           "$network_name" "$places" "$transitions" "$algorithm" "$threads" "$states" "${duration}s"
}

# Función para inicializar tabla
init_results_table() {
    echo
    echo "🔬 RESULTADOS EN TIEMPO REAL"
    echo "════════════════════════════════════════════════════════════════════════════"
    printf "| %-15s | %-4s | %-4s | %-9s | %-6s | %-10s | %-8s |\n" \
           "Red" "P" "T" "Algoritmo" "Hilos" "Estados" "Tiempo"
    echo "|-----------------|------|------|-----------|--------|------------|----------|"
    
    # Crear archivo markdown de resultados en tiempo real
    LIVE_RESULTS_FILE="$OUTPUT_DIR/resultados_en_vivo.md"
    cat > "$LIVE_RESULTS_FILE" << EOF
# 🔬 BENCHMARK EN VIVO - $(date '+%Y-%m-%d %H:%M:%S')

**Estado:** 🟡 EJECUTÁNDOSE  
**Directorio:** \`$NETWORKS_DIR\`  
**Algoritmos:** [${FINAL_ALGORITHMS[*]}]  
**Hilos:** [${THREADS[*]}]  
**Timeout:** ${TIMEOUT}s  

## 📊 Resultados Actualizados en Tiempo Real

| Red | P | T | Algoritmo | Hilos | Estados | Tiempo | Estado |
|-----|---|---|-----------|-------|---------|--------|--------|
EOF
}

# Función para actualizar markdown en vivo
update_live_results() {
    local network_name="$1"
    local algorithm="$2" 
    local threads="$3"
    local places="$4"
    local transitions="$5"
    local states="$6"
    local duration="$7"
    local status="$8"
    
    local status_emoji="✅"
    if [[ "$status" != "SUCCESS" ]]; then
        status_emoji="❌"
        states="FAIL"
    fi
    
    # Agregar línea al markdown en vivo
    echo "| $network_name | $places | $transitions | $algorithm | $threads | $states | ${duration}s | $status_emoji |" >> "$LIVE_RESULTS_FILE"
    
    # Actualizar estadísticas al final del archivo
    sed -i '' '/## 📈 Estadísticas Actuales/,$d' "$LIVE_RESULTS_FILE" 2>/dev/null || true
    
    cat >> "$LIVE_RESULTS_FILE" << EOF

## 📈 Estadísticas Actuales

- **Ejecuciones completadas:** $((current_run))/$total_runs
- **Progreso:** $((current_run * 100 / total_runs))%
- **Exitosas:** $successful_runs
- **Fallidas:** $failed_runs
- **Última actualización:** $(date '+%H:%M:%S')

---

💡 **Nota:** Este archivo se actualiza automáticamente. Los resultados finales estarán en \`informe_comparativo.md\`
EOF
}

show_usage() {
    cat << EOF
🔬 Benchmark Comparativo de Algoritmos S3PR

Uso: $0 <directorio_redes> <array_hilos> <timeout> [opciones]

Argumentos:
  directorio_redes    Directorio con archivos net_dividida.json
  array_hilos         Array de hilos JSON: "[1,4,8,16]"
  timeout             Timeout por ejecución en segundos

Opciones:
  --algorithms LISTA  Algoritmos a probar (default: "java,c,tina")
  --runs INT         Ejecuciones por configuración (default: 1)
  --output DIR       Directorio de salida (default: DIR/benchmark_results)
  --csv-only         Solo generar CSV, sin markdown
  --no-cleanup       No limpiar archivos temporales
  -h, --help         Mostrar esta ayuda

Algoritmos disponibles:
  java    - Algoritmo Java multihilo (omega deshabilitado para S3PR)
  c       - Algoritmo C optimizado (omega deshabilitado para S3PR)
  tina    - Herramienta Tina (referencia)

Ejemplos:
  $0 s3pr_networks "[1,4,8]" 300
  $0 test_nets "[1,2,4,8,16]" 600 --algorithms "java,c" --runs 3
  $0 large_nets "[1,10,20]" 1800 --output results_2024

Salida:
  DIR/benchmark_results/
    ├── benchmark_TIMESTAMP.csv     (resultados CSV)
    ├── benchmark_report.md         (reporte markdown)
    └── raw_logs/                   (logs detallados)
EOF
}

# Parsear array JSON de hilos
parse_thread_array() {
    local json_array="$1"
    # Remover corchetes y espacios, dividir por comas
    json_array="${json_array//[\[\]]/}"
    json_array="${json_array//[[:space:]]/}"
    IFS=',' read -ra THREAD_ARRAY <<< "$json_array"
    
    # Validar que todos son números
    for thread in "${THREAD_ARRAY[@]}"; do
        if ! [[ "$thread" =~ ^[0-9]+$ ]]; then
            log_error "Thread count inválido: $thread"
            exit 1
        fi
    done
    
    printf '%s\n' "${THREAD_ARRAY[@]}"
}

# Función para ejecutar algoritmo Java (sin --omega para redes S3PR bounded)
run_java_algorithm() {
    local network_file="$1"
    local threads="$2"
    local timeout="$3"
    local output_file="$4"
    
    local start_time end_time duration
    
    start_time=$(date +%s.%N)
    
    if timeout "${timeout}s" mvn -q exec:java \
        -Dexec.mainClass="algorithm.Main" \
        -Dexec.args="--input \"$network_file\" --nthreads $threads" \
        &>"$output_file"; then
        end_time=$(date +%s.%N)
        duration=$(echo "$end_time - $start_time" | bc -l)
        
        # Extraer número de estados de varias fuentes posibles
        local states_explored=$(grep -E "(Final number of states|Total states|States: )" "$output_file" 2>/dev/null | grep -o "[0-9]\+" | tail -1 || echo "0")
        local max_memory=$(grep -o "Max memory: [0-9]*" "$output_file" 2>/dev/null | head -1 | grep -o "[0-9]*" || echo "0")
        
        echo "SUCCESS,$duration,$states_explored,$max_memory"
    else
        end_time=$(date +%s.%N)
        duration=$(echo "$end_time - $start_time" | bc -l)
        echo "TIMEOUT,$timeout,0,0"
    fi
}

# Función para ejecutar algoritmo C (sin --omega para redes S3PR bounded)
run_c_algorithm() {
    local network_file="$1"
    local threads="$2"
    local timeout="$3"
    local output_file="$4"
    
    local c_binary="convert_to_c/proposal_best/bin/reachability-analyzer"
    
    if [[ ! -x "$c_binary" ]]; then
        echo "ERROR,0,0,0"
        return
    fi
    
    local start_time end_time duration
    
    start_time=$(date +%s.%N)
    
    if timeout "${timeout}s" "$c_binary" --input "$network_file" --nthreads "$threads" &>"$output_file"; then
        end_time=$(date +%s.%N)
        duration=$(echo "$end_time - $start_time" | bc -l)
        
        # Extraer estados de múltiples formatos de salida del algoritmo C
        local states_explored=$(grep -E "(Estados totales encontrados|States explored|Final states)" "$output_file" 2>/dev/null | grep -o "[0-9]\+" | tail -1 || echo "0")
        local max_memory=$(grep -o "Memory: [0-9]*" "$output_file" 2>/dev/null | head -1 | grep -o "[0-9]*" || echo "0")
        
        echo "SUCCESS,$duration,$states_explored,$max_memory"
    else
        end_time=$(date +%s.%N)
        duration=$(echo "$end_time - $start_time" | bc -l)
        echo "TIMEOUT,$timeout,0,0"
    fi
}

# Función para ejecutar Tina
run_tina_algorithm() {
    local network_dir="$1"  # Directorio que contiene net.ndr
    local timeout="$2"
    local output_file="$3"
    
    local ndr_file=$(find "$network_dir" -name "net.ndr" -type f | head -1)
    
    if [[ ! -f "$ndr_file" ]]; then
        echo "ERROR,0,0,0"
        return
    fi
    
    local start_time end_time duration
    local temp_dir=$(mktemp -d)
    
    start_time=$(date +%s.%N)
    
    if timeout "${timeout}s" bash -c "cd '$temp_dir' && tina -R '$ndr_file' output.ts" &>"$output_file"; then
        end_time=$(date +%s.%N)
        duration=$(echo "$end_time - $start_time" | bc -l)
        
        # Intentar extraer número de estados del archivo de salida
        local states_count=0
        if [[ -f "$temp_dir/output.ts" ]]; then
            states_count=$(wc -l < "$temp_dir/output.ts" 2>/dev/null || echo "0")
        fi
        
        rm -rf "$temp_dir"
        echo "SUCCESS,$duration,$states_count,0"
    else
        end_time=$(date +%s.%N)
        duration=$(echo "$end_time - $start_time" | bc -l)
        rm -rf "$temp_dir"
        echo "TIMEOUT,$timeout,0,0"
    fi
}

# Validar argumentos
if [[ $# -lt 3 ]]; then
    show_usage
    exit 1
fi

NETWORKS_DIR="$1"
THREADS_JSON="$2"  
TIMEOUT="$3"

# Opciones por defecto
ALGORITHMS="java,c,tina"
RUNS_PER_CONFIG=1
OUTPUT_DIR=""
CSV_ONLY=false
NO_CLEANUP=false

# Parsear opciones adicionales
shift 3
while [[ $# -gt 0 ]]; do
    case $1 in
        --algorithms)
            ALGORITHMS="$2"
            shift 2
            ;;
        --runs)
            RUNS_PER_CONFIG="$2"
            shift 2
            ;;
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --csv-only)
            CSV_ONLY=true
            shift
            ;;
        --no-cleanup)
            NO_CLEANUP=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            log_error "Opción desconocida: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Validar directorio de redes
if [[ ! -d "$NETWORKS_DIR" ]]; then
    log_error "Directorio de redes no existe: $NETWORKS_DIR"
    exit 1
fi

# Configurar directorio de salida
if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="$NETWORKS_DIR/benchmark_results"
fi

mkdir -p "$OUTPUT_DIR/raw_logs"
mkdir -p "$OUTPUT_DIR/complete_outputs"

# Parsear configuraciones
IFS=',' read -ra ENABLED_ALGORITHMS <<< "$ALGORITHMS"
THREADS=($(parse_thread_array "$THREADS_JSON"))

log_info "🔬 Benchmark Comparativo S3PR"
log_info "📁 Redes: $NETWORKS_DIR"
log_info "🧵 Hilos: [${THREADS[*]}]"
log_info "🎯 Algoritmos: [${ENABLED_ALGORITHMS[*]}]"
log_info "⏱️  Timeout: ${TIMEOUT}s"
log_info "🔄 Ejecuciones: $RUNS_PER_CONFIG"
log_info "📊 Salida: $OUTPUT_DIR"

# Buscar todas las redes disponibles (compatible con bash antiguo)
NETWORKS=()
while IFS= read -r -d '' file; do
    NETWORKS+=("$file")
done < <(find "$NETWORKS_DIR" -name "net_dividida.json" -type f -print0 | sort -z)

if [[ ${#NETWORKS[@]} -eq 0 ]]; then
    log_error "No se encontraron redes (net_dividida.json) en $NETWORKS_DIR"
    exit 1
fi

log_info "🎯 Encontradas ${#NETWORKS[@]} redes S3PR"

# Verificar herramientas disponibles
AVAILABLE_ALGORITHMS=()

# Verificar Java
if command -v mvn >/dev/null 2>&1 && [[ -f "src/main/java/algorithm/Main.java" ]]; then
    AVAILABLE_ALGORITHMS+=("java")
else
    log_warning "Java/Maven no disponible"
fi

# Verificar C
if [[ -x "convert_to_c/proposal_best/bin/reachability-analyzer" ]]; then
    AVAILABLE_ALGORITHMS+=("c")
else
    log_warning "Algoritmo C no disponible"
fi

# Verificar Tina
if command -v tina >/dev/null 2>&1; then
    AVAILABLE_ALGORITHMS+=("tina")
else
    log_warning "Tina no disponible"
fi

# Filtrar algoritmos habilitados que están disponibles
FINAL_ALGORITHMS=()
for algo in "${ENABLED_ALGORITHMS[@]}"; do
    if [[ " ${AVAILABLE_ALGORITHMS[*]} " =~ " $algo " ]]; then
        FINAL_ALGORITHMS+=("$algo")
    else
        log_warning "Algoritmo '$algo' no disponible, se omite"
    fi
done

if [[ ${#FINAL_ALGORITHMS[@]} -eq 0 ]]; then
    log_error "No hay algoritmos disponibles para ejecutar"
    exit 1
fi

log_info "🚀 Algoritmos a ejecutar: [${FINAL_ALGORITHMS[*]}]"

# Crear archivo CSV de resultados
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
CSV_FILE="$OUTPUT_DIR/benchmark_${TIMESTAMP}.csv"

echo "Network,Algorithm,Threads,Run,Status,Duration_s,States,Memory_MB,Network_Places,Network_Transitions" > "$CSV_FILE"

# Variables de progreso
total_runs=$(( ${#NETWORKS[@]} * ${#FINAL_ALGORITHMS[@]} * ${#THREADS[@]} * RUNS_PER_CONFIG ))
current_run=0
successful_runs=0
failed_runs=0

start_benchmark=$(date +%s)

log_info "🏁 Iniciando benchmark: $total_runs ejecuciones totales"

# Inicializar tabla de resultados en tiempo real
init_results_table

# Ejecutar benchmark
for network_file in "${NETWORKS[@]}"; do
    network_name=$(basename "$(dirname "$network_file")")
    network_dir=$(dirname "$network_file")
    
    # Extraer estadísticas de la red
    network_places=$(jq '.M0 | length' "$network_file" 2>/dev/null || echo "0")
    network_transitions=$(jq '.I_minus[0] | length' "$network_file" 2>/dev/null || echo "0")
    
    log_info "📊 Red: $network_name ($network_places lugares, $network_transitions transiciones)"
    
    for algorithm in "${FINAL_ALGORITHMS[@]}"; do
        for threads in "${THREADS[@]}"; do
            # Para Tina, threads no aplica
            if [[ "$algorithm" == "tina" && "$threads" != "1" ]]; then
                continue
            fi
            
            for ((run=1; run<=RUNS_PER_CONFIG; run++)); do
                current_run=$((current_run + 1))
                
                progress=$((current_run * 100 / total_runs))
                log_info "    [$current_run/$total_runs] ${progress}% - $algorithm (threads: $threads, run: $run)"
                
                # Preparar archivos de log
                log_file="$OUTPUT_DIR/raw_logs/${network_name}_${algorithm}_t${threads}_r${run}.log"
                complete_output="$OUTPUT_DIR/complete_outputs/${network_name}_${algorithm}_t${threads}_r${run}_complete.txt"
                
                # Ejecutar algoritmo según tipo
                case "$algorithm" in
                    "java")
                        result=$(run_java_algorithm "$network_file" "$threads" "$TIMEOUT" "$log_file")
                        ;;
                    "c")
                        result=$(run_c_algorithm "$network_file" "$threads" "$TIMEOUT" "$log_file")
                        ;;
                    "tina")
                        result=$(run_tina_algorithm "$network_dir" "$TIMEOUT" "$log_file")
                        threads="1"  # Tina es siempre single-thread
                        ;;
                    *)
                        log_error "Algoritmo no soportado: $algorithm"
                        continue
                        ;;
                esac
                
                # Parsear resultado
                IFS=',' read -ra RESULT_PARTS <<< "$result"
                status="${RESULT_PARTS[0]}"
                duration="${RESULT_PARTS[1]}"
                states="${RESULT_PARTS[2]}"
                memory="${RESULT_PARTS[3]}"
                
                # Guardar salida completa con metadatos
                cat > "$complete_output" << EOF
# BENCHMARK RESULT FOR: $network_name
# Algorithm: $algorithm
# Threads: $threads
# Run: $run
# Status: $status
# Duration: ${duration}s
# States: $states
# Network: $network_places places, $network_transitions transitions
# Timestamp: $(date)

=== COMPLETE OUTPUT ===
$(cat "$log_file")
EOF
                
                # Escribir al CSV
                echo "$network_name,$algorithm,$threads,$run,$status,$duration,$states,$memory,$network_places,$network_transitions" >> "$CSV_FILE"
                
                # Mostrar en tabla en tiempo real Y actualizar markdown
                if [[ "$status" == "SUCCESS" ]]; then
                    show_live_results "$network_name" "$algorithm" "$threads" "$network_places" "$network_transitions" "$states" "$duration" "$status"
                    update_live_results "$network_name" "$algorithm" "$threads" "$network_places" "$network_transitions" "$states" "$duration" "$status"
                    successful_runs=$((successful_runs + 1))
                else
                    show_live_results "$network_name" "$algorithm" "$threads" "$network_places" "$network_transitions" "FAIL" "$duration" "$status"
                    update_live_results "$network_name" "$algorithm" "$threads" "$network_places" "$network_transitions" "FAIL" "$duration" "$status"
                    failed_runs=$((failed_runs + 1))
                fi
                
                # Cleanup de logs si no se requiere (pero mantener salidas completas)
                if [[ "$NO_CLEANUP" == "false" && "$status" == "SUCCESS" ]]; then
                    rm -f "$log_file"
                fi
            done
        done
    done
    
    echo  # Separación visual entre redes
done

end_benchmark=$(date +%s)
total_duration=$((end_benchmark - start_benchmark))

# Generar reporte markdown si se requiere
if [[ "$CSV_ONLY" == "false" ]]; then
    REPORT_FILE="$OUTPUT_DIR/benchmark_report.md"
    
    cat > "$REPORT_FILE" << EOF
# 🔬 Reporte de Benchmark S3PR

**Fecha:** $(date '+%Y-%m-%d %H:%M:%S')  
**Directorio:** \`$NETWORKS_DIR\`  
**Algoritmos:** [${FINAL_ALGORITHMS[*]}]  
**Configuraciones de hilos:** [${THREADS[*]}]  
**Timeout:** ${TIMEOUT}s  
**Ejecuciones por configuración:** $RUNS_PER_CONFIG  

---

## 📊 Resumen Ejecutivo

- **Ejecuciones totales:** $total_runs
- **Ejecuciones exitosas:** $successful_runs ($(( successful_runs * 100 / total_runs ))%)
- **Ejecuciones fallidas:** $failed_runs ($(( failed_runs * 100 / total_runs ))%)
- **Tiempo total:** ${total_duration}s ($(( total_duration / 60 ))min)

---

## 📈 Análisis por Algoritmo

EOF

    # Generar estadísticas por algoritmo usando awk
    for algorithm in "${FINAL_ALGORITHMS[@]}"; do
        echo "### $algorithm" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        
        # Extraer estadísticas básicas del CSV
        avg_duration=$(awk -F',' -v algo="$algorithm" '$2 == algo && $5 == "SUCCESS" { sum += $6; count++ } END { if (count > 0) print sum/count; else print 0 }' "$CSV_FILE")
        success_rate=$(awk -F',' -v algo="$algorithm" '$2 == algo { total++; if ($5 == "SUCCESS") success++ } END { if (total > 0) print int(success*100/total); else print 0 }' "$CSV_FILE")
        
        echo "- **Tasa de éxito:** ${success_rate}%" >> "$REPORT_FILE"
        echo "- **Duración promedio:** ${avg_duration}s" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    done

    cat >> "$REPORT_FILE" << EOF

---

## 📁 Archivos Generados

- **\`benchmark_${TIMESTAMP}.csv\`** - Resultados detallados en CSV
- **\`raw_logs/\`** - Logs detallados por ejecución (si --no-cleanup)

---

## 🧮 Comandos de Análisis

\`\`\`bash
# Ver mejores resultados por algoritmo
sort -t',' -k6 -n benchmark_${TIMESTAMP}.csv | head -20

# Comparar algoritmos por red específica
grep "network_name" benchmark_${TIMESTAMP}.csv

# Análisis estadístico avanzado
python3 -c "
import pandas as pd
df = pd.read_csv('benchmark_${TIMESTAMP}.csv')
print(df.groupby(['Algorithm', 'Threads'])['Duration_s'].describe())
"
\`\`\`

EOF
    
    log_info "📄 Reporte generado: $REPORT_FILE"
fi

# Resumen final
echo
log_info "🏁 BENCHMARK COMPLETADO"
log_info "📊 Ejecuciones exitosas: $successful_runs/$total_runs ($(( successful_runs * 100 / total_runs ))%)"
log_info "⏱️  Tiempo total: ${total_duration}s ($(( total_duration / 60 ))min)"
log_info "📈 Resultados CSV: $CSV_FILE"

if [[ $failed_runs -gt 0 ]]; then
    log_warning "⚠️  $failed_runs ejecuciones fallaron - revisar logs o aumentar timeout"
fi

# Mostrar top 5 mejores resultados
echo
log_info "🏆 TOP 5 MEJORES RESULTADOS:"
echo "Red,Algoritmo,Hilos,Duración(s),Estados"
awk -F',' '$5 == "SUCCESS" { print $1 "," $2 "," $3 "," $6 "," $7 }' "$CSV_FILE" | sort -t',' -k4 -n | head -5

# Generar cuadro comparativo para informe
generate_comparison_report() {
    local report_file="$OUTPUT_DIR/informe_comparativo.md"
    
    cat > "$report_file" << 'EOF'
# 📊 INFORME COMPARATIVO DE ALGORITMOS DE REACHABILITY TREE

**Fecha:** $(date '+%Y-%m-%d %H:%M:%S')  
**Autor:** [Tu Nombre]  
**Configuración:** Comparación Java vs C vs Tina  

## 🎯 Resumen Ejecutivo

Esta comparación evalúa la **correctitud** y **rendimiento** de tres implementaciones del algoritmo de construcción de árboles de alcanzabilidad para redes de Petri S3PR:

- **Java**: Implementación original multihilo
- **C**: Implementación optimizada en C  
- **Tina**: Herramienta de referencia (ground truth)

## 📈 Resultados Detallados

EOF

    # Generar tabla comparativa usando datos del CSV
    echo "| Red | P | T | Java (5h) | Java (10h) | C (5h) | C (10h) | Tina | Estados Tina |" >> "$report_file"
    echo "|-----|---|---|-----------|------------|--------|---------|------|--------------|" >> "$report_file"
    
    # Procesar resultados por red
    for network_file in "${NETWORKS[@]}"; do
        network_name=$(basename "$(dirname "$network_file")")
        network_places=$(jq '.M0 | length' "$network_file" 2>/dev/null || echo "?")
        network_transitions=$(jq '.I_minus[0] | length' "$network_file" 2>/dev/null || echo "?")
        
        # Extraer tiempos para cada configuración
        java_5h=$(awk -F',' -v net="$network_name" -v algo="java" -v threads="5" '$1 == net && $2 == algo && $3 == threads && $5 == "SUCCESS" { printf "%.2fs", $6; exit }' "$CSV_FILE" || echo "FAIL")
        java_10h=$(awk -F',' -v net="$network_name" -v algo="java" -v threads="10" '$1 == net && $2 == algo && $3 == threads && $5 == "SUCCESS" { printf "%.2fs", $6; exit }' "$CSV_FILE" || echo "FAIL")
        c_5h=$(awk -F',' -v net="$network_name" -v algo="c" -v threads="5" '$1 == net && $2 == algo && $3 == threads && $5 == "SUCCESS" { printf "%.2fs", $6; exit }' "$CSV_FILE" || echo "FAIL")
        c_10h=$(awk -F',' -v net="$network_name" -v algo="c" -v threads="10" '$1 == net && $2 == algo && $3 == threads && $5 == "SUCCESS" { printf "%.2fs", $6; exit }' "$CSV_FILE" || echo "FAIL")
        tina_time=$(awk -F',' -v net="$network_name" -v algo="tina" '$1 == net && $2 == algo && $5 == "SUCCESS" { printf "%.2fs", $6; exit }' "$CSV_FILE" || echo "FAIL")
        tina_states=$(awk -F',' -v net="$network_name" -v algo="tina" '$1 == net && $2 == algo && $5 == "SUCCESS" { print $7; exit }' "$CSV_FILE" || echo "?")
        
        echo "| $network_name | $network_places | $network_transitions | $java_5h | $java_10h | $c_5h | $c_10h | $tina_time | $tina_states |" >> "$report_file"
    done
    
    cat >> "$report_file" << 'EOF'

## 🔍 Análisis de Correctitud

### Comparación con Tina (Ground Truth)

EOF

    # Verificar correctitud comparando estados con Tina
    echo "| Red | Java Estados | C Estados | Tina Estados | Java ✓ | C ✓ |" >> "$report_file"
    echo "|-----|--------------|-----------|--------------|---------|-----|" >> "$report_file"
    
    for network_file in "${NETWORKS[@]}"; do
        network_name=$(basename "$(dirname "$network_file")")
        
        java_states=$(awk -F',' -v net="$network_name" -v algo="java" '$1 == net && $2 == algo && $5 == "SUCCESS" { print $7; exit }' "$CSV_FILE" || echo "?")
        c_states=$(awk -F',' -v net="$network_name" -v algo="c" '$1 == net && $2 == algo && $5 == "SUCCESS" { print $7; exit }' "$CSV_FILE" || echo "?")
        tina_states=$(awk -F',' -v net="$network_name" -v algo="tina" '$1 == net && $2 == algo && $5 == "SUCCESS" { print $7; exit }' "$CSV_FILE" || echo "?")
        
        java_correct="❌"
        c_correct="❌"
        
        if [[ "$java_states" == "$tina_states" && "$java_states" != "?" ]]; then
            java_correct="✅"
        fi
        
        if [[ "$c_states" == "$tina_states" && "$c_states" != "?" ]]; then
            c_correct="✅"
        fi
        
        echo "| $network_name | $java_states | $c_states | $tina_states | $java_correct | $c_correct |" >> "$report_file"
    done

    cat >> "$report_file" << 'EOF'

## 🚀 Análisis de Rendimiento

### Speedup por Hilos

| Algoritmo | Speedup 5→10 hilos | Eficiencia |
|-----------|-------------------|------------|
EOF

    # Calcular speedup promedio para Java y C
    java_speedup=$(awk -F',' '$2 == "java" && $5 == "SUCCESS" { 
        if ($3 == "5") t5 += $6; 
        if ($3 == "10") t10 += $6; 
        count++
    } END { 
        if (t5 > 0 && t10 > 0) printf "%.2fx", t5/t10; else print "N/A" 
    }' "$CSV_FILE")
    
    c_speedup=$(awk -F',' '$2 == "c" && $5 == "SUCCESS" { 
        if ($3 == "5") t5 += $6; 
        if ($3 == "10") t10 += $6; 
        count++
    } END { 
        if (t5 > 0 && t10 > 0) printf "%.2fx", t5/t10; else print "N/A" 
    }' "$CSV_FILE")

    echo "| Java | $java_speedup | $(echo "$java_speedup" | sed 's/x//' | awk '{if($1>0) printf "%.1f%%", ($1/2)*100; else print "N/A"}') |" >> "$report_file"
    echo "| C | $c_speedup | $(echo "$c_speedup" | sed 's/x//' | awk '{if($1>0) printf "%.1f%%", ($1/2)*100; else print "N/A"}') |" >> "$report_file"

    cat >> "$report_file" << 'EOF'

## 📋 Conclusiones

### ✅ Correctitud Verificada
- **Java**: [Mostrar % de redes correctas vs Tina]
- **C**: [Mostrar % de redes correctas vs Tina]  
- **Referencia**: Tina como ground truth

### 🏃‍♂️ Rendimiento Relativo
- **Algoritmo más rápido**: [Determinar ganador]
- **Mejor escalabilidad**: [Analizar speedup]
- **Recomendación**: [Basada en resultados]

### 📊 Datos Técnicos
- **Archivos CSV**: `benchmark_*.csv`
- **Salidas completas**: `complete_outputs/`
- **Configuración**: $(cat "$CSV_FILE" | wc -l) ejecuciones totales

---
**Nota**: Esta comparación demuestra la equivalencia funcional y diferencias de rendimiento entre las implementaciones.
EOF

    log_info "📄 Informe comparativo generado: $report_file"
}

# Generar informe automáticamente
generate_comparison_report

# Marcar archivo en vivo como completado
if [[ -f "$LIVE_RESULTS_FILE" ]]; then
    sed -i '' 's/🟡 EJECUTÁNDOSE/✅ COMPLETADO/' "$LIVE_RESULTS_FILE"
    
    cat >> "$LIVE_RESULTS_FILE" << EOF

## ✅ BENCHMARK COMPLETADO

**Tiempo total:** ${total_duration}s ($(( total_duration / 60 ))min)  
**Ejecuciones exitosas:** $successful_runs/$total_runs ($(( successful_runs * 100 / total_runs ))%)  
**Archivos generados:**
- 📊 **CSV detallado:** \`benchmark_${TIMESTAMP}.csv\`
- 📄 **Informe final:** \`informe_comparativo.md\`
- 🔍 **Salidas completas:** \`complete_outputs/\`
- 📋 **Reporte markdown:** \`benchmark_report.md\`

**Finalizado:** $(date '+%Y-%m-%d %H:%M:%S')
EOF

    log_info "📄 Archivo en vivo actualizado: $LIVE_RESULTS_FILE"
fi

log_success "✅ Benchmark completado exitosamente!"