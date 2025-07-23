package subdivide;

import java.io.File;

/**
 * CLI principal para el divisor inteligente de redes
 */
public class SubdividerMain {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String inputFile = null;
        String outputFile = null;
        boolean verbose = false;
        
        // Parsear argumentos
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i":
                case "--input":
                    if (i + 1 < args.length) {
                        inputFile = args[++i];
                    }
                    break;
                case "-o":
                case "--output":
                    if (i + 1 < args.length) {
                        outputFile = args[++i];
                    }
                    break;
                case "-v":
                case "--verbose":
                    verbose = true;
                    break;
                case "-h":
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    if (inputFile == null) {
                        inputFile = args[i];
                    }
                    break;
            }
        }
        
        if (inputFile == null) {
            System.err.println("Error: Archivo de entrada requerido");
            printUsage();
            System.exit(1);
        }
        
        // Generar archivo de salida automáticamente si no se especifica
        if (outputFile == null) {
            outputFile = generateOutputFileName(inputFile);
        }
        
        try {
            // Procesar red
            NetworkSubdivider subdivider = new NetworkSubdivider();
            
            if (verbose) {
                System.out.println("=== DIVISOR INTELIGENTE DE REDES DE PETRI ===");
                System.out.println("Entrada: " + inputFile);
                System.out.println("Salida: " + outputFile);
                System.out.println();
            }
            
            subdivider.processAndExport(inputFile, outputFile);
            
            if (verbose) {
                System.out.println();
                System.out.println("¡Proceso completado exitosamente!");
                System.out.println("La red ha sido dividida inteligentemente manteniendo trenes juntos.");
                System.out.println("Ahora puedes usar: java -cp target/classes algorithm.Main --input " + outputFile + " --omega");
            }
            
        } catch (Exception e) {
            System.err.println("Error procesando red: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    /**
     * Genera nombre de archivo de salida automáticamente
     */
    private static String generateOutputFileName(String inputFile) {
        File file = new File(inputFile);
        String baseName = file.getName().replaceFirst("[.][^.]+$", ""); // Remover extensión
        String directory = file.getParent();
        
        if (directory != null) {
            return directory + "/" + baseName + "_divided.json";
        } else {
            return baseName + "_divided.json";
        }
    }
    
    /**
     * Imprime ayuda de uso
     */
    private static void printUsage() {
        System.out.println("Divisor Inteligente de Redes de Petri");
        System.out.println("=====================================");
        System.out.println();
        System.out.println("Convierte la salida del generador S3PR al formato requerido por el algoritmo");
        System.out.println("de alcanzabilidad, dividiendo inteligentemente por transiciones y manteniendo");
        System.out.println("los trenes juntos para minimizar complejidad.");
        System.out.println();
        System.out.println("Uso:");
        System.out.println("  java -cp target/classes subdivide.SubdividerMain [opciones] <archivo_entrada>");
        System.out.println();
        System.out.println("Argumentos:");
        System.out.println("  archivo_entrada              Archivo JSON generado por S3PRGeneratorMain");
        System.out.println();
        System.out.println("Opciones:");
        System.out.println("  -i, --input <archivo>        Archivo de entrada (alternativo)");
        System.out.println("  -o, --output <archivo>       Archivo de salida (auto-generado si se omite)");
        System.out.println("  -v, --verbose                Mostrar información detallada");
        System.out.println("  -h, --help                   Mostrar esta ayuda");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  # Procesar red generada");
        System.out.println("  java -cp target/classes subdivide.SubdividerMain data/20250723-163456_0/net.json");
        System.out.println();
        System.out.println("  # Con salida personalizada y modo verbose");
        System.out.println("  java -cp target/classes subdivide.SubdividerMain \\");
        System.out.println("    -i data/20250723-163456_0/net.json \\");
        System.out.println("    -o data/net_algorithm_ready.json \\");
        System.out.println("    -v");
        System.out.println();
        System.out.println("  # Pipeline completo:");
        System.out.println("  # 1. Generar red S3PR");
        System.out.println("  java -cp target/classes generated.S3PRGeneratorMain -n 1 -c 1 -fj 1 -o data");
        System.out.println();
        System.out.println("  # 2. Dividir red para algoritmo");
        System.out.println("  java -cp target/classes subdivide.SubdividerMain data/*/net.json -v");
        System.out.println();
        System.out.println("  # 3. Ejecutar algoritmo de alcanzabilidad");
        System.out.println("  java -cp target/classes algorithm.Main --input data/*_divided.json --omega");
        System.out.println();
        System.out.println("El divisor detecta automáticamente:");
        System.out.println("  • Secuencias de trenes (P->T->P->T->...)");
        System.out.println("  • Recursos compartidos entre trenes");
        System.out.println("  • Transiciones que requieren subdivisión");
        System.out.println("  • Optimizaciones para minimizar complejidad de subredes");
    }
}