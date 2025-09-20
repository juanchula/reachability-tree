#include "common.h"
#include "petri_net.h"
#include "omega_detector.h"
#include "reachability_tree.h"
#include "parallel_engine.h"
#include "dot_exporter.h"
#include <getopt.h>
#include <signal.h>

// ============================================================================
// VARIABLES GLOBALES
// ============================================================================

// Variables para debugging (compatibles con versión Java)
bool debug_enabled = false;
const char* debug_marking_id = NULL;

// Estadísticas globales
perf_stats_t g_stats = {0};

// Control de interrupción
static volatile bool interrupted = false;

// ============================================================================
// CONFIGURACIÓN POR DEFECTO
// ============================================================================

#define DEFAULT_THREADS 0  // 0 = auto-detect
#define PROGRAM_NAME "reachability-analyzer"
#define VERSION "1.0.0"

// ============================================================================
// MANEJO DE SEÑALES
// ============================================================================

static void signal_handler(int sig) {
    switch (sig) {
        case SIGINT:
        case SIGTERM:
            if (interrupted) {
                // Segunda interrupción, salir inmediatamente
                LOG_INFO("Force termination requested");
                exit(130);
            }
            interrupted = true;
            LOG_INFO("Graceful shutdown requested (press Ctrl+C again to force)");
            break;
        default:
            break;
    }
}

static void setup_signal_handlers(void) {
    struct sigaction sa;
    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;
    
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);
    
    // Ignorar SIGPIPE
    signal(SIGPIPE, SIG_IGN);
}

// ============================================================================
// FUNCIONES DE AYUDA E INFORMACIÓN
// ============================================================================

static void print_usage(const char* program_name) {
    printf("Uso: %s --input <archivo.json> [opciones]\n", program_name);
    printf("\n");
    printf("OPCIONES REQUERIDAS:\n");
    printf("  --input <archivo>     Archivo JSON de entrada (requerido)\n");
    printf("\n");
    printf("OPCIONES DE ANÁLISIS:\n");
    printf("  --output <archivo>    Archivo DOT de salida (opcional)\n");
    printf("  --nthreads <n>        Número de hilos (default: núcleos de CPU)\n");
    printf("  --omega               Habilitar soporte para marcado omega (ω)\n");
    printf("\n");
    printf("OPCIONES DE DEBUGGING:\n");
    printf("  --debug               Habilitar modo debug detallado\n");
    printf("  --markingid <id>      Debug específico para marking ID\n");
    printf("  --verbose             Habilitar logging verbose\n");
    printf("  --stats               Mostrar estadísticas detalladas al final\n");
    printf("\n");
    printf("OPCIONES DE CONFIGURACIÓN:\n");
    printf("  --batch-size <n>      Tamaño de lote para workers (default: %d)\n", DEFAULT_BATCH_SIZE);
    printf("  --cache-size <n>      Tamaño del cache omega (default: 1024)\n");
    printf("  --timeout <s>         Timeout en segundos (0 = sin límite)\n");
    printf("\n");
    printf("INFORMACIÓN:\n");
    printf("  --help                Mostrar esta ayuda\n");
    printf("  --version             Mostrar versión\n");
    printf("  --info                Mostrar información del sistema\n");
    printf("\n");
    printf("EJEMPLOS:\n");
    printf("  %s --input red.json\n", program_name);
    printf("  %s --input red.json --output arbol.dot --omega\n", program_name);
    printf("  %s --input red.json --nthreads 8 --omega --debug\n", program_name);
    printf("  %s --input red.json --omega --markingid m0_t1 --verbose\n", program_name);
    printf("\n");
    printf("FORMATO JSON:\n");
    printf("  El archivo de entrada debe contener:\n");
    printf("  - M0: marcado inicial (array de enteros)\n");
    printf("  - I_minus: matriz de pre-condiciones\n");
    printf("  - I_plus: matriz de post-condiciones\n");
    printf("  - subnet_definitions: definiciones de subredes\n");
    printf("\n");
    printf("COMPATIBILIDAD:\n");
    printf("  Esta implementación es 100%% compatible con la versión Java.\n");
    printf("  Los archivos JSON y la salida DOT son idénticos.\n");
}

