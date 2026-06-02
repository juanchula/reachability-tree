#!/bin/bash

# Script para generar redes S3PR de diferentes tamaños de forma escalable
# Utiliza el generador S3PR para crear redes con parámetros incrementales

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colores para logging
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

show_usage() {
    cat << EOF
🏗️  Generador Escalable de Redes S3PR

Uso: $0 [opciones]

Opciones:
  -o, --output DIR        Directorio de salida (default: s3pr_networks)
  -s, --sizes ARRAY       Tamaños a generar (default: "small,medium,large,xlarge")
  -n, --count INT         Redes por tamaño (default: 3)
  -t, --timeout SEC       Timeout por red (default: 120)
  --dry-run              Solo mostrar comandos, no ejecutar
  -h, --help             Mostrar esta ayuda

Tamaños disponibles (rangos granulares):
  micro      - 1 circuito, 1 fj, 2-3 recursos (5-15 lugares)
  tiny       - 1 circuito, 1 fj, 3-4 recursos (10-20 lugares)
  xxsmall    - 2 circuitos, 1 fj, 4-5 recursos (15-25 lugares)
  xsmall     - 2 circuitos, 1 fj, 5-6 recursos (20-30 lugares)
  small      - 2 circuitos, 2 fj, 6-8 recursos (25-35 lugares)
  smallplus  - 3 circuitos, 2 fj, 7-9 recursos (30-40 lugares)
  medium     - 3 circuitos, 2 fj, 8-12 recursos (35-50 lugares)
  mediumplus - 4 circuitos, 3 fj, 10-15 recursos (45-60 lugares)
  large      - 4 circuitos, 3 fj, 12-18 recursos (55-75 lugares)
  largeplus  - 5 circuitos, 4 fj, 15-22 recursos (70-90 lugares)
  xlarge     - 5 circuitos, 4 fj, 18-25 recursos (80-110 lugares)
  xlargeplus - 6 circuitos, 5 fj, 22-30 recursos (100-130 lugares)
  xxlarge    - 7 circuitos, 5 fj, 25-35 recursos (120-160 lugares)
  huge       - 8 circuitos, 6 fj, 30-40 recursos (150-200 lugares)
  massive    - 10 circuitos, 7 fj, 35-50 recursos (180-250 lugares)
  gigantic   - 12 circuitos, 8 fj, 40-60 recursos (220-300 lugares)

Ejemplos:
  $0 -s "micro,tiny,xsmall,small" -n 2 -o small_range_nets
  $0 --sizes "medium,large,xlarge,huge" --count 3 --output large_range_nets
  $0 -s "tiny,small,medium,large,xlarge,massive" -n 5 -o complete_analysis
  $0 --sizes "micro,xxsmall,smallplus,mediumplus,largeplus" -n 1 -o intermediate_sizes
  $0 --dry-run

Salida:
  DIR/
    ├── SIZE_INDEX/
    │   ├── net_dividida.json    (para Java/C)
    │   ├── net.ndr              (para Tina)
    │   ├── net.pflow            (formato base)
    │   └── subnets.dot          (visualización)
    └── generation_report.md
EOF
}

