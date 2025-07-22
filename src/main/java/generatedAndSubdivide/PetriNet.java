package generatedAndSubdivide;

public class PetriNet {
    private final int[][] incidence;
    private final int[] marking;

    public PetriNet(int[][] incidence, int[] marking) {
        this.incidence = incidence;
        this.marking = marking;
    }

    public int[][] getIncidence() {
        return incidence;
    }

    public int[] getMarking() {
        return marking;
    }
}
