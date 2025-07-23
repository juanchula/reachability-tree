package subdivide;

import java.util.*;

/**
 * Divisor inteligente de redes de Petri que mantiene trenes juntos
 * y divide por transiciones minimizando complejidad
 */
public class IntelligentNetworkDivider {
    
    private PetriNetStructure structure;
    
    public IntelligentNetworkDivider(PetriNetStructure structure) {
        this.structure = structure;
    }
    
    /**
     * Divide la red inteligentemente manteniendo trenes juntos
     */
    public DivisionResult divideNetwork() {
        List<OptimizedSubnet> subnets = new ArrayList<>();
        Set<Integer> processedTransitions = new HashSet<>();
        int subnetId = 0;
        
        // Paso 1: Crear subredes para cada tren completo
        for (TrainSequence train : structure.getDetectedTrains()) {
            OptimizedSubnet trainSubnet = createTrainSubnet(subnetId++, train);
            subnets.add(trainSubnet);
            processedTransitions.addAll(train.getTransitions());
        }
        
        // Paso 2: Procesar transiciones de recursos compartidos
        Set<Integer> sharedTransitions = structure.getSharedResourceTransitions();
        for (Integer sharedTransition : sharedTransitions) {
            if (!processedTransitions.contains(sharedTransition)) {
                OptimizedSubnet sharedSubnet = createSharedResourceSubnet(subnetId++, sharedTransition);
                subnets.add(sharedSubnet);
                processedTransitions.add(sharedTransition);
            }
        }
        
        // Paso 3: Procesar transiciones restantes
        for (int t = 0; t < structure.getTransitionCount(); t++) {
            if (!processedTransitions.contains(t)) {
                OptimizedSubnet remainingSubnet = createTransitionSubnet(subnetId++, t);
                subnets.add(remainingSubnet);
                processedTransitions.add(t);
            }
        }
        
        // Paso 4: Optimizar subredes añadiendo recursos compartidos necesarios
        optimizeSubnetsWithSharedResources(subnets, sharedTransitions);
        
        return new DivisionResult(subnets, structure);
    }
    
    /**
     * Crea una subred para un tren completo
     */
    private OptimizedSubnet createTrainSubnet(int id, TrainSequence train) {
        Set<Integer> places = new HashSet<>(train.getPlaces());
        Set<Integer> transitions = new HashSet<>(train.getTransitions());
        
        // Añadir lugares adicionales conectados a las transiciones del tren
        for (Integer transition : train.getTransitions()) {
            places.addAll(structure.getPlacesAffectedBy(transition));
        }
        
        return new OptimizedSubnet(id, places, transitions, SubnetType.TRAIN);
    }
    
    /**
     * Crea una subred para un recurso compartido
     */
    private OptimizedSubnet createSharedResourceSubnet(int id, int sharedTransition) {
        Set<Integer> places = structure.getPlacesAffectedBy(sharedTransition);
        Set<Integer> transitions = new HashSet<>();
        transitions.add(sharedTransition);
        
        return new OptimizedSubnet(id, places, transitions, SubnetType.SHARED_RESOURCE);
    }
    
    /**
     * Crea una subred para una transición individual
     */
    private OptimizedSubnet createTransitionSubnet(int id, int transition) {
        Set<Integer> places = structure.getPlacesAffectedBy(transition);
        Set<Integer> transitions = new HashSet<>();
        transitions.add(transition);
        
        return new OptimizedSubnet(id, places, transitions, SubnetType.INDIVIDUAL);
    }
    
    /**
     * Optimiza subredes añadiendo recursos compartidos donde sea necesario
     */
    private void optimizeSubnetsWithSharedResources(List<OptimizedSubnet> subnets, 
                                                   Set<Integer> sharedTransitions) {
        // Para cada transición compartida, añadirla a todas las subredes que la necesiten
        for (Integer sharedTransition : sharedTransitions) {
            Set<Integer> sharedPlaces = structure.getPlacesAffectedBy(sharedTransition);
            
            for (OptimizedSubnet subnet : subnets) {
                // Si la subred tiene lugares que interactúan con esta transición compartida
                if (subnet.intersectsWithPlaces(sharedPlaces) && 
                    subnet.getType() != SubnetType.SHARED_RESOURCE) {
                    
                    // Añadir la transición compartida y sus lugares
                    subnet.addTransition(sharedTransition);
                    subnet.addPlaces(sharedPlaces);
                }
            }
        }
    }
    
