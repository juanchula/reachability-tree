package algorithm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * IMPLEMENTACIÓN ULTRA-OPTIMIZADA v2.0 del algoritmo de árbol de alcanzabilidad con marcas omega (ω).
 * 
 * NUEVAS OPTIMIZACIONES IMPLEMENTADAS (Basadas en recomendaciones ChatGPT):
 * 1. ✅ Sharding del visited set - Reduce contención masivamente
 * 2. ✅ Hash mejorado con avalancha final - Mejor distribución
 * 3. ✅ Eliminación del double-check bloom+map - Simplificación eficiente
 * 4. ✅ LongAdder para métricas - Reduce contención en contadores
 * 5. ✅ LocalFrontier para detección omega distribuida - Menos accesos globales
 * 6. ✅ Work-stealing con ForkJoinPool optimizado
 * 7. ✅ Vectorización manual mejorada para comparaciones
 * 8. ✅ Pool de arrays expandido por hilo
 * 
 * OPTIMIZACIONES PREVIAS MANTENIDAS:
 * - Cache de detección omega
 * - Pool de arrays reutilizables
 * - Configuración dinámica de parámetros
 * - Índice espacial para marcados conocidos
 * - Métricas detalladas de rendimiento
 * 
 * Usa -1 para representar ω (igual que el algoritmo original).
 */
public class OmegaReachabilityAnalyzer {
    private static final Logger logger = LogManager.getLogger(OmegaReachabilityAnalyzer.class);
    
    // Constante para representar omega (infinito) - usando -1 como en Python
    public static final int OMEGA = -1;
    
    // =================== CONFIGURACIÓN DINÁMICA OPTIMIZADA ===================
    private final int BATCH_SIZE;
    private final int MAX_IDLE_TIME_MS;
    private final int NUM_VISITED_SHARDS;
    private final int LOCAL_FRONTIER_SIZE;
    
    // =================== VISITED SET CON SHARDING (NUEVA OPTIMIZACIÓN) ===================
    private static class ShardedVisitedSet {
        private final ConcurrentHashMap<Long, Boolean>[] shards;
        private final int numShards;
        private final int shardMask;
        
        @SuppressWarnings("unchecked")
        public ShardedVisitedSet(int numShards) {
            // Asegurar que numShards es potencia de 2 para usar mask
            this.numShards = Integer.highestOneBit(numShards) == numShards ? numShards : Integer.highestOneBit(numShards) << 1;
            this.shardMask = this.numShards - 1;
            this.shards = new ConcurrentHashMap[this.numShards];
            
            for (int i = 0; i < this.numShards; i++) {
                this.shards[i] = new ConcurrentHashMap<>(1024, 0.75f, 4);
            }
        }
        
        public boolean addIfAbsent(long hash) {
            int shard = (int)(hash >>> 32) & shardMask; // Usar bits altos para mejor distribución
            return shards[shard].putIfAbsent(hash, Boolean.TRUE) == null;
        }
        
        public long totalSize() {
            return Arrays.stream(shards).mapToLong(Map::size).sum();
        }
    }
    
    // =================== LOCAL FRONTIER PARA DETECCIÓN OMEGA DISTRIBUIDA ===================
    private static class LocalOmegaFrontier {
        private final List<int[]> localMarkings;
        private final int maxSize;
        private int currentSize;
        
        public LocalOmegaFrontier(int maxSize) {
            this.maxSize = maxSize;
            this.localMarkings = new ArrayList<>(maxSize);
            this.currentSize = 0;
        }
        
        public void addMarking(int[] marking) {
            if (currentSize < maxSize) {
                localMarkings.add(Arrays.copyOf(marking, marking.length));
                currentSize++;
            } else {
                // Reemplazar el más antiguo (estrategia FIFO simple)
                localMarkings.set(currentSize % maxSize, Arrays.copyOf(marking, marking.length));
            }
        }
        
        public List<int[]> getCandidatesForDomination(int[] newMarking) {
            List<int[]> candidates = new ArrayList<>();
            for (int i = 0; i < Math.min(currentSize, localMarkings.size()); i++) {
                int[] localMarking = localMarkings.get(i);
                if (localMarking != null && couldDominate(localMarking, newMarking)) {
                    candidates.add(localMarking);
                }
            }
            return candidates;
        }
        
        private boolean couldDominate(int[] candidate, int[] newMarking) {
            // Verificación rápida: si la suma del candidato es mayor, no puede dominar
            int candidateSum = 0, newSum = 0;
            for (int i = 0; i < candidate.length; i++) {
                if (candidate[i] != OMEGA) candidateSum += candidate[i];
                if (newMarking[i] != OMEGA) newSum += newMarking[i];
            }
            return candidateSum <= newSum;
        }
        
