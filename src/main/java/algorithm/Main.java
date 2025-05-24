package algorithm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
        import java.util.concurrent.*;
        import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Clase principal que contiene el punto de entrada de la aplicación.
 */
public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static boolean DEBUG = false;
    public static String DEBUG_MARKING_ID = null;

    public static void main(String[] args) {
        String inputFile = null;
        String outputFile = null;
        int nThreads = Runtime.getRuntime().availableProcessors();

        // Procesar argumentos
        for (int i = 0; i < args.length; i++) {
            if ("--input".equals(args[i]) && i + 1 < args.length) {
                inputFile = args[i + 1];
                i++;
            } else if ("--nthreads".equals(args[i]) && i + 1 < args.length) {
                try {
                    nThreads = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException e) {
                    logger.error("Error parsing number of threads: {}", args[i + 1]);
                    System.exit(1);
                }
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputFile = args[i + 1];
                i++;
            } else if ("--debug".equals(args[i])) {
                DEBUG = true;
            } else if ("--markingid".equals(args[i]) && i + 1 < args.length) {
                DEBUG_MARKING_ID = args[i + 1];
                i++;
            }
        }

        if (inputFile == null) {
            logger.error("Input file is required. Use --input <filename>");
            System.exit(1);
        }

        try {
            // Cargar la red de Petri desde el archivo JSON
            PetriNet petriNet = PetriNet.fromJson(inputFile);

            // Start time
            long startTime = System.nanoTime();

            // Crear y ejecutar el analizador de alcanzabilidad
            ReachabilityAnalyzer analyzer = new ReachabilityAnalyzer(petriNet, nThreads);
            analyzer.analyze();

            // Test for repeated markings in the reachability tree
            Map<String, Node> reachabilityTree = analyzer.getReachabilityTree();
            Set<String> seenMarkings = new HashSet<>();
            Set<String> repeatedMarkings = new HashSet<>();
            for (Map.Entry<String, Node> entry : reachabilityTree.entrySet()) {
                Node node = entry.getValue();
                int[] marking = node.getFinalGlobalMarking() != null ? node.getFinalGlobalMarking() : node.buildGlobalMarking(petriNet);
                String markingStr = Arrays.toString(marking);
                if (!seenMarkings.add(markingStr)) {
                    repeatedMarkings.add(markingStr);
                }
            }
            if (!repeatedMarkings.isEmpty()) {
                logger.warn("Repeated markings found: {}", repeatedMarkings);
            } else {
                logger.info("No repeated markings found in the reachability tree.");
            }

            // End time
            long endTime = System.nanoTime();

            // Duration in nanoseconds
            long duration = endTime - startTime;
            // Convert to milliseconds (optional)
            double millis = duration / 1_000_000.0;

            System.out.println("Time taken: " + millis + " ms");

            // Informar el resultado
            logger.info("Analysis completed. Total states in reachability tree: {}",
                    analyzer.getReachabilityTreeSize());

            // Exportar a DOT si se especificó un archivo de salida
            if (outputFile != null) {
                DotExporter.exportToDot(analyzer.getReachabilityTree(), petriNet, outputFile);
                logger.info("Reachability tree exported to DOT file: {}", outputFile);
            }

        } catch (IOException e) {
            logger.error("Error loading Petri net from file: {}", e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            logger.error("Error during analysis: {}", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}