package generated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Generador principal de redes de Petri S3PR.
 * Equivalente a petrinet_gen_v2.py y auto_gen.py
 */
public class S3PRNetworkGenerator {
    
    private S3PRGeneratorConfig config;
    private S3PRFigureGenerator figureGenerator;
    private Random random;
    
    public S3PRNetworkGenerator(S3PRGeneratorConfig config) {
        this.config = config;
        this.figureGenerator = new S3PRFigureGenerator(config);
        this.random = new Random();
    }
    
    /**
     * Genera una red de Petri S3PR completa
     */
    public GeneratedNetwork generateNetwork() {
        figureGenerator.resetCounters();
        List<PetriNetFigure> figures = new ArrayList<>();
        
        // Generar figuras según configuración
        for (int i = 0; i < config.getCircuits(); i++) {
            figures.add(figureGenerator.generateCircuit());
        }
        
        for (int i = 0; i < config.getForkJoin(); i++) {
            figures.add(figureGenerator.generateForkJoin());
        }
        
        for (int i = 0; i < config.getInitForkJoin(); i++) {
            figures.add(generateInitTrainForkJoin());
        }
        
        for (int i = 0; i < config.getFinalForkJoin(); i++) {
            figures.add(generateFinalTrainForkJoin());
        }
        
        for (int i = 0; i < config.getInitFinalForkJoin(); i++) {
            figures.add(generateInitFinalTrainForkJoin());
        }
        
        if (figures.isEmpty()) {
            throw new IllegalStateException("Debe generar al menos una figura");
        }
        
        // Combinar todas las figuras
        GeneratedNetwork network = combineFigures(figures);
        
        // Añadir recursos compartidos
        addSharedResources(network, figures);
        
        // Generar subdivisión para paralelización
        generateSubnetDefinitions(network, figures);
        
        return network;
    }
    
    /**
     * Genera fork/join con tren inicial
     */
    private PetriNetFigure generateInitTrainForkJoin() {
        PetriNetFigure train = figureGenerator.generateTrain();
        PetriNetFigure forkJoin = figureGenerator.generateForkJoin();
        
        return concatenateFigures(train, forkJoin, PetriNetFigure.FigureType.INIT_TRAIN_FORK_JOIN);
    }
    
    /**
     * Genera fork/join con tren final
     */
    private PetriNetFigure generateFinalTrainForkJoin() {
        PetriNetFigure forkJoin = figureGenerator.generateForkJoin();
        PetriNetFigure train = figureGenerator.generateTrain();
        
        return concatenateFigures(forkJoin, train, PetriNetFigure.FigureType.FINAL_TRAIN_FORK_JOIN);
    }
    
    /**
     * Genera fork/join con tren inicial y final
     */
    private PetriNetFigure generateInitFinalTrainForkJoin() {
        PetriNetFigure initTrain = figureGenerator.generateTrain();
        PetriNetFigure forkJoin = figureGenerator.generateForkJoin();
        PetriNetFigure finalTrain = figureGenerator.generateTrain();
        
        PetriNetFigure initFJ = concatenateFigures(initTrain, forkJoin, 
                                                  PetriNetFigure.FigureType.INIT_FINAL_TRAIN_FORK_JOIN);
        return concatenateFigures(initFJ, finalTrain, PetriNetFigure.FigureType.INIT_FINAL_TRAIN_FORK_JOIN);
    }
    
