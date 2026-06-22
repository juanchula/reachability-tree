package algorithm;

/**
 * Utilidades relacionadas con la comprobación de habilitación de transiciones.
 */
public final class TransitionUtils {
    private TransitionUtils() {}

    /**
     * Devuelve <code>true</code> si la transición <code>t</code> está habilitada en el marcado <code>marking</code>.
     * Se considera que la constante ω (-1) satisface cualquier requisito de tokens.
     *
     * @param marking vector de marcado (copiado externamente)
     * @param t índice de la transición
     * @param Iminus matriz de consumos (|P| × |T|)
     * @return <code>true</code> si habilitada
     */
    public static boolean isEnabled(int[] marking, int t, int[][] Iminus) {
        for (int p = 0; p < marking.length; p++) {
            int need = Iminus[p][t];
            int have = marking[p];
            if (have != OmegaDetector.OMEGA && have < need) {
                return false;
            }
        }
        return true;
    }

    /**
     * Devuelve <code>true</code> si la transición <code>t</code> está habilitada en
     * todos los marcados del lote. Útil para podar disparos cuya transición
     * sensibilizada no es uniforme en un lote.
     */
    public static boolean isEnabledInAll(int[][] markings, int t, int[][] Iminus) {
        if (markings == null) return false;
        for (int[] m : markings) {
            if (!isEnabled(m, t, Iminus)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cuenta cuántos marcados del lote tienen la transición <code>t</code> habilitada.
     */
    public static int countEnabledIn(int[][] markings, int t, int[][] Iminus) {
        if (markings == null) return 0;
        int count = 0;
        for (int[] m : markings) {
            if (isEnabled(m, t, Iminus)) {
                count++;
            }
        }
        return count;
    }
}
