#ifndef REACHABILITY_TREE_H
#define REACHABILITY_TREE_H

#include "common.h"
#include "petri_net.h"
#include <pthread.h>

// ============================================================================
// ESTRUCTURAS DE DATOS DEL ÁRBOL DE ALCANZABILIDAD
// ============================================================================

typedef struct reachability_node {
    char *marking_id;                    // ID único del nodo (ej: "m0_t1_t3")
    int *global_marking;                 // Marcado global final
    int marking_size;                    // Tamaño del marcado
    
    // Marcados por subred
    int **subnet_markings;               // Array de marcados por subred
    int *subnet_marking_sizes;           // Tamaños de cada marcado de subred
    
    // Control de completitud para paralelización
    atomic_int completion_counter;       // Contador para sincronización
    int expected_completions;            // Número esperado de tareas
    
    // Metadata
    bool is_complete;                    // Si el nodo está completamente procesado
    bool has_omega;                      // Si contiene marcas omega
    
    pthread_mutex_t node_mutex;          // Mutex para modificaciones thread-safe
} reachability_node_t;

typedef struct reachability_tree {
    // Hash table para nodos (marking_id -> node)
    reachability_node_t **nodes;
    char **node_ids;
    size_t *node_buckets;
    size_t table_size;
    size_t node_count;
    
    // Hash table para marcados visitados (para detección de duplicados)
    uint64_t *visited_hashes;
    size_t visited_table_size;
    size_t visited_count;
    
    // Estadísticas
    atomic_size_t total_states;
    atomic_size_t omega_states;
    atomic_size_t duplicate_states;
    
    // Thread safety
    pthread_rwlock_t tree_lock;          // Lock para modificaciones del árbol
    pthread_mutex_t visited_lock;        // Lock para tabla de visitados
} reachability_tree_t;

// ============================================================================
// FUNCIONES DEL ÁRBOL DE ALCANZABILIDAD
// ============================================================================

// Gestión del árbol
reachability_tree_t* reachability_tree_create(void);
reachability_tree_t* reachability_tree_create_with_capacity(size_t initial_capacity);
void reachability_tree_destroy(reachability_tree_t *tree);

// Gestión de nodos
reachability_node_t* reachability_node_create(const char *marking_id, 
                                             int expected_completions,
                                             int marking_size);
void reachability_node_destroy(reachability_node_t *node);

// Operaciones del árbol
petri_error_t reachability_tree_add_node(reachability_tree_t *tree, 
                                        reachability_node_t *node);
reachability_node_t* reachability_tree_get_node(reachability_tree_t *tree, 
                                               const char *marking_id);
bool reachability_tree_remove_node(reachability_tree_t *tree, 
                                  const char *marking_id);

// Marcados y duplicados
bool reachability_tree_is_marking_visited(reachability_tree_t *tree, 
                                         const int *marking, int size);
petri_error_t reachability_tree_mark_visited(reachability_tree_t *tree, 
                                            const int *marking, int size);

// Utilidades de nodos
petri_error_t reachability_node_set_subnet_marking(reachability_node_t *node,
                                                  int subnet_id,
                                                  const int *marking,
                                                  int marking_size);
int* reachability_node_get_subnet_marking(reachability_node_t *node,
                                         int subnet_id);

// Finalización de nodos
petri_error_t reachability_node_complete(reachability_node_t *node,
                                        const petri_net_t *net);
int reachability_node_decrement_completion(reachability_node_t *node);

// Estadísticas
size_t reachability_tree_size(const reachability_tree_t *tree);
size_t reachability_tree_count_omega_states(const reachability_tree_t *tree);
void reachability_tree_print_stats(const reachability_tree_t *tree);

// Hash functions
uint64_t marking_hash(const int *marking, int size);
uint32_t string_hash(const char *str);

// Debug y utilidades
void reachability_tree_validate(const reachability_tree_t *tree);
void reachability_node_print(const reachability_node_t *node);

#endif // REACHABILITY_TREE_H