# 🚀 Benchmark de Redes Omega - Reporte Comparativo

**Fecha:** 2025-08-13 17:16:19  
**Carpeta de datos:** `/Users/juan/PPS/2025/reachability-tree/data/test_1`  
**Algoritmos:** both  
**Configuraciones de hilos:** 5 10 20  
**Timeout:** 30s  

---

## 📊 Resumen Ejecutivo

- **Total de tests:** 36
- **Tests exitosos:**        6
- **Tasa de éxito:** 16.6%
- **Redes probadas:**        6
- **Archivos DOT generados:**        4

---

## 📋 Tabla Comparativa Completa

| Red | Algoritmo | Hilos | Estados | Omegas | Tiempo (ms) | Throughput (estados/s) | Estado | DOT |
|-----|-----------|-------|---------|--------|-------------|----------------------|--------|-----|
| net1 | **c** | 5 | 81 | 1057 | 0.2 | 5 | ✅ | [net1_c_t5.dot](net1_c_t5.dot) |
| net1 | **c** | 10 | 81 | 1057 | 0.2 | 10 | ✅ | [net1_c_t10.dot](net1_c_t10.dot) |
| net1 | **c** | 20 | 81 | 1057 | 0.2 | 20 | ✅ | [net1_c_t20.dot](net1_c_t20.dot) |
| net1 | **java** | 5 | 27 | 54 | 138.587167 | 194 | ✅ | [net1_java_t5.dot](net1_java_t5.dot) |
| net1 | **java** | 10 | 27 | 54 | 131.368375 | 205 | ✅ | - |
| net1 | **java** | 20 | 27 | 54 | 125.110333 | 215 | ✅ | - |
| net2 | **c** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net2 | **c** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net2 | **c** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net2 | **java** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net2 | **java** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net2 | **java** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net3 | **c** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net3 | **c** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net3 | **c** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net3 | **java** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net3 | **java** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net3 | **java** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net4 | **c** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net4 | **c** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net4 | **c** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net4 | **java** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net4 | **java** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net4 | **java** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net5 | **c** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net5 | **c** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net5 | **c** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net5 | **java** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net5 | **java** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net5 | **java** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net6 | **c** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net6 | **c** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net6 | **c** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net6 | **java** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net6 | **java** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net6 | **java** | 20 | 0 | 0 | 0 | 0 | ❌ | - |

---

## 🔬 Análisis por Algoritmo

### 📈 java

- **Tests realizados:**       18
- **Tests exitosos:**        3
- **Tasa de éxito:** 16.6%

**Top 3 mejores rendimientos:**

| Red | Hilos | Estados/s | Estados | Omegas |
|-----|-------|-----------|---------|--------|
| net1 | 20 | **215** | 27 | 54 |
| net1 | 10 | **205** | 27 | 54 |
| net1 | 5 | **194** | 27 | 54 |

**Top 3 más detecciones omega:**

| Red | Hilos | Omegas | Estados | Tiempo |
|-----|-------|--------|---------|--------|
| net1 | 5 | **54** | 27 | 138.587167ms |
| net1 | 20 | **54** | 27 | 125.110333ms |
| net1 | 10 | **54** | 27 | 131.368375ms |

### 📈 c

- **Tests realizados:**       18
- **Tests exitosos:**        3
- **Tasa de éxito:** 16.6%

**Top 3 mejores rendimientos:**

| Red | Hilos | Estados/s | Estados | Omegas |
|-----|-------|-----------|---------|--------|
| net1 | 20 | **20** | 81 | 1057 |
| net1 | 10 | **10** | 81 | 1057 |
| net1 | 5 | **5** | 81 | 1057 |

**Top 3 más detecciones omega:**

| Red | Hilos | Omegas | Estados | Tiempo |
|-----|-------|--------|---------|--------|
| net1 | 5 | **1057** | 81 | 0.2ms |
| net1 | 20 | **1057** | 81 | 0.2ms |
| net1 | 10 | **1057** | 81 | 0.2ms |

## 🧵 Análisis de Escalabilidad

Comparación de rendimiento entre Java y C para diferentes configuraciones de hilos:

| Hilos | Java (estados/s) | C (estados/s) | Speedup C vs Java |
|-------|------------------|---------------|-------------------|
| 5 | 194 | 5 | .02x |
| 10 | 205 | 10 | .04x |
| 20 | 215 | 20 | .09x |


---

## 📁 Archivos Generados

### Archivos DOT
Los siguientes archivos DOT fueron generados para tests exitosos:

- net1_c_t10.dot
- net1_c_t20.dot
- net1_c_t5.dot
- net1_java_t5.dot

### Datos Raw
- **CSV completo:** [results.csv](results.csv)
- **Total archivos DOT:**        4

---

**Generado automáticamente por test_all_omega_networks.sh**  
**Timestamp:** 20250813_171550
