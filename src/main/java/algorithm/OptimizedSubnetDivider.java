package algorithm;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Divisor optimizado de subredes que corrige el problema de las "mega-subredes"
 * con todas las transiciones concentradas en una sola subred.
 *
 * Estrategia:
 * 1. Detectar subredes problemáticas (>50% transiciones)
 * 2. Redistribuir transiciones de forma balanceada
 * 3. Mantener conectividad semántica S3PR
 * 4. Crear subredes de tamaño similar para paralelización eficiente
 */
public class OptimizedSubnetDivider {
    private static final Logger logger = LogManager.getLogger(OptimizedSubnetDivider.class);

    // Configuración del algoritmo optimizado
    private static final int MAX_TRANSITIONS_PER_SUBNET = 5; // Límite para evitar mega-subredes
    private static final int TARGET_SUBNETS = 4; // Objetivo para redes pequeñas-medianas
    private static final double BALANCE_THRESHOLD = 0.4; // Máximo 40% de transiciones en una subred

    private final int[][] iMinus;
    private final int[][] iPlus;
    private final int numPlaces;
    private final int numTransitions;

    public OptimizedSubnetDivider(int[][] iMinus, int[][] iPlus) {
        this.iMinus = iMinus;
        this.iPlus = iPlus;
        this.numPlaces = iMinus.length;
        this.numTransitions = iMinus[0].length;

        logger.info("OptimizedSubnetDivider: {} lugares, {} transiciones", numPlaces, numTransitions);
    }

    /**
     * Crea una división optimizada basada en análisis de conectividad
     */
    public List<OptimizedSubnet> createOptimizedDivision() {
        logger.info("Iniciando división optimizada...");

        // Paso 1: Analizar conectividad entre transiciones
        TransitionConnectivity connectivity = analyzeTransitionConnectivity();

        // Paso 2: Crear grupos de transiciones balanceados
        List<Set<Integer>> transitionGroups = createBalancedTransitionGroups(connectivity);

        // Paso 3: Asignar lugares a cada grupo
        List<OptimizedSubnet> subnets = assignPlacesToGroups(transitionGroups);

        // Paso 4: Validar y optimizar la división
        validateAndOptimize(subnets);

        logger.info("División optimizada completada: {} subredes balanceadas", subnets.size());
        logDivisionStats(subnets);

        return subnets;
    }

    /**
     * Analiza la conectividad entre transiciones basada en lugares compartidos
     */
    private TransitionConnectivity analyzeTransitionConnectivity() {
        TransitionConnectivity connectivity = new TransitionConnectivity(numTransitions);

        // Para cada par de transiciones, calcular lugares compartidos
        for (int t1 = 0; t1 < numTransitions; t1++) {
            for (int t2 = t1 + 1; t2 < numTransitions; t2++) {
                int sharedPlaces = 0;

                for (int p = 0; p < numPlaces; p++) {
                    // Si ambas transiciones están conectadas al mismo lugar
                    boolean t1Connected = (iMinus[p][t1] > 0 || iPlus[p][t1] > 0);
                    boolean t2Connected = (iMinus[p][t2] > 0 || iPlus[p][t2] > 0);

                    if (t1Connected && t2Connected) {
                        sharedPlaces++;
                    }
                }

                connectivity.setConnectivity(t1, t2, sharedPlaces);
            }
        }

        return connectivity;
    }

    /**
     * Crea grupos balanceados de transiciones usando clustering por conectividad
     */
    private List<Set<Integer>> createBalancedTransitionGroups(TransitionConnectivity connectivity) {
        List<Set<Integer>> groups = new ArrayList<>();
        Set<Integer> unassigned = new HashSet<>();

        for (int t = 0; t < numTransitions; t++) {
            unassigned.add(t);
        }

        // Algoritmo greedy para clustering balanceado
        while (!unassigned.isEmpty() && groups.size() < TARGET_SUBNETS) {
            Set<Integer> newGroup = new HashSet<>();

            // Seleccionar transición inicial (la más conectada)
            int startTransition = findMostConnectedTransition(unassigned, connectivity);
            newGroup.add(startTransition);
            unassigned.remove(startTransition);

            // Agregar transiciones relacionadas hasta el límite
            while (newGroup.size() < MAX_TRANSITIONS_PER_SUBNET && !unassigned.isEmpty()) {
                int bestCandidate = findBestCandidateForGroup(newGroup, unassigned, connectivity);

                if (bestCandidate != -1) {
                    newGroup.add(bestCandidate);
                    unassigned.remove(bestCandidate);
                } else {
                    break; // No hay candidatos conectados
                }
            }

            groups.add(newGroup);
        }

        // Distribuir transiciones restantes
        distributeRemainingTransitions(groups, unassigned);

        return groups;
    }