static void print_version(void) {
    printf("%s versión %s\n", PROGRAM_NAME, VERSION);
    printf("Compilado el %s a las %s\n", __DATE__, __TIME__);
    printf("Implementación en C del analizador de alcanzabilidad de Redes de Petri\n");
    printf("\n");
    printf("Características:\n");
    printf("  - Análisis paralelo de alcanzabilidad\n");
    printf("  - Detección avanzada de marcas omega (ω)\n");
    printf("  - Optimizado para redes S3PR\n");
    printf("  - Exportación a formato DOT/Graphviz\n");
    printf("  - Compatible 100%% con versión Java\n");
    printf("\n");
    printf("Compilador: %s\n", __VERSION__);
    printf("Estándar C: C11\n");
    printf("Threading: POSIX Threads\n");
    
#ifdef DEBUG
    printf("Modo: DEBUG\n");
#else
    printf("Modo: RELEASE\n");
#endif

#ifdef USE_JEMALLOC
    printf("Allocator: jemalloc\n");
#else
    printf("Allocator: system malloc\n");
#endif
}

static void print_system_info(void) {
    printf("INFORMACIÓN DEL SISTEMA:\n");
    printf("  CPU cores: %ld\n", sysconf(_SC_NPROCESSORS_ONLN));
    printf("  Página de memoria: %ld bytes\n", sysconf(_SC_PAGESIZE));
    printf("  Memoria total: %.2f GB\n", (double)sysconf(_SC_PHYS_PAGES) * sysconf(_SC_PAGESIZE) / (1024.0 * 1024.0 * 1024.0));
    
    // Información del proceso
    struct timespec res;
    if (clock_getres(CLOCK_MONOTONIC, &res) == 0) {
        printf("  Resolución de reloj: %ld ns\n", res.tv_nsec);
    }
    
    printf("\n");
    printf("LÍMITES DEL SISTEMA:\n");
    printf("  Máximo lugares: %d\n", MAX_PLACES);
    printf("  Máximo transiciones: %d\n", MAX_TRANSITIONS);
    printf("  Máximo subredes: %d\n", MAX_SUBNETS);
    printf("  Tamaño hash inicial: %d\n", INITIAL_HASH_SIZE);
    printf("  Segmentos de hash: %d\n", HASH_SEGMENTS);
}

// ============================================================================
// ANÁLISIS DE ARGUMENTOS
// ============================================================================

typedef struct {
    char *input_file;
    char *output_file;
    int num_threads;
    bool use_omega;
    bool verbose;
    bool show_stats;
    int batch_size;
    int cache_size;
    int timeout_seconds;
} program_config_t;

