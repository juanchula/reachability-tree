package subdivide;

import java.util.*;

/**
 * Representa la estructura de una red de Petri para análisis y división
 */
public class PetriNetStructure {
    private int[][] iMinus;
    private int[][] iPlus;
    private int[] initialMarking;
    private List<SubnetDefinition> originalSubnets;
    
    // Análisis estructural
    private Map<Integer, Set<Integer>> transitionToPlaces;
    private Map<Integer, Set<Integer>> placeToTransitions;
    private List<TrainSequence> detectedTrains;
    
    public PetriNetStructure(int[][] iMinus, int[][] iPlus, int[] initialMarking, 
                           List<SubnetDefinition> originalSubnets) {
        this.iMinus = iMinus;
        this.iPlus = iPlus;
        this.initialMarking = initialMarking;
        this.originalSubnets = originalSubnets;
        
        analyzeStructure();
    }
    
    /**
     * Analiza la estructura de la red para identificar conexiones
     */
    private void analyzeStructure() {
        transitionToPlaces = new HashMap<>();
        placeToTransitions = new HashMap<>();
        
        // Construir mapas de conectividad
        for (int t = 0; t < getTransitionCount(); t++) {
            transitionToPlaces.put(t, new HashSet<>());
            
            for (int p = 0; p < getPlaceCount(); p++) {
                // Transición consume de lugar
                if (iMinus[p][t] > 0) {
                    transitionToPlaces.get(t).add(p);
                    placeToTransitions.computeIfAbsent(p, k -> new HashSet<>()).add(t);
                }
                // Transición produce en lugar
                if (iPlus[p][t] > 0) {
                    transitionToPlaces.get(t).add(p);
                    placeToTransitions.computeIfAbsent(p, k -> new HashSet<>()).add(t);
                }
            }
        }
        
        // Detectar trenes
        detectTrainSequences();
    }
    
    /**
     * Detecta secuencias de trenes en la red
     */
    private void detectTrainSequences() {
        detectedTrains = new ArrayList<>();
        Set<Integer> processedPlaces = new HashSet<>();
        
        // Buscar secuencias lineales P->T->P->T->...
        for (int startPlace = 0; startPlace < getPlaceCount(); startPlace++) {
            if (processedPlaces.contains(startPlace)) continue;
            
            TrainSequence train = findTrainStartingAt(startPlace);
            if (train != null && train.getLength() >= 2) { // Mínimo 2 elementos
                detectedTrains.add(train);
                processedPlaces.addAll(train.getPlaces());
            }
        }
    }
    
    /**
     * Encuentra un tren que comience en un lugar dado
     */
    private TrainSequence findTrainStartingAt(int startPlace) {
        List<Integer> places = new ArrayList<>();
        List<Integer> transitions = new ArrayList<>();
        
        int currentPlace = startPlace;
        places.add(currentPlace);
        
        // Seguir la secuencia mientras sea lineal
        while (true) {
            Set<Integer> nextTransitions = placeToTransitions.get(currentPlace);
            if (nextTransitions == null || nextTransitions.size() != 1) {
                break; // No es secuencia lineal
            }
            
            int nextTransition = nextTransitions.iterator().next();
            
            // Verificar que la transición solo conecte con lugares secuenciales
            Set<Integer> transitionPlaces = transitionToPlaces.get(nextTransition);
            if (transitionPlaces.size() > 2) {
                break; // Transición conecta con demasiados lugares
            }
            
            transitions.add(nextTransition);
            
            // Buscar siguiente lugar
            Integer nextPlace = null;
            for (Integer place : transitionPlaces) {
                if (!place.equals(currentPlace) && iPlus[place][nextTransition] > 0) {
                    nextPlace = place;
                    break;
                }
            }
            
            if (nextPlace == null) {
                break; // No hay siguiente lugar
            }
            
            places.add(nextPlace);
            currentPlace = nextPlace;
        }
        
        if (places.size() < 3) { // Mínimo P->T->P
            return null;
        }
        
        return new TrainSequence(places, transitions);
    }
    
