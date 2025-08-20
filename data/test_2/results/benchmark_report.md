# 🚀 Benchmark de Redes Omega - Reporte Comparativo

**Fecha:** 2025-08-20 17:42:01  
**Carpeta de datos:** `/Users/juan/PPS/2025/reachability-tree/data/test_2`  
**Algoritmos:** both  
**Configuraciones de hilos:** 5 10 20  
**Timeout:** 30s  

---

## 📊 Resumen Ejecutivo

- **Total de tests:** 36
- **Tests exitosos:**       21
- **Tasa de éxito:** 58.3%
- **Redes probadas:**        6
- **Archivos DOT generados:**       15

---

## 📋 Tabla Comparativa Completa

| Red | Algoritmo | Hilos | Estados | Omegas | Tiempo (ms) | Throughput (estados/s) | Estado | DOT |
|-----|-----------|-------|---------|--------|-------------|----------------------|--------|-----|
| net_large_bounded_48p_64t | **c** | 5 | 500 | 16124 | 6.3 | 5 | ✅ | [net_large_bounded_48p_64t_c_t5.dot](net_large_bounded_48p_64t_c_t5.dot) |
| net_large_bounded_48p_64t | **c** | 10 | 500 | 16124 | 6.1 | 10 | ✅ | [net_large_bounded_48p_64t_c_t10.dot](net_large_bounded_48p_64t_c_t10.dot) |
| net_large_bounded_48p_64t | **c** | 20 | 500 | 16124 | 6.3 | 20 | ✅ | [net_large_bounded_48p_64t_c_t20.dot](net_large_bounded_48p_64t_c_t20.dot) |
| net_large_bounded_48p_64t | **java** | 5 | 249900 | 0 | 3915.329 | 63826 | ✅ | - |
| net_large_bounded_48p_64t | **java** | 10 | 249900 | 0 | 3334.694291 | 74939 | ✅ | - |
| net_large_bounded_48p_64t | **java** | 20 | 249900 | 0 | 3438.84075 | 72669 | ✅ | - |
| net_large_bounded_72p_96t | **c** | 5 | 500 | 453 | 4.9 | 5 | ✅ | [net_large_bounded_72p_96t_c_t5.dot](net_large_bounded_72p_96t_c_t5.dot) |
| net_large_bounded_72p_96t | **c** | 10 | 500 | 453 | 5.2 | 10 | ✅ | [net_large_bounded_72p_96t_c_t10.dot](net_large_bounded_72p_96t_c_t10.dot) |
| net_large_bounded_72p_96t | **c** | 20 | 500 | 453 | 4.4 | 20 | ✅ | [net_large_bounded_72p_96t_c_t20.dot](net_large_bounded_72p_96t_c_t20.dot) |
| net_large_bounded_72p_96t | **java** | 5 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_large_bounded_72p_96t | **java** | 10 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_large_bounded_72p_96t | **java** | 20 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_large_unbounded_40p | **c** | 5 | 500 | 470 | 3.8 | 5 | ✅ | [net_large_unbounded_40p_c_t5.dot](net_large_unbounded_40p_c_t5.dot) |
| net_large_unbounded_40p | **c** | 10 | 500 | 470 | 2.6 | 10 | ✅ | [net_large_unbounded_40p_c_t10.dot](net_large_unbounded_40p_c_t10.dot) |
| net_large_unbounded_40p | **c** | 20 | 500 | 470 | 2.2 | 20 | ✅ | [net_large_unbounded_40p_c_t20.dot](net_large_unbounded_40p_c_t20.dot) |
| net_large_unbounded_40p | **java** | 5 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_large_unbounded_40p | **java** | 10 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_large_unbounded_40p | **java** | 20 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_large_unbounded_64p | **c** | 5 | 500 | 411 | 4.1 | 5 | ✅ | [net_large_unbounded_64p_c_t5.dot](net_large_unbounded_64p_c_t5.dot) |
| net_large_unbounded_64p | **c** | 10 | 500 | 411 | 3.1 | 10 | ✅ | [net_large_unbounded_64p_c_t10.dot](net_large_unbounded_64p_c_t10.dot) |
| net_large_unbounded_64p | **c** | 20 | 500 | 411 | 3.2 | 20 | ✅ | [net_large_unbounded_64p_c_t20.dot](net_large_unbounded_64p_c_t20.dot) |
| net_large_unbounded_64p | **java** | 5 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_large_unbounded_64p | **java** | 10 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_large_unbounded_64p | **java** | 20 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_medium_bounded_24p_32t | **c** | 5 | 6 | 0 | 0.2 | 5 | ✅ | [net_medium_bounded_24p_32t_c_t5.dot](net_medium_bounded_24p_32t_c_t5.dot) |
| net_medium_bounded_24p_32t | **c** | 10 | 6 | 0 | 0.1 | 10 | ✅ | [net_medium_bounded_24p_32t_c_t10.dot](net_medium_bounded_24p_32t_c_t10.dot) |
| net_medium_bounded_24p_32t | **c** | 20 | 6 | 0 | 0.2 | 20 | ✅ | [net_medium_bounded_24p_32t_c_t20.dot](net_medium_bounded_24p_32t_c_t20.dot) |
| net_medium_bounded_24p_32t | **java** | 5 | 6 | 0 | 142.8205 | 42 | ✅ | - |
| net_medium_bounded_24p_32t | **java** | 10 | 6 | 0 | 142.469375 | 42 | ✅ | - |
| net_medium_bounded_24p_32t | **java** | 20 | 6 | 0 | 144.633916 | 41 | ✅ | - |
| net_mixed_unbounded_bounded_100p | **c** | 5 | 0 | 0 | 0 | 0 | ❌ | - |
| net_mixed_unbounded_bounded_100p | **c** | 10 | 0 | 0 | 0 | 0 | ❌ | - |
| net_mixed_unbounded_bounded_100p | **c** | 20 | 0 | 0 | 0 | 0 | ❌ | - |
| net_mixed_unbounded_bounded_100p | **java** | 5 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_mixed_unbounded_bounded_100p | **java** | 10 | 0 | 0 | 30000 | 0 | ⏰ | - |
| net_mixed_unbounded_bounded_100p | **java** | 20 | 0 | 0 | 30000 | 0 | ⏰ | - |