# Función para obtener configuración por tamaño (16 rangos granulares)
get_size_config() {
    case "$1" in
        "micro")
            echo "--circuits 1 --fork_join 1 --min_shrd_src 2 --max_shrd_src 3 --cmpx_src 0"
            ;;
        "tiny")
            echo "--circuits 1 --fork_join 1 --min_shrd_src 3 --max_shrd_src 4 --cmpx_src 0"
            ;;
        "xxsmall")
            echo "--circuits 2 --fork_join 1 --min_shrd_src 4 --max_shrd_src 5 --cmpx_src 0"
            ;;
        "xsmall")
            echo "--circuits 2 --fork_join 1 --min_shrd_src 5 --max_shrd_src 6 --cmpx_src 0"
            ;;
        "small")
            echo "--circuits 2 --init_fj 1 --fork_join 2 --min_shrd_src 6 --max_shrd_src 8 --cmpx_src 0"
            ;;
        "smallplus")
            echo "--circuits 3 --init_fj 1 --fork_join 2 --min_shrd_src 7 --max_shrd_src 9 --cmpx_src 1"
            ;;
        "medium")
            echo "--circuits 3 --init_fj 2 --fork_join 2 --min_shrd_src 8 --max_shrd_src 12 --cmpx_src 1"
            ;;
        "mediumplus")
            echo "--circuits 4 --init_fj 2 --final_fj 1 --fork_join 3 --min_shrd_src 10 --max_shrd_src 15 --cmpx_src 2"
            ;;
        "large")
            echo "--circuits 4 --init_fj 3 --final_fj 1 --fork_join 3 --min_shrd_src 12 --max_shrd_src 18 --cmpx_src 2"
            ;;
        "largeplus")
            echo "--circuits 5 --init_fj 3 --final_fj 2 --fork_join 4 --init_final_fj 1 --min_shrd_src 15 --max_shrd_src 22 --cmpx_src 3"
            ;;
        "xlarge")
            echo "--circuits 5 --init_fj 4 --final_fj 2 --fork_join 4 --init_final_fj 1 --min_shrd_src 18 --max_shrd_src 25 --cmpx_src 3"
            ;;
        "xlargeplus")
            echo "--circuits 6 --init_fj 4 --final_fj 3 --fork_join 5 --init_final_fj 2 --min_shrd_src 22 --max_shrd_src 30 --cmpx_src 4"
            ;;
        "xxlarge")
            echo "--circuits 7 --init_fj 5 --final_fj 3 --fork_join 5 --init_final_fj 2 --min_shrd_src 25 --max_shrd_src 35 --cmpx_src 4"
            ;;
        "huge")
            echo "--circuits 8 --init_fj 5 --final_fj 4 --fork_join 6 --init_final_fj 3 --min_shrd_src 30 --max_shrd_src 40 --cmpx_src 5"
            ;;
        "massive")
            echo "--circuits 10 --init_fj 6 --final_fj 4 --fork_join 7 --init_final_fj 3 --min_shrd_src 35 --max_shrd_src 50 --cmpx_src 6"
            ;;
        "gigantic")
            echo "--circuits 12 --init_fj 7 --final_fj 5 --fork_join 8 --init_final_fj 4 --min_shrd_src 40 --max_shrd_src 60 --cmpx_src 8"
            ;;
        # NUEVOS TAMAÑOS OPTIMIZADOS PARA JAVA (2-5 minutos)
        "java_light")
            echo "--circuits 2 --fork_join 2 --min_shrd_src 4 --max_shrd_src 6 --cmpx_src 0"
            ;;
        "java_medium")
            echo "--circuits 2 --init_fj 1 --fork_join 2 --min_shrd_src 5 --max_shrd_src 7 --cmpx_src 0"
            ;;
        "java_balanced")
            echo "--circuits 3 --init_fj 1 --fork_join 2 --min_shrd_src 6 --max_shrd_src 8 --cmpx_src 1"
            ;;
        "java_complex")
            echo "--circuits 3 --init_fj 1 --fork_join 3 --min_shrd_src 7 --max_shrd_src 10 --cmpx_src 1"
            ;;
        "java_heavy")
            echo "--circuits 4 --init_fj 2 --fork_join 3 --min_shrd_src 8 --max_shrd_src 12 --cmpx_src 2"
            ;;
        # CONFIGURACIONES ULTRA-SIMPLES PARA JAVA (incremento solo recursos)
        "ultra_1")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 1 --max_shrd_src 1"
            ;;
        "ultra_2")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 2 --max_shrd_src 2"
            ;;
        "ultra_3")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 3"
            ;;
        "ultra_4")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 4"
            ;;
        "ultra_5")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 5 --max_shrd_src 5"
            ;;
        # REDES PROGRESIVAMENTE MÁS GRANDES PARA ANALIZAR ESCALABILIDAD
        "ultra_6")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 6 --max_shrd_src 6"
            ;;
        "ultra_7")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 7 --max_shrd_src 7"
            ;;
        "ultra_8")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 8 --max_shrd_src 8"
            ;;
        "ultra_9")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 9 --max_shrd_src 9"
            ;;
        "ultra_10")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 10 --max_shrd_src 10"
            ;;
        # VARIACIÓN EN CIRCUITOS (manteniendo recursos bajos)
        "circuit_2")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 4"
            ;;
        "circuit_3")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 4"
            ;;
        # VARIACIÓN EN FORK/JOIN (manteniendo circuitos bajos)
        "fj_2")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 4"
            ;;
        "fj_3")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 4"
            ;;
        # CONFIGURACIONES PARA JAVA TARDANDO 45-60 SEGUNDOS
        "target_45s_1")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 8 --max_shrd_src 12"
            ;;
        "target_45s_2")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 5 --max_shrd_src 6"
            ;;
        "target_45s_3")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 4"
            ;;
        "target_45s_4")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 15 --max_shrd_src 20"
            ;;
        "target_45s_5")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 4"
            ;;
        "target_60s_1")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 6"
            ;;
        "target_60s_2")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 2 --max_shrd_src 3"
            ;;
        "target_60s_3")
            echo "--circuits 4 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 2 --max_shrd_src 3"
            ;;
        "target_60s_4")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 5"
            ;;
        "target_60s_5")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 20 --max_shrd_src 25"
            ;;
        # CONFIGURACIONES MÁS AGRESIVAS PARA 45-60 SEGUNDOS
        "heavy_45s_1")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 6"
            ;;
        "heavy_45s_2")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 4"
            ;;
        "heavy_45s_3")
            echo "--circuits 4 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 2 --max_shrd_src 3"
            ;;
        "heavy_60s_1")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 4"
            ;;
        "heavy_60s_2")
            echo "--circuits 4 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 5"
            ;;
        "heavy_60s_3")
            echo "--circuits 5 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 2 --max_shrd_src 3"
            ;;
        # CONFIGURACIONES OPTIMIZADAS PARA 45-60 SEGUNDOS (basadas en testing)
        "optimal_45s_1")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 4"
            ;;
        "optimal_50s_1")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 2 --max_shrd_src 3"
            ;;
        "optimal_55s_1")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 3 --max_shrd_src 4"
            ;;
        "optimal_60s_1")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 3 --cmpx_src 0 --min_shrd_src 4 --max_shrd_src 5"
            ;;
        # CONFIGURACIONES SIMPLES SOLO VARIANDO SHARED RESOURCES (<60 segundos)
        "simple_15")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 15 --max_shrd_src 15"
            ;;
        "simple_20")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 3 --fork_join 2 --cmpx_src 2 --min_shrd_src 15 --max_shrd_src 15"
            ;;
        "simple_25")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 4 --fork_join 3 --cmpx_src 3 --min_shrd_src 15 --max_shrd_src 15"
            ;;
        "simple_30")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 5 --fork_join 4 --cmpx_src 1 --min_shrd_src 15 --max_shrd_src 15"
            ;;
        "simple_35")
            echo "--circuits 3 --init_fj 0 --final_fj 0 --init_final_fj 1 --fork_join 1 --cmpx_src 1 --min_shrd_src 15 --max_shrd_src 15"
            ;;
        "simple_40")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 2 --min_shrd_src 40 --max_shrd_src 40"
            ;;
        "simple_45")
           echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 1 --fork_join 1 --cmpx_src 1 --min_shrd_src 1125 --max_shrd_src 1125"
            ;;
        "simple_50")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 3 --fork_join 1 --cmpx_src 1 --min_shrd_src 105 --max_shrd_src 105"
            ;;
        # CONFIGURACIONES BALANCEADAS PARA CRECIMIENTO CONTROLADO (<60s)
        "balanced_10s")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 8 --max_shrd_src 10"
            ;;
        "balanced_20s")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 12 --max_shrd_src 15"
            ;;
        "balanced_30s")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 10 --max_shrd_src 12"
            ;;
        "balanced_40s")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 15 --max_shrd_src 18"
            ;;
        "balanced_50s")
            echo "--circuits 1 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 2 --cmpx_src 0 --min_shrd_src 12 --max_shrd_src 15"
            ;;
        "balanced_60s")
            echo "--circuits 2 --init_fj 0 --final_fj 0 --init_final_fj 0 --fork_join 1 --cmpx_src 0 --min_shrd_src 18 --max_shrd_src 22"
            ;;
        *)
            echo ""
            ;;
    esac
}

