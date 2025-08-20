#include "petri_net.h"
#include <cjson/cJSON.h>

// ============================================================================
// FUNCIONES DE CREACIÓN Y DESTRUCCIÓN
// ============================================================================

petri_net_t* petri_net_create(int num_places, int num_transitions, int num_subnets) {
    if (num_places <= 0 || num_transitions <= 0 || num_subnets < 0) {
        LOG_ERROR("Invalid dimensions: places=%d, transitions=%d, subnets=%d", 
                  num_places, num_transitions, num_subnets);
        return NULL;
    }
    
    petri_net_t *net = calloc(1, sizeof(petri_net_t));
    ALLOC_CHECK(net, sizeof(petri_net_t));
    
    net->num_places = num_places;
    net->num_transitions = num_transitions;
    net->num_subnets = num_subnets;
    
    // Allocate initial marking
    net->initial_marking = calloc(num_places, sizeof(int));
    ALLOC_CHECK_GOTO(net->initial_marking, num_places * sizeof(int), cleanup);
    
    // Allocate matrices
    net->i_minus = calloc(num_places, sizeof(int*));
    net->i_plus = calloc(num_places, sizeof(int*));
    ALLOC_CHECK_GOTO(net->i_minus, num_places * sizeof(int*), cleanup);
    ALLOC_CHECK_GOTO(net->i_plus, num_places * sizeof(int*), cleanup);
    
    for (int p = 0; p < num_places; p++) {
        net->i_minus[p] = calloc(num_transitions, sizeof(int));
        net->i_plus[p] = calloc(num_transitions, sizeof(int));
        ALLOC_CHECK_GOTO(net->i_minus[p], num_transitions * sizeof(int), cleanup);
        ALLOC_CHECK_GOTO(net->i_plus[p], num_transitions * sizeof(int), cleanup);
    }
    
    // Allocate subnets if needed
    if (num_subnets > 0) {
        net->subnets = calloc(num_subnets, sizeof(subnet_t));
        ALLOC_CHECK_GOTO(net->subnets, num_subnets * sizeof(subnet_t), cleanup);
    }
    
    // Allocate auxiliary structures
    net->transition_enabled_cache = calloc(num_transitions, sizeof(bool));
    net->enabled_transitions_buffer = calloc(num_transitions, sizeof(int));
    net->enabled_transitions_capacity = num_transitions;
    ALLOC_CHECK_GOTO(net->transition_enabled_cache, num_transitions * sizeof(bool), cleanup);
    ALLOC_CHECK_GOTO(net->enabled_transitions_buffer, num_transitions * sizeof(int), cleanup);
    
    return net;
    
cleanup:
    petri_net_destroy(net);
    return NULL;
}

void petri_net_destroy(petri_net_t *net) {
    if (!net) return;
    
    // Free initial marking
    SAFE_FREE(net->initial_marking);
    
    // Free matrices
    if (net->i_minus) {
        for (int p = 0; p < net->num_places; p++) {
            SAFE_FREE(net->i_minus[p]);
        }
        SAFE_FREE(net->i_minus);
    }
    
    if (net->i_plus) {
        for (int p = 0; p < net->num_places; p++) {
            SAFE_FREE(net->i_plus[p]);
        }
        SAFE_FREE(net->i_plus);
    }
    
    // Free subnets
    if (net->subnets) {
        for (int s = 0; s < net->num_subnets; s++) {
            subnet_destroy(&net->subnets[s]);
        }
        SAFE_FREE(net->subnets);
    }
    
    // Free auxiliary structures
    SAFE_FREE(net->transition_enabled_cache);
    SAFE_FREE(net->enabled_transitions_buffer);
    SAFE_FREE(net->source_file);
    
    SAFE_FREE(net);
}

