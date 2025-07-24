#ifndef COMMON_H
#define COMMON_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdatomic.h>
#include <pthread.h>
#include <unistd.h>
#include <time.h>
#include <errno.h>
#include <assert.h>

// ============================================================================
// CONSTANTES GLOBALES
// ============================================================================

// Valor especial para omega (infinito)
#define OMEGA (-1)

// Límites del sistema
#define MAX_PLACES 2048
#define MAX_TRANSITIONS 2048
#define MAX_SUBNETS 512
#define MAX_MARKING_ID_LEN 512
#define MAX_PATH_LEN 1024

// Configuración de paralelización
#define DEFAULT_BATCH_SIZE 256
#define MAX_IDLE_TIME_MS 50
#define MONITOR_INTERVAL_SEC 3

// Configuración de hash tables
#define INITIAL_HASH_SIZE 16384
#define HASH_LOAD_FACTOR 0.75
#define HASH_SEGMENTS 32

// Configuración de memory pools
#define NODE_POOL_SIZE 4096
#define MARKING_POOL_SIZE 8192
#define TASK_POOL_SIZE 16384

// ============================================================================
// TIPOS DE ERROR
// ============================================================================

typedef enum {
    PETRI_SUCCESS = 0,
    PETRI_ERROR_MEMORY,
    PETRI_ERROR_IO,
    PETRI_ERROR_JSON,
    PETRI_ERROR_INVALID_INPUT,
    PETRI_ERROR_THREAD,
    PETRI_ERROR_TIMEOUT,
    PETRI_ERROR_NOT_FOUND
} petri_error_t;

// ============================================================================
// MACROS DE LOGGING Y DEBUG
// ============================================================================

extern bool debug_enabled;
extern const char* debug_marking_id;

