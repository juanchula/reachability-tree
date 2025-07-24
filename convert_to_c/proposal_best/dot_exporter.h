#ifndef DOT_EXPORTER_H
#define DOT_EXPORTER_H

#include "common.h"
#include "petri_net.h"
#include "reachability_tree.h"

// ============================================================================
// EXPORTADOR DOT PARA ÁRBOL DE ALCANZABILIDAD
// ============================================================================

// Configuraciones del exportador
typedef struct dot_export_config {
    bool show_node_ids;                  // Mostrar IDs de nodos
    bool show_transition_labels;         // Mostrar etiquetas de transiciones
    bool compact_omega;                  // Usar ω en lugar de "omega"
    bool color_omega_nodes;              // Colorear nodos con omega
    char *graph_title;                   // Título del gráfico
    char *node_shape;                    // Forma de los nodos (box, circle, etc.)
    char *rankdir;                       // Dirección del layout (TB, LR, etc.)
} dot_export_config_t;

// ============================================================================
// FUNCIONES PRINCIPALES
// ============================================================================

// Exportación con configuración por defecto
petri_error_t dot_exporter_export(const reachability_tree_t *tree, 
                                 const petri_net_t *net, const char *filename);

// Exportación con configuración personalizada
petri_error_t dot_exporter_export_with_config(const reachability_tree_t *tree,
                                             const petri_net_t *net, 
                                             const char *filename,
                                             const dot_export_config_t *config);

// ============================================================================
// CONFIGURACIÓN
// ============================================================================

dot_export_config_t* dot_export_config_create_default(void);
void dot_export_config_destroy(dot_export_config_t *config);

void dot_export_config_set_title(dot_export_config_t *config, const char *title);
void dot_export_config_set_node_shape(dot_export_config_t *config, const char *shape);
void dot_export_config_set_rankdir(dot_export_config_t *config, const char *rankdir);

// ============================================================================
// UTILIDADES DE FORMATEO
// ============================================================================

// Convierte marcado a string con formato DOT
char* marking_to_dot_string(const int *marking, int size, bool compact_omega);

// Escapa caracteres especiales para DOT
char* escape_dot_string(const char *str);

// Extrae número de transición del ID del nodo
int extract_transition_from_id(const char *marking_id);

// Obtiene el ID del nodo padre
char* get_parent_id(const char *marking_id);

// ============================================================================
// ANÁLISIS DE ANCESTROS PARA TRANSICIONES
// ============================================================================

// Información de una transición en el árbol
typedef struct transition_info {
    int transition_index;
    char *parent_id;
    char *child_id;
    char *label;
} transition_info_t;

// Obtiene todas las transiciones del árbol
transition_info_t* get_tree_transitions(const reachability_tree_t *tree, size_t *count);
void free_transition_info_array(transition_info_t *transitions, size_t count);

// ============================================================================
// VALIDACIÓN Y DEBUG
// ============================================================================

// Valida que el archivo DOT sea válido
bool validate_dot_file(const char *filename);

// Obtiene estadísticas del DOT generado
typedef struct dot_stats {
    size_t node_count;
    size_t edge_count;
    size_t omega_node_count;
    size_t max_path_length;
} dot_stats_t;

dot_stats_t get_dot_statistics(const reachability_tree_t *tree);

#endif // DOT_EXPORTER_H