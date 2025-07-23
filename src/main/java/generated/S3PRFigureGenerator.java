package generated;

import java.util.*;

/**
 * Generador de figuras S3PR individual.
 * Equivalente a las funciones de petrinet_gen_v2.py
 */
public class S3PRFigureGenerator {
    
    private Random random;
    private S3PRGeneratorConfig config;
    private int currentPlaceIndex;
    private int currentTransitionIndex;
    
    public S3PRFigureGenerator(S3PRGeneratorConfig config) {
        this.config = config;
        this.random = new Random();
        this.currentPlaceIndex = 1;
        this.currentTransitionIndex = 1;
    }
    
    /**
     * Genera un tren (secuencia de transiciones y lugares)
     */
    public PetriNetFigure generateTrain() {
        int numTransitions = random.nextInt(config.getMaxTransitionsTrains() - 
                                          config.getMinTransitionsTrains() + 1) + 
                                          config.getMinTransitionsTrains();
        int numPlaces = numTransitions - 1; // Tren tiene una transición más que lugares
        
        // Crear matrices I+ e I-
        int[][] iPlus = new int[numPlaces][numTransitions];
        int[][] iMinus = new int[numPlaces][numTransitions];
        
        // Estructura de tren: P1 -> T1 -> P2 -> T2 -> ... -> Tn
        for (int i = 0; i < numPlaces; i++) {
            if (i > 0) {
                iPlus[i][i] = 1;  // Transición i produce token en lugar i+1
            }
            iMinus[i][i + 1] = 1;  // Lugar i consume token para transición i+1
        }
        
        // Marcado inicial (sin tokens en lugares internos)
        int[] initialMarking = new int[numPlaces];
        
        // T-invariante (todas las transiciones)
        List<Integer> invariant = new ArrayList<>();
        for (int i = 0; i < numTransitions; i++) {
            invariant.add(currentTransitionIndex + i);
        }
        
        PetriNetFigure figure = new PetriNetFigure(
            PetriNetFigure.FigureType.CIRCUIT, // Se usa como componente de circuito
            iPlus, iMinus, initialMarking, invariant,
            currentPlaceIndex, currentTransitionIndex
        );
        
        currentPlaceIndex += numPlaces;
        currentTransitionIndex += numTransitions;
        
        return figure;
    }
    
    /**
     * Genera un circuito (tren + plaza idle)
     */
    public PetriNetFigure generateCircuit() {
        PetriNetFigure train = generateTrain();
        
        // Añadir plaza idle al principio
        int trainPlaces = train.getPlaceCount();
        int trainTransitions = train.getTransitionCount();
        
        int[][] iPlus = new int[trainPlaces + 1][trainTransitions];
        int[][] iMinus = new int[trainPlaces + 1][trainTransitions];
        
        // Plaza idle (índice 0)
        iMinus[0][0] = 1; // Idle consume para primera transición
        iPlus[0][trainTransitions - 1] = 1; // Última transición produce en idle
        
        // Copiar matrices del tren (desplazadas)
        for (int i = 0; i < trainPlaces; i++) {
            for (int j = 0; j < trainTransitions; j++) {
                iPlus[i + 1][j] = train.getIPlus()[i][j];
                iMinus[i + 1][j] = train.getIMinus()[i][j];
            }
        }
        
        // Marcado inicial con tokens en plaza idle
        int[] initialMarking = new int[trainPlaces + 1];
        initialMarking[0] = random.nextInt(config.getMaxTokensIdle() - 
                                         config.getMinTokensIdle() + 1) + 
                                         config.getMinTokensIdle();
        
        return new PetriNetFigure(
            PetriNetFigure.FigureType.CIRCUIT,
            iPlus, iMinus, initialMarking, train.getInvariant(),
            train.getStartPlaceIndex() - 1, // Ajustar por plaza idle
            train.getStartTransitionIndex()
        );
    }
    