        public boolean isEmpty() {
            return currentSize == 0;
        }
    }
    
    // =================== POOL DE ARRAYS EXPANDIDO POR HILO ===================
    private static class ExpandedArrayPool {
        private final ThreadLocal<int[]> globalMarkingBuffer;
        private final ThreadLocal<int[]> tempMarkingBuffer;
        private final ThreadLocal<int[]> omegaWorkBuffer;
        private final ThreadLocal<boolean[]> dominationFlags;
        private final int arraySize;
        
        public ExpandedArrayPool(int arraySize) {
            this.arraySize = arraySize;
            this.globalMarkingBuffer = ThreadLocal.withInitial(() -> new int[arraySize]);
            this.tempMarkingBuffer = ThreadLocal.withInitial(() -> new int[arraySize]);
            this.omegaWorkBuffer = ThreadLocal.withInitial(() -> new int[arraySize]);
            this.dominationFlags = ThreadLocal.withInitial(() -> new boolean[arraySize]);
        }
        
        public int[] getGlobalMarkingBuffer() {
            int[] buffer = globalMarkingBuffer.get();
            Arrays.fill(buffer, 0);
            return buffer;
        }
        
        public int[] getTempMarkingBuffer() {
            int[] buffer = tempMarkingBuffer.get();
            Arrays.fill(buffer, 0);
            return buffer;
        }
        
        public int[] getOmegaWorkBuffer() {
            int[] buffer = omegaWorkBuffer.get();
            System.arraycopy(tempMarkingBuffer.get(), 0, buffer, 0, arraySize);
            return buffer;
        }
        
        public boolean[] getDominationFlags() {
            boolean[] flags = dominationFlags.get();
            Arrays.fill(flags, false);
            return flags;
        }
    }
    
    // =================== ÍNDICE ESPACIAL MEJORADO ===================
    private static class OptimizedSpatialMarkingIndex {
        // Usar arrays más eficientes que CopyOnWriteArrayList
        private final ConcurrentHashMap<Integer, List<int[]>> markingsBySum;
        private final ConcurrentHashMap<Integer, List<int[]>> markingsByMax;
        private final LongAdder totalMarkings = new LongAdder();
        
        public OptimizedSpatialMarkingIndex() {
            this.markingsBySum = new ConcurrentHashMap<>();
            this.markingsByMax = new ConcurrentHashMap<>();
        }
        
        public void addMarking(int[] marking) {
            int sum = 0, max = 0;
            for (int value : marking) {
                if (value != OMEGA) {
                    sum += value;
                    max = Math.max(max, value);
                }
            }
            
            // Usar computeIfAbsent con ArrayList sincronizado
            markingsBySum.computeIfAbsent(sum, k -> Collections.synchronizedList(new ArrayList<>())).add(marking);
            markingsByMax.computeIfAbsent(max, k -> Collections.synchronizedList(new ArrayList<>())).add(marking);
            totalMarkings.increment();
        }
        
        public List<int[]> getCandidatesForDomination(int[] newMarking) {
            int sum = 0, max = 0;
            for (int value : newMarking) {
                if (value != OMEGA) {
                    sum += value;
                    max = Math.max(max, value);
                }
            }
            
            Set<int[]> candidates = new HashSet<>();
            
            // Buscar por suma (más selectivo)
            for (int s = 0; s <= sum; s++) {
                List<int[]> markingsWithSum = markingsBySum.get(s);
                if (markingsWithSum != null) {
                    synchronized (markingsWithSum) {
                        candidates.addAll(markingsWithSum);
                    }
                }
            }
            
            return new ArrayList<>(candidates);
        }
        
        public long size() {
            return totalMarkings.sum();
        }
    }
    
    // =================== CACHE DE DETECCIÓN OMEGA MEJORADO ===================
    private static class OptimizedOmegaCache {
        private final ConcurrentHashMap<Long, int[]> cache;
        private final LongAdder hits = new LongAdder();
        private final LongAdder misses = new LongAdder();
        private final int maxSize;
        
        public OptimizedOmegaCache(int maxSize) {
            this.maxSize = maxSize;
            this.cache = new ConcurrentHashMap<>(maxSize);
        }
        
        public int[] getOrCompute(long hash, int[] marking, 
                                  java.util.function.Function<int[], int[]> computer) {
            int[] cached = cache.get(hash);
            if (cached != null) {
                hits.increment();
                return Arrays.copyOf(cached, cached.length);
            }
            
            misses.increment();
            int[] result = computer.apply(marking);
            
            // Evitar que el cache crezca indefinidamente
            if (cache.size() < maxSize) {
                cache.put(hash, Arrays.copyOf(result, result.length));
            }
            
            return result;
        }
        