petri_net_t* petri_net_from_json(const char *filename) {
    if (!filename) {
        LOG_ERROR("Filename is NULL");
        return NULL;
    }
    
    double start_time = get_time_seconds();
    
    // Read file
    FILE *file = fopen(filename, "r");
    if (!file) {
        LOG_ERROR("Cannot open file: %s", filename);
        return NULL;
    }
    
    fseek(file, 0, SEEK_END);
    long file_size = ftell(file);
    fseek(file, 0, SEEK_SET);
    
    char *json_string = malloc(file_size + 1);
    ALLOC_CHECK(json_string, file_size + 1);
    
    fread(json_string, 1, file_size, file);
    json_string[file_size] = '\0';
    fclose(file);
    
    // Parse JSON
    cJSON *json = cJSON_Parse(json_string);
    SAFE_FREE(json_string);
    
    if (!json) {
        LOG_ERROR("Invalid JSON format in file: %s", filename);
        return NULL;
    }
    
    petri_net_t *net = NULL;
    
    // Parse M0 (initial marking)
    cJSON *m0_array = cJSON_GetObjectItem(json, "M0");
    if (!cJSON_IsArray(m0_array)) {
        LOG_ERROR("M0 must be an array");
        goto cleanup;
    }
    
    int num_places = cJSON_GetArraySize(m0_array);
    
    // Parse I_minus to get number of transitions
    cJSON *i_minus_array = cJSON_GetObjectItem(json, "I_minus");
    if (!cJSON_IsArray(i_minus_array)) {
        LOG_ERROR("I_minus must be an array");
        goto cleanup;
    }
    
    cJSON *first_row = cJSON_GetArrayItem(i_minus_array, 0);
    if (!cJSON_IsArray(first_row)) {
        LOG_ERROR("I_minus rows must be arrays");
        goto cleanup;
    }
    
    int num_transitions = cJSON_GetArraySize(first_row);
    
    // Parse subnet definitions to get number of subnets
    cJSON *subnets_array = cJSON_GetObjectItem(json, "subnet_definitions");
    int num_subnets = cJSON_IsArray(subnets_array) ? cJSON_GetArraySize(subnets_array) : 0;
    
    // Create net
    net = petri_net_create(num_places, num_transitions, num_subnets);
    if (!net) {
        goto cleanup;
    }
    
    // Store source file name
    net->source_file = strdup(filename);
    
    // Fill initial marking
    for (int p = 0; p < num_places; p++) {
        cJSON *value = cJSON_GetArrayItem(m0_array, p);
        net->initial_marking[p] = cJSON_IsNumber(value) ? value->valueint : 0;
    }
    
    // Fill I_minus matrix
    for (int p = 0; p < num_places; p++) {
        cJSON *row = cJSON_GetArrayItem(i_minus_array, p);
        for (int t = 0; t < num_transitions; t++) {
            cJSON *value = cJSON_GetArrayItem(row, t);
            net->i_minus[p][t] = cJSON_IsNumber(value) ? value->valueint : 0;
        }
    }
    
    // Fill I_plus matrix
    cJSON *i_plus_array = cJSON_GetObjectItem(json, "I_plus");
    if (!cJSON_IsArray(i_plus_array)) {
        LOG_ERROR("I_plus must be an array");
        goto cleanup;
    }
    
    for (int p = 0; p < num_places; p++) {
        cJSON *row = cJSON_GetArrayItem(i_plus_array, p);
        for (int t = 0; t < num_transitions; t++) {
            cJSON *value = cJSON_GetArrayItem(row, t);
            net->i_plus[p][t] = cJSON_IsNumber(value) ? value->valueint : 0;
        }
    }
    
    // Fill subnets
    if (num_subnets > 0) {
        for (int s = 0; s < num_subnets; s++) {
            cJSON *subnet_obj = cJSON_GetArrayItem(subnets_array, s);
            
            cJSON *id_item = cJSON_GetObjectItem(subnet_obj, "id");
            int subnet_id = cJSON_IsNumber(id_item) ? id_item->valueint : s;
            
            // Parse place indices
            cJSON *place_indices_array = cJSON_GetObjectItem(subnet_obj, "place_indices");
            int subnet_num_places = cJSON_GetArraySize(place_indices_array);
            
            int *place_indices = malloc(subnet_num_places * sizeof(int));
            ALLOC_CHECK_GOTO(place_indices, subnet_num_places * sizeof(int), cleanup);
            
            for (int i = 0; i < subnet_num_places; i++) {
                cJSON *idx = cJSON_GetArrayItem(place_indices_array, i);
                place_indices[i] = cJSON_IsNumber(idx) ? idx->valueint : 0;
            }
            
            // Parse transition indices
            cJSON *trans_indices_array = cJSON_GetObjectItem(subnet_obj, "trans_indices");
            int subnet_num_transitions = cJSON_GetArraySize(trans_indices_array);
            
            int *trans_indices = malloc(subnet_num_transitions * sizeof(int));
            ALLOC_CHECK_GOTO(trans_indices, subnet_num_transitions * sizeof(int), cleanup);
            
            for (int i = 0; i < subnet_num_transitions; i++) {
                cJSON *idx = cJSON_GetArrayItem(trans_indices_array, i);
                trans_indices[i] = cJSON_IsNumber(idx) ? idx->valueint : 0;
            }
            
            // Create subnet
            subnet_t *subnet = subnet_create(subnet_id, net, place_indices, subnet_num_places,
                                           trans_indices, subnet_num_transitions);
            
            SAFE_FREE(place_indices);
            SAFE_FREE(trans_indices);
            
            if (subnet) {
                net->subnets[s] = *subnet;
                SAFE_FREE(subnet); // We copied the structure, free the wrapper
            }
        }
    }
    
    net->load_time = get_time_seconds() - start_time;
    
cleanup:
    cJSON_Delete(json);
    return net;
}