---

## 🔬 Análisis por Algoritmo

### 📈 java

- **Tests realizados:**       18
- **Tests exitosos:**        6
- **Tasa de éxito:** 33.3%

**Top 3 mejores rendimientos:**

| Red | Hilos | Estados/s | Estados | Omegas |
|-----|-------|-----------|---------|--------|
| net_large_bounded_48p_64t | 10 | **74939** | 249900 | 0 |
| net_large_bounded_48p_64t | 20 | **72669** | 249900 | 0 |
| net_large_bounded_48p_64t | 5 | **63826** | 249900 | 0 |

**Top 3 más detecciones omega:**

| Red | Hilos | Omegas | Estados | Tiempo |
|-----|-------|--------|---------|--------|
| net_medium_bounded_24p_32t | 5 | **0** | 6 | 142.8205ms |
| net_medium_bounded_24p_32t | 20 | **0** | 6 | 144.633916ms |
| net_medium_bounded_24p_32t | 10 | **0** | 6 | 142.469375ms |

### 📈 c

- **Tests realizados:**       18
- **Tests exitosos:**       15
- **Tasa de éxito:** 83.3%

**Top 3 mejores rendimientos:**

| Red | Hilos | Estados/s | Estados | Omegas |
|-----|-------|-----------|---------|--------|
| net_medium_bounded_24p_32t | 20 | **20** | 6 | 0 |
| net_large_unbounded_64p | 20 | **20** | 500 | 411 |
| net_large_unbounded_40p | 20 | **20** | 500 | 470 |

**Top 3 más detecciones omega:**

| Red | Hilos | Omegas | Estados | Tiempo |
|-----|-------|--------|---------|--------|
| net_large_bounded_48p_64t | 5 | **16124** | 500 | 6.3ms |
| net_large_bounded_48p_64t | 20 | **16124** | 500 | 6.3ms |
| net_large_bounded_48p_64t | 10 | **16124** | 500 | 6.1ms |

## 🧵 Análisis de Escalabilidad

Comparación de rendimiento entre Java y C para diferentes configuraciones de hilos:

| Hilos | Java (estados/s) | C (estados/s) | Speedup C vs Java |
|-------|------------------|---------------|-------------------|
| 5 | 31934 | 5 | 0x |
| 10 | 37490 | 10 | 0x |
| 20 | 36355 | 20 | 0x |


---

## 📁 Archivos Generados

### Archivos DOT
Los siguientes archivos DOT fueron generados para tests exitosos:

- net_large_bounded_48p_64t_c_t10.dot
- net_large_bounded_48p_64t_c_t20.dot
- net_large_bounded_48p_64t_c_t5.dot
- net_large_bounded_72p_96t_c_t10.dot
- net_large_bounded_72p_96t_c_t20.dot
- net_large_bounded_72p_96t_c_t5.dot
- net_large_unbounded_40p_c_t10.dot
- net_large_unbounded_40p_c_t20.dot
- net_large_unbounded_40p_c_t5.dot
- net_large_unbounded_64p_c_t10.dot
- net_large_unbounded_64p_c_t20.dot
- net_large_unbounded_64p_c_t5.dot
- net_medium_bounded_24p_32t_c_t10.dot
- net_medium_bounded_24p_32t_c_t20.dot
- net_medium_bounded_24p_32t_c_t5.dot

### Datos Raw
- **CSV completo:** [results.csv](results.csv)
- **Total archivos DOT:**       15

---

**Generado automáticamente por test_all_omega_networks.sh**  
**Timestamp:** 20250820_173504