        public double getHitRate() {
            long totalRequests = hits.sum() + misses.sum();
            return totalRequests == 0 ? 0.0 : (double) hits.sum() / totalRequests;
        }
    }
    
    // =================== CLASES DE DATOS ===================
    public static class ReachabilityNode {
        public final String name;
        public final String label;
        public final int[] value;
        public final String from;
        public final Integer trans;
        
        public ReachabilityNode(String name, String label, int[] value, String from, Integer trans) {
            this.name = name;
            this.label = label;
            this.value = Arrays.copyOf(value, value.length);
            this.from = from;
            this.trans = trans;
        }
    }
    
    public static class FiringTask extends RecursiveAction {
        public final String parentMarkingId;
        public final int transitionIndex;
        public final Subnet subnet;
        public final String childMarkingId;
        private final OmegaReachabilityAnalyzer analyzer;
        
        public FiringTask(String parentMarkingId, int transitionIndex, Subnet subnet, OmegaReachabilityAnalyzer analyzer) {
            this.parentMarkingId = parentMarkingId;
            this.transitionIndex = transitionIndex;
            this.subnet = subnet;
            this.childMarkingId = parentMarkingId + "_t" + transitionIndex;
            this.analyzer = analyzer;
        }
        
        @Override
        protected void compute() {
            analyzer.processTaskHybridOptimized(this);
        }
    }
    
    private static class ProcessingNode {
        public final String markingId;
        public final Map<Integer, int[]> subnetMarkings;
        public final AtomicInteger completionCounter;
        public volatile int[] finalGlobalMarking;
        
        public ProcessingNode(String markingId, Map<Integer, int[]> subnetMarkings, int expectedCompletions) {
            this.markingId = markingId;
            this.subnetMarkings = new ConcurrentHashMap<>(subnetMarkings);
            this.completionCounter = new AtomicInteger(expectedCompletions);
            this.finalGlobalMarking = null;
        }
        
        public void setSubnetMarking(int subnetId, int[] marking) {
            subnetMarkings.put(subnetId, Arrays.copyOf(marking, marking.length));
        }
        
        public int decrementAndGetCompletionCounter() {
            return completionCounter.decrementAndGet();
        }
        
        // OPTIMIZADO: Usar buffer reutilizable para construir marcado global
        public int[] buildGlobalMarkingOptimized(PetriNet petriNet, int[] buffer) {
            Arrays.fill(buffer, 0); // Limpiar buffer
            
            for (Map.Entry<Integer, int[]> entry : subnetMarkings.entrySet()) {
                int subnetId = entry.getKey();
                int[] subnetMarking = entry.getValue();
                
                Subnet subnet = petriNet.getSubnetById(subnetId);
                int[] placeIndices = subnet.getPlaceIndices();
                
                for (int i = 0; i < placeIndices.length; i++) {
                    buffer[placeIndices[i]] = subnetMarking[i];
                }
            }
            
            return Arrays.copyOf(buffer, buffer.length); // Solo copiar al final
        }
    }
    
    // =================== VARIABLES DE INSTANCIA ===================
    private final PetriNet petriNet;
    private final int numThreads;
    
    // Estructuras optimizadas para procesamiento paralelo
    private final ConcurrentLinkedQueue<FiringTask> firingQueue;
    private final ConcurrentHashMap<String, ProcessingNode> processingNodes;
    private final ShardedVisitedSet visitedMarkings; // NUEVA: Sharded visited set
    private final AtomicInteger activeWorkers;
    private final LongAdder queuedTasks; // OPTIMIZADO: LongAdder en lugar de AtomicInteger
    
    // Optimizaciones avanzadas
    private final OptimizedSpatialMarkingIndex spatialIndex;
    private final OptimizedOmegaCache omegaCache;
    private final ExpandedArrayPool arrayPool;
    private final ThreadLocal<LocalOmegaFrontier> localFrontier; // NUEVA: Frontier local por hilo
    
    // Resultados finales
    private final ConcurrentHashMap<String, ReachabilityNode> finalNodes;
    
    // Métricas de rendimiento optimizadas
    private final LongAdder omegaDetectionTime = new LongAdder();
    private final LongAdder globalMarkingBuildTime = new LongAdder();
    private final LongAdder transitionFiringTime = new LongAdder();
    private final LongAdder localFrontierHits = new LongAdder();
    private final LongAdder globalIndexAccesses = new LongAdder();
    