#define LOG_ERROR(fmt, ...) \
    fprintf(stderr, "[ERROR] %s:%d: " fmt "\n", __func__, __LINE__, ##__VA_ARGS__)

#define LOG_WARN(fmt, ...) \
    fprintf(stderr, "[WARN] %s:%d: " fmt "\n", __func__, __LINE__, ##__VA_ARGS__)

#define LOG_INFO(fmt, ...) \
    printf("[INFO] " fmt "\n", ##__VA_ARGS__)

#define LOG_DEBUG(fmt, ...) \
    do { \
        if (debug_enabled) { \
            printf("[DEBUG] %s:%d: " fmt "\n", __func__, __LINE__, ##__VA_ARGS__); \
        } \
    } while(0)

#define LOG_DEBUG_MARKING(marking_id, fmt, ...) \
    do { \
        if (debug_enabled && (!debug_marking_id || \
            (marking_id && strstr(marking_id, debug_marking_id)))) { \
            printf("[DEBUG:%s] " fmt "\n", marking_id, ##__VA_ARGS__); \
        } \
    } while(0)

// ============================================================================
// MACROS DE OPTIMIZACIÓN
// ============================================================================

// Hints del compilador para optimización
#define LIKELY(x)       __builtin_expect(!!(x), 1)
#define UNLIKELY(x)     __builtin_expect(!!(x), 0)
#define FORCE_INLINE    __attribute__((always_inline)) inline
#define CACHE_ALIGNED   __attribute__((aligned(64)))
#define PREFETCH(addr)  __builtin_prefetch(addr, 0, 3)

// Macros para operaciones atómicas frecuentes
#define ATOMIC_LOAD(ptr)        atomic_load_explicit(ptr, memory_order_acquire)
#define ATOMIC_STORE(ptr, val)  atomic_store_explicit(ptr, val, memory_order_release)
#define ATOMIC_INC(ptr)         atomic_fetch_add_explicit(ptr, 1, memory_order_acq_rel)
#define ATOMIC_DEC(ptr)         atomic_fetch_sub_explicit(ptr, 1, memory_order_acq_rel)
#define ATOMIC_CAS(ptr, exp, des) \
    atomic_compare_exchange_strong_explicit(ptr, exp, des, memory_order_acq_rel, memory_order_relaxed)

// ============================================================================
// MACROS DE GESTIÓN DE MEMORIA
// ============================================================================

#define SAFE_FREE(ptr) \
    do { \
        if (ptr) { \
            free(ptr); \
            ptr = NULL; \
        } \
    } while(0)

#define ALLOC_CHECK(ptr, size) \
    do { \
        if (!(ptr)) { \
            LOG_ERROR("Memory allocation failed: %zu bytes", (size_t)(size)); \
            return NULL; \
        } \
    } while(0)

#define ALLOC_CHECK_GOTO(ptr, size, label) \
    do { \
        if (!(ptr)) { \
            LOG_ERROR("Memory allocation failed: %zu bytes", (size_t)(size)); \
            goto label; \
        } \
    } while(0)

// ============================================================================
// ESTRUCTURAS BÁSICAS
// ============================================================================

// Forward declarations
typedef struct petri_net petri_net_t;
typedef struct subnet subnet_t;
typedef struct node node_t;
typedef struct reachability_tree reachability_tree_t;
typedef struct parallel_analyzer parallel_analyzer_t;

// Estructura para marcados
typedef struct marking {
    int *tokens;        // Array de tokens por lugar
    int size;           // Número de lugares
    uint64_t hash;      // Hash precalculado para eficiencia
} marking_t;

// Estructura para estadísticas de rendimiento
typedef struct perf_stats {
    atomic_size_t states_explored;
    atomic_size_t omega_detections;
    atomic_size_t tasks_processed;
    atomic_size_t cache_hits;
    atomic_size_t cache_misses;
    double start_time;
    double end_time;
} perf_stats_t;

// ============================================================================
// FUNCIONES UTILITARIAS INLINE
// ============================================================================

// Obtener tiempo actual en segundos con alta precisión
FORCE_INLINE double get_time_seconds(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

// Obtener tiempo en milisegundos
FORCE_INLINE double get_time_ms(void) {
    return get_time_seconds() * 1000.0;
}

// Función rápida para comparar marcados
FORCE_INLINE bool marking_equals(const int *m1, const int *m2, int size) {
    for (int i = 0; i < size; i++) {
        if (m1[i] != m2[i]) return false;
    }
    return true;
}

// Función rápida para copiar marcados
FORCE_INLINE void marking_copy(int *dest, const int *src, int size) {
    memcpy(dest, src, size * sizeof(int));
}

// Verificar si un marcado tiene omega
FORCE_INLINE bool marking_has_omega(const int *marking, int size) {
    for (int i = 0; i < size; i++) {
        if (marking[i] == OMEGA) return true;
    }
    return false;
}

// Potencia de 2 más cercana (para hash tables)
FORCE_INLINE size_t next_power_of_2(size_t n) {
    n--;
    n |= n >> 1;
    n |= n >> 2;
    n |= n >> 4;
    n |= n >> 8;
    n |= n >> 16;
    n |= n >> 32;
    n++;
    return n;
}

// ============================================================================
// CONFIGURACIÓN DE COMPILACIÓN CONDICIONAL
// ============================================================================

#ifdef DEBUG
    #define ASSERT(condition) assert(condition)
    #define DEBUG_ONLY(x) x
#else
    #define ASSERT(condition) ((void)0)
    #define DEBUG_ONLY(x) ((void)0)
#endif

#ifdef ENABLE_STATISTICS
    #define STATS_INC(counter) ATOMIC_INC(&(counter))
    #define STATS_ADD(counter, value) atomic_fetch_add(&(counter), (value))
#else
    #define STATS_INC(counter) ((void)0)
    #define STATS_ADD(counter, value) ((void)0)
#endif

// ============================================================================
// CONFIGURACIÓN DE THREADING
// ============================================================================

// Configuración para diferentes arquitecturas
#ifdef __x86_64__
    #define CPU_PAUSE() __builtin_ia32_pause()
#elif defined(__aarch64__)
    #define CPU_PAUSE() __asm__ __volatile__("yield" ::: "memory")
#else
    #define CPU_PAUSE() usleep(1)
#endif

// Spinlock básico para casos específicos
typedef struct {
    atomic_flag flag;
} spinlock_t;

FORCE_INLINE void spinlock_init(spinlock_t *lock) {
    atomic_flag_clear(&lock->flag);
}

FORCE_INLINE void spinlock_lock(spinlock_t *lock) {
    while (atomic_flag_test_and_set_explicit(&lock->flag, memory_order_acquire)) {
        CPU_PAUSE();
    }
}

FORCE_INLINE void spinlock_unlock(spinlock_t *lock) {
    atomic_flag_clear_explicit(&lock->flag, memory_order_release);
}

// ============================================================================
// DECLARACIONES DE VARIABLES GLOBALES
// ============================================================================

extern perf_stats_t g_stats;

// ============================================================================
// PROTOTIPOS DE FUNCIONES GLOBALES
// ============================================================================

// Inicialización y limpieza del sistema
void system_init(void);
void system_cleanup(void);

// Gestión de errores
const char* petri_error_string(petri_error_t error);

// Funciones de hash optimizadas (declaradas aquí para uso frecuente)
uint64_t fast_hash_marking(const int *marking, int size);
uint64_t fast_hash_string(const char *str);

#endif // COMMON_H