# Valores por defecto
OUTPUT_DIR="s3pr_networks"
SIZES="tiny,xsmall,small,medium,large,xlarge"
COUNT_PER_SIZE=3
TIMEOUT=120
DRY_RUN=false

# Parsear argumentos
while [[ $# -gt 0 ]]; do
    case $1 in
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -s|--sizes)
            SIZES="$2"
            shift 2
            ;;
        -n|--count)
            COUNT_PER_SIZE="$2"
            shift 2
            ;;
        -t|--timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
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

# Validar pipeline generator
GENERATOR_SCRIPT="$SCRIPT_DIR/src/scripts/generator_s3pr/pipeline_gen_pflow_ndr_json.sh"
if [[ ! -f "$GENERATOR_SCRIPT" ]]; then
    log_error "No se encontró el generador: $GENERATOR_SCRIPT"
    exit 1
fi

# Crear directorio de salida
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"

# Convertir sizes a array
IFS=',' read -ra SIZE_ARRAY <<< "$SIZES"

log_info "🏗️  Generador Escalable de Redes S3PR"
log_info "📁 Directorio salida: $OUTPUT_DIR"
log_info "🎯 Tamaños: ${SIZE_ARRAY[*]}"
log_info "🔢 Redes por tamaño: $COUNT_PER_SIZE"
log_info "⏱️  Timeout: ${TIMEOUT}s"

