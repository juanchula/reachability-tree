package generatedAndSubdivide;

public class SubnetDef {
    private final int id;
    private final int[] place_indices;
    private final int[] trans_indices;

    public SubnetDef(int id, int[] place_indices, int[] trans_indices) {
        this.id = id;
        this.place_indices = place_indices;
        this.trans_indices = trans_indices;
    }

    public int getId() {
        return id;
    }

    public int[] getPlace_indices() {
        return place_indices;
    }

    public int[] getTrans_indices() {
        return trans_indices;
    }
}
