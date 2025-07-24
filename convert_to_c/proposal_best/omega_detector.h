#ifndef OMEGA_DETECTOR_H
#define OMEGA_DETECTOR_H

#include "common.h"
#include "petri_net.h"

// ============================================================================
// ESTRUCTURAS PARA DETECCIÓN OMEGA
// ============================================================================

// Estructura para almacenar información de un ancestro
typedef struct ancestor_info {
    const int *marking;         // Marcado del ancestro (referencia, no copia)
    const char *marking_id;     // ID del marcado para debugging
    int depth;                  // Profundidad en el árbol de alcanzabilidad
    bool transition_enabled;    // Si la transición estaba habilitada en este ancestro
} ancestor_info_t;

// Lista de ancestros para análisis omega
typedef struct ancestor_list {
    ancestor_info_t *ancestors; // Array de ancestros
    int count;                  // Número actual de ancestros
    int capacity;               // Capacidad del array
    int max_depth;              // Profundidad máxima a considerar para omega
} ancestor_list_t;

// Cache de resultados de detección omega para optimización
typedef struct omega_cache_entry {
    uint64_t marking_hash;      // Hash del marcado
    int transition_index;       // Transición analizada
    bool *omega_places;         // Resultados de detección (lugares con omega)
    bool has_omega;             // Si se detectó al menos un omega
    double timestamp;           // Timestamp para invalidación de cache
} omega_cache_entry_t;

// Cache LRU para resultados de detección omega
typedef struct omega_cache {
    omega_cache_entry_t *entries; // Array de entradas del cache
    int size;                   // Número actual de entradas
    int capacity;               // Capacidad máxima del cache
    pthread_mutex_t mutex;      // Mutex para acceso concurrente
    uint64_t hit_count;         // Estadísticas: aciertos del cache
    uint64_t miss_count;        // Estadísticas: fallos del cache
} omega_cache_t;

// Estructura principal del detector omega
typedef struct omega_detector {
    const petri_net_t *net;     // Red de Petri asociada
    omega_cache_t *cache;       // Cache de resultados
    bool enabled;               // Si la detección omega está habilitada
    
    // Parámetros de configuración
    int min_ancestors;          // Mínimo número de ancestros para detección
    int max_ancestors;          // Máximo número de ancestros a considerar
    double growth_threshold;    // Umbral para considerar crecimiento significativo
    
    // Estadísticas
    atomic_size_t detections_attempted;
    atomic_size_t detections_successful;
    atomic_size_t cycles_detected;
    atomic_size_t false_positives;
} omega_detector_t;

// ============================================================================
// FUNCIONES DE CREACIÓN Y DESTRUCCIÓN
// ============================================================================

/**
 * Crea un nuevo detector omega.
 * 
 * @param net Red de Petri asociada
 * @param cache_size Tamaño del cache de resultados (0 para deshabilitar cache)
 * @return Detector omega o NULL en caso de error
 */
omega_detector_t* omega_detector_create(const petri_net_t *net, int cache_size);

/**
 * Destruye un detector omega y libera todos sus recursos.
 */
void omega_detector_destroy(omega_detector_t *detector);

/**
 * Habilita o deshabilita la detección omega.
 */
void omega_detector_set_enabled(omega_detector_t *detector, bool enabled);

/**
 * Configura parámetros del detector omega.
 */
void omega_detector_configure(omega_detector_t *detector, 
                             int min_ancestors, int max_ancestors, 
                             double growth_threshold);

// ============================================================================
// FUNCIONES DE MANEJO DE ANCESTROS
// ============================================================================

/**
 * Crea una nueva lista de ancestros.
 */
ancestor_list_t* ancestor_list_create(int initial_capacity);

/**
 * Destruye una lista de ancestros.
 */
void ancestor_list_destroy(ancestor_list_t *list);

/**
 * Añade un ancestro a la lista.
 * 
 * @param list Lista de ancestros
 * @param marking Marcado del ancestro (se mantiene referencia, no se copia)
 * @param marking_id ID del marcado para debugging
 * @param depth Profundidad en el árbol
 * @param transition_enabled Si la transición estaba habilitada en este ancestro
 */
