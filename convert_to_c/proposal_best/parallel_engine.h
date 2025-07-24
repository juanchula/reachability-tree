#ifndef PARALLEL_ENGINE_H
#define PARALLEL_ENGINE_H

#include "common.h"
#include "petri_net.h"
#include "reachability_tree.h"
#include "omega_detector.h"
#include <pthread.h>

// ============================================================================
// ESTRUCTURAS PARA ANÁLISIS PARALELO
// ============================================================================

// Tarea de disparo de transición
typedef struct firing_task {
    char *parent_marking_id;             // ID del nodo padre
    char *child_marking_id;              // ID del nodo hijo
    int transition_index;                // Índice de la transición a disparar
    int subnet_id;                       // ID de la subred involucrada
    struct firing_task *next;            // Para cola enlazada
} firing_task_t;

// Cola thread-safe de tareas
typedef struct task_queue {
    firing_task_t *head;
    firing_task_t *tail;
    atomic_size_t size;
    pthread_mutex_t mutex;
    pthread_cond_t not_empty;
    bool shutdown;
} task_queue_t;

// Worker thread context
typedef struct worker_context {
    int worker_id;
    struct parallel_analyzer *analyzer;
    pthread_t thread;
    atomic_bool active;
    atomic_size_t tasks_processed;
} worker_context_t;

// Analizador paralelo principal
typedef struct parallel_analyzer {
    // Configuración
    petri_net_t *net;
    int num_threads;
    bool use_omega;
    int batch_size;
    int timeout_seconds;
    
    // Estructuras de datos principales
    reachability_tree_t *tree;
    omega_detector_t *omega_detector;
    task_queue_t *task_queue;
    
    // Workers y threads
    worker_context_t *workers;
    pthread_t monitor_thread;
    atomic_int active_workers;
    atomic_bool analysis_complete;
    
    // Sincronización
    pthread_mutex_t analysis_mutex;
    pthread_cond_t completion_cond;
    
    // Estadísticas de rendimiento
    struct timespec start_time;
    struct timespec end_time;
    atomic_size_t total_tasks_created;
    atomic_size_t total_tasks_processed;
} parallel_analyzer_t;

// ============================================================================
// FUNCIONES DEL ANALIZADOR PARALELO
// ============================================================================

// Gestión del analizador
parallel_analyzer_t* parallel_analyzer_create(petri_net_t *net, int num_threads, 
                                            bool use_omega, int batch_size, int cache_size);
void parallel_analyzer_destroy(parallel_analyzer_t *analyzer);

// Análisis principal
petri_error_t parallel_analyzer_analyze(parallel_analyzer_t *analyzer);
reachability_tree_t* parallel_analyzer_get_tree(const parallel_analyzer_t *analyzer);

// Configuración
void parallel_analyzer_set_timeout(parallel_analyzer_t *analyzer, int timeout_seconds);
void parallel_analyzer_set_batch_size(parallel_analyzer_t *analyzer, int batch_size);

// Estadísticas y monitoring
void parallel_analyzer_print_omega_stats(parallel_analyzer_t *analyzer);
void parallel_analyzer_print_performance_stats(parallel_analyzer_t *analyzer);

// ============================================================================
// FUNCIONES DE COLA DE TAREAS
// ============================================================================

task_queue_t* task_queue_create(void);
void task_queue_destroy(task_queue_t *queue);

petri_error_t task_queue_enqueue(task_queue_t *queue, firing_task_t *task);
firing_task_t* task_queue_dequeue(task_queue_t *queue, int timeout_ms);
firing_task_t* task_queue_try_dequeue(task_queue_t *queue);

size_t task_queue_size(task_queue_t *queue);
void task_queue_shutdown(task_queue_t *queue);

// ============================================================================
// FUNCIONES DE TAREAS DE DISPARO
// ============================================================================

firing_task_t* firing_task_create(const char *parent_id, const char *child_id,
                                 int transition_index, int subnet_id);
void firing_task_destroy(firing_task_t *task);

char* generate_child_marking_id(const char *parent_id, int transition_index);

// ============================================================================
// WORKER THREADS
// ============================================================================

void* worker_thread_main(void *arg);
void* monitor_thread_main(void *arg);

petri_error_t process_firing_task(parallel_analyzer_t *analyzer, firing_task_t *task);
petri_error_t expand_node(parallel_analyzer_t *analyzer, reachability_node_t *node);

// ============================================================================
// UTILIDADES
// ============================================================================

bool is_analysis_complete(parallel_analyzer_t *analyzer);
void signal_analysis_completion(parallel_analyzer_t *analyzer);
void wait_for_completion(parallel_analyzer_t *analyzer);

double get_elapsed_time_seconds(const struct timespec *start, const struct timespec *end);

#endif // PARALLEL_ENGINE_H