// ============================================================================
// FUNCIONES DE CONSULTA Y ANÁLISIS
// ============================================================================

bool petri_net_is_transition_enabled(const petri_net_t *net, int trans_idx, const int *marking) {
    if (!net || !marking || !VALID_TRANS_INDEX(net, trans_idx)) {
        return false;
    }
    
    FOR_EACH_PLACE(net, p) {
        int required = I_MINUS(net, p, trans_idx);
        if (required > 0) {
            if (marking[p] == OMEGA) {
                continue; // Omega always satisfies requirements
            }
            if (marking[p] < required) {
                return false;
            }
        }
    }
    
    return true;
}

const int* petri_net_get_enabled_transitions(const petri_net_t *net, const int *marking, int *count) {
    if (!net || !marking || !count) {
        if (count) *count = 0;
        return NULL;
    }
    
    *count = 0;
    
    FOR_EACH_TRANSITION(net, t) {
        if (petri_net_is_transition_enabled(net, t, marking)) {
            net->enabled_transitions_buffer[(*count)++] = t;
        }
    }
    
    return net->enabled_transitions_buffer;
}

bool petri_net_fire_transition(const petri_net_t *net, int trans_idx, const int *marking, int *new_marking) {
    if (!net || !marking || !new_marking || !VALID_TRANS_INDEX(net, trans_idx)) {
        return false;
    }
    
    // Check if transition is enabled
    if (!petri_net_is_transition_enabled(net, trans_idx, marking)) {
        return false;
    }
    
    // Fire transition
    FOR_EACH_PLACE(net, p) {
        if (marking[p] == OMEGA) {
            new_marking[p] = OMEGA; // Omega stays omega
        } else {
            int consumed = I_MINUS(net, p, trans_idx);
            int produced = I_PLUS(net, p, trans_idx);
            new_marking[p] = marking[p] - consumed + produced;
        }
    }
    
    return true;
}

// ============================================================================
// FUNCIONES DE SUBRED - IMPLEMENTACIÓN BÁSICA
// ============================================================================

