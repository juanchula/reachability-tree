#!/bin/bash
# Generador S3PR Optimizado con División Balanceada
# Compatible con generate_s3pr_networks.sh

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

show_help() {
    cat << EOF
🚀 Generador S3PR Optimizado con División Balanceada

Uso: $0 [opciones]

Opciones:
  -s, --sizes ARRAY       Tamaños a generar (default: "small,medium,large")
  -n, --count INT         Redes por tamaño (default: 3)
  -o, --output DIR        Directorio de salida (default: s3pr_optimized)
  -t, --timeout SEC       Timeout por red (default: 120)
  --original              Usar división original (sin optimizar)
  --analysis              Incluir análisis de división automático
  --dry-run              Solo mostrar comandos, no ejecutar
  -h, --help             Mostrar esta ayuda

Tamaños disponibles:
  micro, tiny, xxsmall, xsmall, small, smallplus, medium, mediumplus,
  large, largeplus, xlarge, xlargeplus, xxlarge, huge, massive, gigantic

Tamaños especiales:
  simple_15, simple_20, simple_25, simple_30 (redes simples para testing)
  java_light, java_medium, java_balanced (optimizados para Java)

División Optimizada (default):
  ✅ Máximo 40% transiciones por subred
  ✅ Sin mega-subredes problemáticas
  ✅ Paralelización eficiente en Java

Ejemplos:
  $0 -s "simple_15,simple_20" -n 2 -o test_nets
  $0 --sizes "medium,large" --count 3 --analysis
  $0 -s "small" -n 1 --original  # Usar división legacy
EOF
}

get_size_config() {
    case "$1" in
        "micro") echo "--circuits 1 --fork_join 1 --min_shrd_src 2 --max_shrd_src 3 --cmpx_src 0" ;;
        "tiny") echo "--circuits 1 --fork_join 1 --min_shrd_src 3 --max_shrd_src 4 --cmpx_src 0" ;;
        "xxsmall") echo "--circuits 2 --fork_join 1 --min_shrd_src 4 --max_shrd_src 5 --cmpx_src 0" ;;
        "xsmall") echo "--circuits 2 --fork_join 1 --min_shrd_src 5 --max_shrd_src 6 --cmpx_src 0" ;;
        "small") echo "--circuits 2 --init_fj 1 --fork_join 2 --min_shrd_src 6 --max_shrd_src 8 --cmpx_src 0" ;;
        "smallplus") echo "--circuits 3 --init_fj 1 --fork_join 2 --min_shrd_src 7 --max_shrd_src 9 --cmpx_src 1" ;;
        "medium") echo "--circuits 3 --init_fj 2 --fork_join 2 --min_shrd_src 8 --max_shrd_src 12 --cmpx_src 1" ;;
        "mediumplus") echo "--circuits 4 --init_fj 2 --final_fj 1 --fork_join 3 --min_shrd_src 10 --max_shrd_src 15 --cmpx_src 2" ;;
        "large") echo "--circuits 4 --init_fj 3 --final_fj 1 --fork_join 3 --min_shrd_src 12 --max_shrd_src 18 --cmpx_src 2" ;;
        "largeplus") echo "--circuits 5 --init_fj 3 --final_fj 2 --fork_join 4 --init_final_fj 1 --min_shrd_src 15 --max_shrd_src 22 --cmpx_src 3" ;;
        "xlarge") echo "--circuits 5 --init_fj 4 --final_fj 2 --fork_join 4 --init_final_fj 1 --min_shrd_src 18 --max_shrd_src 25 --cmpx_src 3" ;;
        "xlargeplus") echo "--circuits 6 --init_fj 4 --final_fj 3 --fork_join 5 --init_final_fj 2 --min_shrd_src 22 --max_shrd_src 30 --cmpx_src 4" ;;
        "xxlarge") echo "--circuits 7 --init_fj 5 --final_fj 3 --fork_join 5 --init_final_fj 2 --min_shrd_src 25 --max_shrd_src 35 --cmpx_src 4" ;;
        "huge") echo "--circuits 8 --init_fj 5 --final_fj 4 --fork_join 6 --init_final_fj 3 --min_shrd_src 30 --max_shrd_src 40 --cmpx_src 5" ;;
        "massive") echo "--circuits 10 --init_fj 6 --final_fj 4 --fork_join 7 --init_final_fj 3 --min_shrd_src 35 --max_shrd_src 50 --cmpx_src 6" ;;
        "gigantic") echo "--circuits 12 --init_fj 7 --final_fj 5 --fork_join 8 --init_final_fj 4 --min_shrd_src 40 --max_shrd_src 60 --cmpx_src 8" ;;

        # Tamaños simples para testing
        "simple_15") echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 15 --max_shrd_src 15" ;;
        "simple_20") echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 3 --fork_join 2 --cmpx_src 2 --min_shrd_src 15 --max_shrd_src 15" ;;
        "simple_25") echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 4 --fork_join 3 --cmpx_src 3 --min_shrd_src 15 --max_shrd_src 15" ;;
        "simple_30") echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 5 --fork_join 4 --cmpx_src 1 --min_shrd_src 15 --max_shrd_src 15" ;;

        # Tamaños optimizados para Java
        "java_light") echo "--circuits 2 --fork_join 2 --min_shrd_src 4 --max_shrd_src 6 --cmpx_src 0" ;;
        "java_medium") echo "--circuits 2 --init_fj 1 --fork_join 2 --min_shrd_src 5 --max_shrd_src 7 --cmpx_src 0" ;;
        "java_balanced") echo "--circuits 3 --init_fj 1 --fork_join 2 --min_shrd_src 6 --max_shrd_src 8 --cmpx_src 1" ;;

        *) echo "" ;;
    esac
}