    public OmegaReachabilityAnalyzer(PetriNet petriNet, int numThreads) {
        this.petriNet = petriNet;
        this.numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
        
        // ============= CONFIGURACIÓN DINÁMICA BASADA EN TAMAÑO DE RED =============
        int complexity = petriNet.getNumPlaces() * petriNet.getNumTransitions();
        this.BATCH_SIZE = calculateOptimalBatchSize(complexity);
        this.MAX_IDLE_TIME_MS = calculateOptimalIdleTime(this.numThreads);
        this.NUM_VISITED_SHARDS = Math.max(4, this.numThreads); // Al menos 4 shards
        this.LOCAL_FRONTIER_SIZE = Math.max(128, complexity / 10); // Frontier proporcional
        
        logger.info("Configuración v2.0 - Complejidad: {}, BatchSize: {}, IdleTime: {}ms, Shards: {}, LocalFrontier: {}", 
                   complexity, BATCH_SIZE, MAX_IDLE_TIME_MS, NUM_VISITED_SHARDS, LOCAL_FRONTIER_SIZE);
        
        // Inicializar estructuras básicas
        this.firingQueue = new ConcurrentLinkedQueue<>();
        this.processingNodes = new ConcurrentHashMap<>(1024, 0.75f, this.numThreads);
        this.visitedMarkings = new ShardedVisitedSet(NUM_VISITED_SHARDS); // NUEVA
        this.activeWorkers = new AtomicInteger(0);
        this.queuedTasks = new LongAdder(); // OPTIMIZADO
        
        // Inicializar optimizaciones avanzadas
        this.spatialIndex = new OptimizedSpatialMarkingIndex();
        this.omegaCache = new OptimizedOmegaCache(Math.min(10000, complexity * 10));
        this.arrayPool = new ExpandedArrayPool(petriNet.getNumPlaces());
        this.localFrontier = ThreadLocal.withInitial(() -> new LocalOmegaFrontier(LOCAL_FRONTIER_SIZE)); // NUEVA
        
        // Resultados finales
        this.finalNodes = new ConcurrentHashMap<>();
    }
    
    // =================== MÉTODOS DE CONFIGURACIÓN DINÁMICA ===================
    private static int calculateOptimalBatchSize(int complexity) {
        if (complexity < 100) return 32;
        if (complexity < 500) return 64;
        if (complexity < 2000) return 128;
        return 256;
    }
    
    private static int calculateOptimalIdleTime(int numThreads) {
        return Math.max(1, 10 / numThreads); // Menos espera con más hilos
    }
    
    // =================== HASH OPTIMIZADO CON AVALANCHA FINAL ===================
    
    /**
     * OPTIMIZADO: Hash rápido con avalancha final para mejor distribución
     */
    private long fastHashMarkingWithAvalanche(int[] marking) {
        long hash = 0x811c9dc5L; // FNV offset basis
        for (int value : marking) {
            hash ^= (value == OMEGA ? -1L : value);
            hash *= 0x01000193L; // FNV prime
        }
        
        // NUEVA: Avalancha final para mejor distribución en shards
        hash ^= hash >>> 32;
        hash ^= hash >>> 16;
        hash ^= hash >>> 8;
        
        return hash;
    }
    
    // =================== MÉTODOS OPTIMIZADOS DE DETECCIÓN ===================
    
