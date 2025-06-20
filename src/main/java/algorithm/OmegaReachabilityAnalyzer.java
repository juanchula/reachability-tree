package algorithm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementación del algoritmo de árbol de alcanzabilidad con marcas omega (ω).
 * Usa Integer.MAX_VALUE para representar ω.
 */
public class OmegaReachabilityAnalyzer {
    private static final Logger logger = LogManager.getLogger(OmegaReachabilityAnalyzer.class);
    
    // Constante para representar omega (infinito)
    public static final int OMEGA = Integer.MAX_VALUE;
    
    // Clase auxiliar para pares de marcado y vector de disparo
    public static class MarkingPair {
        public final int[] marking;
        public final int[] firingVector;
        public final String parentId;
        
        public MarkingPair(int[] marking, int[] firingVector, String parentId) {
            this.marking = Arrays.copyOf(marking, marking.length);
            this.firingVector = Arrays.copyOf(firingVector, firingVector.length);
            this.parentId = parentId;
        }
    }
    
    // Clase para representar nodos del árbol de alcanzabilidad
    public static class ReachabilityNode {
        public final String id;
        public final int[] marking;
        public final List<String> children;
        public final String parentId;
        public final int[] firingVector;
        
        public ReachabilityNode(String id, int[] marking, String parentId, int[] firingVector) {
            this.id = id;
            this.marking = Arrays.copyOf(marking, marking.length);
            this.children = new ArrayList<>();
            this.parentId = parentId;
            this.firingVector = firingVector != null ? Arrays.copyOf(firingVector, firingVector.length) : null;
        }
    }
    
    /**
     * Obtiene las transiciones habilitadas para un marcado dado.
     * Una transición t está habilitada si para cada plaza i:
     * marking[i] == OMEGA || marking[i] >= I_minus[i][t]
     */
    public static boolean[] getEnabledTransitions(int[][] I_minus, int[] marking) {
        int numTransitions = I_minus[0].length;
        int numPlaces = I_minus.length;
        boolean[] enabled = new boolean[numTransitions];
        
        for (int t = 0; t < numTransitions; t++) {
            enabled[t] = true;
            for (int i = 0; i < numPlaces; i++) {
                if (marking[i] != OMEGA && marking[i] < I_minus[i][t]) {
                    enabled[t] = false;
                    break;
                }
            }
        }
        
        return enabled;
    }
    
    /**
     * Dispara una transición en un marcado dado.
     * Calcula newMarking = marking + incidence · firingVector
     * Si marking[i] == OMEGA, newMarking[i] = OMEGA
     */
    public static int[] fireTransition(int[] marking, int[][] incidence, int[] firingVector) {
        int numPlaces = marking.length;
        int[] newMarking = new int[numPlaces];
        
        for (int i = 0; i < numPlaces; i++) {
            if (marking[i] == OMEGA) {
                newMarking[i] = OMEGA;
            } else {
                // Calcular el efecto de la transición: incidence[i] · firingVector
                int effect = 0;
                for (int t = 0; t < firingVector.length; t++) {
                    effect += incidence[i][t] * firingVector[t];
                }
                
                long result = (long) marking[i] + effect;
                if (result < 0) {
                    newMarking[i] = 0; // No puede ser negativo
                } else if (result > OMEGA - 1) {
                    newMarking[i] = OMEGA; // Overflow protection
                } else {
                    newMarking[i] = (int) result;
                }
            }
        }
        
        return newMarking;
    }
    
    /**
     * Actualiza un marcado aplicando la regla omega.
     * Para cada marcado conocido k:
     * - Comprueba dominancia
     * - Si domina y existe al menos una plaza con newMarking[i] > k[i], marca como OMEGA
     */
    public static int[] updateMarking(int[] newMarking, List<int[]> knownMarkings) {
        int[] updatedMarking = Arrays.copyOf(newMarking, newMarking.length);
        
        for (int[] knownMarking : knownMarkings) {
            if (isDominated(updatedMarking, knownMarking)) {
                // Aplicar regla omega
                for (int i = 0; i < updatedMarking.length; i++) {
                    if (knownMarking[i] == OMEGA) {
                        updatedMarking[i] = OMEGA;
                    } else if (knownMarking[i] != OMEGA && 
                              (updatedMarking[i] == OMEGA || updatedMarking[i] > knownMarking[i])) {
                        updatedMarking[i] = OMEGA;
                    }
                }
            }
        }
        
        return updatedMarking;
    }
    
