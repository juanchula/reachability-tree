#!/bin/bash

# Script mejorado para benchmark de redes omega con parámetros configurables
# Uso: ./test_all_omega_networks.sh <data_folder> <algorithms> <threads_array> [timeout]
# Ejemplo: ./test_all_omega_networks.sh ./data "both" "[1,4,8]" 60

show_usage() {
    echo "🚀 Benchmark de Redes Omega - Uso:"
    echo "Usage: $0 <data_folder> <algorithms> <threads_array> [timeout]"
    echo ""
    echo "Parámetros:"
    echo "  data_folder    - Carpeta donde buscar archivos net*.json"
    echo "  algorithms     - 'java', 'c', o 'both'"
    echo "  threads_array  - Array de hilos en formato '[1,4,8,16]' o '1,4,8,16'"
    echo "  timeout        - Timeout por test en segundos (opcional, default: 30)"
    echo ""
    echo "Ejemplos:"
    echo "  $0 ./data both '[1,4,8]'"
    echo "  $0 ./data c '[1,8,16]' 60"
    echo "  $0 ./data java '1,4' 45"
    echo "  $0 /path/to/networks both '[1,2,4,8,16]' 120"
    echo ""
    echo "📁 Salida:"
    echo "  - Carpeta 'results' dentro de data_folder"
    echo "  - Archivos DOT para cada test exitoso"
    echo "  - benchmark_report.md con tabla comparativa"
    echo "  - results.csv con datos detallados"
}

# Validar parámetros
if [ $# -lt 3 ]; then
    echo "❌ Error: Faltan parámetros requeridos"
    show_usage
    exit 1
fi

DATA_FOLDER="$1"
ALGORITHMS="$2"
THREADS_INPUT="$3"
TIMEOUT="${4:-30}"

# Validar carpeta de datos
if [ ! -d "$DATA_FOLDER" ]; then
    echo "❌ Error: La carpeta '$DATA_FOLDER' no existe"
    exit 1
fi

# Crear carpeta results
RESULTS_DIR="$DATA_FOLDER/results"
mkdir -p "$RESULTS_DIR"

# Parsear lista de hilos
parse_threads() {
    local input="$1"
    # Remover corchetes y espacios
    input=$(echo "$input" | sed 's/\[//g' | sed 's/\]//g' | sed 's/ //g')
    # Convertir a array
    echo "$input" | tr ',' ' '
}

THREADS_ARRAY=($(parse_threads "$THREADS_INPUT"))

# Validar algoritmos
case "$ALGORITHMS" in
    "java"|"c"|"both")
        ;;
    *)
        echo "❌ Error: Algoritmo '$ALGORITHMS' inválido. Usa 'java', 'c', o 'both'"
        exit 1
        ;;
esac

# Validar que tenemos al menos un hilo
if [ ${#THREADS_ARRAY[@]} -eq 0 ]; then
    echo "❌ Error: Array de hilos vacío o inválido"
    exit 1
fi

# Mostrar configuración
echo "🚀 Benchmark de Redes Omega - Configuración"
echo "============================================="
echo "📁 Carpeta de datos: $DATA_FOLDER"
echo "📁 Carpeta de resultados: $RESULTS_DIR"
echo "⚙️  Algoritmos: $ALGORITHMS"
echo "🧵 Hilos: ${THREADS_ARRAY[*]}"
echo "⏱️  Timeout: ${TIMEOUT}s"
echo ""

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_FILE="$RESULTS_DIR/results.csv"
REPORT_FILE="$RESULTS_DIR/benchmark_report.md"

echo "📊 Archivos de salida:"
echo "  - CSV: $RESULTS_FILE"
echo "  - Reporte: $REPORT_FILE"
echo "  - DOTs: $RESULTS_DIR/*.dot"
echo ""

# Crear header del CSV
echo "Network,Algorithm,Threads,States,Omega_Detections,Time_ms,Throughput_states_per_sec,Status,DOT_File" > "$RESULTS_FILE"

# Buscar redes
OMEGA_NETWORKS=$(find "$DATA_FOLDER" -name "net*.json" 2>/dev/null | sort)

if [ -z "$OMEGA_NETWORKS" ]; then
    echo "❌ No se encontraron redes (net*.json) en '$DATA_FOLDER'"
    exit 1
fi

NETWORK_COUNT=$(echo "$OMEGA_NETWORKS" | wc -l)
echo "🔍 Encontradas $NETWORK_COUNT redes omega"
echo ""

# Determinar qué algoritmos ejecutar
ALGOS_TO_RUN=()
case "$ALGORITHMS" in
    "java")
        ALGOS_TO_RUN=("java")
        ;;
    "c")
        ALGOS_TO_RUN=("c")
        ;;
    "both")
        ALGOS_TO_RUN=("java" "c")
        ;;
