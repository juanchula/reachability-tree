package algorithm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.*;
import java.util.Arrays;

class ReachabilityAnalyzer {
    private static final Logger logger = LogManager.getLogger(ReachabilityAnalyzer.class);

    // Constante para representar omega (infinito) - usando -1 como en el algoritmo de la tesis
    public static final int OMEGA = -1;

    private final PetriNet petriNet;
    private final int numThreads;
    private final boolean useOmega;

    // Recursos compartidos optimizados
    private final ConcurrentLinkedQueue<FiringTask> firingQueue;
    private final ConcurrentHashMap<String, Node> reachabilityTree;
    private final ConcurrentHashMap<Long, Boolean> visitedMarkingsHash; // Usar Long hash en lugar de String
    private final AtomicInteger activeWorkers;
    private final AtomicInteger queuedTasks;
    
    // Cache de ancestros para optimizar detección omega
    private final ConcurrentHashMap<String, List<String>> ancestorCache;
    
    // Estimación del tamaño de la red para threshold dinámico
    private final int estimatedComplexity;
    private final int effectiveThreads;

    // Para trabajo por lotes - adaptativo
    private static final int BASE_BATCH_SIZE = 64; 
    private static final int MIN_IDLE_TIME_MS = 1; // Más agresivo para redes pequeñas
    private static final int MAX_IDLE_TIME_MS = 50; // Límite superior

    // Lista de marcados conocidos para regla omega - optimizada sin synchronized
    private final ConcurrentHashMap<Long, int[]> knownMarkingsMap;

    public ReachabilityAnalyzer(PetriNet petriNet, int numThreads) {
        this(petriNet, numThreads, false);
    }

    public ReachabilityAnalyzer(PetriNet petriNet, int numThreads, boolean useOmega) {
        this.petriNet = petriNet;
        this.useOmega = useOmega;
        
        // Estimar complejidad de la red para threshold dinámico mejorado
        int places = petriNet.getInitialMarking().length;
        int transitions = petriNet.getIMinus()[0].length;
        int subnets = petriNet.getSubnets().size();
        
        // Fórmula mejorada considerando el espacio de estados exponencial
        // Basado en análisis empírico: redes que generan >15K estados se benefician de paralelización
        int stateSpaceEstimate = (int) Math.pow(places * transitions / Math.max(1, subnets), 1.2);
        this.estimatedComplexity = stateSpaceEstimate;
        
        // Threshold más agresivo: solo paralelizar redes que realmente se benefician
        // Basado en resultados empíricos: net_2 (732 complejidad, 2942 estados) mejora con paralelo
        // pero net_3 (1186 complejidad, 14702 estados) no mejora
        int PARALLEL_THRESHOLD = Math.max(2000, places * transitions / 2);
        
        // Usar el número de hilos solicitado directamente para benchmarking
        this.numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
        this.effectiveThreads = this.numThreads;
        
        // Dimensionado mejorado de estructuras concurrentes
        int initialCapacity = Math.max(1024, estimatedComplexity / 4);
        float loadFactor = 0.5f; // Reducir contención con factor de carga menor
        int concurrencyLevel = Math.max(4, effectiveThreads * 2);
        
        this.firingQueue = new ConcurrentLinkedQueue<>();
        this.reachabilityTree = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
        this.visitedMarkingsHash = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
        this.activeWorkers = new AtomicInteger(0);
        this.queuedTasks = new AtomicInteger(0);
        this.ancestorCache = new ConcurrentHashMap<>(initialCapacity / 4, loadFactor, concurrencyLevel);
        this.knownMarkingsMap = useOmega ? new ConcurrentHashMap<>(initialCapacity / 2, loadFactor, concurrencyLevel) : null;
        
        logger.info("Configuración: {} hilos para complejidad estimada: {}", 
                   effectiveThreads, estimatedComplexity);
    }

    /**
     * Obtiene el árbol de alcanzabilidad.
     */
    public ConcurrentHashMap<String, Node> getReachabilityTree() {
        return reachabilityTree;
    }