    /**
     * Resultado de la división de red
     */
    public static class DivisionResult {
        private List<OptimizedSubnet> subnets;
        private PetriNetStructure originalStructure;
        
        public DivisionResult(List<OptimizedSubnet> subnets, PetriNetStructure originalStructure) {
            this.subnets = subnets;
            this.originalStructure = originalStructure;
        }
        
        /**
         * Convierte el resultado a formato JSON para el algoritmo
         */
        public DividedNetworkOutput toAlgorithmFormat() {
            // Crear matrices divididas
            int[][] iMinus = originalStructure.getIMinus();
            int[][] iPlus = originalStructure.getIPlus();
            int[] initialMarking = originalStructure.getInitialMarking();
            
            // Convertir subredes al formato requerido
            List<AlgorithmSubnet> algorithmSubnets = new ArrayList<>();
            for (OptimizedSubnet subnet : subnets) {
                algorithmSubnets.add(new AlgorithmSubnet(
                    subnet.getId(),
                    new ArrayList<>(subnet.getPlaces()),
                    new ArrayList<>(subnet.getTransitions())
                ));
            }
            
            return new DividedNetworkOutput(initialMarking, iMinus, iPlus, algorithmSubnets);
        }
        
        public List<OptimizedSubnet> getSubnets() { return subnets; }
        public PetriNetStructure getOriginalStructure() { return originalStructure; }
        
        @Override
        public String toString() {
            return String.format("DivisionResult[subnets=%d, avgSize=%.1f]", 
                    subnets.size(),
                    subnets.stream().mapToInt(s -> s.getPlaces().size() + s.getTransitions().size())
                           .average().orElse(0.0));
        }
    }
}

/**
 * Subred optimizada resultado de la división
 */
class OptimizedSubnet {
    private int id;
    private Set<Integer> places;
    private Set<Integer> transitions;
    private SubnetType type;
    
    public OptimizedSubnet(int id, Set<Integer> places, Set<Integer> transitions, SubnetType type) {
        this.id = id;
        this.places = new HashSet<>(places);
        this.transitions = new HashSet<>(transitions);
        this.type = type;
    }
    
    public void addTransition(int transition) {
        transitions.add(transition);
    }
    
    public void addPlaces(Set<Integer> newPlaces) {
        places.addAll(newPlaces);
    }
    
    public boolean intersectsWithPlaces(Set<Integer> otherPlaces) {
        return places.stream().anyMatch(otherPlaces::contains);
    }
    
    // Getters
    public int getId() { return id; }
    public Set<Integer> getPlaces() { return places; }
    public Set<Integer> getTransitions() { return transitions; }
    public SubnetType getType() { return type; }
    
    @Override
    public String toString() {
        return String.format("Subnet[id=%d, type=%s, places=%d, trans=%d]", 
                id, type, places.size(), transitions.size());
    }
}

/**
 * Tipos de subred para optimización
 */
enum SubnetType {
    TRAIN,           // Subred que contiene un tren completo
    SHARED_RESOURCE, // Subred centrada en recurso compartido
    INDIVIDUAL       // Subred de transición individual
}

/**
 * Definición de subred para el algoritmo
 */
class AlgorithmSubnet {
    private int id;
    private List<Integer> placeIndices;
    private List<Integer> transIndices;
    
    public AlgorithmSubnet(int id, List<Integer> placeIndices, List<Integer> transIndices) {
        this.id = id;
        this.placeIndices = placeIndices;
        this.transIndices = transIndices;
    }
    
    // Getters
    public int getId() { return id; }
    public List<Integer> getPlaceIndices() { return placeIndices; }
    public List<Integer> getTransIndices() { return transIndices; }
}

/**
 * Salida final en formato del algoritmo
 */
class DividedNetworkOutput {
    private int[] initialMarking;
    private int[][] iMinus;
    private int[][] iPlus;
    private List<AlgorithmSubnet> subnetDefinitions;
    
    public DividedNetworkOutput(int[] initialMarking, int[][] iMinus, int[][] iPlus, 
                               List<AlgorithmSubnet> subnetDefinitions) {
        this.initialMarking = initialMarking;
        this.iMinus = iMinus;
        this.iPlus = iPlus;
        this.subnetDefinitions = subnetDefinitions;
    }
    
    // Getters
    public int[] getInitialMarking() { return initialMarking; }
    public int[][] getIMinus() { return iMinus; }
    public int[][] getIPlus() { return iPlus; }
    public List<AlgorithmSubnet> getSubnetDefinitions() { return subnetDefinitions; }
}