#!/bin/bash

# ============================================================================
# Script de Conversión de Redes JSON a Formatos Petrinator y Tina
# ============================================================================
# 
# Convierte archivos JSON de redes de Petri a formatos Petrinator (XML) y Tina (PNML)
# usando los convertidores Java disponibles en src/main/java/convert/
#
# Uso: ./convert_to_formats.sh --converters <tipos> --input <carpeta> [--output <carpeta>] [--timeout <seg>]
#
# Parámetros:
#   --converters  - Tipos de conversión: petrinator, tina, both (default: both)
#   --input       - Carpeta que contiene archivos .json a convertir
#   --output      - Carpeta de salida (opcional, default: <input>/convert)
#   --timeout     - Timeout por conversión en segundos (opcional, default: 60)
#
# Ejemplos:
#   ./convert_to_formats.sh --converters both --input data/
#   ./convert_to_formats.sh --converters petrinator --input data/casos/ --timeout 120
#   ./convert_to_formats.sh --converters tina --input data/ --output out/
#
# ============================================================================

set -euo pipefail

# Configuración por defecto
DEFAULT_TIMEOUT=60
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Funciones de utilidad
log_info() { echo "📄 [$(date '+%H:%M:%S')] $*"; }
log_success() { echo "✅ [$(date '+%H:%M:%S')] $*"; }
log_error() { echo "❌ [$(date '+%H:%M:%S')] $*" >&2; }
log_warn() { echo "⚠️  [$(date '+%H:%M:%S')] $*" >&2; }

show_usage() {
    cat << 'EOF'
🔄 Script de Conversión JSON → Petrinator/Tina

USAGE:
    ./convert_to_formats.sh --converters <tipos> --input <carpeta> [opciones]

PARÁMETROS REQUERIDOS:
    --converters    Tipos de conversión a ejecutar:
                   - petrinator  (solo XML Petrinator)
                   - tina        (solo PNML Tina)  
                   - both        (ambos formatos)
    --input         Carpeta con archivos .json a convertir

PARÁMETROS OPCIONALES:
    --output        Carpeta de salida (default: <input>/convert)
    --timeout       Timeout por conversión en segundos (default: 60)
    --help          Mostrar esta ayuda

EJEMPLOS:
    # Convertir todo a ambos formatos
    ./convert_to_formats.sh --converters both --input data/

    # Solo conversión a Petrinator con timeout personalizado
    ./convert_to_formats.sh --converters petrinator --input data/casos/ --timeout 120

    # Solo conversión a Tina con carpeta de salida personalizada
    ./convert_to_formats.sh --converters tina --input data/ --output outputs/

SALIDA:
    📁 <output>/
    ├── net_1_petrinator.xml    (si --converters contiene petrinator)
    ├── net_1_tina.pnml         (si --converters contiene tina)
    ├── net_2_petrinator.xml
    ├── net_2_tina.pnml
    └── conversion_report.md

CONVERTIDORES JAVA:
    • ConvertDividedToPetrinator → Formato XML Petrinator
    • ConvertDividedToTina       → Formato PNML Tina
EOF
}

# Variables de configuración
CONVERTERS=""
JSON_DIR=""
OUTPUT_DIR=""
TIMEOUT="$DEFAULT_TIMEOUT"

# Parsing de argumentos
while [[ $# -gt 0 ]]; do
    case $1 in
        --converters)
            CONVERTERS="$2"
            shift 2
            ;;
        --input)
            JSON_DIR="$2"
            shift 2
            ;;
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            log_error "Parámetro desconocido: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Validaciones de argumentos requeridos
if [[ -z "$CONVERTERS" ]]; then
    log_error "Parámetro --converters es requerido"
    show_usage
    exit 1
fi

if [[ -z "$JSON_DIR" ]]; then
    log_error "Parámetro --input es requerido"
    show_usage
    exit 1
fi

# Validar tipos de conversores
case "$CONVERTERS" in
    petrinator|tina|both)
        ;;
    *)
        log_error "Valor inválido para --converters: $CONVERTERS (debe ser: petrinator, tina, both)"
        exit 1
        ;;
