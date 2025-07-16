package algorithm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
        import java.util.concurrent.*;
        import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.Arrays;


/**
 * Representa una red de Petri con sus matrices y subredes.
 */
class PetriNet {
    private static final Logger logger = LogManager.getLogger(PetriNet.class);

    private int[] initialMarking;
    private int[][] iMinus;     // Matriz de Pre (I-)
    private int[][] iPlus;      // Matriz de Post (I+)
    private List<Subnet> subnets;

    public PetriNet(int[] initialMarking, int[][] iMinus, int[][] iPlus, List<Subnet> subnets) {
        this.initialMarking = initialMarking;
        this.iMinus = iMinus;
        this.iPlus = iPlus;
        this.subnets = subnets;
    }

    public int[] getInitialMarking() {
        return initialMarking;
    }

    public int[][] getIMinus() {
        return iMinus;
    }

    public int[][] getIPlus() {
        return iPlus;
    }

    public List<Subnet> getSubnets() {
        return subnets;
    }

    /**
     * Verifica si una transición está habilitada en un marcado dado.
     */
    public boolean isTransitionEnabled(int transIndex, int[] marking) {
        for (int placeIndex = 0; placeIndex < marking.length; placeIndex++) {
            if (marking[placeIndex] == -1) continue; // omega always satisfies
            if (iMinus[placeIndex][transIndex] > marking[placeIndex]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Obtiene todas las transiciones habilitadas para un marcado dado.
     */
    public List<Integer> getEnabledTransitions(int[] marking) {
        List<Integer> enabledTransitions = new ArrayList<>();
        for (int t = 0; t < iMinus[0].length; t++) {
            if (isTransitionEnabled(t, marking)) {
                enabledTransitions.add(t);
            }
        }
        return enabledTransitions;
    }

    /**
     * Obtiene las subredes que contienen una transición específica.
     */
    public List<Subnet> getSubnetsContainingTransition(int transIndex) {
        return subnets.stream()
                .filter(subnet -> subnet.containsTransition(transIndex))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene una subred por su ID.
     */
    public Subnet getSubnetById(int subnetId) {
        return subnets.stream()
                .filter(subnet -> subnet.getId() == subnetId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Obtiene el número de lugares en la red de Petri.
     */
    public int getNumPlaces() {
        return initialMarking.length;
    }

    /**
     * Obtiene el número de transiciones en la red de Petri.
     */
    public int getNumTransitions() {
        return iMinus[0].length;
    }

    /**
     * Carga una red de Petri desde un archivo JSON.
     */
    public static PetriNet fromJson(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(new File(filePath));

        // Cargar el marcado inicial
        JsonNode m0Node = rootNode.get("M0");
        int[] initialMarking = new int[m0Node.size()];
        for (int i = 0; i < m0Node.size(); i++) {
            initialMarking[i] = m0Node.get(i).asInt();
        }

        // Cargar la matriz I-
        JsonNode iMinusNode = rootNode.get("I_minus");
        int[][] iMinus = new int[iMinusNode.size()][iMinusNode.get(0).size()];
        for (int i = 0; i < iMinusNode.size(); i++) {
            JsonNode row = iMinusNode.get(i);
            for (int j = 0; j < row.size(); j++) {
                iMinus[i][j] = row.get(j).asInt();
            }
        }

        // Cargar la matriz I+
        JsonNode iPlusNode = rootNode.get("I_plus");
        int[][] iPlus = new int[iPlusNode.size()][iPlusNode.get(0).size()];
        for (int i = 0; i < iPlusNode.size(); i++) {
            JsonNode row = iPlusNode.get(i);
            for (int j = 0; j < row.size(); j++) {
                iPlus[i][j] = row.get(j).asInt();
            }
        }

        // Cargar las definiciones de subredes
        JsonNode subnetsNode = rootNode.get("subnet_definitions");
        List<Subnet> subnets = new ArrayList<>();
        for (JsonNode subnetNode : subnetsNode) {
            int id = subnetNode.get("id").asInt();

            // Cargar índices de lugares
            JsonNode placeIndicesNode = subnetNode.get("place_indices");
            int[] placeIndices = new int[placeIndicesNode.size()];
            for (int i = 0; i < placeIndicesNode.size(); i++) {
                placeIndices[i] = placeIndicesNode.get(i).asInt();
            }

            // Cargar índices de transiciones
            JsonNode transIndicesNode = subnetNode.get("trans_indices");
            int[] transIndices = new int[transIndicesNode.size()];
            for (int i = 0; i < transIndicesNode.size(); i++) {
                transIndices[i] = transIndicesNode.get(i).asInt();
            }

            // Construir la subred
            Subnet subnet = new Subnet(id, placeIndices, transIndices, iMinus, iPlus);
            subnets.add(subnet);
        }

        return new PetriNet(initialMarking, iMinus, iPlus, subnets);
    }
}