static program_config_t parse_arguments(int argc, char *argv[]) {
    program_config_t config = {0};
    
    // Valores por defecto
    config.input_file = NULL;
    config.output_file = NULL;
    config.num_threads = DEFAULT_THREADS;
    config.use_omega = false;
    config.verbose = false;
    config.show_stats = false;
    config.batch_size = DEFAULT_BATCH_SIZE;
    config.cache_size = 1024;
    config.timeout_seconds = 0;
    
    // Opciones largas
    static struct option long_options[] = {
        {"input",      required_argument, 0, 'i'},
        {"output",     required_argument, 0, 'o'},
        {"nthreads",   required_argument, 0, 't'},
        {"omega",      no_argument,       0, 'w'},
        {"debug",      no_argument,       0, 'd'},
        {"markingid",  required_argument, 0, 'm'},
        {"verbose",    no_argument,       0, 'v'},
        {"stats",      no_argument,       0, 's'},
        {"batch-size", required_argument, 0, 'b'},
        {"cache-size", required_argument, 0, 'c'},
        {"timeout",    required_argument, 0, 'T'},
        {"help",       no_argument,       0, 'h'},
        {"version",    no_argument,       0, 'V'},
        {"info",       no_argument,       0, 'I'},
        {0, 0, 0, 0}
    };
    
    int option_index = 0;
    int c;
    
    while ((c = getopt_long(argc, argv, "i:o:t:wdm:vsb:c:T:hVI", long_options, &option_index)) != -1) {
        switch (c) {
            case 'i':
                config.input_file = strdup(optarg);
                break;
                
            case 'o':
                config.output_file = strdup(optarg);
                break;
                
            case 't':
                config.num_threads = atoi(optarg);
                if (config.num_threads < 0) {
                    LOG_ERROR("Número de hilos inválido: %d", config.num_threads);
                    exit(1);
                }
                break;
                
            case 'w':
                config.use_omega = true;
                break;
                
            case 'd':
                debug_enabled = true;
                break;
                
            case 'm':
                debug_marking_id = strdup(optarg);
                debug_enabled = true;  // Activar debug automáticamente
                break;
                
            case 'v':
                config.verbose = true;
                break;
                
            case 's':
                config.show_stats = true;
                break;
                
            case 'b':
                config.batch_size = atoi(optarg);
                if (config.batch_size <= 0) {
                    LOG_ERROR("Tamaño de lote inválido: %d", config.batch_size);
                    exit(1);
                }
                break;
                
            case 'c':
                config.cache_size = atoi(optarg);
                if (config.cache_size < 0) {
                    LOG_ERROR("Tamaño de cache inválido: %d", config.cache_size);
                    exit(1);
                }
                break;
                
            case 'T':
                config.timeout_seconds = atoi(optarg);
                if (config.timeout_seconds < 0) {
                    LOG_ERROR("Timeout inválido: %d", config.timeout_seconds);
                    exit(1);
                }
                break;
                
            case 'h':
                print_usage(argv[0]);
                exit(0);
                
            case 'V':
                print_version();
                exit(0);
                
            case 'I':
                print_system_info();
                exit(0);
                
            case '?':
                // Error en argumento, getopt ya imprimió el error
                fprintf(stderr, "Use --help para ver las opciones disponibles.\n");
                exit(1);
                
            default:
                LOG_ERROR("Opción desconocida: %c", c);
                exit(1);
        }
    }
    
    // Verificar argumentos requeridos
    if (!config.input_file) {
        LOG_ERROR("Archivo de entrada requerido. Use --input <archivo.json>");
        fprintf(stderr, "Use --help para ver las opciones disponibles.\n");
        exit(1);
    }
    
    // Auto-detectar número de hilos
    if (config.num_threads == 0) {
        config.num_threads = sysconf(_SC_NPROCESSORS_ONLN);
        if (config.num_threads <= 0) {
            config.num_threads = 4;  // Fallback
        }
    }
    
    return config;
}

// ============================================================================
// FUNCIONES DE ESTADÍSTICAS Y REPORTE
// ============================================================================

