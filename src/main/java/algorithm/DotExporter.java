package algorithm;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Exporta el árbol de alcanzabilidad a formato DOT para visualización.
 */
public class DotExporter {
    private static final Logger logger = LogManager.getLogger(DotExporter.class);
    private static final Pattern TRANSITION_PATTERN = Pattern.compile("_t(\\d+)$");

    /**
     * Exporta el árbol de alcanzabilidad a un archivo DOT.
     */
    public static void exportToDot(ConcurrentHashMap<String, Node> reachabilityTree,
                                   PetriNet petriNet, String outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            // Escribir encabezado del archivo DOT
            writer.write("digraph ReachabilityTree {\n");
            writer.write("  node [shape=box];\n\n");

            // Escribir definiciones de nodos
            for (Map.Entry<String, Node> entry : reachabilityTree.entrySet()) {
                String markingId = entry.getKey();
                Node node = entry.getValue();

                // Obtener el marcado global como etiqueta
                int[] globalMarking = node.getFinalGlobalMarking() != null ? node.getFinalGlobalMarking() : node.buildGlobalMarking(petriNet);
                String markingLabel = arrayToString(globalMarking);

                // Escapar caracteres especiales en el ID
                String escapedId = escapeId(markingId);

                // Escribir definición del nodo
                writer.write("  " + escapedId + " [label=\"" + markingLabel + "\"];\n");
            }

            writer.write("\n");

            // Escribir conexiones entre nodos
            for (Map.Entry<String, Node> entry : reachabilityTree.entrySet()) {
                String markingId = entry.getKey();

                // Para cada componente del ID separado por "_t"
                String[] parts = markingId.split("_t");
                if (parts.length > 1) {
                    // Construir el ID del nodo padre
                    StringBuilder parentId = new StringBuilder(parts[0]);
                    for (int i = 1; i < parts.length - 1; i++) {
                        parentId.append("_t").append(parts[i]);
                    }

                    // La última parte es el número de transición
                    String transitionLabel = "t" + parts[parts.length - 1];

                    // Escapar caracteres especiales en los IDs
                    String escapedParentId = escapeId(parentId.toString());
                    String escapedCurrentId = escapeId(markingId);

                    // Escribir la conexión
                    writer.write("  " + escapedParentId + " -> " + escapedCurrentId +
                            " [label=\"" + transitionLabel + "\"];\n");
                }
            }

            // Cerrar el gráfico
            writer.write("}\n");
        } catch (IOException e) {
            logger.error("Error writing DOT file: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Convierte un array a su representación en cadena.
     */
    private static String arrayToString(int[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (array[i] == -1 || array[i] == Integer.MAX_VALUE) {
                sb.append("ω");
            } else {
                sb.append(array[i]);
            }
            if (i < array.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Escapa caracteres especiales en IDs para el formato DOT.
     */
    private static String escapeId(String id) {
        return "\"" + id.replace("\"", "\\\"") + "\"";
    }
}