    /**
     * Concatena dos figuras secuencialmente
     */
    private PetriNetFigure concatenateFigures(PetriNetFigure first, PetriNetFigure second, 
                                            PetriNetFigure.FigureType resultType) {
        int totalPlaces = first.getPlaceCount() + second.getPlaceCount() - 1; // Compartir lugar de conexión
        int totalTransitions = first.getTransitionCount() + second.getTransitionCount();
        
        int[][] iPlus = new int[totalPlaces][totalTransitions];
        int[][] iMinus = new int[totalPlaces][totalTransitions];
        
        // Copiar primera figura
        for (int i = 0; i < first.getPlaceCount(); i++) {
            for (int j = 0; j < first.getTransitionCount(); j++) {
                iPlus[i][j] = first.getIPlus()[i][j];
                iMinus[i][j] = first.getIMinus()[i][j];
            }
        }
        
        // Copiar segunda figura (desplazada)
        int placeOffset = first.getPlaceCount() - 1; // -1 para compartir lugar
        int transOffset = first.getTransitionCount();
        
        for (int i = 0; i < second.getPlaceCount(); i++) {
            for (int j = 0; j < second.getTransitionCount(); j++) {
                if (i == 0 && placeOffset >= 0) {
                    // Conectar al lugar compartido
                    iPlus[placeOffset][transOffset + j] += second.getIPlus()[i][j];
                    iMinus[placeOffset][transOffset + j] += second.getIMinus()[i][j];
                } else {
                    iPlus[placeOffset + i][transOffset + j] = second.getIPlus()[i][j];
                    iMinus[placeOffset + i][transOffset + j] = second.getIMinus()[i][j];
                }
            }
        }
        
        // Marcado inicial combinado
        int[] initialMarking = new int[totalPlaces];
        System.arraycopy(first.getInitialMarking(), 0, initialMarking, 0, first.getPlaceCount());
        if (second.getPlaceCount() > 1) {
            System.arraycopy(second.getInitialMarking(), 1, initialMarking, 
                           first.getPlaceCount(), second.getPlaceCount() - 1);
        }
        
        // Invariante combinado
        List<Integer> invariant = new ArrayList<>(first.getInvariant());
        invariant.addAll(second.getInvariant());
        
        return new PetriNetFigure(resultType, iPlus, iMinus, initialMarking, invariant,
                                 first.getStartPlaceIndex(), first.getStartTransitionIndex());
    }
    
    /**
     * Combina todas las figuras en una red única
     */
    private GeneratedNetwork combineFigures(List<PetriNetFigure> figures) {
        int totalPlaces = figures.stream().mapToInt(PetriNetFigure::getPlaceCount).sum();
        int totalTransitions = figures.stream().mapToInt(PetriNetFigure::getTransitionCount).sum();
        
        int[][] iPlus = new int[totalPlaces][totalTransitions];
        int[][] iMinus = new int[totalPlaces][totalTransitions];
        int[] initialMarking = new int[totalPlaces];
        
        int placeOffset = 0;
        int transOffset = 0;
        
        for (PetriNetFigure figure : figures) {
            // Copiar matrices
            for (int i = 0; i < figure.getPlaceCount(); i++) {
                for (int j = 0; j < figure.getTransitionCount(); j++) {
                    iPlus[placeOffset + i][transOffset + j] = figure.getIPlus()[i][j];
                    iMinus[placeOffset + i][transOffset + j] = figure.getIMinus()[i][j];
                }
            }
            
            // Copiar marcado inicial
            System.arraycopy(figure.getInitialMarking(), 0, initialMarking, placeOffset, 
                           figure.getPlaceCount());
            
            placeOffset += figure.getPlaceCount();
            transOffset += figure.getTransitionCount();
        }
        
        return new GeneratedNetwork(iPlus, iMinus, initialMarking, figures);
    }
    
    /**
     * Añade recursos compartidos entre figuras
     */
    private void addSharedResources(GeneratedNetwork network, List<PetriNetFigure> figures) {
        int numSimpleResources = random.nextInt(config.getMaxSharedResources() - 
                                              config.getMinSharedResources() + 1) + 
                                              config.getMinSharedResources();
        
        // Añadir recursos compartidos simples
        for (int r = 0; r < numSimpleResources; r++) {
            addSimpleSharedResource(network, figures);
        }
        
        // Añadir recursos compartidos complejos
        for (int r = 0; r < config.getComplexResources(); r++) {
            addComplexSharedResource(network, figures);
        }
    }
    