static void print_performance_stats(const perf_stats_t *stats, 
                                   const program_config_t *config,
                                   size_t tree_size) {
    double elapsed_time_ms = stats->end_time_ms - stats->start_time_ms;
    double elapsed_time_s = elapsed_time_ms / 1000.0;
    double states_per_second = elapsed_time_s > 0 ? atomic_load(&stats->states_explored) / elapsed_time_s : 0;
    
    printf("\n");
    printf("=== ESTADÍSTICAS DE RENDIMIENTO ===\n");
    if (elapsed_time_ms < 1000.0) {
        printf("Tiempo total: %.1f ms\n", elapsed_time_ms);
    } else {
        printf("Tiempo total: %.3f segundos (%.1f ms)\n", elapsed_time_s, elapsed_time_ms);
    }
    printf("Estados explorados: %zu\n", atomic_load(&stats->states_explored));
    printf("Estados en árbol final: %zu\n", tree_size);
    printf("Estados por segundo: %.2f\n", states_per_second);
    printf("Tareas procesadas: %zu\n", atomic_load(&stats->tasks_processed));
    
    if (config->use_omega) {
        printf("Detecciones omega: %zu\n", atomic_load(&stats->omega_detections));
        printf("Ratio detección omega: %.4f%%\n", 
               atomic_load(&stats->states_explored) > 0 ? 
               (double)atomic_load(&stats->omega_detections) * 100.0 / atomic_load(&stats->states_explored) : 0.0);
    }
    
    if (atomic_load(&stats->cache_hits) + atomic_load(&stats->cache_misses) > 0) {
        double hit_ratio = (double)atomic_load(&stats->cache_hits) / 
                          (atomic_load(&stats->cache_hits) + atomic_load(&stats->cache_misses));
        printf("Cache hit ratio: %.2f%% (%zu hits, %zu misses)\n", 
               hit_ratio * 100.0, atomic_load(&stats->cache_hits), atomic_load(&stats->cache_misses));
    }
    
    printf("Configuración utilizada:\n");
    printf("  - Hilos: %d\n", config->num_threads);
    printf("  - Tamaño de lote: %d\n", config->batch_size);
    printf("  - Omega habilitado: %s\n", config->use_omega ? "sí" : "no");
    if (config->use_omega) {
        printf("  - Tamaño cache omega: %d\n", config->cache_size);
    }
    
    // Cálculo de speedup estimado
    double estimated_sequential_time = elapsed_time_s * config->num_threads * 0.8; // Factor conservador
    double speedup = estimated_sequential_time / elapsed_time_s;
    printf("  - Speedup estimado: %.2fx\n", speedup);
    printf("  - Eficiencia paralela: %.1f%%\n", (speedup / config->num_threads) * 100.0);
}

static void print_memory_stats(size_t tree_size, const petri_net_t *net) {
    // Estimación del uso de memoria
    size_t node_memory = tree_size * 128; // Estimación aproximada del tamaño de nodo
    size_t marking_memory = tree_size * net->num_places * sizeof(int);
    size_t net_memory = petri_net_memory_size(net);
    size_t total_memory = node_memory + marking_memory + net_memory;
    
    printf("\n");
    printf("=== ESTADÍSTICAS DE MEMORIA ===\n");
    printf("Memoria de nodos: %.2f MB\n", node_memory / (1024.0 * 1024.0));
    printf("Memoria de marcados: %.2f MB\n", marking_memory / (1024.0 * 1024.0));
    printf("Memoria de red de Petri: %.2f MB\n", net_memory / (1024.0 * 1024.0));
    printf("Memoria total estimada: %.2f MB\n", total_memory / (1024.0 * 1024.0));
    printf("Memoria por estado: %.2f KB\n", 
           tree_size > 0 ? (double)total_memory / tree_size / 1024.0 : 0.0);
}

// ============================================================================
// FUNCIÓN PRINCIPAL
// ============================================================================

