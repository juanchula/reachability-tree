package generated;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa una figura de red de Petri S3PR.
 * Equivalente a las figuras generadas en petrinet_gen_v2.py
 */
public class PetriNetFigure {
    
    public enum FigureType {
        CIRCUIT,                    // Circuito simple
        FORK_JOIN,                  // Fork/Join simple
        CIRCUIT_FORK_JOIN,          // Circuito con Fork/Join
        INIT_TRAIN_FORK_JOIN,       // Fork/Join con tren inicial
        FINAL_TRAIN_FORK_JOIN,      // Fork/Join con tren final
        INIT_FINAL_TRAIN_FORK_JOIN  // Fork/Join con tren inicial y final
    }
    
    private FigureType type;
    private int[][] iPlus;      // Matriz I+
    private int[][] iMinus;     // Matriz I-
    private int[] initialMarking; // Marcado inicial
    private List<Integer> invariant; // T-invariante de la figura
    private int startPlaceIndex;     // Índice de la primera plaza
    private int startTransitionIndex; // Índice de la primera transición
    
    public PetriNetFigure(FigureType type, int[][] iPlus, int[][] iMinus, 
                          int[] initialMarking, List<Integer> invariant,
                          int startPlaceIndex, int startTransitionIndex) {
        this.type = type;
        this.iPlus = iPlus;
        this.iMinus = iMinus;
        this.initialMarking = initialMarking;
        this.invariant = new ArrayList<>(invariant);
        this.startPlaceIndex = startPlaceIndex;
        this.startTransitionIndex = startTransitionIndex;
    }
    
    /**
     * Obtiene el número de lugares en la figura
     */
    public int getPlaceCount() {
        return iPlus.length;
    }
    
    /**
     * Obtiene el número de transiciones en la figura
     */
    public int getTransitionCount() {
        return iPlus[0].length;
    }
    
    /**
     * Verifica si la figura tiene una plaza idle (para circuitos)
     */
    public boolean hasIdlePlace() {
        return type == FigureType.CIRCUIT || 
               type == FigureType.CIRCUIT_FORK_JOIN ||
               type == FigureType.INIT_TRAIN_FORK_JOIN ||
               type == FigureType.FINAL_TRAIN_FORK_JOIN ||
               type == FigureType.INIT_FINAL_TRAIN_FORK_JOIN;
    }
    
    /**
     * Obtiene los índices de lugares operacionales (excluye idle y recursos)
     */
    public List<Integer> getOperationalPlaces() {
        List<Integer> operational = new ArrayList<>();
        int placeCount = getPlaceCount();
        
        // Para circuito, la primera plaza es idle
        int startIdx = hasIdlePlace() ? 1 : 0;
        
        for (int i = startIdx; i < placeCount; i++) {
            operational.add(startPlaceIndex + i);
        }
        
        return operational;
    }
    
    /**
     * Obtiene el índice de la plaza idle si existe
     */
    public int getIdlePlaceIndex() {
        if (hasIdlePlace()) {
            return startPlaceIndex; // Primera plaza es idle
        }
        return -1;
    }
    
    // Getters
    public FigureType getType() { return type; }
    public int[][] getIPlus() { return iPlus; }
    public int[][] getIMinus() { return iMinus; }
    public int[] getInitialMarking() { return initialMarking; }
    public List<Integer> getInvariant() { return invariant; }
    public int getStartPlaceIndex() { return startPlaceIndex; }
    public int getStartTransitionIndex() { return startTransitionIndex; }
    
    @Override
    public String toString() {
        return String.format("Figure[type=%s, places=%d, transitions=%d, startP=%d, startT=%d]",
                type, getPlaceCount(), getTransitionCount(), startPlaceIndex, startTransitionIndex);
    }
}