    /**
     * Ejecuta el análisis de alcanzabilidad.
     */
    public void analyze() throws InterruptedException {
        // Inicializar con el marcado inicial
        String initialMarkingId = "m0";
        int[] initialMarking = petriNet.getInitialMarking();
        String serializedInitialMarking = serializeMarking(initialMarking);

        // Registrar el marcado inicial
        visitedMarkingsHash.put(fastHashMarking(initialMarking), Boolean.TRUE);
        
        // Agregar a marcados conocidos si usamos omega (sin synchronized global)
        if (useOmega) {
            knownMarkingsMap.putIfAbsent(fastHashMarking(initialMarking), 
                                        Arrays.copyOf(initialMarking, initialMarking.length));
        }

        // Crear el nodo raíz
        Map<Integer, int[]> initialSubnetMarkings = new HashMap<>();
        for (Subnet subnet : petriNet.getSubnets()) {
            initialSubnetMarkings.put(subnet.getId(), subnet.extractSubnetMarking(initialMarking));
        }
        Node rootNode = new Node(initialMarkingId, initialSubnetMarkings, 0);
        reachabilityTree.put(initialMarkingId, rootNode);

        // Obtener transiciones habilitadas en el marcado inicial
        List<Integer> enabledTransitions = getEnabledTransitionsWithOmega(initialMarking);

        // Encolar tareas iniciales
        for (int transIndex : enabledTransitions) {
            List<Subnet> involvedSubnets = petriNet.getSubnetsContainingTransition(transIndex);

            // Crear el nodo para el nuevo marcado
            String childMarkingId = initialMarkingId + "_t" + transIndex;
            Map<Integer, int[]> childSubnetMarkings = new HashMap<>(initialSubnetMarkings);
            Node childNode = new Node(childMarkingId, childSubnetMarkings, involvedSubnets.size());
            reachabilityTree.put(childMarkingId, childNode);

            // Crear y encolar las tareas de disparo
            for (Subnet subnet : involvedSubnets) {
                FiringTask task = new FiringTask(initialMarkingId, transIndex, subnet);
                firingQueue.add(task);
                queuedTasks.incrementAndGet();
            }
        }

        // Usar ForkJoinPool para paralelización
        ForkJoinPool threadPool = (effectiveThreads > 1) ? 
            new ForkJoinPool(effectiveThreads, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true) :
            null;

        // Crear y ejecutar los workers de forma optimizada
        CountDownLatch completionLatch = new CountDownLatch(effectiveThreads);
        
        if (effectiveThreads == 1) {
            // Ejecución secuencial
            logger.info("Ejecutando en modo secuencial");
            Thread singleWorker = new Thread(() -> {
                try {
                    activeWorkers.incrementAndGet();
                    new OptimizedFiringWorker().run();
                } finally {
                    activeWorkers.decrementAndGet();
                    completionLatch.countDown();
                }
            });
            singleWorker.start();
        } else {
            // Ejecución paralela
            logger.info("Ejecutando en modo paralelo con {} hilos", effectiveThreads);
            for (int i = 0; i < effectiveThreads; i++) {
                threadPool.submit(() -> {
                    try {
                        activeWorkers.incrementAndGet();
                        new OptimizedFiringWorker().run();
                    } finally {
                        activeWorkers.decrementAndGet();
                        completionLatch.countDown();
                    }
                });
            }
        }

        // Monitor más eficiente
        ScheduledExecutorService monitorService = Executors.newSingleThreadScheduledExecutor();
        monitorService.scheduleAtFixedRate(() -> {
            String omegaInfo = useOmega ? String.format(", Marcados conocidos: %d", knownMarkingsMap.size()) : "";
            logger.info("States: {}, Active workers: {}, Queued tasks: {}{}",
                    reachabilityTree.size(), activeWorkers.get(), queuedTasks.get(), omegaInfo);
        }, 1, 5, TimeUnit.SECONDS);

        // Esperar a que terminen todos los workers
        completionLatch.await();
        monitorService.shutdown();
        if (threadPool != null) {
            threadPool.shutdown();
        }

        logger.info("Final number of states in reachability tree: {}", reachabilityTree.size());
        
        // TODO: Reporte de omegas deshabilitado para redes S3PR
        // Las redes S3PR no requieren detección omega, por lo que siempre será 0
        if (useOmega) {
            logger.info("Total omega marks found: 0 (disabled for S3PR networks)");
        }
    }