    /**
     * Verifica si newMarking está dominado por knownMarking según las reglas omega.
     */
    private static boolean isDominated(int[] newMarking, int[] knownMarking) {
        boolean hasStrictlyGreater = false;
        
        for (int i = 0; i < newMarking.length; i++) {
            if (knownMarking[i] == OMEGA) {
                if (newMarking[i] != OMEGA) {
                    return false; // No está dominado
                }
            } else {
                if (newMarking[i] != OMEGA && newMarking[i] < knownMarking[i]) {
                    return false; // No está dominado
                }
                if (newMarking[i] == OMEGA || newMarking[i] > knownMarking[i]) {
                    hasStrictlyGreater = true;
                }
            }
        }
        
        return hasStrictlyGreater;
    }
    
    /**
     * Construye el árbol de alcanzabilidad con marcas omega.
     */
    public static Map<String, ReachabilityNode> buildReachabilityTree(String jsonFilePath) throws IOException {
        // Cargar datos del archivo JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(new File(jsonFilePath));
        
        // Cargar marcado inicial
        JsonNode m0Node = rootNode.get("M0");
        int[] initialMarking = new int[m0Node.size()];
        for (int i = 0; i < m0Node.size(); i++) {
            initialMarking[i] = m0Node.get(i).asInt();
        }
        
        // Cargar matriz I_minus
        JsonNode iMinusNode = rootNode.get("I_minus");
        int[][] I_minus = new int[iMinusNode.size()][iMinusNode.get(0).size()];
        for (int i = 0; i < iMinusNode.size(); i++) {
            JsonNode row = iMinusNode.get(i);
            for (int j = 0; j < row.size(); j++) {
                I_minus[i][j] = row.get(j).asInt();
            }
        }
        
        // Cargar matriz I_plus
        JsonNode iPlusNode = rootNode.get("I_plus");
        int[][] I_plus = new int[iPlusNode.size()][iPlusNode.get(0).size()];
        for (int i = 0; i < iPlusNode.size(); i++) {
            JsonNode row = iPlusNode.get(i);
            for (int j = 0; j < row.size(); j++) {
                I_plus[i][j] = row.get(j).asInt();
            }
        }
        
        // Calcular matriz de incidencia
        int[][] incidence = new int[I_plus.length][I_plus[0].length];
        for (int i = 0; i < I_plus.length; i++) {
            for (int j = 0; j < I_plus[0].length; j++) {
                incidence[i][j] = I_plus[i][j] - I_minus[i][j];
            }
        }
        
        return buildReachabilityTreeInternal(initialMarking, I_minus, incidence);
    }
    
