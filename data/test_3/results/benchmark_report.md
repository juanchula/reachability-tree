# 🚀 Benchmark de Redes Omega - Reporte Comparativo

**Fecha:** 2025-08-20 17:09:44  
**Carpeta de datos:** `/Users/juan/PPS/2025/reachability-tree/data/test_3`  
**Algoritmos:** both  
**Configuraciones de hilos:** 5 10 20  
**Timeout:** 30s  

---

## 📊 Resumen Ejecutivo

- **Total de tests:** 12
- **Tests exitosos:**       12
- **Tasa de éxito:** 100.0%
- **Redes probadas:**        2
- **Archivos DOT generados:**        7

---

## 📋 Tabla Comparativa Completa

| Red | Algoritmo | Hilos | Estados | Omegas | Tiempo (ms) | Throughput (estados/s) | Estado | DOT |
|-----|-----------|-------|---------|--------|-------------|----------------------|--------|-----|
| net_medium_bounded_divided | **c** | 5 | 500 | 8481 | 2.5 | 5 | ✅ | [net_medium_bounded_divided_c_t5.dot](net_medium_bounded_divided_c_t5.dot) |
| net_medium_bounded_divided | **c** | 10 | 500 | 8481 | 2.3 | 10 | ✅ | [net_medium_bounded_divided_c_t10.dot](net_medium_bounded_divided_c_t10.dot) |
| net_medium_bounded_divided | **c** | 20 | 500 | 8481 | 2.5 | 20 | ✅ | [net_medium_bounded_divided_c_t20.dot](net_medium_bounded_divided_c_t20.dot) |
| net_medium_bounded_divided | **java** | 5 | 4100 | 0 | 237.045417 | 17296 | ✅ | [net_medium_bounded_divided_java_t5.dot](net_medium_bounded_divided_java_t5.dot) |
| net_medium_bounded_divided | **java** | 10 | 4100 | 0 | 223.357833 | 18356 | ✅ | - |
| net_medium_bounded_divided | **java** | 20 | 4100 | 0 | 243.058625 | 16868 | ✅ | - |
| net_medium_unbounded_divided | **c** | 5 | 500 | 8481 | 2.8 | 5 | ✅ | [net_medium_unbounded_divided_c_t5.dot](net_medium_unbounded_divided_c_t5.dot) |
| net_medium_unbounded_divided | **c** | 10 | 500 | 8481 | 2.5 | 10 | ✅ | [net_medium_unbounded_divided_c_t10.dot](net_medium_unbounded_divided_c_t10.dot) |
| net_medium_unbounded_divided | **c** | 20 | 500 | 8481 | 2.3 | 20 | ✅ | [net_medium_unbounded_divided_c_t20.dot](net_medium_unbounded_divided_c_t20.dot) |
| net_medium_unbounded_divided | **java** | 5 | 45072 | 704580 | 2395.005792 | 18819 | ✅ | - |
| net_medium_unbounded_divided | **java** | 10 | 45072 | 704580 | 2453.049167 | 18373 | ✅ | - |
| net_medium_unbounded_divided | **java** | 20 | 45072 | 704580 | 2287.051083 | 19707 | ✅ | - |

---

## 🔬 Análisis por Algoritmo

### 📈 java

- **Tests realizados:**        6
- **Tests exitosos:**        6
- **Tasa de éxito:** 100.0%

**Top 3 mejores rendimientos:**

| Red | Hilos | Estados/s | Estados | Omegas |
|-----|-------|-----------|---------|--------|
| net_medium_unbounded_divided | 20 | **19707** | 45072 | 704580 |
| net_medium_unbounded_divided | 5 | **18819** | 45072 | 704580 |
| net_medium_unbounded_divided | 10 | **18373** | 45072 | 704580 |

**Top 3 más detecciones omega:**

| Red | Hilos | Omegas | Estados | Tiempo |
|-----|-------|--------|---------|--------|
| net_medium_unbounded_divided | 5 | **704580** | 45072 | 2395.005792ms |
| net_medium_unbounded_divided | 20 | **704580** | 45072 | 2287.051083ms |
| net_medium_unbounded_divided | 10 | **704580** | 45072 | 2453.049167ms |

### 📈 c

- **Tests realizados:**        6
- **Tests exitosos:**        6
- **Tasa de éxito:** 100.0%

**Top 3 mejores rendimientos:**

| Red | Hilos | Estados/s | Estados | Omegas |
|-----|-------|-----------|---------|--------|
| net_medium_unbounded_divided | 20 | **20** | 500 | 8481 |
| net_medium_bounded_divided | 20 | **20** | 500 | 8481 |
| net_medium_unbounded_divided | 10 | **10** | 500 | 8481 |

**Top 3 más detecciones omega:**

| Red | Hilos | Omegas | Estados | Tiempo |
|-----|-------|--------|---------|--------|
| net_medium_unbounded_divided | 5 | **8481** | 500 | 2.8ms |
| net_medium_unbounded_divided | 20 | **8481** | 500 | 2.3ms |
| net_medium_unbounded_divided | 10 | **8481** | 500 | 2.5ms |

## 🧵 Análisis de Escalabilidad

Comparación de rendimiento entre Java y C para diferentes configuraciones de hilos:

| Hilos | Java (estados/s) | C (estados/s) | Speedup C vs Java |
|-------|------------------|---------------|-------------------|
| 5 | 18058 | 5 | 0x |
| 10 | 18364 | 10 | 0x |
| 20 | 18288 | 20 | 0x |


---

## 📁 Archivos Generados

### Archivos DOT
Los siguientes archivos DOT fueron generados para tests exitosos:

- net_medium_bounded_divided_c_t10.dot
- net_medium_bounded_divided_c_t20.dot
- net_medium_bounded_divided_c_t5.dot
- net_medium_bounded_divided_java_t5.dot
- net_medium_unbounded_divided_c_t10.dot
- net_medium_unbounded_divided_c_t20.dot
- net_medium_unbounded_divided_c_t5.dot

### Datos Raw
- **CSV completo:** [results.csv](results.csv)
- **Total archivos DOT:**        7

---

**Generado automáticamente por test_all_omega_networks.sh**  
**Timestamp:** 20250820_170924