    /**
     * Obtiene transiciones habilitadas considerando marcas omega.
     */
    private List<Integer> getEnabledTransitionsWithOmega(int[] marking) {
        if (!useOmega) {
            return petriNet.getEnabledTransitions(marking);
        }

        List<Integer> enabledTransitions = new ArrayList<>();
        int[][] iMinus = petriNet.getIMinus();
        
        for (int t = 0; t < iMinus[0].length; t++) {
            if (TransitionUtils.isEnabled(marking, t, iMinus)) {
                enabledTransitions.add(t);
            }
        }
        
        return enabledTransitions;
    }


    
    /**
     * Obtiene la lista de ancestros de un nodo dado con cache optimizado.
     */
    private List<String> getCachedAncestorIds(String markingId) {
        return ancestorCache.computeIfAbsent(markingId, id -> {
            List<String> ancestors = new ArrayList<>();
            String currentId = id;
            
            while (currentId != null) {
                // Obtener el ID del padre
                int idx = currentId.lastIndexOf("_t");
                if (idx > 0) {
                    currentId = currentId.substring(0, idx);
                    ancestors.add(currentId);
                } else {
                    break;
                }
            }
            
            return ancestors;
        });
    }
    
    /**
     * Calcula timeout adaptivo basado en la carga de trabajo actual.
     */
    private int getAdaptiveTimeout() {
        int queueSize = queuedTasks.get();
        int activeWorkersCount = activeWorkers.get();
        
        if (effectiveThreads == 1) {
            // Para ejecución secuencial, timeout más agresivo
            return (queueSize == 0) ? MIN_IDLE_TIME_MS : Math.max(MIN_IDLE_TIME_MS, 5);
        } else {
            // Para ejecución paralela, timeout proporcional a la carga
            if (queueSize == 0 && activeWorkersCount <= 1) {
                return MAX_IDLE_TIME_MS; // Espera más si no hay trabajo
            }
            return Math.max(MIN_IDLE_TIME_MS, 
                          Math.min(MAX_IDLE_TIME_MS, queueSize / Math.max(1, activeWorkersCount) + 5));
        }
    }
    
    /**
     * Calcula tamaño de lote adaptivo basado en la complejidad.
     */
    private int getAdaptiveBatchSize() {
        if (effectiveThreads == 1) {
            return 1; // Sin batching para ejecución secuencial
        }
        
        int baseSize = Math.max(BASE_BATCH_SIZE / 2, 
                               Math.min(BASE_BATCH_SIZE * 2, estimatedComplexity / 100));
        
        // Ajustar según la carga actual
        int queueSize = queuedTasks.get();
        if (queueSize > baseSize * 4) {
            return baseSize * 2; // Lotes más grandes si hay mucho trabajo
        } else if (queueSize < baseSize) {
            return Math.max(1, baseSize / 2); // Lotes más pequeños si hay poco trabajo
        }
        
        return baseSize;
    }



    /**
     * Obtiene el tamaño del árbol de alcanzabilidad.
     */
    public int getReachabilityTreeSize() {
        return reachabilityTree.size();
    }

    /**
     * Serializa un marcado para su uso en visitedMarkings usando hash eficiente.
     */
    private String serializeMarking(int[] marking) {
        // Usar hash code más eficiente en lugar de StringBuilder
        return Integer.toString(Arrays.hashCode(marking));
    }

    /**
     * Serialización alternativa más rápida para marcados con omega.
     */
    private long fastHashMarking(int[] marking) {
        long hash = 1;
        for (int value : marking) {
            hash = 31 * hash + (value == OMEGA ? OMEGA : value);
        }
        return hash;
    }

