package subdivide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Procesador principal que toma la salida del generador S3PR y la convierte
 * al formato requerido por el algoritmo de alcanzabilidad
 */
public class NetworkSubdivider {
    
    private ObjectMapper mapper;
    
    public NetworkSubdivider() {
        this.mapper = new ObjectMapper();
    }
    
    /**
     * Procesa un archivo de red generado y lo divide inteligentemente
     */
    public DividedNetworkOutput processNetworkFile(String inputFile) throws IOException {
        // Leer archivo JSON del generador
        JsonNode root = mapper.readTree(new File(inputFile));
        
        // Parsear estructuras
        int[] initialMarking = parseInitialMarking(root.get("M0"));
        int[][] iMinus = parseMatrix(root.get("I_minus"));
        int[][] iPlus = parseMatrix(root.get("I_plus"));
        List<SubnetDefinition> originalSubnets = parseSubnetDefinitions(root.get("subnet_definitions"));
        
        // Crear estructura para análisis
        PetriNetStructure structure = new PetriNetStructure(iMinus, iPlus, initialMarking, originalSubnets);
        
        System.out.println("Analizando red: " + structure);
        System.out.println("Trenes detectados: " + structure.getDetectedTrains().size());
        for (int i = 0; i < structure.getDetectedTrains().size(); i++) {
            System.out.println("  Tren " + i + ": " + structure.getDetectedTrains().get(i));
        }
        System.out.println("Transiciones de recursos compartidos: " + structure.getSharedResourceTransitions().size());
        
        // Dividir inteligentemente
        IntelligentNetworkDivider divider = new IntelligentNetworkDivider(structure);
        IntelligentNetworkDivider.DivisionResult result = divider.divideNetwork();
        
        System.out.println("División completada: " + result);
        for (OptimizedSubnet subnet : result.getSubnets()) {
            System.out.println("  " + subnet);
        }
        
        return result.toAlgorithmFormat();
    }
    
    /**
     * Exporta el resultado al formato JSON del algoritmo
     */
    public void exportToAlgorithmFormat(DividedNetworkOutput output, String outputFile) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        
        // Marcado inicial
        ArrayNode m0 = mapper.createArrayNode();
        for (int token : output.getInitialMarking()) {
            m0.add(token);
        }
        root.set("M0", m0);
        
        // Matriz I-
        ArrayNode iMinus = mapper.createArrayNode();
        for (int[] row : output.getIMinus()) {
            ArrayNode rowNode = mapper.createArrayNode();
            for (int val : row) {
                rowNode.add(val);
            }
            iMinus.add(rowNode);
        }
        root.set("I_minus", iMinus);
        
        // Matriz I+
        ArrayNode iPlus = mapper.createArrayNode();
        for (int[] row : output.getIPlus()) {
            ArrayNode rowNode = mapper.createArrayNode();
            for (int val : row) {
                rowNode.add(val);
            }
            iPlus.add(rowNode);
        }
        root.set("I_plus", iPlus);
        
        // Definiciones de subredes
        ArrayNode subnets = mapper.createArrayNode();
        for (AlgorithmSubnet subnet : output.getSubnetDefinitions()) {
            ObjectNode subnetNode = mapper.createObjectNode();
            subnetNode.put("id", subnet.getId());
            
            ArrayNode places = mapper.createArrayNode();
            for (Integer place : subnet.getPlaceIndices()) {
                places.add(place);
            }
            subnetNode.set("place_indices", places);
            
            ArrayNode transitions = mapper.createArrayNode();
            for (Integer trans : subnet.getTransIndices()) {
                transitions.add(trans);
            }
            subnetNode.set("trans_indices", transitions);
            
            subnets.add(subnetNode);
        }
        root.set("subnet_definitions", subnets);
        
        // Escribir archivo
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFile), root);
    }
    
    /**
     * Procesa y exporta en un solo paso
     */
    public void processAndExport(String inputFile, String outputFile) throws IOException {
        System.out.println("Procesando red: " + inputFile);
        DividedNetworkOutput output = processNetworkFile(inputFile);
        
        System.out.println("Exportando formato algoritmo: " + outputFile);
        exportToAlgorithmFormat(output, outputFile);
        
        System.out.println("¡División completada exitosamente!");
        System.out.println("  Lugares: " + output.getInitialMarking().length);
        System.out.println("  Transiciones: " + output.getIMinus()[0].length);
        System.out.println("  Subredes optimizadas: " + output.getSubnetDefinitions().size());
    }
    
    // Métodos de parsing
    private int[] parseInitialMarking(JsonNode m0Node) {
        int[] marking = new int[m0Node.size()];
        for (int i = 0; i < m0Node.size(); i++) {
            marking[i] = m0Node.get(i).asInt();
        }
        return marking;
    }
    
    private int[][] parseMatrix(JsonNode matrixNode) {
        int rows = matrixNode.size();
        int cols = matrixNode.get(0).size();
        int[][] matrix = new int[rows][cols];
        
        for (int i = 0; i < rows; i++) {
            JsonNode row = matrixNode.get(i);
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = row.get(j).asInt();
            }
        }
        
        return matrix;
    }
    
    private List<SubnetDefinition> parseSubnetDefinitions(JsonNode subnetsNode) {
        List<SubnetDefinition> subnets = new ArrayList<>();
        
        for (JsonNode subnetNode : subnetsNode) {
            int id = subnetNode.get("id").asInt();
            
            List<Integer> places = new ArrayList<>();
            for (JsonNode placeNode : subnetNode.get("place_indices")) {
                places.add(placeNode.asInt());
            }
            
            List<Integer> transitions = new ArrayList<>();
            for (JsonNode transNode : subnetNode.get("trans_indices")) {
                transitions.add(transNode.asInt());
            }
            
            subnets.add(new SubnetDefinition(id, places, transitions));
        }
        
        return subnets;
    }
}