    /**
     * Genera fork/join simple
     */
    public PetriNetFigure generateForkJoin() {
        // Fork: 1 lugar inicial -> 2 procesos paralelos -> 1 lugar final
        // Estructura: P1 -> T1 (fork) -> P2, P3 -> T2, T3 -> P4 <- T4 (join)
        
        int numPlaces = 4; // P1(inicial), P2, P3(paralelos), P4(final)
        int numTransitions = 4; // T1(fork), T2, T3(procesos), T4(join)
        
        int[][] iPlus = new int[numPlaces][numTransitions];
        int[][] iMinus = new int[numPlaces][numTransitions];
        
        // Fork: T1 consume P1, produce P2 y P3
        iMinus[0][0] = 1; // P1 -> T1
        iPlus[1][0] = 1;  // T1 -> P2
        iPlus[2][0] = 1;  // T1 -> P3
        
        // Procesos paralelos: T2 consume P2, T3 consume P3
        iMinus[1][1] = 1; // P2 -> T2
        iMinus[2][2] = 1; // P3 -> T3
        
        // Join: T4 espera tokens de procesos paralelos, produce P4
        iPlus[3][1] = 1;  // T2 -> P4
        iPlus[3][2] = 1;  // T3 -> P4
        iMinus[3][3] = 1; // P4 -> T4 (salida)
        
        int[] initialMarking = new int[numPlaces];
        initialMarking[0] = 1; // Token inicial en lugar de entrada
        
        List<Integer> invariant = new ArrayList<>();
        for (int i = 0; i < numTransitions; i++) {
            invariant.add(currentTransitionIndex + i);
        }
        
        PetriNetFigure figure = new PetriNetFigure(
            PetriNetFigure.FigureType.FORK_JOIN,
            iPlus, iMinus, initialMarking, invariant,
            currentPlaceIndex, currentTransitionIndex
        );
        
        currentPlaceIndex += numPlaces;
        currentTransitionIndex += numTransitions;
        
        return figure;
    }
    
    /**
     * Genera circuito fork/join (fork/join + plaza idle)
     */
    public PetriNetFigure generateCircuitForkJoin() {
        PetriNetFigure forkJoin = generateForkJoin();
        
        // Añadir plaza idle que se conecta al inicio y final
        int fjPlaces = forkJoin.getPlaceCount();
        int fjTransitions = forkJoin.getTransitionCount();
        
        int[][] iPlus = new int[fjPlaces + 1][fjTransitions + 2]; // +2 transiciones para idle
        int[][] iMinus = new int[fjPlaces + 1][fjTransitions + 2];
        
        // Plaza idle (índice 0)
        iMinus[0][fjTransitions] = 1; // Idle -> T_start
        iPlus[0][fjTransitions + 1] = 1; // T_end -> Idle
        
        // Conectar idle con fork/join
        iPlus[1][fjTransitions] = 1; // T_start -> P1 (entrada fork/join)
        iMinus[fjPlaces][fjTransitions + 1] = 1; // P4 (salida fork/join) -> T_end
        
        // Copiar matrices fork/join
        for (int i = 0; i < fjPlaces; i++) {
            for (int j = 0; j < fjTransitions; j++) {
                iPlus[i + 1][j] = forkJoin.getIPlus()[i][j];
                iMinus[i + 1][j] = forkJoin.getIMinus()[i][j];
            }
        }
        
        int[] initialMarking = new int[fjPlaces + 1];
        initialMarking[0] = random.nextInt(config.getMaxTokensIdle() - 
                                         config.getMinTokensIdle() + 1) + 
                                         config.getMinTokensIdle();
        
        List<Integer> invariant = new ArrayList<>(forkJoin.getInvariant());
        invariant.add(currentTransitionIndex + fjTransitions);
        invariant.add(currentTransitionIndex + fjTransitions + 1);
        
        PetriNetFigure figure = new PetriNetFigure(
            PetriNetFigure.FigureType.CIRCUIT_FORK_JOIN,
            iPlus, iMinus, initialMarking, invariant,
            forkJoin.getStartPlaceIndex() - 1,
            forkJoin.getStartTransitionIndex()
        );
        
        currentPlaceIndex += fjPlaces + 1;
        currentTransitionIndex += fjTransitions + 2;
        
        return figure;
    }
    
    /**
     * Resetea los contadores de índices
     */
    public void resetCounters() {
        currentPlaceIndex = 1;
        currentTransitionIndex = 1;
    }
    
    // Getters para índices actuales
    public int getCurrentPlaceIndex() { return currentPlaceIndex; }
    public int getCurrentTransitionIndex() { return currentTransitionIndex; }
}