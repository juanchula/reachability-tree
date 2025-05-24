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

class ReachabilityAnalyzer {
    private static final Logger logger = LogManager.getLogger(ReachabilityAnalyzer.class);

    private final PetriNet petriNet;
    private final int numThreads;

    // Recursos compartidos optimizados
    private final ConcurrentLinkedQueue<FiringTask> firingQueue;
    private final ConcurrentHashMap<String, Node> reachabilityTree;
    private final ConcurrentHashMap<String, Boolean> visitedMarkings; // Cambio de Set a ConcurrentHashMap
    private final AtomicInteger activeWorkers;
    private final AtomicInteger queuedTasks;

    // Para trabajo por lotes
    private static final int BATCH_SIZE = 64;

    public ReachabilityAnalyzer(PetriNet petriNet, int numThreads) {
        this.petriNet = petriNet;
        // Limitar hilos al número óptimo (típicamente número de núcleos disponibles)
        this.numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());

        this.firingQueue = new ConcurrentLinkedQueue<>();
        this.reachabilityTree = new ConcurrentHashMap<>(1024, 0.75f, numThreads);
        this.visitedMarkings = new ConcurrentHashMap<>(1024, 0.75f, numThreads);
        this.activeWorkers = new AtomicInteger(0);
        this.queuedTasks = new AtomicInteger(0);
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
        visitedMarkings.put(serializedInitialMarking, Boolean.TRUE);

        // Crear el nodo raíz
        Map<Integer, int[]> initialSubnetMarkings = new HashMap<>();
        for (Subnet subnet : petriNet.getSubnets()) {
            initialSubnetMarkings.put(subnet.getId(), subnet.extractSubnetMarking(initialMarking));
        }
        Node rootNode = new Node(initialMarkingId, initialSubnetMarkings, 0);
        reachabilityTree.put(initialMarkingId, rootNode);

        // Obtener transiciones habilitadas en el marcado inicial
        List<Integer> enabledTransitions = petriNet.getEnabledTransitions(initialMarking);

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
            logger.info("States: {}, Active workers: {}, Queued tasks: {}",
                    reachabilityTree.size(), activeWorkers.get(), queuedTasks.get());
        }, 1, 5, TimeUnit.SECONDS);

        // Esperar a que terminen todos los workers
        completionLatch.await();
        monitorService.shutdown();
        threadPool.shutdown();

        logger.info("Final number of states in reachability tree: {}", reachabilityTree.size());
    }

    /**
     * Obtiene el tamaño del árbol de alcanzabilidad.
     */
    public int getReachabilityTreeSize() {
        return reachabilityTree.size();
    }

    /**
     * Serializa un marcado para su uso en visitedMarkings.
     */
    private String serializeMarking(int[] marking) {
        // Versión más eficiente para serializar arrays
        StringBuilder sb = new StringBuilder(marking.length * 4);
        sb.append('[');
        for (int i = 0; i < marking.length; i++) {
            if (i > 0)
                sb.append(',');
            sb.append(marking[i]);
        }
        sb.append(']');
        return sb.toString();
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
                        Thread.sleep(10); // Espera breve antes de reintentar
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
                if (subnetMarking[i] == -1) {
                    newSubnetMarking[i] = -1;
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

                // --- OMEGA DETECTION AND PROPAGATION ---
                // Recorre la cadena de ancestros para detectar condiciones omega
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
                boolean hasOmega = false;
                for (boolean b : omegaPlaces) {
                    if (b) { hasOmega = true; break; }
                }
                if (hasOmega) {
                    globalMarking = Node.setOmegas(globalMarking, omegaPlaces);
                }
                childNode.setFinalGlobalMarking(globalMarking);
                // Propagate omegas to all subnet markings
                for (Subnet s : petriNet.getSubnets()) {
                    int[] updatedSubnetMarking = s.extractSubnetMarking(globalMarking);
                    childNode.setSubnetMarking(s.getId(), updatedSubnetMarking);
                }
                // --- END OMEGA DETECTION ---

                String serializedMarking = serializeMarking(globalMarking);

                // Verificar si ya se visitó este marcado (operación atómica)
                if (visitedMarkings.putIfAbsent(serializedMarking, Boolean.TRUE) != null) {
                    // Si ya existe, eliminar el nodo hijo
                    reachabilityTree.remove(childMarkingId);
                } else {
                    // Obtener transiciones habilitadas en el nuevo marcado
                    List<Integer> enabledTransitions = petriNet.getEnabledTransitions(globalMarking);

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
