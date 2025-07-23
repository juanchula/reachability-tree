package generated;

/**
 * Configuración para el generador de redes de Petri S3PR.
 * Equivalente a los parámetros de auto_gen.py
 */
public class S3PRGeneratorConfig {
    
    // Parámetros básicos
    private int numNets = 1;
    private String outputFolder = "gen_nets";
    
    // Tipos de figuras a generar
    private int circuits = 0;                    // -c: circuitos simples
    private int initForkJoin = 0;               // -ifj: fork/join con tren inicial
    private int finalForkJoin = 0;              // -ffj: fork/join con tren final
    private int initFinalForkJoin = 0;          // -iffj: fork/join con tren inicial y final
    private int forkJoin = 0;                   // -fj: fork/join simples
    
    // Recursos compartidos
    private int complexResources = 0;           // -cr: recursos compartidos complejos
    private int minSharedResources = 0;         // -msr: mínimo recursos compartidos simples
    private int maxSharedResources = 0;         // -Msr: máximo recursos compartidos simples
    
    // Configuración de trenes
    private int minTransitionsTrains = 3;
    private int maxTransitionsTrains = 6;
    private int minTokensIdle = 1;
    private int maxTokensIdle = 3;
    
    // Constructor por defecto
    public S3PRGeneratorConfig() {}
    
    // Constructor con parámetros principales
    public S3PRGeneratorConfig(int circuits, int initForkJoin, int finalForkJoin, 
                               int initFinalForkJoin, int forkJoin, int complexResources, 
                               int minSharedResources) {
        this.circuits = circuits;
        this.initForkJoin = initForkJoin;
        this.finalForkJoin = finalForkJoin;
        this.initFinalForkJoin = initFinalForkJoin;
        this.forkJoin = forkJoin;
        this.complexResources = complexResources;
        this.minSharedResources = minSharedResources;
        
        // Calcular valores automáticos como en Python
        int totalFigures = circuits + initForkJoin + finalForkJoin + initFinalForkJoin + forkJoin;
        if (minSharedResources < 1) {
            this.minSharedResources = totalFigures + 3;
        }
        if (maxSharedResources < this.minSharedResources) {
            this.maxSharedResources = this.minSharedResources * 2;
        }
    }
    
    /**
     * Valida que la configuración sea válida
     */
    public boolean isValid() {
        int totalFigures = circuits + initForkJoin + finalForkJoin + initFinalForkJoin + forkJoin;
        return totalFigures >= 2 && numNets >= 1;
    }
    
    /**
     * Obtiene el número total de figuras
     */
    public int getTotalFigures() {
        return circuits + initForkJoin + finalForkJoin + initFinalForkJoin + forkJoin;
    }
    
    // Getters y Setters
    public int getNumNets() { return numNets; }
    public void setNumNets(int numNets) { this.numNets = numNets; }
    
    public String getOutputFolder() { return outputFolder; }
    public void setOutputFolder(String outputFolder) { this.outputFolder = outputFolder; }
    
    public int getCircuits() { return circuits; }
    public void setCircuits(int circuits) { this.circuits = circuits; }
    
    public int getInitForkJoin() { return initForkJoin; }
    public void setInitForkJoin(int initForkJoin) { this.initForkJoin = initForkJoin; }
    
    public int getFinalForkJoin() { return finalForkJoin; }
    public void setFinalForkJoin(int finalForkJoin) { this.finalForkJoin = finalForkJoin; }
    
    public int getInitFinalForkJoin() { return initFinalForkJoin; }
    public void setInitFinalForkJoin(int initFinalForkJoin) { this.initFinalForkJoin = initFinalForkJoin; }
    
    public int getForkJoin() { return forkJoin; }
    public void setForkJoin(int forkJoin) { this.forkJoin = forkJoin; }
    
    public int getComplexResources() { return complexResources; }
    public void setComplexResources(int complexResources) { this.complexResources = complexResources; }
    
    public int getMinSharedResources() { return minSharedResources; }
    public void setMinSharedResources(int minSharedResources) { this.minSharedResources = minSharedResources; }
    
    public int getMaxSharedResources() { return maxSharedResources; }
    public void setMaxSharedResources(int maxSharedResources) { this.maxSharedResources = maxSharedResources; }
    
    public int getMinTransitionsTrains() { return minTransitionsTrains; }
    public void setMinTransitionsTrains(int minTransitionsTrains) { this.minTransitionsTrains = minTransitionsTrains; }
    
    public int getMaxTransitionsTrains() { return maxTransitionsTrains; }
    public void setMaxTransitionsTrains(int maxTransitionsTrains) { this.maxTransitionsTrains = maxTransitionsTrains; }
    
    public int getMinTokensIdle() { return minTokensIdle; }
    public void setMinTokensIdle(int minTokensIdle) { this.minTokensIdle = minTokensIdle; }
    
    public int getMaxTokensIdle() { return maxTokensIdle; }
    public void setMaxTokensIdle(int maxTokensIdle) { this.maxTokensIdle = maxTokensIdle; }
}