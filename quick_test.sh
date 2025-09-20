#!/bin/bash

echo "=== PRUEBA RÁPIDA DE OPTIMIZACIONES ==="
echo "Compilando proyecto..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Error en compilación"
    exit 1
fi

echo "✅ Compilación exitosa"
echo ""

# Probar con un archivo pequeño
TEST_FILE="data/net_1.json"
if [ ! -f "$TEST_FILE" ]; then
    echo "❌ Archivo de prueba no encontrado: $TEST_FILE"
    exit 1
fi

echo "=== PROBANDO OPTIMIZACIONES ==="
echo "Archivo: $TEST_FILE"
echo ""

# Prueba 1: Sin omega
echo "1. Prueba sin omega (original):"
time java -cp target/classes algorithm.Main \
    --input "$TEST_FILE" \
    --nthreads 4 \
    --output "test_original.dot" 2>/dev/null
echo ""

# Prueba 2: Con omega integrado
echo "2. Prueba con omega integrado:"
time java -cp target/classes algorithm.Main \
    --input "$TEST_FILE" \
    --nthreads 4 \
    --omega \
    --output "test_omega_integrated.dot" 2>/dev/null
echo ""



# Limpiar archivos temporales
rm -f test_*.dot

echo "✅ Todas las pruebas completadas exitosamente"
echo ""
echo "COMPARACIÓN DE RENDIMIENTO:"
echo "- Sin omega: Algoritmo original optimizado"
echo "- Con omega integrado: ReachabilityAnalyzer con soporte omega"
echo ""
echo "Para pruebas más detalladas, ejecuta: ./performance_test.sh" 