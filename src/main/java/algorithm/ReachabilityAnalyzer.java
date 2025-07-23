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

    // Para trabajo por lotes - optimizado
    private static final int BATCH_SIZE = 128; // Aumentar tamaño de lote
    private static final int MAX_IDLE_TIME_MS = 25; // Reducir tiempo de espera

    // Lista de marcados conocidos para regla omega - optimizada
    private final ConcurrentHashMap<Long, int[]> knownMarkingsMap;

    public ReachabilityAnalyzer(PetriNet petriNet, int numThreads) {
        this(petriNet, numThreads, false);
    }

    public ReachabilityAnalyzer(PetriNet petriNet, int numThreads, boolean useOmega) {
        this.petriNet = petriNet;
        // Limitar hilos al número óptimo (típicamente número de núcleos disponibles)
        this.numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
        this.useOmega = useOmega;

        this.firingQueue = new ConcurrentLinkedQueue<>();
        this.reachabilityTree = new ConcurrentHashMap<>(1024, 0.75f, numThreads);
        this.visitedMarkingsHash = new ConcurrentHashMap<>(1024, 0.75f, numThreads);
        this.activeWorkers = new AtomicInteger(0);
        this.queuedTasks = new AtomicInteger(0);
        this.knownMarkingsMap = useOmega ? new ConcurrentHashMap<>() : null;
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
        
        // Agregar a marcados conocidos si usamos omega
        if (useOmega) {
            synchronized (knownMarkingsMap) {
                knownMarkingsMap.put(fastHashMarking(initialMarking), Arrays.copyOf(initialMarking, initialMarking.length));
            }
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

        // Usar ForkJoinPool para mejor balanceo de carga
        ForkJoinPool threadPool = new ForkJoinPool(numThreads,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);

        // Crear y ejecutar los workers
        CountDownLatch completionLatch = new CountDownLatch(numThreads);
        for (int i = 0; i < numThreads; i++) {
            threadPool.submit(() -> {
                try {
                    activeWorkers.incrementAndGet();
                    new FiringWorker().run();
                } finally {
                    activeWorkers.decrementAndGet();
                    completionLatch.countDown();
                }
            });
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
        threadPool.shutdown();

        logger.info("Final number of states in reachability tree: {}", reachabilityTree.size());
        
        if (useOmega) {
            long omegaCount = reachabilityTree.values().stream()
                    .flatMapToInt(node -> Arrays.stream(node.buildGlobalMarking(petriNet)))
                    .filter(mark -> mark == OMEGA)
                    .count();
            logger.info("Total omega marks found: {}", omegaCount);
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
     * Obtiene la lista de ancestros de un nodo dado.
     */
    private List<Node> getAncestors(String markingId) {
        List<Node> ancestors = new ArrayList<>();
        String currentId = markingId;
        
        while (currentId != null && reachabilityTree.containsKey(currentId)) {
            Node ancestorNode = reachabilityTree.get(currentId);
            if (ancestorNode.getFinalGlobalMarking() != null) {
                ancestors.add(ancestorNode);
            }
            
            // Obtener el ID del padre
            int idx = currentId.lastIndexOf("_t");
            if (idx > 0) {
                currentId = currentId.substring(0, idx);
            } else {
                currentId = null;
            }
        }
        
        return ancestors;
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
     * Worker que procesa tareas de disparo con optimizaciones.
     */
    private class FiringWorker implements Runnable {
        // Buffer local para procesar tareas en lotes
        private final List<FiringTask> localTasks = new ArrayList<>(BATCH_SIZE);

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // Intenta obtener tareas en lote para reducir contención
                    FiringTask task = firingQueue.poll();
                    if (task == null) {
                        // Si no hay tareas y ningún worker está activo, termina
                        if (queuedTasks.get() == 0) {
                            Thread.sleep(50); // Pequeña pausa para reducir CPU
                            if (queuedTasks.get() == 0) {
                                break;
                            }
                        }
                        Thread.sleep(MAX_IDLE_TIME_MS); // Espera breve antes de reintentar
                        continue;
                    }

                    // Procesa la tarea actual
                    processTask(task);
                    queuedTasks.decrementAndGet();

                    // Intenta obtener más tareas para procesamiento en lotes
                    drainQueueToBatch();
                    for (FiringTask batchTask : localTasks) {
                        processTask(batchTask);
                        queuedTasks.decrementAndGet();
                    }
                    localTasks.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void drainQueueToBatch() {
            localTasks.clear();
            // Intentar extraer hasta BATCH_SIZE elementos
            for (int i = 0; i < BATCH_SIZE; i++) {
                FiringTask task = firingQueue.poll();
                if (task == null)
                    break;
                localTasks.add(task);
            }
        }

        private void processTask(FiringTask task) {
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

            // Ensure omega propagation: if parent subnet marking has omega, child must too
            for (int i = 0; i < subnetMarking.length; i++) {
                if (subnetMarking[i] == OMEGA) {
                    newSubnetMarking[i] = OMEGA;
                }
            }

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
                    // Usar detección omega original (si está implementada en Node)
                    String ancestorId = parentMarkingId;
                    boolean[] omegaPlaces = new boolean[globalMarking.length];
                    while (ancestorId != null && reachabilityTree.containsKey(ancestorId)) {
                        Node ancestorNode = reachabilityTree.get(ancestorId);
                        int[] ancestorMarking = ancestorNode.buildGlobalMarking(petriNet);
                        boolean[] omegas = Node.getOmegaPlaces(ancestorMarking, globalMarking, ancestorId);
                        for (int i = 0; i < omegaPlaces.length; i++) {
                            omegaPlaces[i] = omegaPlaces[i] || omegas[i];
                        }
                        // Move to previous ancestor in the chain
                        int idx = ancestorId.lastIndexOf("_t");
                        if (idx > 0) {
                            ancestorId = ancestorId.substring(0, idx);
                        } else {
                            ancestorId = null;
                        }
                    }
                    boolean hasOmegaOriginal = false;
                    for (boolean b : omegaPlaces) {
                        if (b) { hasOmegaOriginal = true; break; }
                    }
                    if (hasOmegaOriginal) {
                        globalMarking = Node.setOmegas(globalMarking, omegaPlaces);
                    }
                // }
                
                childNode.setFinalGlobalMarking(globalMarking);
                
                // Propagar omegas a todos los marcados de subred
                for (Subnet s : petriNet.getSubnets()) {
                    int[] updatedSubnetMarking = s.extractSubnetMarking(globalMarking);
                    childNode.setSubnetMarking(s.getId(), updatedSubnetMarking);
                }
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