    /**
     * Encuentra la transición más conectada en el conjunto dado
     */
    private int findMostConnectedTransition(Set<Integer> candidates, TransitionConnectivity connectivity) {
        int bestTransition = -1;
        int maxConnections = -1;

        for (int t : candidates) {
            int connections = connectivity.getTotalConnections(t, candidates);
            if (connections > maxConnections) {
                maxConnections = connections;
                bestTransition = t;
            }
        }

        return bestTransition != -1 ? bestTransition : candidates.iterator().next();
    }

    /**
     * Encuentra el mejor candidato para agregar al grupo actual
     */
    private int findBestCandidateForGroup(Set<Integer> group, Set<Integer> candidates,
                                         TransitionConnectivity connectivity) {
        int bestCandidate = -1;
        int maxConnectionToGroup = -1;

        for (int candidate : candidates) {
            int connectionToGroup = 0;

            for (int groupMember : group) {
                connectionToGroup += connectivity.getConnectivity(candidate, groupMember);
            }

            if (connectionToGroup > maxConnectionToGroup) {
                maxConnectionToGroup = connectionToGroup;
                bestCandidate = candidate;
            }
        }

        return maxConnectionToGroup > 0 ? bestCandidate : -1;
    }

    /**
     * Distribuye transiciones restantes de forma balanceada
     */
    private void distributeRemainingTransitions(List<Set<Integer>> groups, Set<Integer> unassigned) {
        if (unassigned.isEmpty()) return;

        // Si no hay grupos, crear uno nuevo
        if (groups.isEmpty()) {
            groups.add(new HashSet<>(unassigned));
            return;
        }

        // Distribuir de forma round-robin para mantener balance
        List<Integer> remaining = new ArrayList<>(unassigned);
        for (int i = 0; i < remaining.size(); i++) {
            int groupIndex = i % groups.size();
            groups.get(groupIndex).add(remaining.get(i));
        }
    }

    /**
     * Asigna lugares a cada grupo de transiciones
     */
    private List<OptimizedSubnet> assignPlacesToGroups(List<Set<Integer>> transitionGroups) {
        List<OptimizedSubnet> subnets = new ArrayList<>();

        for (int i = 0; i < transitionGroups.size(); i++) {
            Set<Integer> transitions = transitionGroups.get(i);
            Set<Integer> places = new HashSet<>();

            // Encontrar todos los lugares conectados a las transiciones del grupo
            for (int transition : transitions) {
                for (int p = 0; p < numPlaces; p++) {
                    if (iMinus[p][transition] > 0 || iPlus[p][transition] > 0) {
                        places.add(p);
                    }
                }
            }

            subnets.add(new OptimizedSubnet(i, places, transitions));
        }

        return subnets;
    }

    /**
     * Valida y optimiza la división creada
     */
    private void validateAndOptimize(List<OptimizedSubnet> subnets) {
        // Verificar que todas las transiciones estén asignadas
        Set<Integer> allAssignedTransitions = new HashSet<>();
        for (OptimizedSubnet subnet : subnets) {
            allAssignedTransitions.addAll(subnet.getTransitions());
        }

        if (allAssignedTransitions.size() != numTransitions) {
            logger.warn("¡Advertencia! No todas las transiciones fueron asignadas: {} de {}",
                       allAssignedTransitions.size(), numTransitions);
        }

        // Verificar balance
        for (OptimizedSubnet subnet : subnets) {
            double transitionRatio = (double) subnet.getTransitions().size() / numTransitions;
            if (transitionRatio > BALANCE_THRESHOLD) {
                logger.warn("Subred {} excede el threshold de balance: {:.2f}",
                           subnet.getId(), transitionRatio);
            }
        }
    }

    /**
     * Log de estadísticas de la división
     */
    private void logDivisionStats(List<OptimizedSubnet> subnets) {
        logger.info("=== ESTADÍSTICAS DE DIVISIÓN OPTIMIZADA ===");

        for (OptimizedSubnet subnet : subnets) {
            double transitionRatio = (double) subnet.getTransitions().size() / numTransitions * 100;
            double placeRatio = (double) subnet.getPlaces().size() / numPlaces * 100;

            logger.info("Subred {}: {} transiciones ({:.1f}%), {} lugares ({:.1f}%)",
                       subnet.getId(),
                       subnet.getTransitions().size(), transitionRatio,
                       subnet.getPlaces().size(), placeRatio);
        }

        // Detectar mega-subredes
        OptimizedSubnet largest = subnets.stream()
            .max(Comparator.comparing(s -> s.getTransitions().size()))
            .orElse(null);

        if (largest != null) {
            double largestRatio = (double) largest.getTransitions().size() / numTransitions;
            if (largestRatio > BALANCE_THRESHOLD) {
                logger.warn("¡MEGA-SUBRED DETECTADA! Subred {} tiene {:.1f}% de las transiciones",
                           largest.getId(), largestRatio * 100);
            } else {
                logger.info("✅ División balanceada - subred más grande: {:.1f}% de transiciones",
                           largestRatio * 100);
            }
        }
    }