subnet_t* subnet_create(int id, const petri_net_t *parent_net, 
                       const int *place_indices, int num_places,
                       const int *trans_indices, int num_transitions) {
    if (!parent_net || !place_indices || !trans_indices || num_places <= 0 || num_transitions <= 0) {
        return NULL;
    }
    
    subnet_t *subnet = calloc(1, sizeof(subnet_t));
    ALLOC_CHECK(subnet, sizeof(subnet_t));
    
    subnet->id = id;
    subnet->num_places = num_places;
    subnet->num_transitions = num_transitions;
    
    // Allocate and copy indices
    subnet->place_indices = malloc(num_places * sizeof(int));
    subnet->trans_indices = malloc(num_transitions * sizeof(int));
    ALLOC_CHECK_GOTO(subnet->place_indices, num_places * sizeof(int), cleanup);
    ALLOC_CHECK_GOTO(subnet->trans_indices, num_transitions * sizeof(int), cleanup);
    
    memcpy(subnet->place_indices, place_indices, num_places * sizeof(int));
    memcpy(subnet->trans_indices, trans_indices, num_transitions * sizeof(int));
    
    // Create reverse mappings
    subnet->global_to_local_place = malloc(parent_net->num_places * sizeof(int));
    subnet->global_to_local_trans = malloc(parent_net->num_transitions * sizeof(int));
    ALLOC_CHECK_GOTO(subnet->global_to_local_place, parent_net->num_places * sizeof(int), cleanup);
    ALLOC_CHECK_GOTO(subnet->global_to_local_trans, parent_net->num_transitions * sizeof(int), cleanup);
    
    // Initialize reverse mappings to -1
    for (int i = 0; i < parent_net->num_places; i++) {
        subnet->global_to_local_place[i] = -1;
    }
    for (int i = 0; i < parent_net->num_transitions; i++) {
        subnet->global_to_local_trans[i] = -1;
    }
    
    // Fill reverse mappings
    for (int i = 0; i < num_places; i++) {
        subnet->global_to_local_place[place_indices[i]] = i;
    }
    for (int i = 0; i < num_transitions; i++) {
        subnet->global_to_local_trans[trans_indices[i]] = i;
    }
    
    // Create local matrices
    subnet->i_minus = calloc(num_places, sizeof(int*));
    subnet->i_plus = calloc(num_places, sizeof(int*));
    ALLOC_CHECK_GOTO(subnet->i_minus, num_places * sizeof(int*), cleanup);
    ALLOC_CHECK_GOTO(subnet->i_plus, num_places * sizeof(int*), cleanup);
    
    for (int lp = 0; lp < num_places; lp++) {
        subnet->i_minus[lp] = calloc(num_transitions, sizeof(int));
        subnet->i_plus[lp] = calloc(num_transitions, sizeof(int));
        ALLOC_CHECK_GOTO(subnet->i_minus[lp], num_transitions * sizeof(int), cleanup);
        ALLOC_CHECK_GOTO(subnet->i_plus[lp], num_transitions * sizeof(int), cleanup);
        
        int gp = place_indices[lp];
        for (int lt = 0; lt < num_transitions; lt++) {
            int gt = trans_indices[lt];
            subnet->i_minus[lp][lt] = parent_net->i_minus[gp][gt];
            subnet->i_plus[lp][lt] = parent_net->i_plus[gp][gt];
        }
    }
    
    // Allocate marking cache
    subnet->marking_cache = calloc(num_places, sizeof(int));
    ALLOC_CHECK_GOTO(subnet->marking_cache, num_places * sizeof(int), cleanup);
    
    return subnet;
    
cleanup:
    subnet_destroy(subnet);
    return NULL;
}

