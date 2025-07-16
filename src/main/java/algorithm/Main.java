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
        boolean useOmega = false;
        boolean useOmegaStandalone = false;
        boolean useOmegaOptimized = false;
        boolean useOmegaUltraOptimized = false;

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
            } else if ("--omega".equals(args[i])) {
                useOmega = true;
            } else if ("--omega-standalone".equals(args[i])) {
                useOmegaStandalone = true;
            } else if ("--omega-optimized".equals(args[i])) {
                useOmegaOptimized = true;
            } else if ("--omega-ultra".equals(args[i])) {
                useOmegaUltraOptimized = true;
            }
        }

        if (inputFile == null) {
            logger.error("Input file is required. Use --input <filename>");
            logger.info("Available options:");
            logger.info("  --input <file>     : Input JSON file");
            logger.info("  --output <file>    : Output DOT file (optional)");
            logger.info("  --nthreads <n>     : Number of threads (default: CPU cores)");
            logger.info("  --omega            : Enable omega marking support (integrated)");
            logger.info("  --omega-standalone : Use standalone omega analyzer (Python-based)");
            logger.info("  --omega-optimized  : Use optimized omega analyzer (distributed detection)");
            logger.info("  --omega-ultra      : Use ULTRA-OPTIMIZED analyzer (all optimizations)");
            logger.info("  --debug            : Enable debug mode");
            logger.info("  --markingid <id>   : Debug specific marking ID");
            System.exit(1);
        }

        try {
            // Start time
            long startTime = System.nanoTime();
            long endTime;
            double millis;

            if (useOmegaStandalone || useOmegaOptimized || useOmegaUltraOptimized) {
                // Usar el analizador omega (standalone, optimizado o ultra-optimizado)
                List<OmegaReachabilityAnalyzer.ReachabilityNode> nodes = 
                    OmegaReachabilityAnalyzer.buildReachabilityTree(inputFile, nThreads);
                
                endTime = System.nanoTime();
                millis = (endTime - startTime) / 1_000_000.0;
                
                System.out.println("Time taken: " + millis + " ms");
                System.out.println("Total states: " + nodes.size());
                
                if (useOmegaUltraOptimized) {
                    logger.info("🚀 Análisis completado con OmegaReachabilityAnalyzer ULTRA-OPTIMIZADO (todas las optimizaciones)");
                } else if (useOmegaOptimized) {
                    logger.info("Análisis completado con OmegaReachabilityAnalyzer OPTIMIZADO (detección distribuida)");
                } else {
                    logger.info("Análisis completado con OmegaReachabilityAnalyzer standalone");
                }
                
                // Exportar a DOT si se especificó un archivo de salida
                if (outputFile != null) {
                    OmegaReachabilityAnalyzer.exportToDot(nodes, outputFile);
                    logger.info("Reachability tree exported to DOT file: {}", outputFile);
                }
                
            } else {
                // Usar el analizador original con soporte omega integrado
                PetriNet petriNet = PetriNet.fromJson(inputFile);
                
                // Crear y ejecutar el analizador de alcanzabilidad
                ReachabilityAnalyzer analyzer = new ReachabilityAnalyzer(petriNet, nThreads, useOmega);
                analyzer.analyze();
                
                if (useOmega) {
                    logger.info("Análisis completado con soporte para marcas omega (ω)");
                }
                
                endTime = System.nanoTime();
                millis = (endTime - startTime) / 1_000_000.0;
                
                System.out.println("Time taken: " + millis + " ms");
                
                // Informar el resultado
                logger.info("Analysis completed. Total states in reachability tree: {}",
                        analyzer.getReachabilityTreeSize());

                // Exportar a DOT si se especificó un archivo de salida
                if (outputFile != null) {
                    DotExporter.exportToDot(analyzer.getReachabilityTree(), petriNet, outputFile);
                    logger.info("Reachability tree exported to DOT file: {}", outputFile);
                }
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