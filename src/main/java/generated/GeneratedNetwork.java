package generated;

import java.util.List;

/**
 * Representa una red de Petri S3PR generada completa
 */
public class GeneratedNetwork {
    private int[][] iPlus;
    private int[][] iMinus;
    private int[] initialMarking;
    private List<PetriNetFigure> figures;
    private List<SubnetDefinition> subnetDefinitions;
    
    public GeneratedNetwork(int[][] iPlus, int[][] iMinus, int[] initialMarking, 
                           List<PetriNetFigure> figures) {
        this.iPlus = iPlus;
        this.iMinus = iMinus;
        this.initialMarking = initialMarking;
        this.figures = figures;
    }
    
    public int getPlaceCount() {
        return iPlus.length;
    }
    
    public int getTransitionCount() {
        return iPlus[0].length;
    }
    
    public void setMatrices(int[][] iPlus, int[][] iMinus, int[] initialMarking) {
        this.iPlus = iPlus;
        this.iMinus = iMinus;
        this.initialMarking = initialMarking;
    }
    
    // Getters y Setters
    public int[][] getIPlus() { return iPlus; }
    public void setIPlus(int[][] iPlus) { this.iPlus = iPlus; }
    
    public int[][] getIMinus() { return iMinus; }
    public void setIMinus(int[][] iMinus) { this.iMinus = iMinus; }
    
    public int[] getInitialMarking() { return initialMarking; }
    public void setInitialMarking(int[] initialMarking) { this.initialMarking = initialMarking; }
    
    public List<PetriNetFigure> getFigures() { return figures; }
    public void setFigures(List<PetriNetFigure> figures) { this.figures = figures; }
    
    public List<SubnetDefinition> getSubnetDefinitions() { return subnetDefinitions; }
    public void setSubnetDefinitions(List<SubnetDefinition> subnetDefinitions) { 
        this.subnetDefinitions = subnetDefinitions; 
    }
    
    @Override
    public String toString() {
        return String.format("Network[places=%d, transitions=%d, figures=%d, subnets=%d]",
                getPlaceCount(), getTransitionCount(), figures.size(), 
                subnetDefinitions != null ? subnetDefinitions.size() : 0);
    }
}