void subnet_destroy(subnet_t *subnet) {
    if (!subnet) return;
    
    SAFE_FREE(subnet->place_indices);
    SAFE_FREE(subnet->trans_indices);
    SAFE_FREE(subnet->global_to_local_place);
    SAFE_FREE(subnet->global_to_local_trans);
    SAFE_FREE(subnet->marking_cache);
    
    if (subnet->i_minus) {
        for (int p = 0; p < subnet->num_places; p++) {
            SAFE_FREE(subnet->i_minus[p]);
        }
        SAFE_FREE(subnet->i_minus);
    }
    
    if (subnet->i_plus) {
        for (int p = 0; p < subnet->num_places; p++) {
            SAFE_FREE(subnet->i_plus[p]);
        }
        SAFE_FREE(subnet->i_plus);
    }
}

bool subnet_contains_transition(const subnet_t *subnet, int global_trans_idx) {
    if (!subnet || global_trans_idx < 0) return false;
    
    for (int i = 0; i < subnet->num_transitions; i++) {
        if (subnet->trans_indices[i] == global_trans_idx) {
            return true;
        }
    }
    return false;
}

int subnet_get_local_trans_index(const subnet_t *subnet, int global_trans_idx) {
    if (!subnet || global_trans_idx < 0) return -1;
    
    // Use reverse mapping if available
    if (subnet->global_to_local_trans) {
        return subnet->global_to_local_trans[global_trans_idx];
    }
    
    // Fallback to linear search
    for (int i = 0; i < subnet->num_transitions; i++) {
        if (subnet->trans_indices[i] == global_trans_idx) {
            return i;
        }
    }
    return -1;
}

const int* subnet_extract_marking(subnet_t *subnet, const int *global_marking) {
    if (!subnet || !global_marking) return NULL;
    
    for (int i = 0; i < subnet->num_places; i++) {
        subnet->marking_cache[i] = global_marking[subnet->place_indices[i]];
    }
    
    return subnet->marking_cache;
}

bool subnet_fire_transition(const subnet_t *subnet, int local_trans_idx, int *subnet_marking) {
    if (!subnet || !subnet_marking || local_trans_idx < 0 || local_trans_idx >= subnet->num_transitions) {
        return false;
    }
    
    // Check if transition is enabled
    for (int p = 0; p < subnet->num_places; p++) {
        int required = subnet->i_minus[p][local_trans_idx];
        if (required > 0 && subnet_marking[p] != OMEGA && subnet_marking[p] < required) {
            return false;
        }
    }
    
    // Fire transition
    for (int p = 0; p < subnet->num_places; p++) {
        if (subnet_marking[p] != OMEGA) {
            int consumed = subnet->i_minus[p][local_trans_idx];
            int produced = subnet->i_plus[p][local_trans_idx];
            subnet_marking[p] = subnet_marking[p] - consumed + produced;
        }
    }
    
    return true;
}

// ============================================================================
// FUNCIONES UTILITARIAS
// ============================================================================

char* marking_to_string(const int *marking, int size) {
    if (!marking || size <= 0) return NULL;
    
    size_t buffer_size = size * 12 + 10; // Enough for numbers and brackets
    char *buffer = malloc(buffer_size);
    ALLOC_CHECK(buffer, buffer_size);
    
    strcpy(buffer, "[");
    for (int i = 0; i < size; i++) {
        if (i > 0) strcat(buffer, ", ");
        if (marking[i] == OMEGA) {
            strcat(buffer, "ω");
        } else {
            char num_str[12];
            snprintf(num_str, sizeof(num_str), "%d", marking[i]);
            strcat(buffer, num_str);
        }
    }
    strcat(buffer, "]");
    
    return buffer;
}

int marking_compare(const int *m1, const int *m2, int size) {
    if (!m1 || !m2) return 0;
    
    for (int i = 0; i < size; i++) {
        if (m1[i] < m2[i]) return -1;
        if (m1[i] > m2[i]) return 1;
        // Note: OMEGA == OMEGA, but this is a special case handled elsewhere
    }
    return 0;
}