    /**
     * Añade un recurso compartido simple (conecta 2 figuras)
     */
    private void addSimpleSharedResource(GeneratedNetwork network, List<PetriNetFigure> figures) {
        if (figures.size() < 2) return;
        
        // Seleccionar 2 figuras aleatoriamente
        PetriNetFigure fig1 = figures.get(random.nextInt(figures.size()));
        PetriNetFigure fig2;
        do {
            fig2 = figures.get(random.nextInt(figures.size()));
        } while (fig2 == fig1);
        
        // Añadir lugar de recurso compartido
        int[][] newIPlus = expandMatrix(network.getIPlus(), 1, 0);
        int[][] newIMinus = expandMatrix(network.getIMinus(), 1, 0);
        int[] newInitialMarking = expandArray(network.getInitialMarking(), 1);
        
        int resourcePlaceIdx = newIPlus.length - 1;
        newInitialMarking[resourcePlaceIdx] = 1; // Recurso disponible inicialmente
        
        // Conectar recurso con transiciones de las figuras seleccionadas
        connectResourceToFigure(newIPlus, newIMinus, resourcePlaceIdx, fig1);
        connectResourceToFigure(newIPlus, newIMinus, resourcePlaceIdx, fig2);
        
        network.setMatrices(newIPlus, newIMinus, newInitialMarking);
    }
    
    /**
     * Añade un recurso compartido complejo (conecta 3+ figuras)
     */
    private void addComplexSharedResource(GeneratedNetwork network, List<PetriNetFigure> figures) {
        if (figures.size() < 3) return;
        
        int numConnections = Math.min(figures.size(), random.nextInt(3) + 3); // 3-5 conexiones
        List<PetriNetFigure> selectedFigures = new ArrayList<>(figures);
        Collections.shuffle(selectedFigures);
        selectedFigures = selectedFigures.subList(0, numConnections);
        
        // Añadir lugar de recurso compartido
        int[][] newIPlus = expandMatrix(network.getIPlus(), 1, 0);
        int[][] newIMinus = expandMatrix(network.getIMinus(), 1, 0);
        int[] newInitialMarking = expandArray(network.getInitialMarking(), 1);
        
        int resourcePlaceIdx = newIPlus.length - 1;
        newInitialMarking[resourcePlaceIdx] = numConnections - 1; // Recursos suficientes
        
        // Conectar recurso con todas las figuras seleccionadas
        for (PetriNetFigure figure : selectedFigures) {
            connectResourceToFigure(newIPlus, newIMinus, resourcePlaceIdx, figure);
        }
        
        network.setMatrices(newIPlus, newIMinus, newInitialMarking);
    }
    
    /**
     * Conecta un recurso compartido con una figura
     */
    private void connectResourceToFigure(int[][] iPlus, int[][] iMinus, int resourceIdx, 
                                       PetriNetFigure figure) {
        List<Integer> operationalPlaces = figure.getOperationalPlaces();
        if (operationalPlaces.isEmpty()) return;
        
        // Seleccionar lugar operacional aleatorio para conectar el recurso
        int targetPlace = operationalPlaces.get(random.nextInt(operationalPlaces.size()));
        
        // Encontrar transiciones que afectan este lugar
        for (int t = 0; t < iPlus[0].length; t++) {
            if (iPlus[targetPlace][t] > 0) { // Transición produce en lugar operacional
                iMinus[resourceIdx][t] = 1;   // Requiere recurso
            }
            if (iMinus[targetPlace][t] > 0) { // Transición consume lugar operacional  
                iPlus[resourceIdx][t] = 1;    // Libera recurso
            }
        }
    }
    