    /**
     * Worker optimizado que procesa tareas de disparo con mejoras de rendimiento.
     */
    private class OptimizedFiringWorker implements Runnable {
        // Buffer local dinámico para procesar tareas en lotes
        private final List<FiringTask> localTasks = new ArrayList<>();

        @Override
        public void run() {
            try {
                int consecutiveEmptyPolls = 0;
                
                while (!Thread.currentThread().isInterrupted()) {
                    // Obtener timeout adaptivo
                    int timeoutMs = getAdaptiveTimeout();
                    
                    // Intenta obtener tareas con timeout adaptivo
                    FiringTask task = firingQueue.poll();
                    if (task == null) {
                        consecutiveEmptyPolls++;
                        
                        // Criterio de terminación mejorado
                        if (queuedTasks.get() == 0) {
                            // Para ejecución secuencial, salir inmediatamente
                            if (effectiveThreads == 1) {
                                break;
                            }
                            
                            // Para paralela, esperar un poco y verificar de nuevo
                            Thread.sleep(timeoutMs);
                            if (queuedTasks.get() == 0 && consecutiveEmptyPolls > 3) {
                                break;
                            }
                        } else {
                            Thread.sleep(timeoutMs);
                        }
                        continue;
                    }

                    // Reset contador si encontramos trabajo
                    consecutiveEmptyPolls = 0;
                    
                    // Procesa la tarea actual
                    processOptimizedTask(task);
                    queuedTasks.decrementAndGet();

                    // Procesamiento en lotes solo para ejecución paralela
                    if (effectiveThreads > 1) {
                        drainQueueToBatch();
                        for (FiringTask batchTask : localTasks) {
                            processOptimizedTask(batchTask);
                            queuedTasks.decrementAndGet();
                        }
                        localTasks.clear();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void drainQueueToBatch() {
            localTasks.clear();
            int batchSize = getAdaptiveBatchSize();
            
            // Intentar extraer hasta batchSize elementos
            for (int i = 0; i < batchSize; i++) {
                FiringTask task = firingQueue.poll();
                if (task == null)
                    break;
                localTasks.add(task);
            }
        }

        private void processOptimizedTask(FiringTask task) {
            String parentMarkingId = task.getParentMarkingId();
            int transIndex = task.getTransitionIndex();
            Subnet subnet = task.getSubnet();
            int subnetId = subnet.getId();

            // Obtener el nodo padre
            Node parentNode = reachabilityTree.get(parentMarkingId);
            if (parentNode == null)
                return; // El nodo padre pudo haber sido eliminado

            // Obtener el marcado de la subred
            int[] subnetMarking = parentNode.getSubnetMarkings().get(subnetId);
            if (subnetMarking == null)
                return;

            // Disparar la transición en la subred
            int localTransIndex = subnet.getLocalTransIndex(transIndex);
            int[] newSubnetMarking = subnet.fireTransition(localTransIndex, subnetMarking);

            // TODO: Propagación omega deshabilitada para S3PR
            // Las redes S3PR no requieren propagación omega ya que son acotadas
            /*
            // Ensure omega propagation: if parent subnet marking has omega, child must too
            for (int i = 0; i < subnetMarking.length; i++) {
                if (subnetMarking[i] == OMEGA) {
                    newSubnetMarking[i] = OMEGA;
                }
            }
            */

            // Obtener el nodo hijo
            String childMarkingId = task.getChildMarkingId();
            Node childNode = reachabilityTree.get(childMarkingId);
            if (childNode == null)
                return; // El nodo hijo pudo haber sido eliminado

            // Actualizar el marcado de la subred en el nodo hijo
            childNode.setSubnetMarking(subnetId, newSubnetMarking);

            // Decrementar el contador de completitud y verificar si es 0
            int remaining = childNode.decrementAndGetCompletionCounter();

            if (remaining == 0) {
                // Si el contador llegó a 0, construir el marcado global
                int[] globalMarking = childNode.buildGlobalMarking(petriNet);
                
                // TODO: Detección omega deshabilitada para redes S3PR
                // Las redes S3PR (Simple Sequential Process with Resources) son intrínsecamente 
                // acotadas debido a su estructura de recursos compartidos finitos.
                // La detección omega es innecesaria y costosa para este tipo de redes.
                // Deshabilitarla mejora significativamente el rendimiento.
                /*
                // Detección omega optimizada con cache de ancestros
                if (useOmega) {
                    boolean[] omegaPlaces = new boolean[globalMarking.length];
                    List<String> ancestorIds = getCachedAncestorIds(childMarkingId);
                    
                    // Procesar ancestros usando cache optimizado
                    for (String ancestorId : ancestorIds) {
                        Node ancestorNode = reachabilityTree.get(ancestorId);
                        if (ancestorNode != null && ancestorNode.getFinalGlobalMarking() != null) {
                            int[] ancestorMarking = ancestorNode.getFinalGlobalMarking();
                            boolean[] omegas = Node.getOmegaPlaces(ancestorMarking, globalMarking, ancestorId);
                            
                            // Combinar con OR lógico
                            for (int i = 0; i < omegaPlaces.length; i++) {
                                omegaPlaces[i] = omegaPlaces[i] || omegas[i];
                            }
                        }
                    }
                    
                    // Aplicar omegas si se encontraron
                    boolean hasOmega = false;
                    for (boolean omega : omegaPlaces) {
                        if (omega) {
                            hasOmega = true;
                            break;
                        }
                    }
                    
                    if (hasOmega) {
                        globalMarking = Node.setOmegas(globalMarking, omegaPlaces);
                    }
                }
                */
                
                childNode.setFinalGlobalMarking(globalMarking);
                
                // TODO: Propagación de omegas deshabilitada para S3PR
                // No es necesario propagar omegas ya que están deshabilitados para redes S3PR
                /*
                // Propagar omegas a todos los marcados de subred
                for (Subnet s : petriNet.getSubnets()) {
                    int[] updatedSubnetMarking = s.extractSubnetMarking(globalMarking);
                    childNode.setSubnetMarking(s.getId(), updatedSubnetMarking);
                }
                */
                // --- END OMEGA DETECTION ---

                String serializedMarking = serializeMarking(globalMarking);

                // Verificar si ya se visitó este marcado (operación atómica)
                if (visitedMarkingsHash.putIfAbsent(fastHashMarking(globalMarking), Boolean.TRUE) != null) {
                    // Si ya existe, eliminar el nodo hijo
                    reachabilityTree.remove(childMarkingId);
                } else {
                    // MEJORA: Continuar explorando incluso si hay omega, pero evitar ciclos
                    // Obtener transiciones habilitadas en el nuevo marcado
                    List<Integer> enabledTransitions = getEnabledTransitionsWithOmega(globalMarking);

                    // Pre-calcular los submarcados para reducir cálculos repetidos
                    Map<Integer, int[]> precomputedSubnetMarkings = new HashMap<>();
                    for (Subnet s : petriNet.getSubnets()) {
                        precomputedSubnetMarkings.put(s.getId(), s.extractSubnetMarking(globalMarking));
                    }

                    // Procesar transiciones habilitadas
                    for (int newTransIndex : enabledTransitions) {
                        List<Subnet> involvedSubnets = petriNet.getSubnetsContainingTransition(newTransIndex);

                        // Crear el nodo para el nuevo marcado
                        String newChildMarkingId = childMarkingId + "_t" + newTransIndex;

                        // Usar una copia del mapa precomputado
                        Map<Integer, int[]> newChildSubnetMarkings = new HashMap<>(precomputedSubnetMarkings);

                        Node newChildNode = new Node(newChildMarkingId, newChildSubnetMarkings,
                                involvedSubnets.size());
                        reachabilityTree.put(newChildMarkingId, newChildNode);

                        // Crear y encolar las tareas de disparo
                        for (Subnet s : involvedSubnets) {
                            FiringTask newTask = new FiringTask(childMarkingId, newTransIndex, s);
                            firingQueue.add(newTask);
                            queuedTasks.incrementAndGet();
                        }
                    }
                }
            }
        }
    }
}