bool marking_greater_equal(const int *m1, const int *m2, int size) {
    if (!m1 || !m2) return false;
    
    for (int i = 0; i < size; i++) {
        if (m1[i] == OMEGA || m2[i] == OMEGA) {
            continue; // Omega is always >= any value
        }
        if (m1[i] < m2[i]) {
            return false;
        }
    }
    return true;
}

void petri_net_print_info(const petri_net_t *net) {
    if (!net) return;
    
    printf("\n=== INFORMACIÓN DE LA RED DE PETRI ===\n");
    printf("Dimensiones:\n");
    printf("  - Lugares: %d\n", net->num_places);
    printf("  - Transiciones: %d\n", net->num_transitions);
    printf("  - Subredes: %d\n", net->num_subnets);
    
    if (net->source_file) {
        printf("Archivo fuente: %s\n", net->source_file);
        printf("Tiempo de carga: %.3f ms\n", net->load_time * 1000.0);
    }
    
    char *initial_str = marking_to_string(net->initial_marking, net->num_places);
    if (initial_str) {
        printf("Marcado inicial: %s\n", initial_str);
        SAFE_FREE(initial_str);
    }
    
    printf("Subredes:\n");
    for (int s = 0; s < net->num_subnets; s++) {
        subnet_t *subnet = &net->subnets[s];
        printf("  - Subred %d: %d lugares, %d transiciones\n", 
               subnet->id, subnet->num_places, subnet->num_transitions);
    }
}

size_t petri_net_memory_size(const petri_net_t *net) {
    if (!net) return 0;
    
    size_t size = sizeof(petri_net_t);
    size += net->num_places * sizeof(int); // initial_marking
    size += net->num_places * sizeof(int*) * 2; // matrix pointers
    size += net->num_places * net->num_transitions * sizeof(int) * 2; // matrix data
    size += net->num_subnets * sizeof(subnet_t); // subnets
    
    // Subnet memory
    for (int s = 0; s < net->num_subnets; s++) {
        subnet_t *subnet = &net->subnets[s];
        size += subnet->num_places * sizeof(int) * 3; // indices and cache
        size += subnet->num_transitions * sizeof(int);
        size += subnet->num_places * subnet->num_transitions * sizeof(int) * 2; // local matrices
    }
    
    return size;
}

petri_error_t petri_net_validate(const petri_net_t *net) {
    if (!net) {
        LOG_ERROR("Net is NULL");
        return PETRI_ERROR_INVALID_INPUT;
    }
    
    if (net->num_places <= 0 || net->num_transitions <= 0) {
        LOG_ERROR("Invalid dimensions: places=%d, transitions=%d", 
                  net->num_places, net->num_transitions);
        return PETRI_ERROR_INVALID_INPUT;
    }
    
    if (!net->initial_marking || !net->i_minus || !net->i_plus) {
        LOG_ERROR("Missing required data structures");
        return PETRI_ERROR_INVALID_INPUT;
    }
    
    // Validate initial marking
    for (int p = 0; p < net->num_places; p++) {
        if (net->initial_marking[p] < 0 && net->initial_marking[p] != OMEGA) {
            LOG_ERROR("Invalid initial marking at place %d: %d", p, net->initial_marking[p]);
            return PETRI_ERROR_INVALID_INPUT;
        }
    }
    
    // Validate matrices
    for (int p = 0; p < net->num_places; p++) {
        if (!net->i_minus[p] || !net->i_plus[p]) {
            LOG_ERROR("Missing matrix row at place %d", p);
            return PETRI_ERROR_INVALID_INPUT;
        }
        
        for (int t = 0; t < net->num_transitions; t++) {
            if (net->i_minus[p][t] < 0 || net->i_plus[p][t] < 0) {
                LOG_ERROR("Negative matrix value at [%d][%d]: minus=%d, plus=%d", 
                          p, t, net->i_minus[p][t], net->i_plus[p][t]);
                return PETRI_ERROR_INVALID_INPUT;
            }
        }
    }
    
    // Validate subnets
    for (int s = 0; s < net->num_subnets; s++) {
        subnet_t *subnet = &net->subnets[s];
        
        if (subnet->num_places <= 0 || subnet->num_transitions <= 0) {
            LOG_ERROR("Invalid subnet %d dimensions: places=%d, transitions=%d", 
                      s, subnet->num_places, subnet->num_transitions);
            return PETRI_ERROR_INVALID_INPUT;
        }
        
        // Validate subnet indices
        for (int i = 0; i < subnet->num_places; i++) {
            if (!VALID_PLACE_INDEX(net, subnet->place_indices[i])) {
                LOG_ERROR("Invalid place index in subnet %d: %d", s, subnet->place_indices[i]);
                return PETRI_ERROR_INVALID_INPUT;
            }
        }
        
        for (int i = 0; i < subnet->num_transitions; i++) {
            if (!VALID_TRANS_INDEX(net, subnet->trans_indices[i])) {
                LOG_ERROR("Invalid transition index in subnet %d: %d", s, subnet->trans_indices[i]);
                return PETRI_ERROR_INVALID_INPUT;
            }
        }
    }
    
    LOG_DEBUG("Petri net validation successful");
    return PETRI_SUCCESS;
}