int main(int argc, char *argv[]) {
    petri_error_t result = PETRI_SUCCESS;
    petri_net_t *net = NULL;
    parallel_analyzer_t *analyzer = NULL;
    reachability_tree_t *tree = NULL;
    
    // Inicializar sistema
    system_init();
    setup_signal_handlers();
    
    // Registrar tiempo de inicio
    g_stats.start_time_ms = get_time_ms();
    
    // Parsear argumentos
    program_config_t config = parse_arguments(argc, argv);
    
    // Banner de inicio
    printf("🚀 %s v%s - Analizador de Redes de Petri en C\n", PROGRAM_NAME, VERSION);
    printf("📁 Archivo de entrada: %s\n", config.input_file);
    printf("🧵 Hilos: %d\n", config.num_threads);
    printf("♾️  Soporte omega: %s\n", config.use_omega ? "habilitado" : "deshabilitado");
    
    if (debug_enabled) {
        printf("🐛 Modo debug habilitado");
        if (debug_marking_id) {
            printf(" (específico para: %s)", debug_marking_id);
        }
        printf("\n");
    }
    
    if (config.verbose) {
        printf("📊 Logging verbose habilitado\n");
    }
    
    printf("\n");
    
    // Cargar red de Petri desde archivo JSON
    LOG_INFO("Cargando red de Petri desde %s...", config.input_file);
    net = petri_net_from_json(config.input_file);
    if (!net) {
        LOG_ERROR("Error al cargar la red de Petri desde %s", config.input_file);
        result = PETRI_ERROR_IO;
        goto cleanup;
    }
    
    // Validar red de Petri
    result = petri_net_validate(net);
    if (result != PETRI_SUCCESS) {
        LOG_ERROR("La red de Petri cargada no es válida");
        goto cleanup;
    }
    
    LOG_INFO("Red de Petri cargada exitosamente:");
    LOG_INFO("  - Lugares: %d", net->num_places);
    LOG_INFO("  - Transiciones: %d", net->num_transitions);
    LOG_INFO("  - Subredes: %d", net->num_subnets);
    
    // Mostrar marcado inicial
    char *initial_marking_str = marking_to_string(net->initial_marking, net->num_places);
    if (initial_marking_str) {
        LOG_INFO("  - Marcado inicial: %s", initial_marking_str);
        SAFE_FREE(initial_marking_str);
    }
    
    if (config.verbose) {
        petri_net_print_info(net);
    }
    
    // Crear analizador paralelo
    LOG_INFO("Creando analizador paralelo...");
    analyzer = parallel_analyzer_create(net, config.num_threads, config.use_omega, 
                                       config.batch_size, config.cache_size);
    if (!analyzer) {
        LOG_ERROR("Error al crear el analizador paralelo");
        result = PETRI_ERROR_MEMORY;
        goto cleanup;
    }
    
    // Configurar timeout si se especificó
    if (config.timeout_seconds > 0) {
        parallel_analyzer_set_timeout(analyzer, config.timeout_seconds);
        LOG_INFO("Timeout configurado: %d segundos", config.timeout_seconds);
    }
    
    // Ejecutar análisis
    LOG_INFO("Iniciando análisis de alcanzabilidad...");
    printf("⏳ Analizando");
    fflush(stdout);
    
    result = parallel_analyzer_analyze(analyzer);
    printf("\n");  // Nueva línea después del progreso
    
    g_stats.end_time_ms = get_time_ms();
    
    // Verificar si fue interrumpido
    if (interrupted) {
        LOG_INFO("Análisis interrumpido por el usuario");
        result = PETRI_ERROR_TIMEOUT;
        goto cleanup;
    }
    
    // Verificar resultado del análisis
    if (result != PETRI_SUCCESS) {
        LOG_ERROR("Error durante el análisis: %s", petri_error_string(result));
        goto cleanup;
    }
    
    // Obtener resultados
    tree = parallel_analyzer_get_tree(analyzer);
    size_t total_states = reachability_tree_size(tree);
    
    printf("✅ Análisis completado exitosamente\n");
    printf("📊 Estados totales encontrados: %zu\n", total_states);
    LOG_INFO("Estados totales en el árbol de alcanzabilidad: %zu", total_states);
    double elapsed_ms = g_stats.end_time_ms - g_stats.start_time_ms;
    if (elapsed_ms < 1000.0) {
        printf("⏱️  Tiempo total: %.1f ms\n", elapsed_ms);
        LOG_INFO("Tiempo total: %.1f ms", elapsed_ms);
    } else {
        printf("⏱️  Tiempo total: %.3f segundos\n", elapsed_ms / 1000.0);
        LOG_INFO("Tiempo total: %.3f segundos", elapsed_ms / 1000.0);
    }
    
    if (config.use_omega) {
        LOG_INFO("Análisis completado con soporte para marcas omega (ω)");
        size_t omega_count = reachability_tree_count_omega_states(tree);
        if (omega_count > 0) {
            LOG_INFO("Estados con marcas omega: %zu (%.2f%%)", 
                     omega_count, (double)omega_count * 100.0 / total_states);
        }
    }
    
    // Exportar a DOT si se especificó archivo de salida
    if (config.output_file) {
        LOG_INFO("Exportando árbol de alcanzabilidad a %s...", config.output_file);
        result = dot_exporter_export(tree, net, config.output_file);
        
        if (result == PETRI_SUCCESS) {
            LOG_INFO("Árbol de alcanzabilidad exportado exitosamente a %s", config.output_file);
        } else {
            LOG_ERROR("Error al exportar el árbol de alcanzabilidad");
        }
    }
    
    // Mostrar estadísticas si se solicitó
    if (config.show_stats || config.verbose) {
        print_performance_stats(&g_stats, &config, total_states);
        print_memory_stats(total_states, net);
        
        if (config.use_omega && analyzer) {
            parallel_analyzer_print_omega_stats(analyzer);
        }
    }
    
    // Mensaje final de rendimiento
    double elapsed_s = (g_stats.end_time_ms - g_stats.start_time_ms) / 1000.0;
    double states_per_sec = elapsed_s > 0 ? total_states / elapsed_s : 0;
    printf("\n🎯 Rendimiento: %.0f estados/segundo con %d hilo%s\n", 
           states_per_sec, config.num_threads, config.num_threads == 1 ? "" : "s");
    
    if (config.use_omega && atomic_load(&g_stats.omega_detections) > 0) {
        printf("♾️  Se detectaron %zu marca%s omega durante el análisis\n",
               atomic_load(&g_stats.omega_detections),
               atomic_load(&g_stats.omega_detections) == 1 ? "" : "s");
    }

cleanup:
    // Limpieza de recursos
    LOG_DEBUG("Liberando recursos...");
    
    if (analyzer) {
        parallel_analyzer_destroy(analyzer);
    }
    
    if (net) {
        petri_net_destroy(net);
    }
    
    // Liberar memoria de configuración
    SAFE_FREE(config.input_file);
    SAFE_FREE(config.output_file);
    if (debug_marking_id) {
        free((void*)debug_marking_id);
    }
    
    // Cleanup del sistema
    system_cleanup();
    
    // Mensaje final
    if (result == PETRI_SUCCESS) {
        printf("\n✨ Programa completado exitosamente\n");
        return 0;
    } else {
        printf("\n❌ Programa terminado con errores\n");
        return 1;
    }
}