petri_error_t ancestor_list_add(ancestor_list_t *list, const int *marking, 
                               const char *marking_id, int depth, bool transition_enabled);

/**
 * Limpia la lista de ancestros sin liberar la estructura.
 */
void ancestor_list_clear(ancestor_list_t *list);

/**
 * Obtiene los ancestros relevantes para una transición específica.
 * Filtra ancestros donde la transición no estaba habilitada.
 */
ancestor_list_t* ancestor_list_filter_for_transition(const ancestor_list_t *list, int transition_index);

// ============================================================================
// FUNCIÓN PRINCIPAL DE DETECCIÓN OMEGA
// ============================================================================

/**
 * Aplica el algoritmo de detección omega según la tesis original.
 * 
 * Implementa los 3 criterios:
 * 1. Balance de tokens: Iplus[p][t] - Iminus[p][t] > 0
 * 2. Habilitación continua: Transición enabled en todos los ancestros relevantes
 * 3. Patrón de crecimiento: Incremento monótono en el lugar p
 * 
 * @param detector Detector omega
 * @param marking Marcado actual (modificado in-place)
 * @param marking_size Tamaño del marcado
 * @param ancestors Lista de ancestros para análisis
 * @param transition_index Transición que generó este marcado
 * @param fires Número de veces que se ha disparado la transición (para umbral)
 * @return true si se aplicó al menos una marca omega
 */
bool omega_detector_apply(omega_detector_t *detector, int *marking, int marking_size,
                         const ancestor_list_t *ancestors, int transition_index, int fires);

/**
 * Detecta lugares omega sin modificar el marcado.
 * Útil para análisis sin aplicar cambios.
 * 
 * @param detector Detector omega
 * @param marking Marcado actual
 * @param marking_size Tamaño del marcado
 * @param ancestors Lista de ancestros
 * @param transition_index Transición analizada
 * @param omega_places [out] Array booleano indicando lugares omega (debe tener espacio para marking_size)
 * @return true si se detectó al menos un lugar omega
 */
bool omega_detector_detect_places(omega_detector_t *detector, const int *marking, int marking_size,
                                 const ancestor_list_t *ancestors, int transition_index, 
                                 bool *omega_places);

// ============================================================================
// FUNCIONES AUXILIARES DEL ALGORITMO
// ============================================================================

/**
 * Verifica si existe un ciclo infinito que permite disparos ilimitados.
 * Implementa la verificación rigurosa de la existencia de un ciclo
 * que permita el crecimiento ilimitado de tokens.
 */
bool omega_detector_has_infinite_cycle(omega_detector_t *detector, int transition_index,
                                      const ancestor_list_t *ancestors);

/**
 * Verifica si una transición está siempre habilitada en los ancestros.
 * Solo considera ancestros donde la transición estuvo habilitada.
 */
bool omega_detector_always_enabled(omega_detector_t *detector, int transition_index,
                                  const ancestor_list_t *ancestors);

/**
 * Verifica si hay un patrón de crecimiento en un lugar específico.
 * Requiere crecimiento monótono estricto en el lugar.
 */
bool omega_detector_growth_pattern(omega_detector_t *detector, int place_index, 
                                  int transition_index, const ancestor_list_t *ancestors);

/**
 * Verifica si hay un patrón de crecimiento estricto (monótono creciente).
 * Más restrictivo que growth_pattern, requiere crecimiento en cada paso.
 */
bool omega_detector_strict_growth_pattern(omega_detector_t *detector, int place_index,
                                         int transition_index, const ancestor_list_t *ancestors);

/**
 * Analiza el balance de tokens para una transición y lugar específicos.
 * Calcula Iplus[p][t] - Iminus[p][t] y verifica si es positivo.
 */
int omega_detector_token_balance(omega_detector_t *detector, int place_index, int transition_index);

// ============================================================================
// FUNCIONES DE CACHE
// ============================================================================

/**
 * Busca en el cache un resultado previo de detección omega.
 * 
 * @param detector Detector omega
 * @param marking_hash Hash del marcado
 * @param transition_index Transición analizada
 * @param omega_places [out] Lugares omega (si se encuentra en cache)
 * @return true si se encontró resultado en cache
 */