esac

# Validaciones básicas
if [[ ! -d "$JSON_DIR" ]]; then
    log_error "Carpeta no encontrada: $JSON_DIR"
    exit 1
fi

if ! [[ "$TIMEOUT" =~ ^[0-9]+$ ]] || [[ "$TIMEOUT" -lt 1 ]]; then
    log_error "Timeout debe ser un número positivo: $TIMEOUT"
    exit 1
fi

# Convertir a ruta absoluta
JSON_DIR="$(cd "$JSON_DIR" && pwd)"

# Establecer OUTPUT_DIR por defecto si no se especificó
if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="$JSON_DIR/convert"
else
    OUTPUT_DIR="$(cd "$(dirname "$OUTPUT_DIR")" && pwd)/$(basename "$OUTPUT_DIR")"
fi

log_info "Iniciando conversión de redes JSON"
log_info "Carpeta entrada: $JSON_DIR"
log_info "Carpeta salida: $OUTPUT_DIR"
log_info "Conversores habilitados: $CONVERTERS"
log_info "Timeout por conversión: ${TIMEOUT}s"

# Crear carpeta de salida
mkdir -p "$OUTPUT_DIR"

# Verificar compilación Java
log_info "Verificando compilación Java..."
cd "$PROJECT_ROOT"

if [[ ! -d "target/classes" ]]; then
    log_info "Compilando proyecto Java..."
    if ! mvn compile -q; then
        log_error "Error en compilación Java"
        exit 1
    fi
fi

# Buscar archivos JSON
json_files=()
while IFS= read -r -d '' file; do
    json_files+=("$file")
done < <(find "$JSON_DIR" -maxdepth 1 -name "*.json" -type f -print0 | sort -z)