// ============================================================================
// FUNCIONES DE DISPARO CON SEMÁNTICA OMEGA
// ============================================================================

/**
 * Verifica si algún pre-lugar de una transición tiene marca omega.
 */
static inline bool has_omega_pre(const petri_net_t *net, int t, const int *m) {
    for (int p = 0; p < net->num_places; p++) {
        int w = net->i_minus[p][t];
        if (w > 0 && m[p] == OMEGA) return true;
    }
    return false;
}

bool petri_net_fire_transition_omega(const petri_net_t *net,
                                     int t,
                                     const int *m,
                                     int *out) {
    if (!net || !m || !out) return false;
    if (!VALID_TRANS_INDEX(net, t)) return false;

    // Copia base del marcado de entrada
    for (int p = 0; p < net->num_places; p++) {
        out[p] = m[p];
    }

    // Verificar si la transición está habilitada bajo semántica ω
    // (cualquier pre-lugar ω cuenta como "suficiente", el resto debe tener m[p] >= w)
    for (int p = 0; p < net->num_places; p++) {
        int w = net->i_minus[p][t];
        if (w == 0) continue;
        if (m[p] == OMEGA) continue;  // Omega siempre es suficiente
        if (m[p] < w) return false;   // No hay suficientes tokens
    }

    const bool pre_has_omega = has_omega_pre(net, t, m);

    // Fase 1: Consumir tokens (∞ - w = ∞, finito - w = finito - w)
    for (int p = 0; p < net->num_places; p++) {
        int w = net->i_minus[p][t];
        if (w == 0) continue;
        
        if (m[p] == OMEGA) {
            out[p] = OMEGA;  // ∞ - w = ∞
        } else {
            out[p] = m[p] - w;  // finito - w
        }
    }

    // Fase 2: Producir tokens 
    // Si algún pre-lugar es ω -> todos los post con w>0 pasan a ω
    for (int p = 0; p < net->num_places; p++) {
        int w = net->i_plus[p][t];
        if (w == 0) continue;

        if (out[p] == OMEGA) {
            // Ya es ω, se queda ω
            continue;
        }
        
        if (pre_has_omega) {
            // Si hay omega en pre-lugares, propagar omega a post-lugares
            out[p] = OMEGA;
        } else {
            // Sumar normalmente: finito + w
            out[p] = out[p] + w;
        }
    }

    // Fase 3: Cargar ω a los lugares no afectados que ya eran ω en la entrada
    // (esto asegura que ω se preserva en lugares que no participan en la transición)
    for (int p = 0; p < net->num_places; p++) {
        if (m[p] == OMEGA) {
            out[p] = OMEGA;
        }
    }

    return true;
}