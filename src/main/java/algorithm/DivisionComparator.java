package algorithm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;

/**
 * Comparador de algoritmos de división de subredes.
 * Analiza la división actual vs la división optimizada.
 */
public class DivisionComparator {
    private static final Logger logger = LogManager.getLogger(DivisionComparator.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java DivisionComparator <archivo_red.json>");
            System.exit(1);
        }

        String inputFile = args[0];

        try {
            logger.info("=== COMPARADOR DE ALGORITMOS DE DIVISIÓN ===");
            logger.info("Archivo: {}", inputFile);

            // Cargar red de Petri
            NetworkData network = loadNetwork(inputFile);
            logger.info("Red cargada: {} lugares, {} transiciones",
                       network.numPlaces, network.numTransitions);

            // Analizar división actual
            logger.info("\n🔍 ANALIZANDO DIVISIÓN ACTUAL...");
            analyzCurrentDivision(network);

            // Crear división optimizada
            logger.info("\n🚀 CREANDO DIVISIÓN OPTIMIZADA...");
            OptimizedSubnetDivider divider = new OptimizedSubnetDivider(network.iMinus, network.iPlus);
            List<OptimizedSubnetDivider.OptimizedSubnet> optimizedSubnets = divider.createOptimizedDivision();

            // Comparar resultados
            logger.info("\n📊 COMPARACIÓN DE RESULTADOS...");
            compareResults(network, optimizedSubnets);

        } catch (Exception e) {
            logger.error("Error en comparación de divisiones", e);
            System.exit(1);
        }
    }

    /**
     * Carga la red de Petri desde el archivo JSON
     */
    private static NetworkData loadNetwork(String filename) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(filename));

        // Parse M0
        JsonNode m0Array = root.get("M0");
        int numPlaces = m0Array.size();

        // Parse I_minus
        JsonNode iMinusArray = root.get("I_minus");
        int numTransitions = iMinusArray.get(0).size();

        int[][] iMinus = new int[numPlaces][numTransitions];
        for (int p = 0; p < numPlaces; p++) {
            for (int t = 0; t < numTransitions; t++) {
                iMinus[p][t] = iMinusArray.get(p).get(t).asInt();
            }
        }

        // Parse I_plus
        JsonNode iPlusArray = root.get("I_plus");
        int[][] iPlus = new int[numPlaces][numTransitions];
        for (int p = 0; p < numPlaces; p++) {
            for (int t = 0; t < numTransitions; t++) {
                iPlus[p][t] = iPlusArray.get(p).get(t).asInt();
            }
        }

        // Parse subnet definitions (actual)
        JsonNode subnetsArray = root.get("subnet_definitions");
        int numSubnets = subnetsArray.size();

        SubnetInfo[] currentSubnets = new SubnetInfo[numSubnets];
        for (int s = 0; s < numSubnets; s++) {
            JsonNode subnet = subnetsArray.get(s);

            JsonNode placeIndices = subnet.get("place_indices");
            int[] places = new int[placeIndices.size()];
            for (int i = 0; i < places.length; i++) {
                places[i] = placeIndices.get(i).asInt();
            }

            JsonNode transIndices = subnet.get("trans_indices");
            int[] transitions = new int[transIndices.size()];
            for (int i = 0; i < transitions.length; i++) {
                transitions[i] = transIndices.get(i).asInt();
            }

            currentSubnets[s] = new SubnetInfo(s, places, transitions);
        }

        return new NetworkData(numPlaces, numTransitions, iMinus, iPlus, currentSubnets);
    }

    /**
     * Analiza la división actual del archivo
     */
    private static void analyzCurrentDivision(NetworkData network) {
        logger.info("División actual: {} subredes", network.currentSubnets.length);

        int maxTransitions = 0;
        int totalTransitionsAssigned = 0;
        SubnetInfo problematicSubnet = null;

        for (SubnetInfo subnet : network.currentSubnets) {
            double transitionRatio = (double) subnet.transitions.length / network.numTransitions * 100;
            double placeRatio = (double) subnet.places.length / network.numPlaces * 100;

            logger.info("  Subred {}: {} transiciones ({:.1f}%), {} lugares ({:.1f}%)",
                       subnet.id, subnet.transitions.length, transitionRatio,
                       subnet.places.length, placeRatio);

            totalTransitionsAssigned += subnet.transitions.length;

            if (subnet.transitions.length > maxTransitions) {
                maxTransitions = subnet.transitions.length;
                problematicSubnet = subnet;
            }
        }

        // Detectar problemas
        double maxTransitionRatio = (double) maxTransitions / network.numTransitions;
        if (maxTransitionRatio > 0.5) {
            logger.warn("🚨 MEGA-SUBRED DETECTADA!");
            logger.warn("   Subred {} tiene {:.1f}% de todas las transiciones",
                       problematicSubnet.id, maxTransitionRatio * 100);
            logger.warn("   Esto causará overhead masivo en Java");
        }

        // Advertencia sobre solapamiento de transiciones
        if (totalTransitionsAssigned > network.numTransitions) {
            int overlap = totalTransitionsAssigned - network.numTransitions;
            logger.warn("🚨 SOLAPAMIENTO DE TRANSICIONES!");
            logger.warn("   {} transiciones asignadas múltiples veces", overlap);
            logger.warn("   Esto multiplicará el trabajo en Java");
        }
    }

    /**
     * Compara los resultados de ambas divisiones
     */
    private static void compareResults(NetworkData network,
                                     List<OptimizedSubnetDivider.OptimizedSubnet> optimizedSubnets) {

        logger.info("=== COMPARACIÓN FINAL ===");

        // Estadísticas división actual
        int currentMaxTransitions = 0;
        for (SubnetInfo subnet : network.currentSubnets) {
            currentMaxTransitions = Math.max(currentMaxTransitions, subnet.transitions.length);
        }

        // Estadísticas división optimizada
        int optimizedMaxTransitions = 0;
        for (OptimizedSubnetDivider.OptimizedSubnet subnet : optimizedSubnets) {
            optimizedMaxTransitions = Math.max(optimizedMaxTransitions, subnet.getTransitions().size());
        }

        double currentMaxRatio = (double) currentMaxTransitions / network.numTransitions * 100;
        double optimizedMaxRatio = (double) optimizedMaxTransitions / network.numTransitions * 100;

        logger.info("📈 ACTUAL:");
        logger.info("   Subredes: {}", network.currentSubnets.length);
        logger.info("   Subred más grande: {} transiciones ({:.1f}%)",
                   currentMaxTransitions, currentMaxRatio);

        logger.info("📈 OPTIMIZADA:");
        logger.info("   Subredes: {}", optimizedSubnets.size());
        logger.info("   Subred más grande: {} transiciones ({:.1f}%)",
                   optimizedMaxTransitions, optimizedMaxRatio);

        // Mejora esperada
        double improvement = (currentMaxTransitions - optimizedMaxTransitions) / (double) currentMaxTransitions * 100;
        logger.info("✨ MEJORA ESPERADA:");
        logger.info("   Reducción en subred más grande: {:.1f}%", improvement);

        if (improvement > 0) {
            logger.info("   🚀 La división optimizada debería mejorar significativamente el rendimiento de Java");
        } else {
            logger.info("   ⚠️  La división actual no es tan problemática");
        }

        // Cálculo de overhead esperado en Java
        int currentOverhead = calculateJavaOverhead(network.currentSubnets);
        int optimizedOverhead = calculateOptimizedJavaOverhead(optimizedSubnets);

        logger.info("💻 OVERHEAD JAVA:");
        logger.info("   Actual: {} FiringTasks promedio por transición", currentOverhead);
        logger.info("   Optimizada: {} FiringTasks promedio por transición", optimizedOverhead);

        double overheadReduction = (currentOverhead - optimizedOverhead) / (double) currentOverhead * 100;
        logger.info("   Reducción de overhead: {:.1f}%", overheadReduction);
    }

    /**
     * Calcula el overhead de Java con la división actual
     */
    private static int calculateJavaOverhead(SubnetInfo[] subnets) {
        // En Java, cada transición habilitada crea una FiringTask por cada subred que la contiene
        int totalOverhead = 0;

        for (int t = 0; t < subnets[0].transitions.length; t++) { // Para cada transición
            int subnetsContainingTransition = 0;

            for (SubnetInfo subnet : subnets) {
                for (int subnetTransition : subnet.transitions) {
                    if (subnetTransition == t) {
                        subnetsContainingTransition++;
                        break;
                    }
                }
            }

            totalOverhead += subnetsContainingTransition;
        }

        return totalOverhead;
    }

    /**
     * Calcula el overhead de Java con la división optimizada
     */
    private static int calculateOptimizedJavaOverhead(List<OptimizedSubnetDivider.OptimizedSubnet> subnets) {
        // En la división optimizada, cada transición debería estar en exactamente una subred
        return subnets.stream().mapToInt(s -> s.getTransitions().size()).sum();
    }

    /**
     * Clase para datos de la red
     */
    private static class NetworkData {
        final int numPlaces;
        final int numTransitions;
        final int[][] iMinus;
        final int[][] iPlus;
        final SubnetInfo[] currentSubnets;

        NetworkData(int numPlaces, int numTransitions, int[][] iMinus, int[][] iPlus, SubnetInfo[] currentSubnets) {
            this.numPlaces = numPlaces;
            this.numTransitions = numTransitions;
            this.iMinus = iMinus;
            this.iPlus = iPlus;
            this.currentSubnets = currentSubnets;
        }
    }

    /**
     * Clase para información de subred actual
     */
    private static class SubnetInfo {
        final int id;
        final int[] places;
        final int[] transitions;

        SubnetInfo(int id, int[] places, int[] transitions) {
            this.id = id;
            this.places = places;
            this.transitions = transitions;
        }
    }
}