if [[ ${#json_files[@]} -eq 0 ]]; then
    log_warn "No se encontraron archivos .json en $JSON_DIR"
    exit 0
fi

log_info "Encontrados ${#json_files[@]} archivos JSON"

# Variables para reporte
declare -a successful_conversions=()
declare -a failed_conversions=()
start_time=$(date '+%s')

# Función de conversión individual
convert_file() {
    local json_file="$1"
    local basename="${json_file##*/}"
    local name_without_ext="${basename%.json}"
    
    local petrinator_output="$OUTPUT_DIR/${name_without_ext}_petrinator.xml"
    local tina_output="$OUTPUT_DIR/${name_without_ext}_tina.pnml"
    
    log_info "Convirtiendo: $basename"
    
    # Inicializar estados como exitosos por defecto (para conversiones no ejecutadas)
    petrinator_status="✅"
    tina_status="✅"
    
    # Conversión a Petrinator (XML) - solo si está habilitada
    if [[ "$CONVERTERS" == "petrinator" || "$CONVERTERS" == "both" ]]; then
        log_info "  → Petrinator XML..."
        if timeout "$TIMEOUT" java -cp target/classes convert.ConvertDividedToPetrinator "$json_file" "$petrinator_output" 2>/dev/null; then
            log_success "    ✓ Petrinator: ${name_without_ext}_petrinator.xml"
            petrinator_status="✅"
        else
            log_error "    ✗ Error en conversión Petrinator"
            petrinator_status="❌"
        fi
    else
        # No ejecutada - marcar como no aplicable
        petrinator_status="➖"
    fi
    
    # Conversión a Tina (PNML) - solo si está habilitada
    if [[ "$CONVERTERS" == "tina" || "$CONVERTERS" == "both" ]]; then
        log_info "  → Tina PNML..."
        if timeout "$TIMEOUT" java -cp target/classes convert.ConvertDividedToTina "$json_file" "$tina_output" 2>/dev/null; then
            log_success "    ✓ Tina: ${name_without_ext}_tina.pnml"
            tina_status="✅"
        else
            log_error "    ✗ Error en conversión Tina"
            tina_status="❌"
        fi
    else
        # No ejecutada - marcar como no aplicable
        tina_status="➖"
    fi
    
    # Verificar archivos generados
    local petrinator_size=0
    local tina_size=0
    
    if [[ -f "$petrinator_output" ]]; then
        petrinator_size=$(stat -f%z "$petrinator_output" 2>/dev/null || echo "0")
    fi
    
    if [[ -f "$tina_output" ]]; then
        tina_size=$(stat -f%z "$tina_output" 2>/dev/null || echo "0")
    fi
    
    # Registrar resultado - considerar exitosa si todas las conversiones ejecutadas fueron exitosas
    local conversion_failed=false
    
    # Solo verificar conversiones que fueron ejecutadas
    if [[ "$CONVERTERS" == "petrinator" || "$CONVERTERS" == "both" ]]; then
        if [[ "$petrinator_status" == "❌" ]]; then
            conversion_failed=true
        fi
    fi
    
    if [[ "$CONVERTERS" == "tina" || "$CONVERTERS" == "both" ]]; then
        if [[ "$tina_status" == "❌" ]]; then
            conversion_failed=true
        fi
    fi
    
    if [[ "$conversion_failed" == "false" ]]; then
        successful_conversions+=("$basename")
    else
        failed_conversions+=("$basename")
    fi
    
    # Retornar datos para el reporte
    echo "$basename|$petrinator_status|$tina_status|$petrinator_size|$tina_size"
}

# Conversión en lote
log_info "Iniciando conversiones..."
echo

conversion_results=()
for json_file in "${json_files[@]}"; do
    if result=$(convert_file "$json_file"); then
        conversion_results+=("$result")
    fi
    echo
done

# Calcular estadísticas finales
end_time=$(date '+%s')
total_time=$((end_time - start_time))
total_files=${#json_files[@]}
successful_count=${#successful_conversions[@]}
failed_count=${#failed_conversions[@]}
success_rate=$(( (successful_count * 100) / total_files ))

# Generar reporte Markdown
report_file="$OUTPUT_DIR/conversion_report.md"

cat > "$report_file" << EOF
# 🔄 Reporte de Conversión JSON → Petrinator/Tina

**Fecha:** $(date '+%Y-%m-%d %H:%M:%S')  
**Carpeta origen:** \`$JSON_DIR\`  
**Carpeta destino:** \`$OUTPUT_DIR\`  
**Conversores ejecutados:** $CONVERTERS  
**Timeout:** ${TIMEOUT}s  

---

## 📊 Resumen Ejecutivo

- **Total archivos:** $total_files
- **Conversiones exitosas:** $successful_count
- **Conversiones fallidas:** $failed_count
- **Tasa de éxito:** $success_rate%
- **Tiempo total:** ${total_time}s

---

## 📋 Tabla de Conversiones

| Archivo JSON | Petrinator | Tina | Tamaño XML | Tamaño PNML |
|--------------|------------|------|------------|-------------|
EOF

# Agregar resultados al reporte
for result in "${conversion_results[@]}"; do
    IFS='|' read -r basename petrinator_status tina_status petrinator_size tina_size <<< "$result"
    
    # Formatear tamaños
    if [[ "$petrinator_size" -gt 0 ]]; then
        petrinator_size_fmt="${petrinator_size} bytes"
    else
        petrinator_size_fmt="-"
    fi
    
    if [[ "$tina_size" -gt 0 ]]; then
        tina_size_fmt="${tina_size} bytes"
    else
        tina_size_fmt="-"
    fi
    
    echo "| $basename | $petrinator_status | $tina_status | $petrinator_size_fmt | $tina_size_fmt |" >> "$report_file"
done

# Agregar secciones adicionales al reporte
cat >> "$report_file" << EOF

---

## ✅ Conversiones Exitosas

EOF

if [[ ${#successful_conversions[@]} -gt 0 ]]; then
    for file in "${successful_conversions[@]}"; do
        echo "- ✅ $file" >> "$report_file"
    done
else
    echo "- *Ninguna conversión exitosa*" >> "$report_file"
fi

cat >> "$report_file" << EOF

---

## ❌ Conversiones Fallidas

EOF

if [[ ${#failed_conversions[@]} -gt 0 ]]; then
    for file in "${failed_conversions[@]}"; do
        echo "- ❌ $file" >> "$report_file"
    done
else
    echo "- *Ninguna conversión fallida*" >> "$report_file"
fi

cat >> "$report_file" << EOF

---

## 📁 Archivos Generados

EOF

# Solo incluir secciones para los conversores ejecutados
if [[ "$CONVERTERS" == "petrinator" || "$CONVERTERS" == "both" ]]; then
    cat >> "$report_file" << EOF
### Formatos Petrinator (XML)
EOF
    
    find "$OUTPUT_DIR" -name "*_petrinator.xml" -type f | sort | while read -r file; do
        basename="${file##*/}"
        size=$(stat -f%z "$file" 2>/dev/null || echo "0")
        echo "- [$basename]($basename) - ${size} bytes" >> "$report_file"
    done
    
    # Agregar mensaje si no hay archivos
    if [[ $(find "$OUTPUT_DIR" -name "*_petrinator.xml" -type f | wc -l) -eq 0 ]]; then
        echo "- *No se generaron archivos Petrinator*" >> "$report_file"
    fi
fi

if [[ "$CONVERTERS" == "tina" || "$CONVERTERS" == "both" ]]; then
    cat >> "$report_file" << EOF

### Formatos Tina (PNML)
EOF
    
    find "$OUTPUT_DIR" -name "*_tina.pnml" -type f | sort | while read -r file; do
        basename="${file##*/}"
        size=$(stat -f%z "$file" 2>/dev/null || echo "0")
        echo "- [$basename]($basename) - ${size} bytes" >> "$report_file"
    done
    
    # Agregar mensaje si no hay archivos
    if [[ $(find "$OUTPUT_DIR" -name "*_tina.pnml" -type f | wc -l) -eq 0 ]]; then
        echo "- *No se generaron archivos Tina*" >> "$report_file"
    fi
fi

cat >> "$report_file" << EOF

---

## 🔧 Convertidores Utilizados

- **ConvertDividedToPetrinator**: Convierte JSON → XML (formato Petrinator)
- **ConvertDividedToTina**: Convierte JSON → PNML (formato Tina)

---

**Generado automáticamente por convert_to_formats.sh**  
**Timestamp:** $(date '+%Y%m%d_%H%M%S')
EOF

# Mostrar resumen final
echo
log_info "============================================"
log_info "🎯 CONVERSIÓN COMPLETADA"
log_info "============================================"
log_info "📁 Archivos procesados: $total_files"
log_info "✅ Conversiones exitosas: $successful_count"
log_info "❌ Conversiones fallidas: $failed_count"
log_info "📈 Tasa de éxito: $success_rate%"
log_info "⏱️  Tiempo total: ${total_time}s"
log_info "📄 Reporte generado: $report_file"
log_info "📂 Archivos en: $OUTPUT_DIR"

# Listar archivos generados
echo
log_info "Archivos generados:"

# Construir el patrón de búsqueda según los conversores seleccionados
find_patterns=()
if [[ "$CONVERTERS" == "petrinator" || "$CONVERTERS" == "both" ]]; then
    find_patterns+=("-name" "*.xml")
fi
if [[ "$CONVERTERS" == "tina" || "$CONVERTERS" == "both" ]]; then
    if [[ ${#find_patterns[@]} -gt 0 ]]; then
        find_patterns+=("-o")
    fi
    find_patterns+=("-name" "*.pnml")
fi

# Solo buscar archivos si hay patrones definidos
if [[ ${#find_patterns[@]} -gt 0 ]]; then
    find "$OUTPUT_DIR" "${find_patterns[@]}" | sort | while read -r file; do
        basename="${file##*/}"
        size=$(stat -f%z "$file" 2>/dev/null || echo "0")
        log_success "  $basename (${size} bytes)"
    done
else
    log_info "  No se especificaron conversores válidos"
fi

echo
if [[ $failed_count -eq 0 ]]; then
    log_success "🎉 Todas las conversiones completadas exitosamente!"
else
    log_warn "⚠️  $failed_count conversiones fallaron. Revisar reporte para detalles."
fi

echo
log_info "✨ Conversión finalizada"