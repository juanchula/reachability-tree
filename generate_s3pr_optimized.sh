#!/bin/bash

# 🚀 Generador S3PR Optimizado con División Balanceada
# Compatible con parámetros de generate_s3pr_networks.sh

set -euo pipefail

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Función de log
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Función para mostrar ayuda
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

Tamaños disponibles (mismos que generate_s3pr_networks.sh):
  micro, tiny, xxsmall, xsmall, small, smallplus, medium, mediumplus,
  large, largeplus, xlarge, xlargeplus, xxlarge, huge, massive, gigantic

Ejemplos:
  # Generar redes optimizadas (igual sintaxis que el original)
  $0 -s "small,medium,large" -n 2 -o mis_redes_optimizadas

  # Como el comando original pero con división optimizada
  $0 -s "simple_15,simple_20,simple_25,simple_30" -n 1 -o redes_optimizadas

  # Con análisis automático de división
  $0 -s "small" -n 1 -o test_optimizado --analysis

  # Usar división original (comportamiento legacy)
  $0 -s "medium" -n 1 -o test_original --original

Diferencias vs generate_s3pr_networks.sh:
  ✅ Usa OptimizedSubnetDivider automáticamente (sin mega-subredes)
  ✅ Análisis de división integrado opcional
  ✅ Mantiene división original como opción
  ✅ Compatible con todos los tamaños existentes
  ✅ Misma sintaxis de parámetros

EOF
}

# Configuración por defecto
SIZES="small,medium,large"
COUNT=3
OUTPUT_DIR="s3pr_optimized"
TIMEOUT=120
USE_OPTIMIZED=true
INCLUDE_ANALYSIS=false
DRY_RUN=false

# Parse argumentos
while [[ $# -gt 0 ]]; do
    case $1 in
        -s|--sizes)
            SIZES="$2"
            shift 2
            ;;
        -n|--count)
            COUNT="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -t|--timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --original)
            USE_OPTIMIZED=false
            shift
            ;;
        --analysis)
            INCLUDE_ANALYSIS=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            log_error "Opción desconocida: $1"
            echo ""
            show_help
            exit 1
            ;;
    esac
done

# Header
log_info "🚀 Generador S3PR Optimizado"
if [ "$USE_OPTIMIZED" = true ]; then
    log_info "📁 Directorio salida: $(pwd)/$OUTPUT_DIR"
    log_info "🎯 Tamaños: $SIZES"
    log_info "🔢 Redes por tamaño: $COUNT"
    log_info "⏱️  Timeout: ${TIMEOUT}s"
    log_info "✅ División optimizada: HABILITADA (sin mega-subredes)"
else
    log_info "📁 Directorio salida: $(pwd)/$OUTPUT_DIR"
    log_info "🎯 Tamaños: $SIZES"
    log_info "🔢 Redes por tamaño: $COUNT"
    log_info "⏱️  Timeout: ${TIMEOUT}s"
    log_warn "⚠️  División optimizada: DESHABILITADA (comportamiento legacy)"
fi

if [ "$INCLUDE_ANALYSIS" = true ]; then
    log_info "📊 Análisis de división: HABILITADO"
fi

# Verificar si es dry run
if [ "$DRY_RUN" = true ]; then
    log_warn "🔍 DRY RUN - Solo mostrando comandos"
    echo ""
fi

# Crear directorio de salida
if [ "$DRY_RUN" = false ]; then
    mkdir -p "$OUTPUT_DIR"
fi