if [[ "$DRY_RUN" == "true" ]]; then
    log_warning "🧪 MODO DRY-RUN: Solo mostrando comandos"
fi

# Estadísticas
total_networks=$((${#SIZE_ARRAY[@]} * COUNT_PER_SIZE))
current_network=0
successful_generations=0
failed_generations=0
start_time=$(date +%s)

# Archivo de reporte
report_file="$OUTPUT_DIR/generation_report.md"

cat > "$report_file" << EOF
# 🏗️ Reporte de Generación S3PR

**Fecha:** $(date '+%Y-%m-%d %H:%M:%S')  
**Directorio:** \`$OUTPUT_DIR\`  
**Tamaños solicitados:** ${SIZE_ARRAY[*]}  
**Redes por tamaño:** $COUNT_PER_SIZE  
**Total redes:** $total_networks  

---

## 📊 Configuraciones por Tamaño

EOF

# Documentar configuraciones
for size in "${SIZE_ARRAY[@]}"; do
    config=$(get_size_config "$size")
    if [[ -n "$config" ]]; then
        cat >> "$report_file" << EOF
### $size
\`\`\`
$config
\`\`\`

EOF
    fi
done

cat >> "$report_file" << EOF

---

## 🎯 Redes Generadas

| Tamaño | Índice | Estado | Lugares | Transiciones | Subredes | Tiempo (s) |
|--------|--------|--------|---------|--------------|----------|------------|
EOF

# Función para generar una red específica
generate_network() {
    local size="$1"
    local index="$2"
    local network_dir="$OUTPUT_DIR/${size}_${index}"
    
    log_info "🔨 Generando red $size #$index..."
    
    local config=$(get_size_config "$size")
    if [[ -z "$config" ]]; then
        log_error "Tamaño '$size' no reconocido"
        return 1
    fi
    local gen_start=$(date +%s)
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "    [DRY-RUN] $GENERATOR_SCRIPT -n 1 -o \"$network_dir\" $config"
        echo "| $size | $index | 🧪 DRY-RUN | - | - | - | - |" >> "$report_file"
        return 0
    fi
    
    # Ejecutar generación con timeout
    if timeout "${TIMEOUT}s" "$GENERATOR_SCRIPT" -n 1 -o "$network_dir" $config &>/dev/null; then
        local gen_end=$(date +%s)
        local gen_time=$((gen_end - gen_start))
        
        # Buscar la red generada
        local net_json=$(find "$network_dir" -name "net_dividida.json" -type f | head -1)
        local net_ndr=$(find "$network_dir" -name "net.ndr" -type f | head -1)
        
        if [[ -f "$net_json" && -f "$net_ndr" ]]; then
            # Extraer estadísticas de la red JSON
            local places=$(jq '.M0 | length' "$net_json" 2>/dev/null || echo "?")
            local transitions=$(jq '.I_minus[0] | length' "$net_json" 2>/dev/null || echo "?")  
            local subnets=$(jq '.subnet_definitions | length' "$net_json" 2>/dev/null || echo "?")
            
            log_success "    ✅ $size #$index: $places lugares, $transitions transiciones, $subnets subredes (${gen_time}s)"
            echo "| $size | $index | ✅ OK | $places | $transitions | $subnets | $gen_time |" >> "$report_file"
            return 0
        else
            log_error "    ❌ Archivos de salida no encontrados"
            echo "| $size | $index | ❌ Sin archivos | - | - | - | $gen_time |" >> "$report_file"
            return 1
        fi
    else
        local gen_end=$(date +%s)
        local gen_time=$((gen_end - gen_start))
        log_error "    ❌ Timeout o error en generación (${gen_time}s)"
        echo "| $size | $index | ❌ Timeout | - | - | - | $gen_time |" >> "$report_file"
        return 1
    fi
}

# Generar todas las redes
for size in "${SIZE_ARRAY[@]}"; do
    log_info "📐 Generando redes de tamaño: $size"
    
    for ((i=1; i<=COUNT_PER_SIZE; i++)); do
        current_network=$((current_network + 1))
        
        log_info "    [$current_network/$total_networks] Progreso: $(( current_network * 100 / total_networks ))%"
        
        if generate_network "$size" "$i"; then
            successful_generations=$((successful_generations + 1))
        else
            failed_generations=$((failed_generations + 1))
        fi
    done
    
    echo # Separación visual
done

# Finalizar reporte
end_time=$(date +%s)
total_time=$((end_time - start_time))

cat >> "$report_file" << EOF

---

## 📈 Estadísticas Finales

- **Tiempo total:** ${total_time}s
- **Redes exitosas:** $successful_generations/$total_networks ($(( successful_generations * 100 / total_networks ))%)
- **Redes fallidas:** $failed_generations/$total_networks
- **Promedio por red:** $(( total_time / total_networks ))s

---

## 📁 Estructura de Salida

Cada red generada contiene:
- \`net_dividida.json\` - Para algoritmos Java/C
- \`net.ndr\` - Para herramienta Tina
- \`net.pflow\` - Formato base del generador
- \`subnets.dot\` - Visualización de subredes
- \`net.dot\` y \`net.svg\` - Visualización completa

**Comando Tina:** \`tina -R net.ndr salida.ts\`

EOF

# Resumen final
echo
log_info "🏁 GENERACIÓN COMPLETADA"
log_info "📊 Redes exitosas: $successful_generations/$total_networks"
log_info "⏱️  Tiempo total: ${total_time}s"
log_info "📄 Reporte: $report_file"

if [[ $failed_generations -gt 0 ]]; then
    log_warning "⚠️  $failed_generations redes fallaron - revisar configuraciones o timeout"
fi

# Listar redes generadas
log_info "📁 Redes disponibles para benchmark:"
find "$OUTPUT_DIR" -name "net_dividida.json" -type f | sort | while read -r file; do
    dir=$(dirname "$file")
    size_index=$(basename "$dir")
    places=$(jq '.M0 | length' "$file" 2>/dev/null || echo "?")
    echo "    $size_index ($places lugares)"
done

if [[ "$DRY_RUN" == "false" ]]; then
    echo
    log_success "🎯 Listo para benchmark! Ejecuta:"
    log_success "    ./benchmark_s3pr_algorithms.sh \"$OUTPUT_DIR\" \"[1,4,10]\" 300"
fi