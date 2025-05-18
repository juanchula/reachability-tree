package algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Map;




/**
 * Implementa el algoritmo de análisis de alcanzabilidad.
 */
class ReachabilityAnalyzer {
    private static final Logger logger = LogManager.getLogger(ReachabilityAnalyzer.class);

    private PetriNet petriNet;
    private int numThreads;

    // Recursos compartidos
    private BlockingQueue<FiringTask> firingQueue;
    private ConcurrentHashMap<String, Node> reachabilityTree;
    private Set<String> visitedMarkings;
    private ReadWriteLock visitedMarkingsLock;
    private AtomicInteger activeTaskCount;

    // Thread pool
    private ExecutorService threadPool;

    public ReachabilityAnalyzer(PetriNet petriNet, int numThreads) {
        this.petriNet = petriNet;
        this.numThreads = numThreads;

        this.firingQueue = new LinkedBlockingQueue<>();
        this.reachabilityTree = new ConcurrentHashMap<>();
        this.visitedMarkings = new HashSet<>();
        this.visitedMarkingsLock = new ReentrantReadWriteLock();
        this.activeTaskCount = new AtomicInteger(0);
    }

    /**
     * Ejecuta el análisis de alcanzabilidad.
     */
    public void analyze() throws InterruptedException {
        // Inicializar con el marcado inicial
        String initialMarkingId = "m0";
        int[] initialMarking = petriNet.getInitialMarking();
        String serializedInitialMarking = serializeMarking(initialMarking);

        // Agregar el marcado inicial a visitedMarkings
        visitedMarkingsLock.writeLock().lock();
        try {
            visitedMarkings.add(serializedInitialMarking);
        } finally {
            visitedMarkingsLock.writeLock().unlock();
        }

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
                activeTaskCount.incrementAndGet();
            }
        }

        // Iniciar el thread pool
        threadPool = Executors.newFixedThreadPool(numThreads);

        // Crear y ejecutar los workers
        for (int i = 0; i < numThreads; i++) {
            threadPool.submit(new FiringWorker());
        }

        // Monitorear la terminación
        while (true) {
            Thread.sleep(200);  // Esperar 0.1 segundo
            int currentTasks = activeTaskCount.get();
            logger.info("Current number of states in reachability tree: {}", reachabilityTree.size());

            if (currentTasks == 0 && firingQueue.isEmpty()) {
                break;
            }
        }

        // Finalizar el thread pool
        threadPool.shutdown();
        //threadPool.awaitTermination(10, TimeUnit.SECONDS);

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
        return Arrays.toString(marking);
    }

    /**
     * Worker que procesa tareas de disparo.
     */
    private class FiringWorker implements Runnable {
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    FiringTask task = firingQueue.take();
                    processTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void processTask(FiringTask task) {
            try {
                String parentMarkingId = task.getParentMarkingId();
                int transIndex = task.getTransitionIndex();
                Subnet subnet = task.getSubnet();
                int subnetId = subnet.getId();

                // Obtener el nodo padre
                Node parentNode = reachabilityTree.get(parentMarkingId);

                // Obtener el marcado de la subred
                int[] subnetMarking = parentNode.getSubnetMarkings().get(subnetId);

                // Disparar la transición en la subred
                int localTransIndex = subnet.getLocalTransIndex(transIndex);
                int[] newSubnetMarking = subnet.fireTransition(localTransIndex, subnetMarking);

                // Obtener el nodo hijo
                String childMarkingId = task.getChildMarkingId();
                Node childNode = reachabilityTree.get(childMarkingId);

                // Actualizar el marcado de la subred en el nodo hijo
                childNode.setSubnetMarking(subnetId, newSubnetMarking);

                // Decrementar el contador de completitud y verificar si es 0
                int remaining = childNode.decrementAndGetCompletionCounter();

                if (remaining == 0) {
                    // Si el contador llegó a 0, construir el marcado global
                    int[] globalMarking = childNode.buildGlobalMarking(petriNet);
                    String serializedMarking = serializeMarking(globalMarking);

                    // Verificar si ya se visitó este marcado
                    boolean alreadyVisited = false;

                    visitedMarkingsLock.readLock().lock();
                    try {
                        alreadyVisited = visitedMarkings.contains(serializedMarking);
                    } finally {
                        visitedMarkingsLock.readLock().unlock();
                    }

                    if (alreadyVisited) {
                        reachabilityTree.remove(childMarkingId);
                    } else {
                        visitedMarkingsLock.writeLock().lock();
                        // Volver a verificar con el write lock
                        if (visitedMarkings.contains(serializedMarking)) {
                            visitedMarkingsLock.writeLock().unlock();
                            reachabilityTree.remove(childMarkingId);
                        } else {
                            visitedMarkings.add(serializedMarking);
                            visitedMarkingsLock.writeLock().unlock();

                            // Obtener transiciones habilitadas en el nuevo marcado
                            List<Integer> enabledTransitions = petriNet.getEnabledTransitions(globalMarking);

                            // Encolar nuevas tareas
                            for (int newTransIndex : enabledTransitions) {
                                List<Subnet> involvedSubnets = petriNet.getSubnetsContainingTransition(newTransIndex);

                                // Crear el nodo para el nuevo marcado
                                String newChildMarkingId = childMarkingId + "_t" + newTransIndex;
                                Map<Integer, int[]> newChildSubnetMarkings = new HashMap<>();
                                for (Subnet s : petriNet.getSubnets()) {
                                    newChildSubnetMarkings.put(s.getId(), s.extractSubnetMarking(globalMarking));
                                }

                                Node newChildNode = new Node(newChildMarkingId, newChildSubnetMarkings, involvedSubnets.size());
                                reachabilityTree.put(newChildMarkingId, newChildNode);

                                // Crear y encolar las tareas de disparo
                                for (Subnet s : involvedSubnets) {
                                    FiringTask newTask = new FiringTask(childMarkingId, newTransIndex, s);
                                    firingQueue.add(newTask);
                                    activeTaskCount.incrementAndGet();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing firing task: {}", e.getMessage());
                activeTaskCount.decrementAndGet();
            } finally {
                activeTaskCount.decrementAndGet();
            }
        }
    }

    /**
     * Obtiene el árbol de alcanzabilidad.
     */
    public ConcurrentHashMap<String, Node> getReachabilityTree() {
        return reachabilityTree;
    }

}