    /**
     * Obtiene transiciones que son recursos compartidos (conectan múltiples trenes)
     */
    public Set<Integer> getSharedResourceTransitions() {
        Set<Integer> sharedTransitions = new HashSet<>();
        
        for (int t = 0; t < getTransitionCount(); t++) {
            Set<Integer> connectedPlaces = transitionToPlaces.get(t);
            
            // Si conecta con lugares de múltiples trenes, es recurso compartido
            int trainsConnected = 0;
            for (TrainSequence train : detectedTrains) {
                if (train.intersectsWith(connectedPlaces)) {
                    trainsConnected++;
                }
            }
            
            if (trainsConnected > 1) {
                sharedTransitions.add(t);
            }
        }
        
        return sharedTransitions;
    }
    
    /**
     * Obtiene todas las transiciones que afectan un lugar dado
     */
    public Set<Integer> getTransitionsAffecting(int place) {
        return placeToTransitions.getOrDefault(place, new HashSet<>());
    }
    
    /**
     * Obtiene todos los lugares que afecta una transición dada
     */
    public Set<Integer> getPlacesAffectedBy(int transition) {
        return transitionToPlaces.getOrDefault(transition, new HashSet<>());
    }
    
    /**
     * Verifica si una transición es parte de un tren
     */
    public boolean isTransitionInTrain(int transition) {
        for (TrainSequence train : detectedTrains) {
            if (train.getTransitions().contains(transition)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Obtiene el tren que contiene una transición dada
     */
    public TrainSequence getTrainContaining(int transition) {
        for (TrainSequence train : detectedTrains) {
            if (train.getTransitions().contains(transition)) {
                return train;
            }
        }
        return null;
    }
    
    // Getters
    public int getPlaceCount() { return iMinus.length; }
    public int getTransitionCount() { return iMinus[0].length; }
    public int[][] getIMinus() { return iMinus; }
    public int[][] getIPlus() { return iPlus; }
    public int[] getInitialMarking() { return initialMarking; }
    public List<SubnetDefinition> getOriginalSubnets() { return originalSubnets; }
    public List<TrainSequence> getDetectedTrains() { return detectedTrains; }
    
    @Override
    public String toString() {
        return String.format("PetriNetStructure[places=%d, transitions=%d, trains=%d, shared=%d]",
                getPlaceCount(), getTransitionCount(), detectedTrains.size(), 
                getSharedResourceTransitions().size());
    }
}

/**
 * Representación de una secuencia de tren P->T->P->T->...
 */
class TrainSequence {
    private List<Integer> places;
    private List<Integer> transitions;
    
    public TrainSequence(List<Integer> places, List<Integer> transitions) {
        this.places = new ArrayList<>(places);
        this.transitions = new ArrayList<>(transitions);
    }
    
    public int getLength() {
        return places.size() + transitions.size();
    }
    
    public boolean intersectsWith(Set<Integer> placeSet) {
        return places.stream().anyMatch(placeSet::contains);
    }
    
    public List<Integer> getPlaces() { return places; }
    public List<Integer> getTransitions() { return transitions; }
    
    @Override
    public String toString() {
        return String.format("Train[places=%d, transitions=%d]", places.size(), transitions.size());
    }
}

/**
 * Definición de subred original
 */
class SubnetDefinition {
    private int id;
    private List<Integer> placeIndices;
    private List<Integer> transIndices;
    
    public SubnetDefinition(int id, List<Integer> placeIndices, List<Integer> transIndices) {
        this.id = id;
        this.placeIndices = new ArrayList<>(placeIndices);
        this.transIndices = new ArrayList<>(transIndices);
    }
    
    // Getters
    public int getId() { return id; }
    public List<Integer> getPlaceIndices() { return placeIndices; }
    public List<Integer> getTransIndices() { return transIndices; }
}