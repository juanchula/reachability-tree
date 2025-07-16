package algorithm;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementación del algoritmo de la tesis para detección de omega (ω).
 * Aplica los tres criterios: balance de tokens, sensibilización continua y patrón de crecimiento.
 */
public final class OmegaDetector {
    public static final int OMEGA = -1;
    private static final Logger log = LogManager.getLogger(OmegaDetector.class);

    private OmegaDetector() {}

    /**
     * Aplica el algoritmo de la tesis para detectar omega en un marcado.
     * 
     * @param m marcado a analizar (se modifica in-place)
     * @param ancestors lista de nodos ancestros
     * @param t transición que generó el marcado
     * @param fires contador de disparos para la transición
     * @param Iminus matriz de consumos
     * @param Iplus matriz de producciones
     */
    public static void applyOmega(int[] m,
                                  List<Node> ancestors,
                                  int t,
                                  int fires,
                                  int[][] Iminus,
                                  int[][] Iplus) {
        // Solo aplicar detección de omega si hay suficientes ancestros para detectar ciclos
        if (ancestors.size() < 3) {
            return; // Necesitamos al menos 3 ancestros para detectar patrones
        }
        
        // 1. Verificar si hay un ciclo que permita disparos infinitos
        if (!hasInfiniteCycle(t, ancestors, Iminus, Iplus)) {
            return; // No hay ciclo infinito, no puede haber omega
        }
        
        // 2. Análisis de balance de tokens (solo si hay ciclo infinito)
        for (int p = 0; p < m.length; p++) {
            int balance = Iplus[p][t] - Iminus[p][t];
            if (balance > 0 && fires >= 1 && alwaysEnabled(t, ancestors, Iminus)) {
                m[p] = OMEGA;
                log.debug("ω por balance en plaza {} (bal={})", p, balance);
            }
        }
        
        // 3. Verificación de patrón de crecimiento (solo si hay ciclo infinito)
        for (int p = 0; p < m.length; p++) {
            if (growthPattern(p, t, ancestors, Iminus) && fires >= 1) {
                m[p] = OMEGA;
                log.debug("ω por crecimiento en plaza {}", p);
            }
        }
    }
    
    /**
     * Verifica si existe un ciclo infinito que permita disparos ilimitados de la transición.
     */
    private static boolean hasInfiniteCycle(int t, List<Node> ancestors, int[][] Iminus, int[][] Iplus) {
        // Para detectar un ciclo infinito real, necesitamos:
        // 1. Al menos 3 ancestros para detectar patrones
        if (ancestors.size() < 3) {
            return false;
        }
        
        // 2. Verificar si la transición está habilitada en todos los ancestros
        boolean alwaysEnabled = true;
        for (Node ancestor : ancestors) {
            if (!TransitionUtils.isEnabled(ancestor.getFinalGlobalMarking(), t, Iminus)) {
                alwaysEnabled = false;
                break;
            }
        }
        
        if (!alwaysEnabled) {
            return false;
        }
        
        // 3. Verificar si hay un patrón de crecimiento en alguna plaza
        // que indique que los tokens pueden crecer indefinidamente
        for (int p = 0; p < ancestors.get(0).getFinalGlobalMarking().length; p++) {
            int balance = Iplus[p][t] - Iminus[p][t];
            if (balance > 0) {
                // Si la transición produce tokens en esta plaza, verificar si hay crecimiento
                if (hasStrictGrowthPattern(p, t, ancestors, Iminus)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Verifica si hay un patrón de crecimiento estricto en una plaza.
     * Requiere que los valores crezcan monótonamente.
     */
    private static boolean hasStrictGrowthPattern(int p, int t, List<Node> ancestors, int[][] Iminus) {
        // Verificar que la transición esté habilitada en todos los ancestros
        for (Node ancestor : ancestors) {
            if (!TransitionUtils.isEnabled(ancestor.getFinalGlobalMarking(), t, Iminus)) {
                return false;
            }
        }
        
        // Verificar patrón de crecimiento estricto
        int[] values = new int[ancestors.size()];
        for (int i = 0; i < ancestors.size(); i++) {
            values[i] = ancestors.get(i).getFinalGlobalMarking()[p];
        }
        
        // Verificar si hay crecimiento monótono
        for (int i = 1; i < values.length; i++) {
            if (values[i] <= values[i-1]) {
                return false; // No hay crecimiento monótono
            }
        }
        
        return true;
    }

    /**
     * Verifica si una transición está siempre habilitada en los ancestros.
     */
    private static boolean alwaysEnabled(int t, List<Node> anc, int[][] Iminus) {
        for (Node n : anc) {
            if (!TransitionUtils.isEnabled(n.getFinalGlobalMarking(), t, Iminus)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verifica si hay un patrón de crecimiento en una plaza.
     */
    private static boolean growthPattern(int p, int t, List<Node> anc, int[][] Iminus) {
        Integer prev = null;
        for (Node n : anc) {
            if (!TransitionUtils.isEnabled(n.getFinalGlobalMarking(), t, Iminus)) {
                return false;
            }
            int val = n.getFinalGlobalMarking()[p];
            if (prev != null && val > prev) {
                return true;
            }
            prev = val;
        }
        return false;
    }
} 