esac

# Calcular total de tests
TOTAL_TESTS=$((NETWORK_COUNT * ${#ALGOS_TO_RUN[@]} * ${#THREADS_ARRAY[@]}))
echo "📈 Total de tests: $TOTAL_TESTS"
echo ""

# Función para ejecutar Java
run_java() {
    local network="$1"
    local threads="$2"
    local timeout="$3"
    local output_dot="$4"
    
    cd /Users/juan/PPS/2025/reachability-tree
    
    timeout "${timeout}s" mvn clean compile exec:java \
        -Dexec.mainClass=algorithm.Main \
        -Dexec.args="--input $network --omega --nthreads $threads --export-graph" \
        -q 2>&1
    
    local exit_code=$?
    
    # Mover DOT file si existe
    if [ $exit_code -eq 0 ] && [ -f "salida.dot" ]; then
        mv "salida.dot" "$output_dot"
    fi
    
    return $exit_code
}

# Función para ejecutar C
run_c() {
    local network="$1"
    local threads="$2"
    local timeout="$3"
    local output_dot="$4"
    
    cd /Users/juan/PPS/2025/reachability-tree/convert_to_c/proposal_best
    
    timeout "${timeout}s" ./bin/reachability-analyzer \
        --input "$network" \
        --omega \
        --nthreads "$threads" \
        --output "$output_dot" 2>&1
}

# Función para parsear resultados Java
parse_java_output() {
    local output="$1"
    
    local states=$(echo "$output" | grep "Final number of states in reachability tree:" | grep -o '[0-9]*' | tail -1)
    local omega_marks=$(echo "$output" | grep "Total omega marks found:" | grep -o '[0-9]*' | tail -1)
    local time_ms=$(echo "$output" | grep "Time taken:" | grep -o '[0-9]*\.[0-9]*' | tail -1)
    
    # Calcular throughput
    local throughput=0
    if [ ! -z "$time_ms" ] && [ ! -z "$states" ] && [ "$time_ms" != "0" ]; then
        throughput=$(echo "scale=0; $states * 1000 / $time_ms" | bc -l 2>/dev/null || echo "0")
    fi
    
    echo "${states:-0},${omega_marks:-0},${time_ms:-0},${throughput:-0}"
}

# Función para parsear resultados C
parse_c_output() {
    local output="$1"
    
    local states=$(echo "$output" | grep "Estados totales en el árbol de alcanzabilidad:" | grep -o '[0-9]*' | tail -1)
    local omega_marks=$(echo "$output" | grep "Se detectaron" | grep -o '[0-9]*' | head -1)
    local time_ms=$(echo "$output" | grep "Tiempo total:" | grep -o '[0-9]*\.[0-9]*' | tail -1)
    local throughput=$(echo "$output" | grep "estados/segundo" | grep -o '[0-9]*' | tail -1)
    
    echo "${states:-0},${omega_marks:-0},${time_ms:-0},${throughput:-0}"
}

# Ejecutar tests
test_counter=0

for network in $OMEGA_NETWORKS; do
    network_name=$(basename "$network" .json)
    echo "🧪 Testing: $network_name"
    
    for algorithm in "${ALGOS_TO_RUN[@]}"; do
        for threads in "${THREADS_ARRAY[@]}"; do
            test_counter=$((test_counter + 1))
            echo "  📋 Test $test_counter/$TOTAL_TESTS: $algorithm (threads=$threads)"
            
            # Definir archivo DOT
            dot_file="$RESULTS_DIR/${network_name}_${algorithm}_t${threads}.dot"
            dot_filename=$(basename "$dot_file")
            
            # Ejecutar test
            if [ "$algorithm" = "java" ]; then
                output=$(run_java "$network" "$threads" "$TIMEOUT" "$dot_file")
                exit_code=$?
                metrics=$(parse_java_output "$output")
            else
                output=$(run_c "$network" "$threads" "$TIMEOUT" "$dot_file")
                exit_code=$?
                metrics=$(parse_c_output "$output")
            fi
            
            # Procesar resultado
            if [ $exit_code -eq 0 ]; then
                IFS=',' read -r states omega_marks time_ms throughput <<< "$metrics"
                echo "    ✅ Success: $states states, $omega_marks ω, ${time_ms}ms, ${throughput} states/s"
                
                # Verificar si el DOT existe
                if [ -f "$dot_file" ]; then
                    echo "$network_name,$algorithm,$threads,$states,$omega_marks,$time_ms,$throughput,SUCCESS,$dot_filename" >> "$RESULTS_FILE"
                else
                    echo "$network_name,$algorithm,$threads,$states,$omega_marks,$time_ms,$throughput,SUCCESS,NO_DOT" >> "$RESULTS_FILE"
                fi
            elif [ $exit_code -eq 124 ]; then
                echo "    ⏰ Timeout (${TIMEOUT}s)"
                echo "$network_name,$algorithm,$threads,0,0,$((TIMEOUT * 1000)),0,TIMEOUT,NO_DOT" >> "$RESULTS_FILE"
            else
                echo "    ❌ Error (exit code: $exit_code)"
                echo "$network_name,$algorithm,$threads,0,0,0,0,ERROR,NO_DOT" >> "$RESULTS_FILE"
            fi
        done
    done
    echo ""
done

# Generar reporte MD
echo "📝 Generando reporte markdown..."

cat > "$REPORT_FILE" << 'REPORT_HEADER'
# 🚀 Benchmark de Redes Omega - Reporte Comparativo

**Fecha:** $(date '+%Y-%m-%d %H:%M:%S')  
**Carpeta de datos:** `$DATA_FOLDER`  
**Algoritmos:** $ALGORITHMS  
**Configuraciones de hilos:** ${THREADS_ARRAY[*]}  
**Timeout:** ${TIMEOUT}s  

---

## 📊 Resumen Ejecutivo

REPORT_HEADER

# Substituir variables en el header
sed -i '' "s|\$DATA_FOLDER|$DATA_FOLDER|g" "$REPORT_FILE"
sed -i '' "s|\$ALGORITHMS|$ALGORITHMS|g" "$REPORT_FILE"
sed -i '' "s|\${THREADS_ARRAY\[\*\]}|${THREADS_ARRAY[*]}|g" "$REPORT_FILE"
sed -i '' "s|\${TIMEOUT}|${TIMEOUT}|g" "$REPORT_FILE"
sed -i '' "s|\$(date '+%Y-%m-%d %H:%M:%S')|$(date '+%Y-%m-%d %H:%M:%S')|g" "$REPORT_FILE"

# Agregar estadísticas generales
total_tests=$(wc -l < "$RESULTS_FILE")
total_tests=$((total_tests - 1)) # Restar header
successful_tests=$(grep ",SUCCESS," "$RESULTS_FILE" | wc -l)
success_rate=$(echo "scale=1; $successful_tests * 100 / $total_tests" | bc -l 2>/dev/null || echo "0")

cat >> "$REPORT_FILE" << STATS
- **Total de tests:** $total_tests
- **Tests exitosos:** $successful_tests
- **Tasa de éxito:** ${success_rate}%
- **Redes probadas:** $NETWORK_COUNT
- **Archivos DOT generados:** $(find "$RESULTS_DIR" -name "*.dot" | wc -l)

---

## 📋 Tabla Comparativa Completa

| Red | Algoritmo | Hilos | Estados | Omegas | Tiempo (ms) | Throughput (estados/s) | Estado | DOT |
|-----|-----------|-------|---------|--------|-------------|----------------------|--------|-----|
STATS

# Agregar datos de la tabla
tail -n +2 "$RESULTS_FILE" | sort -t',' -k1,1 -k2,2 -k3,3n | while IFS=',' read -r net alg threads states omega time throughput status dot_file; do
    # Formatear estado con emoji
    case "$status" in
        "SUCCESS") status_emoji="✅" ;;
        "TIMEOUT") status_emoji="⏰" ;;
        "ERROR") status_emoji="❌" ;;
        *) status_emoji="❓" ;;
    esac
    
    # Formatear DOT
    if [ "$dot_file" = "NO_DOT" ]; then
        dot_link="-"
    else
        dot_link="[$dot_file]($dot_file)"
    fi
    
    echo "| $net | **$alg** | $threads | $states | $omega | $time | $throughput | $status_emoji | $dot_link |" >> "$REPORT_FILE"
