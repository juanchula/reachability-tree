package generated;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CLI principal para el generador de redes S3PR.
 * Equivalente a auto_gen.py
 */
public class S3PRGeneratorMain {
    
    public static void main(String[] args) {
        S3PRGeneratorConfig config = parseArguments(args);
        
        if (config == null || !config.isValid()) {
            printUsage();
            System.exit(1);
        }
        
        try {
            generateNetworks(config);
        } catch (Exception e) {
            System.err.println("Error generando redes: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Parsea argumentos de línea de comandos
     */
    private static S3PRGeneratorConfig parseArguments(String[] args) {
        if (args.length == 0) {
            return null;
        }
        
        S3PRGeneratorConfig config = new S3PRGeneratorConfig();
        
        for (int i = 0; i < args.length; i++) {
            try {
                switch (args[i]) {
                    case "-n":
                    case "--net_num":
                        config.setNumNets(Integer.parseInt(args[++i]));
                        break;
                    case "-o":
                    case "--out_dir":
                        config.setOutputFolder(args[++i]);
                        break;
                    case "-c":
                    case "--circuits":
                        config.setCircuits(Integer.parseInt(args[++i]));
                        break;
                    case "-ifj":
                    case "--init_fj":
                        config.setInitForkJoin(Integer.parseInt(args[++i]));
                        break;
                    case "-ffj":
                    case "--final_fj":
                        config.setFinalForkJoin(Integer.parseInt(args[++i]));
                        break;
                    case "-iffj":
                    case "--init_final_fj":
                        config.setInitFinalForkJoin(Integer.parseInt(args[++i]));
                        break;
                    case "-fj":
                    case "--fork_join":
                        config.setForkJoin(Integer.parseInt(args[++i]));
                        break;
                    case "-cr":
                    case "--cmpx_src":
                        config.setComplexResources(Integer.parseInt(args[++i]));
                        break;
                    case "-msr":
                    case "--min_shrd_src":
                        config.setMinSharedResources(Integer.parseInt(args[++i]));
                        break;
                    case "-Msr":
                    case "--max_shrd_src":
                        config.setMaxSharedResources(Integer.parseInt(args[++i]));
                        break;
                    case "-h":
                    case "--help":
                        return null;
                    default:
                        System.err.println("Argumento desconocido: " + args[i]);
                        return null;
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.err.println("Valor inválido para " + args[i]);
                return null;
            }
        }
        
        // Aplicar valores automáticos como en Python
        int totalFigures = config.getTotalFigures();
        if (config.getMinSharedResources() < 1) {
            config.setMinSharedResources(totalFigures + 3);
        }
        if (config.getMaxSharedResources() < config.getMinSharedResources()) {
            config.setMaxSharedResources(config.getMinSharedResources() * 2);
        }
        
        return config;
    }
    
    /**
     * Genera las redes según la configuración
     */
    private static void generateNetworks(S3PRGeneratorConfig config) throws IOException {
        // Crear directorio de salida
        File outputDir = new File(config.getOutputFolder());
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        S3PRNetworkGenerator generator = new S3PRNetworkGenerator(config);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        
        System.out.println("Generando " + config.getNumNets() + " redes S3PR...");
        System.out.println("Configuración:");
        System.out.println("  Circuitos: " + config.getCircuits());
        System.out.println("  Fork/Join simples: " + config.getForkJoin());
        System.out.println("  Fork/Join con tren inicial: " + config.getInitForkJoin());
        System.out.println("  Fork/Join con tren final: " + config.getFinalForkJoin());
        System.out.println("  Fork/Join con tren inicial y final: " + config.getInitFinalForkJoin());
        System.out.println("  Recursos compartidos simples: " + config.getMinSharedResources() + 
                         "-" + config.getMaxSharedResources());
        System.out.println("  Recursos compartidos complejos: " + config.getComplexResources());
        System.out.println();
        
        for (int i = 0; i < config.getNumNets(); i++) {
            try {
                // Generar red
                GeneratedNetwork network = generator.generateNetwork();
                
                // Crear directorio para esta red
                String timestamp = LocalDateTime.now().format(formatter);
                String networkDir = config.getOutputFolder() + "/" + timestamp + "_" + i;
                new File(networkDir).mkdirs();
                
                // Exportar a JSON
                String filename = networkDir + "/net.json";
                generator.exportToJson(network, filename);
                
                System.out.println("Generada red " + (i + 1) + "/" + config.getNumNets() + 
                                 ": " + network + " -> " + filename);
                
                // Pequeña pausa para evitar timestamps idénticos
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
            } catch (Exception e) {
                System.err.println("Error generando red " + (i + 1) + ": " + e.getMessage());
                throw e;
            }
        }
        
        System.out.println("\n¡Generación completada! Se generaron " + config.getNumNets() + 
                         " redes en " + config.getOutputFolder());
    }
    
    /**
     * Imprime ayuda de uso
     */
    private static void printUsage() {
        System.out.println("Generador de Redes de Petri S3PR");
        System.out.println("=================================");
        System.out.println();
        System.out.println("Uso: java -cp target/classes generated.S3PRGeneratorMain [opciones]");
        System.out.println();
        System.out.println("Argumentos requeridos:");
        System.out.println("  -n,  --net_num <num>           Cantidad de redes a generar");
        System.out.println();
        System.out.println("Argumentos opcionales:");
        System.out.println("  -o,  --out_dir <dir>           Directorio de salida (gen_nets por defecto)");
        System.out.println("  -c,  --circuits <num>          Cantidad de circuitos simples (0 por defecto)");
        System.out.println("  -ifj, --init_fj <num>          Circuitos fork/join con tren inicial (0 por defecto)");
        System.out.println("  -ffj, --final_fj <num>         Circuitos fork/join con tren final (0 por defecto)");
        System.out.println("  -iffj,--init_final_fj <num>    Circuitos fork/join con tren inicial y final (0 por defecto)");
        System.out.println("  -fj, --fork_join <num>         Circuitos fork/join simples (0 por defecto)");
        System.out.println("  -cr, --cmpx_src <num>          Recursos compartidos complejos (0 por defecto)");
        System.out.println("  -msr,--min_shrd_src <num>      Recursos compartidos simples mínimos (auto por defecto)");
        System.out.println("  -Msr,--max_shrd_src <num>      Recursos compartidos simples máximos (auto por defecto)");
        System.out.println("  -h,  --help                    Mostrar esta ayuda");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  # Generar 1 red con 1 circuito, 1 fork/join y recursos por defecto");
        System.out.println("  java -cp target/classes generated.S3PRGeneratorMain -n 1 -c 1 -fj 1");
        System.out.println();
        System.out.println("  # Equivalente al comando Python: python auto_gen.py -n 1 -o 1 -c 1 -ifj 1 -ffj 1 -iffj 1 -fj 1 -cr 0 -msr 5");
        System.out.println("  java -cp target/classes generated.S3PRGeneratorMain -n 1 -c 1 -ifj 1 -ffj 1 -iffj 1 -fj 1 -cr 0 -msr 5");
        System.out.println();
        System.out.println("Nota: Debe especificar al menos 2 figuras en total para generar una red S3PR válida.");
    }
}