# Valores por defecto
OUTPUT_DIR="s3pr_optimized"
SIZES="small,medium,large"
COUNT_PER_SIZE=3
TIMEOUT=120
USE_OPTIMIZED=true
INCLUDE_ANALYSIS=false
DRY_RUN=false

# Parsear argumentos
while [[ $# -gt 0 ]]; do
    case $1 in
        -s|--sizes) SIZES="$2"; shift 2 ;;
        -n|--count) COUNT_PER_SIZE="$2"; shift 2 ;;
        -o|--output) OUTPUT_DIR="$2"; shift 2 ;;
        -t|--timeout) TIMEOUT="$2"; shift 2 ;;
        --original) USE_OPTIMIZED=false; shift ;;
        --analysis) INCLUDE_ANALYSIS=true; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        -h|--help) show_help; exit 0 ;;
        *) log_error "Opción desconocida: $1"; show_help; exit 1 ;;
    esac
done

# Validar pipeline generator
GENERATOR_SCRIPT="src/scripts/generator_s3pr/pipeline_gen_pflow_ndr_json.sh"
if [[ ! -f "$GENERATOR_SCRIPT" ]]; then
    log_error "No se encontró el generador: $GENERATOR_SCRIPT"
    exit 1
fi

# Crear directorio y preparar
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
IFS=',' read -ra SIZE_ARRAY <<< "$SIZES"

log_info "🚀 Generador S3PR Optimizado"
log_info "📁 Directorio salida: $OUTPUT_DIR"
log_info "🎯 Tamaños: ${SIZE_ARRAY[*]}"
log_info "🔢 Redes por tamaño: $COUNT_PER_SIZE"
log_info "⏱️  Timeout: ${TIMEOUT}s"

if [[ "$USE_OPTIMIZED" == "true" ]]; then
    log_info "✅ División optimizada: HABILITADA (sin mega-subredes)"
else
    log_warn "⚠️  División optimizada: DESHABILITADA (comportamiento legacy)"
fi

if [[ "$INCLUDE_ANALYSIS" == "true" ]]; then
    log_info "📊 Análisis de división: HABILITADO"