    /**
     * Clase para manejar conectividad entre transiciones
     */
    private static class TransitionConnectivity {
        private final int[][] connectivity;
        private final int numTransitions;

        public TransitionConnectivity(int numTransitions) {
            this.numTransitions = numTransitions;
            this.connectivity = new int[numTransitions][numTransitions];
        }

        public void setConnectivity(int t1, int t2, int value) {
            connectivity[t1][t2] = value;
            connectivity[t2][t1] = value;
        }

        public int getConnectivity(int t1, int t2) {
            return connectivity[t1][t2];
        }

        public int getTotalConnections(int transition, Set<Integer> candidates) {
            int total = 0;
            for (int other : candidates) {
                if (other != transition) {
                    total += connectivity[transition][other];
                }
            }
            return total;
        }
    }

    /**
     * Clase optimizada para representar una subred
     */
    public static class OptimizedSubnet {
        private final int id;
        private final Set<Integer> places;
        private final Set<Integer> transitions;

        public OptimizedSubnet(int id, Set<Integer> places, Set<Integer> transitions) {
            this.id = id;
            this.places = new HashSet<>(places);
            this.transitions = new HashSet<>(transitions);
        }

        public int getId() { return id; }
        public Set<Integer> getPlaces() { return places; }
        public Set<Integer> getTransitions() { return transitions; }

        @Override
        public String toString() {
            return String.format("OptimizedSubnet[id=%d, places=%d, transitions=%d]",
                               id, places.size(), transitions.size());
        }
    }

    /**
     * Main method para ser llamado desde Python para optimizar divisiones
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: OptimizedSubnetDivider <archivo_red.json>");
            System.exit(1);
        }

        String networkFile = args[0];

        try {
            // Cargar red desde JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> networkData = mapper.readValue(new java.io.File(networkFile), java.util.Map.class);

            // Extraer matrices
            @SuppressWarnings("unchecked")
            java.util.List<java.util.List<Integer>> iMinusList = (java.util.List<java.util.List<Integer>>) networkData.get("I_minus");
            @SuppressWarnings("unchecked")
            java.util.List<java.util.List<Integer>> iPlusList = (java.util.List<java.util.List<Integer>>) networkData.get("I_plus");
            @SuppressWarnings("unchecked")
            java.util.List<Integer> m0 = (java.util.List<Integer>) networkData.get("M0");

            // Convertir a arrays
            int[][] iMinus = new int[iMinusList.size()][];
            int[][] iPlus = new int[iPlusList.size()][];

            for (int i = 0; i < iMinusList.size(); i++) {
                iMinus[i] = iMinusList.get(i).stream().mapToInt(Integer::intValue).toArray();
                iPlus[i] = iPlusList.get(i).stream().mapToInt(Integer::intValue).toArray();
            }

            // Crear divisor optimizado
            OptimizedSubnetDivider divider = new OptimizedSubnetDivider(iMinus, iPlus);
            java.util.List<OptimizedSubnet> optimizedSubnets = divider.createOptimizedDivision();

            // Convertir subredes optimizadas a formato JSON
            java.util.List<java.util.Map<String, Object>> subnetDefinitions = new java.util.ArrayList<>();
            for (OptimizedSubnet subnet : optimizedSubnets) {
                java.util.Map<String, Object> subnetDef = new java.util.HashMap<>();
                subnetDef.put("place_indices", new java.util.ArrayList<>(subnet.getPlaces()));
                subnetDef.put("trans_indices", new java.util.ArrayList<>(subnet.getTransitions()));
                subnetDefinitions.add(subnetDef);
            }

            // Actualizar red con división optimizada
            networkData.put("subnet_definitions", subnetDefinitions);

            // Escribir red optimizada de vuelta al archivo
            mapper.writeValue(new java.io.File(networkFile), networkData);

            System.out.println("OptimizedSubnetDivider: División optimizada completada para " + networkFile);

        } catch (Exception e) {
            System.err.println("Error procesando red: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}