    /**
     * Genera definiciones de subredes para paralelización
     */
    private void generateSubnetDefinitions(GeneratedNetwork network, List<PetriNetFigure> figures) {
        List<SubnetDefinition> subnets = new ArrayList<>();
        int subnetId = 0;
        
        int placeOffset = 0;
        int transOffset = 0;
        
        for (PetriNetFigure figure : figures) {
            List<Integer> placeIndices = new ArrayList<>();
            List<Integer> transIndices = new ArrayList<>();
            
            // Lugares de la figura
            for (int i = 0; i < figure.getPlaceCount(); i++) {
                placeIndices.add(placeOffset + i);
            }
            
            // Transiciones de la figura
            for (int i = 0; i < figure.getTransitionCount(); i++) {
                transIndices.add(transOffset + i);
            }
            
            subnets.add(new SubnetDefinition(subnetId++, placeIndices, transIndices));
            
            placeOffset += figure.getPlaceCount();
            transOffset += figure.getTransitionCount();
        }
        
        // Añadir recursos compartidos a subredes apropiadas
        int totalOriginalPlaces = placeOffset;
        for (int resourceIdx = totalOriginalPlaces; resourceIdx < network.getIPlus().length; resourceIdx++) {
            // Encontrar subredes que usan este recurso
            Set<Integer> affectedSubnets = new HashSet<>();
            for (int t = 0; t < network.getIPlus()[0].length; t++) {
                if (network.getIPlus()[resourceIdx][t] > 0 || network.getIMinus()[resourceIdx][t] > 0) {
                    // Encontrar a qué subred pertenece esta transición
                    for (int s = 0; s < subnets.size(); s++) {
                        if (subnets.get(s).getTransIndices().contains(t)) {
                            affectedSubnets.add(s);
                        }
                    }
                }
            }
            
            // Añadir recurso a subredes afectadas
            for (Integer subnetIdx : affectedSubnets) {
                subnets.get(subnetIdx).getPlaceIndices().add(resourceIdx);
            }
        }
        
        network.setSubnetDefinitions(subnets);
    }
    
    /**
     * Expande una matriz añadiendo filas y/o columnas
     */
    private int[][] expandMatrix(int[][] matrix, int addRows, int addCols) {
        int newRows = matrix.length + addRows;
        int newCols = matrix[0].length + addCols;
        int[][] newMatrix = new int[newRows][newCols];
        
        for (int i = 0; i < matrix.length; i++) {
            System.arraycopy(matrix[i], 0, newMatrix[i], 0, matrix[i].length);
        }
        
        return newMatrix;
    }
    
    /**
     * Expande un array añadiendo elementos
     */
    private int[] expandArray(int[] array, int addElements) {
        int[] newArray = new int[array.length + addElements];
        System.arraycopy(array, 0, newArray, 0, array.length);
        return newArray;
    }
    
    /**
     * Exporta la red a formato JSON compatible con el algoritmo
     */
    public void exportToJson(GeneratedNetwork network, String filename) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        
        // Marcado inicial
        ArrayNode m0 = mapper.createArrayNode();
        for (int token : network.getInitialMarking()) {
            m0.add(token);
        }
        root.set("M0", m0);
        
        // Matrix I-
        ArrayNode iMinus = mapper.createArrayNode();
        for (int[] row : network.getIMinus()) {
            ArrayNode rowNode = mapper.createArrayNode();
            for (int val : row) {
                rowNode.add(val);
            }
            iMinus.add(rowNode);
        }
        root.set("I_minus", iMinus);
        
        // Matrix I+
        ArrayNode iPlus = mapper.createArrayNode();
        for (int[] row : network.getIPlus()) {
            ArrayNode rowNode = mapper.createArrayNode();
            for (int val : row) {
                rowNode.add(val);
            }
            iPlus.add(rowNode);
        }
        root.set("I_plus", iPlus);
        
        // Definiciones de subredes
        ArrayNode subnets = mapper.createArrayNode();
        for (SubnetDefinition subnet : network.getSubnetDefinitions()) {
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
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filename), root);
    }
}