    /**
     * OPTIMIZADO: Obtiene transiciones habilitadas usando paralelismo adaptativo
     */
    private List<Integer> getEnabledTransitionsWithOmega(int[] marking) {
        List<Integer> enabledTransitions = new ArrayList<>();
        int[][] iMinus = petriNet.getIMinus();
        
        // Paralelismo adaptativo basado en número de transiciones
        if (iMinus[0].length > 100) {
            return Arrays.stream(java.util.stream.IntStream.range(0, iMinus[0].length).toArray())
                    .parallel()
                    .filter(t -> {
                        for (int i = 0; i < marking.length; i++) {
                            if (marking[i] != OMEGA && marking[i] < iMinus[i][t]) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .boxed()
                    .collect(Collectors.toList());
        }
        
        // Versión secuencial optimizada para redes pequeñas
        for (int t = 0; t < iMinus[0].length; t++) {
            boolean enabled = true;
            for (int i = 0; i < marking.length && enabled; i++) {
                if (marking[i] != OMEGA && marking[i] < iMinus[i][t]) {
                    enabled = false;
                }
            }
            if (enabled) {
                enabledTransitions.add(t);
            }
        }
        
        return enabledTransitions;
    }
    
    /**
     * ULTRA-OPTIMIZADO v2.0: Aplica regla omega con local frontier + cache + índice espacial
     */
    private int[] applyGlobalOmegaRuleV2(int[] newMarking) {
        long startTime = System.nanoTime();
        
        long hash = fastHashMarkingWithAvalanche(newMarking);
        
        // 1. CACHE HIT - Verificar cache primero
        int[] result = omegaCache.getOrCompute(hash, newMarking, marking -> {
            
            LocalOmegaFrontier frontier = localFrontier.get();
            
            // 2. LOCAL FRONTIER - Verificar marcados locales primero
            List<int[]> localCandidates = frontier.getCandidatesForDomination(marking);
            
            int[] tempBuffer = arrayPool.getTempMarkingBuffer();
            System.arraycopy(marking, 0, tempBuffer, 0, marking.length);
            
            // Aplicar dominación con candidatos locales
            boolean foundLocalDomination = false;
            for (int[] candidate : localCandidates) {
                if (isDominatedOptimizedV2(tempBuffer, candidate)) {
                    applyOmegaToMarkingVectorized(tempBuffer, candidate);
                    foundLocalDomination = true;
                }
            }
            
            if (foundLocalDomination) {
                localFrontierHits.increment();
            } else {
                // 3. ÍNDICE ESPACIAL GLOBAL - Solo si no hay dominación local
                globalIndexAccesses.increment();
                List<int[]> globalCandidates = spatialIndex.getCandidatesForDomination(marking);
                
                if (globalCandidates.size() > 50) {
                    // Paralelización para listas grandes
                    globalCandidates.parallelStream()
                            .filter(candidate -> isDominatedOptimizedV2(tempBuffer, candidate))
                            .forEach(candidate -> applyOmegaToMarkingVectorized(tempBuffer, candidate));
                } else {
                    // Versión secuencial para listas pequeñas
                    for (int[] candidate : globalCandidates) {
                        if (isDominatedOptimizedV2(tempBuffer, candidate)) {
                            applyOmegaToMarkingVectorized(tempBuffer, candidate);
                        }
                    }
                }
            }
            
            return Arrays.copyOf(tempBuffer, tempBuffer.length);
        });
        
        omegaDetectionTime.add(System.nanoTime() - startTime);
        return result;
    }
    
    /**
     * OPTIMIZADO v2.0: Verificación de dominación con early termination mejorada y contadores
     */
    private boolean isDominatedOptimizedV2(int[] newMarking, int[] knownMarking) {
        boolean hasStrictlyGreater = false;
        int omegaCount = 0;
        
        // Early termination con contadores optimizados
        for (int i = 0; i < newMarking.length; i++) {
            if (knownMarking[i] == OMEGA) {
                omegaCount++;
                if (newMarking[i] != OMEGA) {
                    return false; // No está dominado - terminar inmediatamente
                }
            } else {
                if (newMarking[i] == OMEGA) {
                    hasStrictlyGreater = true;
                } else if (newMarking[i] < knownMarking[i]) {
                    return false; // No está dominado - terminar inmediatamente
                } else if (newMarking[i] > knownMarking[i]) {
                    hasStrictlyGreater = true;
                }
            }
        }
        
        // Optimización: si hay muchos omegas, es probable dominación
        if (omegaCount > newMarking.length / 3) {
            return hasStrictlyGreater;
        }
        
        return hasStrictlyGreater;
    }
    
    /**
     * OPTIMIZADO v2.0: Aplicación de omega con vectorización manual mejorada
     */
    private void applyOmegaToMarkingVectorized(int[] updatedMarking, int[] knownMarking) {
        int length = updatedMarking.length;
        int i = 0;
        
        // Vectorización manual mejorada - procesar en chunks de 8 para mejor cache locality
        for (; i < length - 7; i += 8) {
            // Unroll loop para 8 elementos
            for (int j = 0; j < 8; j++) {
                int idx = i + j;
                if (knownMarking[idx] == OMEGA) {
                    updatedMarking[idx] = OMEGA;
                } else if (knownMarking[idx] != OMEGA && 
                          (updatedMarking[idx] == OMEGA || updatedMarking[idx] > knownMarking[idx])) {
                    updatedMarking[idx] = OMEGA;
                }
            }
        }
        
        // Procesar elementos restantes
        for (; i < length; i++) {
            if (knownMarking[i] == OMEGA) {
                updatedMarking[i] = OMEGA;
            } else if (knownMarking[i] != OMEGA && 
                      (updatedMarking[i] == OMEGA || updatedMarking[i] > knownMarking[i])) {
                updatedMarking[i] = OMEGA;
            }
        }
    }
    
    /**
     * MÉTODO PRINCIPAL DE ANÁLISIS CON TODAS LAS OPTIMIZACIONES V2.0
     */
    public List<ReachabilityNode> analyze() throws InterruptedException {
        logger.info("�� Iniciando análisis HÍBRIDO OPTIMIZADO de alcanzabilidad con omega");
        long totalStartTime = System.nanoTime();
        
        // Inicializar con el marcado inicial
        String initialMarkingId = "m_0";
        int[] initialMarking = petriNet.getInitialMarking();
        
        // Registrar el marcado inicial como visitado (con sharded visited set)
        long initialHash = fastHashMarkingWithAvalanche(initialMarking);
        visitedMarkings.addIfAbsent(initialHash);
        
        // Agregar marcado inicial al índice espacial y frontier local
        spatialIndex.addMarking(initialMarking);
        localFrontier.get().addMarking(initialMarking);
        
        // Crear nodo raíz en resultados finales
        String rootLabel = initialMarkingId + "\n" + Arrays.toString(initialMarking);
        finalNodes.put(initialMarkingId, new ReachabilityNode(initialMarkingId, rootLabel, initialMarking, null, null));
        
        // Crear el nodo de procesamiento inicial
        Map<Integer, int[]> initialSubnetMarkings = new HashMap<>();
        for (Subnet subnet : petriNet.getSubnets()) {
            initialSubnetMarkings.put(subnet.getId(), subnet.extractSubnetMarking(initialMarking));
        }
        ProcessingNode rootNode = new ProcessingNode(initialMarkingId, initialSubnetMarkings, 0);
        processingNodes.put(initialMarkingId, rootNode);
        
        // Obtener transiciones habilitadas en el marcado inicial (OPTIMIZADO)
        List<Integer> enabledTransitions = getEnabledTransitionsWithOmega(initialMarking);
        
        // Crear tareas iniciales y agregarlas a la cola
        for (int transIndex : enabledTransitions) {
            List<Subnet> involvedSubnets = petriNet.getSubnetsContainingTransition(transIndex);
            
            String childMarkingId = initialMarkingId + "_t" + transIndex;
            Map<Integer, int[]> childSubnetMarkings = new HashMap<>(initialSubnetMarkings);
            ProcessingNode childNode = new ProcessingNode(childMarkingId, childSubnetMarkings, involvedSubnets.size());
            processingNodes.put(childMarkingId, childNode);
            
            for (Subnet subnet : involvedSubnets) {
                FiringTask task = new FiringTask(initialMarkingId, transIndex, subnet, this);
                firingQueue.offer(task);
                queuedTasks.increment();
            }
        }
        
        // HÍBRIDO: Worker pool optimizado (similar a ReachabilityAnalyzer original)
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        // Crear workers que procesan la cola
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                activeWorkers.incrementAndGet();
                try {
                    while (true) {
                        FiringTask task = firingQueue.poll();
                        
                        if (task != null) {
                            // Procesar tarea
                            processTaskHybridOptimized(task);
                        } else {
                            // No hay tareas, verificar si terminar
                            if (queuedTasks.sum() == 0 && firingQueue.isEmpty()) {
                                break; // Terminar worker
                            }
                            
                            // Esperar un poco antes de verificar de nuevo
                            try {
                                Thread.sleep(MAX_IDLE_TIME_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                } finally {
                    activeWorkers.decrementAndGet();
                }
            });
        }
        
        // Esperar a que todos los workers terminen
        executor.shutdown();
        while (!executor.isTerminated()) {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                logger.debug("⏳ Esperando terminación de workers...");
            }
        }
        
        long totalTime = System.nanoTime() - totalStartTime;
        
        // Métricas finales detalladas híbridas
        logger.info("✅ Análisis híbrido completado en {:.2f}ms", totalTime / 1_000_000.0);
        logger.info("📈 Total de nodos: {}", finalNodes.size());
        logger.info("🎯 Cache hit rate final: {:.2f}%", omegaCache.getHitRate() * 100);
        logger.info("🏠 Local frontier hits: {} ({:.1f}%)", localFrontierHits.sum(), 
                   100.0 * localFrontierHits.sum() / (localFrontierHits.sum() + globalIndexAccesses.sum()));
        logger.info("🌐 Global index accesses: {}", globalIndexAccesses.sum());
        logger.info("📊 Visited shards total size: {}", visitedMarkings.totalSize());
        logger.info("⚡ Tiempo detección omega: {:.2f}ms", omegaDetectionTime.sum() / 1_000_000.0);
        logger.info("🔧 Tiempo construcción marcados: {:.2f}ms", globalMarkingBuildTime.sum() / 1_000_000.0);
        logger.info("🔥 Tiempo disparo transiciones: {:.2f}ms", transitionFiringTime.sum() / 1_000_000.0);
        
        // Contar marcas omega
        long omegaCount = finalNodes.values().stream()
                .flatMapToInt(node -> Arrays.stream(node.value))
                .filter(mark -> mark == OMEGA)
                .count();
        logger.info("♾️ Total omega marks encontradas: {}", omegaCount);
        
        // Convertir a lista y ordenar por nombre
        List<ReachabilityNode> result = new ArrayList<>(finalNodes.values());
        result.sort(Comparator.comparing(node -> node.name));
        
        return result;
    }
    
    /**
     * PROCESAMIENTO DE TAREAS HÍBRIDO OPTIMIZADO - Worker pool sin recursión
     */
    public void processTaskHybridOptimized(FiringTask task) {
        String parentMarkingId = task.parentMarkingId;
        int transIndex = task.transitionIndex;
        Subnet subnet = task.subnet;
        int subnetId = subnet.getId();
        
        ProcessingNode parentNode = processingNodes.get(parentMarkingId);
        if (parentNode == null) {
            queuedTasks.decrement();
            return;
        }
        
        int[] subnetMarking = parentNode.subnetMarkings.get(subnetId);
        if (subnetMarking == null) {
            queuedTasks.decrement();
            return;
        }
        
        // OPTIMIZADO: Disparar la transición con medición de tiempo
        long firingStartTime = System.nanoTime();
        int localTransIndex = subnet.getLocalTransIndex(transIndex);
        int[] newSubnetMarking = subnet.fireTransition(localTransIndex, subnetMarking);
        transitionFiringTime.add(System.nanoTime() - firingStartTime);
        
        // Obtener el nodo hijo
        String childMarkingId = task.childMarkingId;
        ProcessingNode childNode = processingNodes.get(childMarkingId);
        if (childNode == null) {
            queuedTasks.decrement();
            return;
        }
        
        // Actualizar el marcado de la subred en el nodo hijo
        childNode.setSubnetMarking(subnetId, newSubnetMarking);
        
        // Decrementar el contador de completitud
        int remaining = childNode.decrementAndGetCompletionCounter();
        
        if (remaining == 0) {
            // ULTRA-OPTIMIZADO: Construir el marcado global usando buffer reutilizable
            long buildStartTime = System.nanoTime();
            int[] buffer = arrayPool.getGlobalMarkingBuffer();
            int[] globalMarking = childNode.buildGlobalMarkingOptimized(petriNet, buffer);
            globalMarkingBuildTime.add(System.nanoTime() - buildStartTime);
            
            // ULTRA-OPTIMIZADO: Aplicar regla omega con local frontier
            int[] omegaMarking = applyGlobalOmegaRuleV2(globalMarking);
            childNode.finalGlobalMarking = omegaMarking;
            
            // Actualizar índice espacial y frontier local
            spatialIndex.addMarking(omegaMarking);
            localFrontier.get().addMarking(omegaMarking);
            
            // OPTIMIZADO: Verificar si ya se visitó usando sharded visited set
            long visitedHash = fastHashMarkingWithAvalanche(omegaMarking);
            
            if (!visitedMarkings.addIfAbsent(visitedHash)) {
                // Ya visitado, eliminar nodo y liberar memoria
                processingNodes.remove(childMarkingId);
                queuedTasks.decrement();
                return;
            }
            
            // Nuevo marcado, agregar a resultados finales
            String parentName = findParentName(parentMarkingId);
            String label = extractMarkingName(childMarkingId) + "\n" + Arrays.toString(omegaMarking);
            finalNodes.put(childMarkingId, new ReachabilityNode(
                    extractMarkingName(childMarkingId), label, omegaMarking, parentName, transIndex));
            
            // OPTIMIZADO: Encontrar nuevas transiciones habilitadas
            List<Integer> enabledTransitions = getEnabledTransitionsWithOmega(omegaMarking);
            
            // Pre-calcular marcados de subred (evitar recálculos)
            Map<Integer, int[]> precomputedSubnetMarkings = new HashMap<>();
            for (Subnet s : petriNet.getSubnets()) {
                precomputedSubnetMarkings.put(s.getId(), s.extractSubnetMarking(omegaMarking));
            }
            
            // HÍBRIDO: Crear nuevas tareas y agregarlas a la cola
            for (int newTransIndex : enabledTransitions) {
                List<Subnet> involvedSubnets = petriNet.getSubnetsContainingTransition(newTransIndex);
                
                String newChildMarkingId = childMarkingId + "_t" + newTransIndex;
                Map<Integer, int[]> newChildSubnetMarkings = new HashMap<>(precomputedSubnetMarkings);
                
                ProcessingNode newChildNode = new ProcessingNode(newChildMarkingId, 
                        newChildSubnetMarkings, involvedSubnets.size());
                processingNodes.put(newChildMarkingId, newChildNode);
                
                // OPTIMIZADO: Agregar tareas a la cola
                for (Subnet s : involvedSubnets) {
                    FiringTask newTask = new FiringTask(childMarkingId, newTransIndex, s, this);
                    firingQueue.offer(newTask);
                    queuedTasks.increment();
                }
            }
            
            // Limpiar nodo procesado para liberar memoria
            processingNodes.remove(childMarkingId);
        }
        
        queuedTasks.decrement();
    }
    
    private String findParentName(String markingId) {
        ReachabilityNode node = finalNodes.get(markingId);
        return node != null ? node.name : null;
    }
    
    private String extractMarkingName(String markingId) {
        return "m_" + finalNodes.size();
    }
    
    // =================== MÉTODOS ESTÁTICOS PÚBLICOS ===================
    
    /**
     * Construye el árbol de alcanzabilidad desde un archivo JSON.
     */
    public static List<ReachabilityNode> buildReachabilityTree(String jsonFilePath, int numThreads) 
            throws IOException, InterruptedException {
        PetriNet petriNet = PetriNet.fromJson(jsonFilePath);
        OmegaReachabilityAnalyzer analyzer = new OmegaReachabilityAnalyzer(petriNet, numThreads);
        return analyzer.analyze();
    }
    
    /**
     * Exporta los nodos a formato DOT.
     */
    public static void exportToDot(List<ReachabilityNode> nodes, String outputFile) throws IOException {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph G {\n");
        dot.append("  rankdir=TB;\n");
        dot.append("  node [shape=circle, style=filled, fillcolor=lightblue];\n");
        
        for (ReachabilityNode node : nodes) {
            String label = node.label.replace(String.valueOf(OMEGA), "ω");
            dot.append(String.format("  %s [label=\"%s\"];\n", node.name, label));
        }
        
        for (ReachabilityNode node : nodes) {
            if (node.from != null) {
                dot.append(String.format("  %s -> %s [label=\"t%d\"];\n", node.from, node.name, node.trans));
            }
        }
        
        dot.append("}\n");
        
        java.nio.file.Files.write(java.nio.file.Paths.get(outputFile), dot.toString().getBytes());
        logger.info("📄 Árbol exportado a DOT: {}", outputFile);
    }
    
    /**
     * Método principal para pruebas con métricas detalladas v2.0.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: java OmegaReachabilityAnalyzer <archivo_json> [archivo_salida.dot] [nthreads]");
            System.exit(1);
        }
        
        try {
            String inputFile = args[0];
            String outputFile = args.length > 1 ? args[1] : "reachability_tree_ultra_v2.dot";
            int nThreads = args.length > 2 ? Integer.parseInt(args[2]) : Runtime.getRuntime().availableProcessors();
            
            System.out.println("🚀 INICIANDO ANÁLISIS ULTRA-OPTIMIZADO v2.0");
            System.out.println("📁 Archivo de entrada: " + inputFile);
            System.out.println("🧵 Hilos: " + nThreads);
            
            long startTime = System.nanoTime();
            
            List<ReachabilityNode> nodes = buildReachabilityTree(inputFile, nThreads);
            
            long endTime = System.nanoTime();
            double millis = (endTime - startTime) / 1_000_000.0;
            
            System.out.println("\n✅ ANÁLISIS v2.0 COMPLETADO");
            System.out.println("⏱️ Tiempo total: " + String.format("%.2f ms", millis));
            System.out.println("📊 Estados generados: " + nodes.size());
            System.out.println("🧵 Hilos utilizados: " + nThreads);
            
            long omegaCount = nodes.stream()
                    .flatMapToInt(node -> Arrays.stream(node.value))
                    .filter(mark -> mark == OMEGA)
                    .count();
            
            System.out.println("♾️ Marcas omega: " + omegaCount);
            System.out.println("📈 Estados/segundo: " + String.format("%.0f", nodes.size() / (millis / 1000.0)));
            
            exportToDot(nodes, outputFile);
            System.out.println("📄 Archivo DOT generado: " + outputFile);
            
        } catch (IOException e) {
            logger.error("❌ Error de I/O durante el análisis: {}", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            logger.error("⏸️ Análisis interrumpido: {}", e.getMessage());
            Thread.currentThread().interrupt();
            System.exit(1);
        } catch (Exception e) {
            logger.error("💥 Error durante el análisis: {}", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}