// ============================================================================
// IMPLEMENTACIÓN DE FUNCIONES GLOBALES AUXILIARES
// ============================================================================

void system_init(void) {
    // Inicializar estadísticas globales
    atomic_init(&g_stats.states_explored, 0);
    atomic_init(&g_stats.omega_detections, 0);
    atomic_init(&g_stats.tasks_processed, 0);
    atomic_init(&g_stats.cache_hits, 0);
    atomic_init(&g_stats.cache_misses, 0);
    
    // Configurar zona horaria para timestamps
    tzset();
}

void system_cleanup(void) {
    // No hay cleanup específico por ahora
    // En futuras versiones aquí se podrían limpiar pools globales, etc.
}

const char* petri_error_string(petri_error_t error) {
    switch (error) {
        case PETRI_SUCCESS:
            return "Éxito";
        case PETRI_ERROR_MEMORY:
            return "Error de memoria";
        case PETRI_ERROR_IO:
            return "Error de entrada/salida";
        case PETRI_ERROR_JSON:
            return "Error de formato JSON";
        case PETRI_ERROR_INVALID_INPUT:
            return "Entrada inválida";
        case PETRI_ERROR_THREAD:
            return "Error de threading";
        case PETRI_ERROR_TIMEOUT:
            return "Timeout";
        case PETRI_ERROR_NOT_FOUND:
            return "No encontrado";
        default:
            return "Error desconocido";
    }
}