fi

if [[ "$DRY_RUN" == "true" ]]; then
    log_warn "🔍 DRY RUN - Solo mostrando comandos"
fi

# Compilar proyecto
log_info "🔨 Compilando proyecto..."
mvn compile -q

# Estadísticas
TOTAL_NETWORKS=$((${#SIZE_ARRAY[@]} * COUNT_PER_SIZE))
CURRENT_NETWORK=0
SUCCESSFUL_NETWORKS=0
FAILED_NETWORKS=0

log_info "📐 Generando $TOTAL_NETWORKS redes en total..."

# Generar redes
for size in "${SIZE_ARRAY[@]}"; do
    log_info "📐 Generando redes de tamaño: $size"

    SIZE_CONFIG=$(get_size_config "$size")
    if [[ -z "$SIZE_CONFIG" ]]; then
        log_error "Tamaño '$size' no reconocido"
        continue
    fi

    for ((i=1; i<=COUNT_PER_SIZE; i++)); do
        CURRENT_NETWORK=$((CURRENT_NETWORK + 1))
        PROGRESS=$((CURRENT_NETWORK * 100 / TOTAL_NETWORKS))
        log_info "    [$CURRENT_NETWORK/$TOTAL_NETWORKS] Progreso: ${PROGRESS}%"

        NETWORK_DIR="$OUTPUT_DIR/${size}_${i}"

        if [[ "$DRY_RUN" == "false" ]]; then
            mkdir -p "$NETWORK_DIR"
        fi

        log_info "🔨 Generando red $size #$i..."

        # Configurar división
        if [[ "$USE_OPTIMIZED" == "true" ]]; then
            DIVISION_FLAG="--optimized"
            DIVISION_TYPE="optimizada"
        else
            DIVISION_FLAG="--original"
            DIVISION_TYPE="original"
        fi

        GEN_CMD="timeout $TIMEOUT src/scripts/generator_s3pr/pipeline_gen_pflow_ndr_json.sh -n 1 $SIZE_CONFIG -o $NETWORK_DIR $DIVISION_FLAG"

        if [[ "$DRY_RUN" == "true" ]]; then
            echo "  Command: $GEN_CMD"
            SUCCESSFUL_NETWORKS=$((SUCCESSFUL_NETWORKS + 1))
        else
            start_time=$(date +%s)
            if eval "$GEN_CMD" > /dev/null 2>&1; then
                end_time=$(date +%s)
                duration=$((end_time - start_time))

                # Buscar archivo generado
                NET_JSON=$(find "$NETWORK_DIR" -name "net_dividida.json" -type f | head -1)
                if [[ -f "$NET_JSON" ]]; then
                    PLACES=$(jq '.M0 | length' "$NET_JSON")
                    TRANSITIONS=$(jq '.I_minus[0] | length' "$NET_JSON")
                    SUBNETS=$(jq '.subnet_definitions | length' "$NET_JSON")

                    log_success "    ✅ $size #$i: $PLACES lugares, $TRANSITIONS transiciones, $SUBNETS subredes (${duration}s, división $DIVISION_TYPE)"
                    SUCCESSFUL_NETWORKS=$((SUCCESSFUL_NETWORKS + 1))

                    # Análisis opcional
                    if [[ "$INCLUDE_ANALYSIS" == "true" ]]; then
                        NET_DIR=$(dirname "$NET_JSON")
                        log_info "📊 Analizando división de $size #$i..."

                        mvn exec:java -Dexec.mainClass="algorithm.DivisionComparator" -Dexec.args="$NET_JSON" -q > "$NET_DIR/division_analysis.log" 2>&1

                        if python3 src/scripts/utils/petri_division_visualizer.py "$NET_JSON" > "$NET_DIR/visualizer_output.log" 2>&1; then
                            log_info "    📊 Visualizaciones DOT generadas correctamente"
                        else
                            log_warn "    ⚠️  Error en visualizaciones DOT (no crítico)"
                        fi

                        # Crear reporte HTML
                        cat > "$NET_DIR/subnet_division_report.html" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>Análisis de División - $size #$i</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f0f8ff; padding: 20px; border-radius: 10px; }
        .stats { background: #f9f9f9; padding: 15px; margin: 10px 0; border-left: 4px solid #4CAF50; }
        .subnet { margin: 10px 0; padding: 10px; border: 1px solid #ddd; border-radius: 5px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>📊 Análisis de División de Subredes</h1>
        <p><strong>Red:</strong> $size #$i</p>
        <p><strong>Archivo:</strong> $(basename "$NET_JSON")</p>
        <p><strong>Fecha:</strong> $(date)</p>
    </div>
    <div class="stats">
        <h2>📈 Estadísticas</h2>
        <p><strong>Lugares:</strong> $PLACES</p>
        <p><strong>Transiciones:</strong> $TRANSITIONS</p>
        <p><strong>Subredes:</strong> $SUBNETS</p>
        <p><strong>División:</strong> $DIVISION_TYPE</p>
    </div>
    <h2>🔧 Distribución de Subredes</h2>
EOF
                        jq -r '.subnet_definitions | to_entries | map("<div class=\"subnet\"><h3>Subred " + (.key | tostring) + "</h3><p><strong>Transiciones:</strong> " + (.value.trans_indices | length | tostring) + "</p><p><strong>Lugares:</strong> " + (.value.place_indices | length | tostring) + "</p></div>") | join("")' "$NET_JSON" >> "$NET_DIR/subnet_division_report.html"
                        echo "</body></html>" >> "$NET_DIR/subnet_division_report.html"

                        # Mover archivos DOT
                        for file in division_actual.dot division_optimizada.dot; do
                            if [[ -f "$file" ]]; then
                                mv "$file" "$NET_DIR/" 2>/dev/null || true
                            fi
                        done

                        log_info "    📁 Análisis guardado en: $NET_DIR/"
                    fi
                else
                    log_error "    ❌ $size #$i: Archivo no generado"
                    FAILED_NETWORKS=$((FAILED_NETWORKS + 1))
                fi
            else
                log_error "    ❌ $size #$i: Error en generación (timeout o falla)"
                FAILED_NETWORKS=$((FAILED_NETWORKS + 1))
            fi
        fi
    done
done

# Resumen final
log_info "🏁 GENERACIÓN COMPLETADA"
log_info "📊 Redes exitosas: $SUCCESSFUL_NETWORKS/$TOTAL_NETWORKS"

if [[ "$USE_OPTIMIZED" == "true" ]]; then
    log_success "✅ Todas las redes usan división optimizada (sin mega-subredes)"
else
    log_warn "⚠️  Todas las redes usan división original (pueden tener mega-subredes)"
fi

if [[ $FAILED_NETWORKS -gt 0 ]]; then
    log_warn "⚠️  Redes fallidas: $FAILED_NETWORKS/$TOTAL_NETWORKS"
fi

log_info "📁 Redes disponibles para análisis:"
if [[ "$INCLUDE_ANALYSIS" == "true" ]]; then
    log_success "📊 Análisis de división incluido en cada red"
    log_success "🎯 Para ver análisis: open $OUTPUT_DIR/*/subnet_division_report.html"
else
    log_info "💡 Para analizar divisiones: ./analyze_division.sh \"$OUTPUT_DIR\""
fi

if [[ "$DRY_RUN" == "false" ]]; then
    log_success "🎯 Listo! Ejecuta:"
    if [[ "$USE_OPTIMIZED" == "true" ]]; then
        log_success "     # Benchmark con división optimizada"
    else
        log_success "     # Benchmark con división original"
    fi
    log_success "     ./benchmark_s3pr_algorithms.sh \"$OUTPUT_DIR\" \"[1,4,10]\" 300"
fi