# Función para obtener configuración de tamaño (reutilizar del script original)
get_size_config() {
    local size="$1"
    case "$size" in
        "micro")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 2 --max_shrd_src 3"
            ;;
        "tiny")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 4"
            ;;
        "xxsmall")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 5"
            ;;
        "xsmall")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 5 --max_shrd_src 6"
            ;;
        "small")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 6 --max_shrd_src 8"
            ;;
        "smallplus")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 7 --max_shrd_src 9"
            ;;
        "medium")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 8 --max_shrd_src 12"
            ;;
        "mediumplus")
            echo "--circuits 4 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 10 --max_shrd_src 15"
            ;;
        "large")
            echo "--circuits 4 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 12 --max_shrd_src 18"
            ;;
        "largeplus")
            echo "--circuits 5 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 4 --cmpx_src 0 --min_shrd_src 15 --max_shrd_src 22"
            ;;
        "xlarge")
            echo "--circuits 5 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 4 --cmpx_src 0 --min_shrd_src 18 --max_shrd_src 25"
            ;;
        "xlargeplus")
            echo "--circuits 6 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 5 --cmpx_src 0 --min_shrd_src 22 --max_shrd_src 30"
            ;;
        "xxlarge")
            echo "--circuits 7 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 5 --cmpx_src 0 --min_shrd_src 25 --max_shrd_src 35"
            ;;
        "huge")
            echo "--circuits 8 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 6 --cmpx_src 0 --min_shrd_src 30 --max_shrd_src 40"
            ;;
        "massive")
            echo "--circuits 10 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 7 --cmpx_src 0 --min_shrd_src 35 --max_shrd_src 50"
            ;;
        "gigantic")
            echo "--circuits 12 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 8 --cmpx_src 0 --min_shrd_src 40 --max_shrd_src 60"
            ;;
        # Compatibilidad con nombres simples
        "simple_15")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 15 --max_shrd_src 15"
            ;;
        "simple_20")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 20 --max_shrd_src 20"
            ;;
        "simple_25")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 25 --max_shrd_src 25"
            ;;
        "simple_30")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 30 --max_shrd_src 30"
            ;;
        *)
            log_error "Tamaño desconocido: $size"
            log_error "Tamaños disponibles: micro, tiny, xxsmall, xsmall, small, smallplus, medium, mediumplus, large, largeplus, xlarge, xlargeplus, xxlarge, huge, massive, gigantic"
            log_error "O tamaños simples: simple_15, simple_20, simple_25, simple_30"
            exit 1
            ;;
    esac
}

# Compilar proyecto
log_info "🔨 Compilando proyecto..."
if [ "$DRY_RUN" = false ]; then
    mvn compile -q
fi

# Convertir sizes string a array
IFS=',' read -ra SIZE_ARRAY <<< "$SIZES"