    /**
     * Implementación interna del algoritmo de construcción del árbol.
     */
    private static Map<String, ReachabilityNode> buildReachabilityTreeInternal(
            int[] initialMarking, int[][] I_minus, int[][] incidence) {
        
        Map<String, ReachabilityNode> nodes = new HashMap<>();
        Set<List<Integer>> visited = new HashSet<>();
        Deque<MarkingPair> queue = new ArrayDeque<>();
        List<int[]> knownMarkings = new ArrayList<>();
        AtomicInteger nodeCounter = new AtomicInteger(0);
        
        // Crear nodo raíz
        String rootId = "m" + nodeCounter.getAndIncrement();
        ReachabilityNode rootNode = new ReachabilityNode(rootId, initialMarking, null, null);
        nodes.put(rootId, rootNode);
        visited.add(Arrays.stream(initialMarking).boxed().collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
        knownMarkings.add(Arrays.copyOf(initialMarking, initialMarking.length));
        
        // Agregar transiciones habilitadas iniciales a la cola
        boolean[] enabledTransitions = getEnabledTransitions(I_minus, initialMarking);
        for (int t = 0; t < enabledTransitions.length; t++) {
            if (enabledTransitions[t]) {
                int[] firingVector = new int[enabledTransitions.length];
                firingVector[t] = 1;
                queue.offer(new MarkingPair(initialMarking, firingVector, rootId));
            }
        }
        
        logger.info("Iniciando construcción del árbol de alcanzabilidad con omega");
        
        // BFS para construir el árbol
        while (!queue.isEmpty()) {
            MarkingPair current = queue.poll();
            
            // Disparar transición
            int[] newMarking = fireTransition(current.marking, incidence, current.firingVector);
            
            // Aplicar regla omega
            newMarking = updateMarking(newMarking, knownMarkings);
            
            // Verificar si ya fue visitado
            List<Integer> markingList = Arrays.stream(newMarking).boxed()
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            if (!visited.contains(markingList)) {
                visited.add(markingList);
                knownMarkings.add(Arrays.copyOf(newMarking, newMarking.length));
                
                // Crear nuevo nodo
                String nodeId = "m" + nodeCounter.getAndIncrement();
                ReachabilityNode newNode = new ReachabilityNode(nodeId, newMarking, current.parentId, current.firingVector);
                nodes.put(nodeId, newNode);
                
                // Agregar arista al padre
                if (current.parentId != null) {
                    nodes.get(current.parentId).children.add(nodeId);
                }
                
                // Agregar nuevas transiciones habilitadas
                boolean[] newEnabledTransitions = getEnabledTransitions(I_minus, newMarking);
                for (int t = 0; t < newEnabledTransitions.length; t++) {
                    if (newEnabledTransitions[t]) {
                        int[] firingVector = new int[newEnabledTransitions.length];
                        firingVector[t] = 1;
                        queue.offer(new MarkingPair(newMarking, firingVector, nodeId));
                    }
                }
                
                if (nodes.size() % 100 == 0) {
                    logger.info("Nodos procesados: {}, Cola: {}", nodes.size(), queue.size());
                }
            }
        }
        
        logger.info("Árbol de alcanzabilidad completado. Total de nodos: {}", nodes.size());
        return nodes;
    }
    
    /**
     * Exporta el árbol de alcanzabilidad a formato DOT.
     */
    public static void exportToDot(Map<String, ReachabilityNode> nodes, String outputFile) throws IOException {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph ReachabilityTree {\n");
        dot.append("  rankdir=TB;\n");
        dot.append("  node [shape=circle];\n\n");
        
        // Agregar nodos
        for (ReachabilityNode node : nodes.values()) {
            String markingStr = Arrays.toString(node.marking).replace(String.valueOf(OMEGA), "ω");
            dot.append(String.format("  %s [label=\"%s\\n%s\"];\n", 
                    node.id, node.id, markingStr));
        }
        
        dot.append("\n");
        
        // Agregar aristas
        for (ReachabilityNode node : nodes.values()) {
            for (String childId : node.children) {
                ReachabilityNode child = nodes.get(childId);
                if (child != null && child.firingVector != null) {
                    // Encontrar la transición disparada
                    int transition = -1;
                    for (int i = 0; i < child.firingVector.length; i++) {
                        if (child.firingVector[i] > 0) {
                            transition = i;
                            break;
                        }
                    }
                    dot.append(String.format("  %s -> %s [label=\"t%d\"];\n", 
                            node.id, childId, transition));
                }
            }
        }
        
        dot.append("}\n");
        
        // Escribir archivo
        java.nio.file.Files.write(java.nio.file.Paths.get(outputFile), 
                dot.toString().getBytes());
        
        logger.info("Árbol exportado a DOT: {}", outputFile);
    }
    
    /**
     * Método principal para pruebas.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: java OmegaReachabilityAnalyzer <archivo_json> [archivo_salida.dot]");
            System.exit(1);
        }
        
        try {
            String inputFile = args[0];
            String outputFile = args.length > 1 ? args[1] : "reachability_tree_omega.dot";
            
            long startTime = System.nanoTime();
            
            Map<String, ReachabilityNode> tree = buildReachabilityTree(inputFile);
            
            long endTime = System.nanoTime();
            double millis = (endTime - startTime) / 1_000_000.0;
            
            System.out.println("Tiempo de construcción: " + millis + " ms");
            System.out.println("Número total de estados: " + tree.size());
            
            // Contar marcados con omega
            long omegaCount = tree.values().stream()
                    .mapToLong(node -> Arrays.stream(node.marking)
                            .filter(mark -> mark == OMEGA)
                            .count())
                    .sum();
            
            System.out.println("Marcas omega encontradas: " + omegaCount);
            
            exportToDot(tree, outputFile);
            
        } catch (Exception e) {
            logger.error("Error durante el análisis: {}", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
} 