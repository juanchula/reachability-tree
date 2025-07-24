#ifndef PETRI_NET_H
#define PETRI_NET_H

#include "common.h"

// ============================================================================
// ESTRUCTURAS DE DATOS PARA RED DE PETRI
// ============================================================================

// Estructura para una subred
struct subnet {
    int id;                     // Identificador único de la subred
    int num_places;             // Número de lugares en la subred
    int num_transitions;        // Número de transiciones en la subred
    
    // Mapeo de índices locales a globales
    int *place_indices;         // [num_places] - índices globales de lugares
    int *trans_indices;         // [num_transitions] - índices globales de transiciones
    
    // Mapeo inverso para búsqueda rápida (global -> local)
    int *global_to_local_place; // [red_global.num_places] - -1 si no está en subred
    int *global_to_local_trans; // [red_global.num_transitions] - -1 si no está en subred
    
    // Matrices de incidencia locales (optimizadas para la subred)
    int **i_minus;              // [num_places][num_transitions] - matriz de consumo
    int **i_plus;               // [num_places][num_transitions] - matriz de producción
    
    // Cache para marcados extraídos (para evitar realocaciones frecuentes)
    int *marking_cache;         // Buffer reutilizable para extraer marcados
};

// Estructura principal de la red de Petri
struct petri_net {
    // Dimensiones de la red
    int num_places;             // Número total de lugares
    int num_transitions;        // Número total de transiciones
    int num_subnets;            // Número de subredes
    
    // Marcado inicial
    int *initial_marking;       // [num_places] - marcado inicial
    
    // Matrices de incidencia globales
    int **i_minus;              // [num_places][num_transitions] - matriz de pre-condiciones
    int **i_plus;               // [num_places][num_transitions] - matriz de post-condiciones
    
    // Subredes para paralelización
    subnet_t *subnets;          // Array de subredes
    
    // Estructuras auxiliares para optimización
    bool *transition_enabled_cache; // Cache para verificación de habilitación
    int *enabled_transitions_buffer; // Buffer reutilizable para transiciones habilitadas
    int enabled_transitions_capacity; // Capacidad del buffer
    
    // Metadatos para debugging y estadísticas
    char *source_file;          // Archivo JSON original (opcional)
    double load_time;           // Tiempo de carga en segundos
};

// ============================================================================
// FUNCIONES DE CREACIÓN Y DESTRUCCIÓN
// ============================================================================

/**
 * Crea una nueva red de Petri con las dimensiones especificadas.
 * Inicializa todas las estructuras pero no las llena con datos.
 */
petri_net_t* petri_net_create(int num_places, int num_transitions, int num_subnets);

/**
 * Libera completamente una red de Petri y todas sus estructuras asociadas.
 */
void petri_net_destroy(petri_net_t *net);

/**
 * Carga una red de Petri desde un archivo JSON.
 * Formato idéntico al usado por la versión Java para compatibilidad 100%.
 */
petri_net_t* petri_net_from_json(const char *filename);

/**
 * Valida la consistencia de una red de Petri cargada.
 * Verifica dimensiones, matrices, subredes, etc.
 */
petri_error_t petri_net_validate(const petri_net_t *net);

// ============================================================================
// FUNCIONES DE CONSULTA Y ANÁLISIS
// ============================================================================

/**
 * Verifica si una transición está habilitada en un marcado dado.
 * Optimizada para manejo de marcas omega.
 */
bool petri_net_is_transition_enabled(const petri_net_t *net, int trans_idx, const int *marking);

/**
 * Obtiene todas las transiciones habilitadas en un marcado.
 * Usa buffer interno reutilizable para evitar allocations.
 * 
 * @param net Red de Petri
 * @param marking Marcado actual
 * @param count [out] Número de transiciones habilitadas
 * @return Array de índices de transiciones habilitadas (no liberar, es interno)
 */
const int* petri_net_get_enabled_transitions(const petri_net_t *net, const int *marking, int *count);

/**
 * Obtiene las subredes que contienen una transición específica.
 * 
 * @param net Red de Petri
 * @param trans_idx Índice global de la transición
 * @param count [out] Número de subredes que contienen la transición
 * @return Array de punteros a subredes (no liberar, son referencias internas)
 */
subnet_t** petri_net_get_subnets_containing_transition(const petri_net_t *net, int trans_idx, int *count);

/**
 * Dispara una transición en un marcado global.
 * Crea un nuevo marcado con el resultado del disparo.
 * 
 * @param net Red de Petri
 * @param trans_idx Índice de la transición a disparar
 * @param marking Marcado actual
 * @param new_marking [out] Nuevo marcado después del disparo (debe tener espacio para num_places)
 * @return true si el disparo fue exitoso, false si la transición no estaba habilitada
 */
