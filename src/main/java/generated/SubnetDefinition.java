package generated;

import java.util.List;

/**
 * Definición de subred para paralelización
 */
public class SubnetDefinition {
    private int id;
    private List<Integer> placeIndices;
    private List<Integer> transIndices;
    
    public SubnetDefinition(int id, List<Integer> placeIndices, List<Integer> transIndices) {
        this.id = id;
        this.placeIndices = placeIndices;
        this.transIndices = transIndices;
    }
    
    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public List<Integer> getPlaceIndices() { return placeIndices; }
    public void setPlaceIndices(List<Integer> placeIndices) { this.placeIndices = placeIndices; }
    
    public List<Integer> getTransIndices() { return transIndices; }
    public void setTransIndices(List<Integer> transIndices) { this.transIndices = transIndices; }
    
    @Override
    public String toString() {
        return String.format("Subnet[id=%d, places=%d, transitions=%d]", 
                           id, placeIndices.size(), transIndices.size());
    }
}