# Contadores
TOTAL_NETWORKS=$((${#SIZE_ARRAY[@]} * COUNT))
CURRENT_NETWORK=0
SUCCESSFUL_NETWORKS=0
FAILED_NETWORKS=0

log_info "📐 Generando $TOTAL_NETWORKS redes en total..."

# Generar cada tamaño
for size in "${SIZE_ARRAY[@]}"; do
    log_info "📐 Generando redes de tamaño: $size"

    # Obtener configuración
    SIZE_CONFIG=$(get_size_config "$size")

    # Generar múltiples redes de este tamaño
    for i in $(seq 1 $COUNT); do
        CURRENT_NETWORK=$((CURRENT_NETWORK + 1))
        PROGRESS=$((CURRENT_NETWORK * 100 / TOTAL_NETWORKS))
        log_info "    [$CURRENT_NETWORK/$TOTAL_NETWORKS] Progreso: ${PROGRESS}%"

        # Crear directorio de salida para esta red (sin timestamp extra, el pipeline lo creará)
        NETWORK_DIR="$OUTPUT_DIR/${size}_${i}"

        if [ "$DRY_RUN" = false ]; then
            mkdir -p "$NETWORK_DIR"
        fi

        log_info "🔨 Generando red $size #$i..."

        # Comando de generación
        if [ "$USE_OPTIMIZED" = true ]; then
            DIVISION_FLAG="--optimized"
            DIVISION_TYPE="optimizada"
        else
            DIVISION_FLAG="--original"
            DIVISION_TYPE="original"
        fi

        GEN_CMD="timeout $TIMEOUT src/scripts/generator_s3pr/pipeline_gen_pflow_ndr_json.sh -n 1 $SIZE_CONFIG -o $NETWORK_DIR $DIVISION_FLAG"

        if [ "$DRY_RUN" = true ]; then
            echo "  Command: $GEN_CMD"
            SUCCESSFUL_NETWORKS=$((SUCCESSFUL_NETWORKS + 1))
        else
            # Ejecutar generación
            start_time=$(date +%s)
            if eval "$GEN_CMD" > /dev/null 2>&1; then
                end_time=$(date +%s)
                duration=$((end_time - start_time))

                # Buscar el archivo en el subdirectorio creado por el pipeline
                NET_JSON=$(find "$NETWORK_DIR" -name "net_dividida.json" -type f | head -1)
                if [ -f "$NET_JSON" ]; then
                    # Obtener estadísticas de la red
                    PLACES=$(jq '.M0 | length' "$NET_JSON")
                    TRANSITIONS=$(jq '.I_minus[0] | length' "$NET_JSON")
                    SUBNETS=$(jq '.subnet_definitions | length' "$NET_JSON")

                    log_success "    ✅ $size #$i: $PLACES lugares, $TRANSITIONS transiciones, $SUBNETS subredes (${duration}s, división $DIVISION_TYPE)"
                    SUCCESSFUL_NETWORKS=$((SUCCESSFUL_NETWORKS + 1))

                    # Análisis de división si está habilitado
                    if [ "$INCLUDE_ANALYSIS" = true ]; then
                        NET_DIR=$(dirname "$NET_JSON")
                        log_info "📊 Analizando división de $size #$i..."

                        # Ejecutar DivisionComparator
                        mvn exec:java -Dexec.mainClass="algorithm.DivisionComparator" -Dexec.args="$NET_JSON" -q > "$NET_DIR/division_analysis.log" 2>&1

                        # Generar visualizaciones DOT (con manejo de errores)
                        if python3 src/scripts/utils/petri_division_visualizer.py "$NET_JSON" > "$NET_DIR/visualizer_output.log" 2>&1; then
                            log_info "    📊 Visualizaciones DOT generadas correctamente"
                        else
                            log_warn "    ⚠️  Error en visualizaciones DOT (no crítico)"
                        fi

                        # Crear reporte HTML simple
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
        .optimized { background: #e8f5e8; }
        .warning { background: #fff3cd; }
        table { border-collapse: collapse; width: 100%; margin: 10px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
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
        <h2>📈 Estadísticas Generales</h2>
        <p><strong>Lugares:</strong> $PLACES</p>
        <p><strong>Transiciones:</strong> $TRANSITIONS</p>
        <p><strong>Subredes:</strong> $SUBNETS</p>
        <p><strong>Tipo de División:</strong> $DIVISION_TYPE</p>
    </div>

    <h2>🔧 Distribución de Subredes</h2>
EOF

                        # Añadir distribución de subredes al HTML
                        jq -r '.subnet_definitions | to_entries | map("<div class=\"subnet\"><h3>Subred " + (.key | tostring) + "</h3><p><strong>Transiciones:</strong> " + (.value.trans_indices | length | tostring) + "</p><p><strong>Lugares:</strong> " + (.value.place_indices | length | tostring) + "</p></div>") | join("")' "$NET_JSON" >> "$NET_DIR/subnet_division_report.html"

                        # Cerrar HTML
                        cat >> "$NET_DIR/subnet_division_report.html" << EOF

    <h2>📁 Archivos Generados</h2>
    <ul>
        <li><strong>net_dividida.json</strong> - Red con división aplicada</li>
        <li><strong>division_analysis.log</strong> - Log del análisis completo</li>
        <li><strong>division_actual.dot</strong> - Visualización de división actual</li>
        <li><strong>division_optimizada.dot</strong> - Visualización de división optimizada</li>
    </ul>

    <h2>💡 Comandos Útiles</h2>
    <pre>
# Convertir DOT a imagen
dot -Tsvg division_actual.dot -o division_actual.svg
dot -Tsvg division_optimizada.dot -o division_optimizada.svg

# Analizar red con Java
mvn exec:java -Dexec.mainClass="algorithm.DivisionComparator" -Dexec.args="$NET_JSON"
    </pre>

</body>
</html>
EOF

                        # Mover archivos DOT generados (solo los que existen)
                        for file in division_actual.dot division_optimizada.dot; do
                            if [ -f "$file" ]; then
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

if [ $FAILED_NETWORKS -gt 0 ]; then
    log_warn "⚠️  Redes fallidas: $FAILED_NETWORKS/$TOTAL_NETWORKS"
fi

if [ "$DRY_RUN" = false ]; then
    if [ "$USE_OPTIMIZED" = true ]; then
        log_success "✅ Todas las redes usan división optimizada (sin mega-subredes)"
    else
        log_warn "⚠️  Todas las redes usan división original (pueden tener mega-subredes)"
    fi

    log_info "📁 Redes disponibles para análisis:"
    if [ "$INCLUDE_ANALYSIS" = true ]; then
        log_success "📊 Análisis de división incluido en cada red"
        log_success "🎯 Para ver análisis: open $OUTPUT_DIR/*/subnet_division_report.html"
    else
        log_info "💡 Para analizar divisiones: ./analyze_division.sh \"$OUTPUT_DIR\""
    fi
fi

log_success "🎯 Listo! Ejecuta:"
if [ "$USE_OPTIMIZED" = true ]; then
    log_success "    # Benchmark con división optimizada"
else
    log_success "    # Benchmark con división original"
fi
log_success "    ./benchmark_s3pr_algorithms.sh \"$(pwd)/$OUTPUT_DIR\" \"[1,4,10]\" 300"