bool petri_net_fire_transition(const petri_net_t *net, int trans_idx, const int *marking, int *new_marking);

// ============================================================================
// FUNCIONES DE SUBRED
// ============================================================================

/**
 * Crea una nueva subred.
 * Construye automáticamente las matrices locales y mapeos.
 */
subnet_t* subnet_create(int id, const petri_net_t *parent_net, 
                       const int *place_indices, int num_places,
                       const int *trans_indices, int num_transitions);

/**
 * Libera una subred.
 */
void subnet_destroy(subnet_t *subnet);

/**
 * Verifica si una subred contiene una transición específica.
 */
bool subnet_contains_transition(const subnet_t *subnet, int global_trans_idx);

/**
 * Obtiene el índice local de una transición global en la subred.
 * @return Índice local, o -1 si la transición no está en la subred
 */
int subnet_get_local_trans_index(const subnet_t *subnet, int global_trans_idx);

/**
 * Extrae el marcado de la subred a partir de un marcado global.
 * Usa el cache interno de la subred para evitar allocations.
 * 
 * @param subnet Subred
 * @param global_marking Marcado global
 * @return Marcado local (no liberar, es el cache interno de la subred)
 */
const int* subnet_extract_marking(subnet_t *subnet, const int *global_marking);

/**
 * Dispara una transición local en un marcado de subred.
 * Modifica el marcado in-place.
 * 
 * @param subnet Subred
 * @param local_trans_idx Índice local de la transición
 * @param subnet_marking Marcado de la subred (modificado in-place)
 * @return true si el disparo fue exitoso
 */
bool subnet_fire_transition(const subnet_t *subnet, int local_trans_idx, int *subnet_marking);

/**
 * Propaga marcas omega desde un marcado anterior.
 * Asegura que las marcas omega se mantengan correctamente.
 */
void subnet_propagate_omega(const subnet_t *subnet, int *subnet_marking, const int *prev_marking);

// ============================================================================
// FUNCIONES UTILITARIAS DE MARCADO
// ============================================================================

/**
 * Serializa un marcado a una cadena para debugging.
 * Maneja marcas omega correctamente.
 */
char* marking_to_string(const int *marking, int size);

/**
 * Compara dos marcados considerando marcas omega.
 * Las marcas omega son iguales entre sí pero diferentes de cualquier número.
 */
int marking_compare(const int *m1, const int *m2, int size);

/**
 * Verifica si un marcado es mayor o igual que otro (para detección omega).
 * m1 >= m2 si para todo i: m1[i] >= m2[i] o m1[i] == OMEGA o m2[i] == OMEGA
 */
bool marking_greater_equal(const int *m1, const int *m2, int size);

// ============================================================================
// FUNCIONES DE INFORMACIÓN Y ESTADÍSTICAS
// ============================================================================

/**
 * Imprime información detallada sobre la red de Petri.
 */
void petri_net_print_info(const petri_net_t *net);

/**
 * Imprime estadísticas de memoria utilizada por la red.
 */
void petri_net_print_memory_stats(const petri_net_t *net);

/**
 * Obtiene el tamaño estimado en bytes de la red de Petri.
 */
size_t petri_net_memory_size(const petri_net_t *net);

// ============================================================================
// MACROS DE CONVENIENCIA
// ============================================================================

// Verificación rápida de validez de índices
#define VALID_PLACE_INDEX(net, idx) ((idx) >= 0 && (idx) < (net)->num_places)
#define VALID_TRANS_INDEX(net, idx) ((idx) >= 0 && (idx) < (net)->num_transitions)
#define VALID_SUBNET_INDEX(net, idx) ((idx) >= 0 && (idx) < (net)->num_subnets)

// Acceso rápido a matrices (con verificación en debug)
#ifdef DEBUG
    #define I_MINUS(net, p, t) \
        (ASSERT(VALID_PLACE_INDEX(net, p) && VALID_TRANS_INDEX(net, t)), \
         (net)->i_minus[p][t])
    #define I_PLUS(net, p, t) \
        (ASSERT(VALID_PLACE_INDEX(net, p) && VALID_TRANS_INDEX(net, t)), \
         (net)->i_plus[p][t])
#else
    #define I_MINUS(net, p, t) ((net)->i_minus[p][t])
    #define I_PLUS(net, p, t) ((net)->i_plus[p][t])
#endif

// Macros para iteración eficiente
#define FOR_EACH_PLACE(net, p) \
    for (int p = 0; p < (net)->num_places; p++)

#define FOR_EACH_TRANSITION(net, t) \
    for (int t = 0; t < (net)->num_transitions; t++)

#define FOR_EACH_SUBNET(net, s) \
    for (int s = 0; s < (net)->num_subnets; s++)

#endif // PETRI_NET_H