done

# Agregar análisis por algoritmo
echo "" >> "$REPORT_FILE"
echo "---" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "## 🔬 Análisis por Algoritmo" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

for algorithm in "${ALGOS_TO_RUN[@]}"; do
    echo "### 📈 $algorithm" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    
    # Estadísticas del algoritmo
    algo_tests=$(grep ",$algorithm," "$RESULTS_FILE" | wc -l)
    algo_success=$(grep ",$algorithm," "$RESULTS_FILE" | grep ",SUCCESS," | wc -l)
    algo_success_rate=$(echo "scale=1; $algo_success * 100 / $algo_tests" | bc -l 2>/dev/null || echo "0")
    
    echo "- **Tests realizados:** $algo_tests" >> "$REPORT_FILE"
    echo "- **Tests exitosos:** $algo_success" >> "$REPORT_FILE"
    echo "- **Tasa de éxito:** ${algo_success_rate}%" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    
    if [ $algo_success -gt 0 ]; then
        echo "**Top 3 mejores rendimientos:**" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        echo "| Red | Hilos | Estados/s | Estados | Omegas |" >> "$REPORT_FILE"
        echo "|-----|-------|-----------|---------|--------|" >> "$REPORT_FILE"
        
        grep ",$algorithm," "$RESULTS_FILE" | grep ",SUCCESS," | sort -t',' -k7 -nr | head -3 | while IFS=',' read -r net alg threads states omega time throughput status dot; do
            echo "| $net | $threads | **$throughput** | $states | $omega |" >> "$REPORT_FILE"
        done
        
        echo "" >> "$REPORT_FILE"
        echo "**Top 3 más detecciones omega:**" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        echo "| Red | Hilos | Omegas | Estados | Tiempo |" >> "$REPORT_FILE"
        echo "|-----|-------|--------|---------|--------|" >> "$REPORT_FILE"
        
        grep ",$algorithm," "$RESULTS_FILE" | grep ",SUCCESS," | sort -t',' -k5 -nr | head -3 | while IFS=',' read -r net alg threads states omega time throughput status dot; do
            echo "| $net | $threads | **$omega** | $states | ${time}ms |" >> "$REPORT_FILE"
        done
        
        echo "" >> "$REPORT_FILE"
    fi
