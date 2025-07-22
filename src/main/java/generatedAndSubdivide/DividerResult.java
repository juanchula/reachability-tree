package generatedAndSubdivide;

import java.util.List;

public class DividerResult {
    // Jackson serializa las variables exactamente con estos nombres:
    // "M0", "I_minus", "I_plus", "subnet_definitions"
    private final int[] M0;
    private final int[][] I_minus;
    private final int[][] I_plus;
    private final List<SubnetDef> subnet_definitions;

    public DividerResult(int[] M0, int[][] I_minus, int[][] I_plus, List<SubnetDef> subnet_definitions) {
        this.M0 = M0;
        this.I_minus = I_minus;
        this.I_plus = I_plus;
        this.subnet_definitions = subnet_definitions;
    }

    public int[] getM0() {
        return M0;
    }

    public int[][] getI_minus() {
        return I_minus;
    }

    public int[][] getI_plus() {
        return I_plus;
    }

    public List<SubnetDef> getSubnet_definitions() {
        return subnet_definitions;
    }
}