bool omega_detector_cache_lookup(omega_detector_t *detector, uint64_t marking_hash,
                                int transition_index, bool *omega_places);

/**
 * Almacena un resultado en el cache.
 */
void omega_detector_cache_store(omega_detector_t *detector, uint64_t marking_hash,
                               int transition_index, const bool *omega_places, 
                               bool has_omega);

/**
 * Limpia el cache de detección omega.
 */
void omega_detector_cache_clear(omega_detector_t *detector);

/**
 * Obtiene estadísticas del cache.
 */
void omega_detector_cache_stats(omega_detector_t *detector, 
                               uint64_t *hit_count, uint64_t *miss_count, double *hit_ratio);

// ============================================================================
// FUNCIONES DE ANÁLISIS Y ESTADÍSTICAS
// ============================================================================

/**
 * Obtiene estadísticas del detector omega.
 */
void omega_detector_get_stats(omega_detector_t *detector,
                             size_t *detections_attempted, size_t *detections_successful,
                             size_t *cycles_detected, size_t *false_positives);

/**
 * Imprime estadísticas detalladas del detector omega.
 */
void omega_detector_print_stats(omega_detector_t *detector);

/**
 * Reinicia las estadísticas del detector.
 */
void omega_detector_reset_stats(omega_detector_t *detector);

// ============================================================================
// FUNCIONES DE DEBUGGING Y VALIDACIÓN
// ============================================================================

/**
 * Valida la consistencia de un resultado de detección omega.
 * Verifica que los lugares marcados como omega cumplan los criterios.
 */
bool omega_detector_validate_result(omega_detector_t *detector, const int *original_marking,
                                   const int *omega_marking, int marking_size,
                                   const ancestor_list_t *ancestors, int transition_index);

/**
 * Imprime información detallada sobre una detección omega para debugging.
 */
void omega_detector_debug_detection(omega_detector_t *detector, const int *marking,
                                   int marking_size, const ancestor_list_t *ancestors,
                                   int transition_index, const bool *omega_places);

/**
 * Exporta el estado del detector omega a un archivo para análisis.
 */
petri_error_t omega_detector_export_state(omega_detector_t *detector, const char *filename);

// ============================================================================
// MACROS DE CONVENIENCIA
// ============================================================================

// Verificación rápida de parámetros de entrada
#define OMEGA_VALID_DETECTOR(det) ((det) && (det)->net)
#define OMEGA_VALID_MARKING(marking, size) ((marking) && (size) > 0)
#define OMEGA_VALID_ANCESTORS(anc) ((anc) && (anc)->count >= 0)

// Macros para estadísticas (solo si están habilitadas)
#ifdef ENABLE_OMEGA_STATISTICS
    #define OMEGA_STATS_INC_ATTEMPTED(det) ATOMIC_INC(&(det)->detections_attempted)
    #define OMEGA_STATS_INC_SUCCESSFUL(det) ATOMIC_INC(&(det)->detections_successful)
    #define OMEGA_STATS_INC_CYCLES(det) ATOMIC_INC(&(det)->cycles_detected)
    #define OMEGA_STATS_INC_FALSE_POSITIVE(det) ATOMIC_INC(&(det)->false_positives)
#else
    #define OMEGA_STATS_INC_ATTEMPTED(det) ((void)0)
    #define OMEGA_STATS_INC_SUCCESSFUL(det) ((void)0)
    #define OMEGA_STATS_INC_CYCLES(det) ((void)0)
    #define OMEGA_STATS_INC_FALSE_POSITIVE(det) ((void)0)
#endif

// Macros para logging específico de omega
#define OMEGA_LOG_DEBUG(det, fmt, ...) \
    LOG_DEBUG("[OMEGA] " fmt, ##__VA_ARGS__)

#define OMEGA_LOG_DETECTION(det, place, reason) \
    LOG_DEBUG("[OMEGA] Place %d marked as ω due to: %s", place, reason)

#endif // OMEGA_DETECTOR_H