done

# Análisis de escalabilidad si hay múltiples algoritmos
if [ ${#ALGOS_TO_RUN[@]} -eq 2 ] && [ ${#THREADS_ARRAY[@]} -gt 1 ]; then
    echo "## 🧵 Análisis de Escalabilidad" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "Comparación de rendimiento entre Java y C para diferentes configuraciones de hilos:" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "| Hilos | Java (estados/s) | C (estados/s) | Speedup C vs Java |" >> "$REPORT_FILE"
    echo "|-------|------------------|---------------|-------------------|" >> "$REPORT_FILE"
    
    for threads in "${THREADS_ARRAY[@]}"; do
        java_avg=$(grep ",java,$threads," "$RESULTS_FILE" | grep ",SUCCESS," | awk -F',' '{sum+=$7; count++} END {if(count>0) printf "%.0f", sum/count; else print "0"}')
        c_avg=$(grep ",c,$threads," "$RESULTS_FILE" | grep ",SUCCESS," | awk -F',' '{sum+=$7; count++} END {if(count>0) printf "%.0f", sum/count; else print "0"}')
        
        if [ "$java_avg" != "0" ] && [ "$c_avg" != "0" ]; then
            speedup=$(echo "scale=2; $c_avg / $java_avg" | bc -l 2>/dev/null || echo "N/A")
            echo "| $threads | $java_avg | $c_avg | ${speedup}x |" >> "$REPORT_FILE"
        elif [ "$java_avg" != "0" ]; then
            echo "| $threads | $java_avg | - | - |" >> "$REPORT_FILE"
        elif [ "$c_avg" != "0" ]; then
            echo "| $threads | - | $c_avg | - |" >> "$REPORT_FILE"
        fi
    done
    
    echo "" >> "$REPORT_FILE"
fi

# Footer del reporte
cat >> "$REPORT_FILE" << FOOTER

---

## 📁 Archivos Generados

### Archivos DOT
Los siguientes archivos DOT fueron generados para tests exitosos:

$(find "$RESULTS_DIR" -name "*.dot" -exec basename {} \; | sort | sed 's/^/- /')

### Datos Raw
- **CSV completo:** [results.csv](results.csv)
- **Total archivos DOT:** $(find "$RESULTS_DIR" -name "*.dot" | wc -l)

---

**Generado automáticamente por test_all_omega_networks.sh**  
**Timestamp:** $TIMESTAMP
FOOTER

echo ""
echo "🏆 ANÁLISIS DE RESULTADOS"
echo "========================="

# Mostrar resumen en consola
echo ""
echo "📊 Resumen:"
echo "  Tests exitosos: $successful_tests/$total_tests (${success_rate}%)"
echo "  Archivos DOT generados: $(find "$RESULTS_DIR" -name "*.dot" | wc -l)"
echo ""

echo "✨ Benchmark completado exitosamente!"
echo "📁 Todos los resultados guardados en: $RESULTS_DIR"
echo "📄 Reporte completo: $REPORT_FILE"
echo "📊 Datos detallados: $RESULTS_FILE"
echo "🎯 Archivos DOT: $RESULTS_DIR/*.dot"
