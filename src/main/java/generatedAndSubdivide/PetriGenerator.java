package generatedAndSubdivide;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generador de redes de Petri S3PR, portado desde el script Python original.
 * Produce las matrices I+ e I-, y el marcado inicial (M0), según:
 * - Trenes
 * - Circuitos
 * - Fork/Join (varias variantes)
 * - Recursos privados, compartidos y complejos
 */
public class PetriGenerator {
    // Parámetros globales (tal como en el script Python)
    private final int minTokensIdle = 1;
    private final int maxTokensIdle = 3;
    private final int minTransitionsTrains = 3;
    private final int maxTransitionsTrains = 6;

    private final Random rnd = new Random();

    // Invariantes: cada tren guarda la lista de sus transiciones (índices 1-based)
    private final List<List<Integer>> invariants = new ArrayList<>();

    // Para acumular tokens iniciales (M0) de cada plaza agregada
    private final List<Integer> markingList = new ArrayList<>();

    /**
     * Genera la red S3PR completa ejecutando en orden:
     * 1) circuitos simples
     * 2) fork/join con tren inicial
     * 3) fork/join con tren final
     * 4) fork/join con tren inicial y final
     * 5) circuitos fork/join
     * 6) recursos compartidos simples
     * 7) recursos compartidos complejos
     *
     * Devuelve un PetriNet con:
     *  - incidence[i][j] = I_plus[i][j] - I_minus[i][j]
     *  - M0[i] según markingList
     */
    public PetriNet generateFull(
            int circuitsQuantity,
            int cirFjWithInitTrainQty,
            int cirFjWithFinalTrainQty,
            int cirFjWithInitFinalTrainQty,
            int cirForkJoinQty,
            int cmpxResourcesQty,
            int minShareResources,
            int maxShareResources
    ) {
        int[][] ip = new int[0][0];
        int[][] im = new int[0][0];

        // 1) Circuitos simples
        List<int[][][]> circuits = generateCircuits(circuitsQuantity);
        for (int[][][] pair : circuits) {
            ip = blockDiag(ip, pair[0]);
            im = blockDiag(im, pair[1]);
        }

        // 2) Fork/Join con tren inicial
        List<int[][][]> forkInit = generateForkJoinWithInitTrain(cirFjWithInitTrainQty);
        for (int[][][] pair : forkInit) {
            ip = blockDiag(ip, pair[0]);
            im = blockDiag(im, pair[1]);
        }

        // 3) Fork/Join con tren final
        List<int[][][]> forkFinal = generateForkJoinWithFinalTrain(cirFjWithFinalTrainQty);
        for (int[][][] pair : forkFinal) {
            ip = blockDiag(ip, pair[0]);
            im = blockDiag(im, pair[1]);
        }

        // 4) Fork/Join con tren inicial y final
        List<int[][][]> forkInitFinal = generateForkJoinWithInitialAndFinalTrains(cirFjWithInitFinalTrainQty);
        for (int[][][] pair : forkInitFinal) {
            ip = blockDiag(ip, pair[0]);
            im = blockDiag(im, pair[1]);
        }

        // 5) Circuitos Fork/Join (con plaza idle adicional)
        List<int[][][]> cirForkJoin = generateCircuitsForkJoin(cirForkJoinQty);
        for (int[][][] pair : cirForkJoin) {
            ip = blockDiag(ip, pair[0]);
            im = blockDiag(im, pair[1]);
        }

        // 6) Recursos compartidos simples
        int numTrans = (ip.length > 0 ? ip[0].length : 0);
        int[][][] shared = generateShareResources(numTrans, minShareResources, maxShareResources);
        ip = vstack(ip, shared[0]);
        im = vstack(im, shared[1]);

        // 7) Recursos compartidos complejos
        numTrans = (ip.length > 0 ? ip[0].length : 0);
        int[][][] cmpx = generateCmpxResources(numTrans, cmpxResourcesQty);
        ip = vstack(ip, cmpx[0]);
        im = vstack(im, cmpx[1]);

        // 8) Construir matriz de incidencia
        int rows = ip.length;
        int cols = (rows > 0 ? ip[0].length : 0);
        int[][] incidence = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                incidence[i][j] = ip[i][j] - im[i][j];
            }
        }

        // 9) Convertir markingList a vector
        int[] M0 = markingList.stream().mapToInt(Integer::intValue).toArray();

        return new PetriNet(incidence, M0);
    }

    // ----------------------------------------
    // Métodos auxiliares para bloques de red
    // ----------------------------------------

    /**
     * Genera 'quantity' trenes.
     * Cada tren: plusMatrix = identidad(rand) sin última fila,
     *             minusMatrix = identidad(rand) desplazada a la derecha sin última fila,
     *             y pone minusMatrix[:,0]=0.
     * Registra las plazas (sin tokens) y las transiciones en invariants.
     */
    private List<int[][][]> generateTrains(int quantity) {
        List<int[][][]> trains = new ArrayList<>();

        for (int t = 0; t < quantity; t++) {
            int rand = rnd.nextInt(maxTransitionsTrains - minTransitionsTrains + 1) + minTransitionsTrains;

            // plusMatrix: identidad rand×rand, eliminar última fila
            int[][] plusMatrix = identity(rand);
            plusMatrix = deleteLastRow(plusMatrix);

            // minusMatrix: identidad(rand) desplazada, eliminar última fila
            int[][] minusMatrix = rollIdentity(rand);
            minusMatrix = deleteLastRow(minusMatrix);
            // minusMatrix[:,0] = 0
            for (int i = 0; i < minusMatrix.length; i++) {
                minusMatrix[i][0] = 0;
            }

            // Registrar transiciones en invariants (1-based)
            List<Integer> newInv = new ArrayList<>();
            for (int k = 1; k <= plusMatrix[0].length; k++) {
                newInv.add(k);
            }
            invariants.add(newInv);

            // Cada fila de plusMatrix corresponde a una plaza sin tokens
            for (int i = 0; i < plusMatrix.length; i++) {
                markingList.add(0);
            }

            trains.add(new int[][][]{plusMatrix, minusMatrix});
        }

        return trains;
    }

    /**
     * Genera 'quantity' circuitos cerrados.
     * Cada circuito = un tren + plaza idle conectada.
     * La plaza idle recibe tokens aleatorios [minTokensIdle..maxTokensIdle].
     */
    private List<int[][][]> generateCircuits(int quantity) {
        List<int[][][]> circuits = new ArrayList<>();

        for (int idx = 0; idx < quantity; idx++) {
            // Generar un tren
            List<int[][][]> trainList = generateTrains(1);
            int[][] plusMatrix = trainList.get(0)[0];
            int[][] minusMatrix = trainList.get(0)[1];
            int numTrans = plusMatrix[0].length;

            // Plaza idle: plus fila con 1 en última transición
            int[] idlePlus = new int[numTrans];
            idlePlus[numTrans - 1] = 1;
            plusMatrix = vstack(plusMatrix, new int[][]{idlePlus});

            // Para minus: roll de idlePlus
            int[] idleMinus = rollLeft(idlePlus);
            minusMatrix = vstack(minusMatrix, new int[][]{idleMinus});

            // Tokens para plaza idle
            int tokens = rnd.nextInt(maxTokensIdle - minTokensIdle + 1) + minTokensIdle;
            markingList.add(tokens);

            circuits.add(new int[][][]{plusMatrix, minusMatrix});
        }

        return circuits;
    }

    /**
     * Genera 'quantity' figuras fork/join simples.
     *  - Dos trenes concatenados (blockDiag),
     *  - Plaza fork: fila con 1 en première trans de ambos trenes (se agrega en minusMatrix),
     *  - Plaza join: fila con 1 en últimas trans de ambos trenes (se agrega en plusMatrix).
     * Ambas plazas tienen marcado 0.
     */
    private List<int[][][]> generateForksJoins(int quantity) {
        List<int[][][]> forks = new ArrayList<>();

        for (int idx = 0; idx < quantity; idx++) {
            // Dos trenes
            List<int[][][]> trains = generateTrains(2);
            int[][] t1Plus = trains.get(0)[0];
            int[][] t1Minus = trains.get(0)[1];
            int[][] t2Plus = trains.get(1)[0];
            int[][] t2Minus = trains.get(1)[1];

            int[][] plusMatrix = blockDiag(t1Plus, t2Plus);
            int[][] minusMatrix = blockDiag(t1Minus, t2Minus);

            int numTrans = plusMatrix[0].length;
            int t1Size = t1Plus[0].length;

            // Plaza fork: en minusMatrix nueva fila
            int[] forkPlace = new int[numTrans];
            forkPlace[0] = 1;           // primera transición tren1
            forkPlace[t1Size] = 1;      // primera transición tren2
            minusMatrix = vstack(minusMatrix, new int[][]{forkPlace});
            plusMatrix = vstack(plusMatrix, new int[][]{new int[numTrans]});
            markingList.add(0);

            // Plaza join: en plusMatrix nueva fila
            int[] joinPlace = new int[numTrans];
            joinPlace[t1Size - 1] = 1;    // última trans tren1
            joinPlace[numTrans - 1] = 1;  // última trans tren2
            plusMatrix = vstack(plusMatrix, new int[][]{joinPlace});
            minusMatrix = vstack(minusMatrix, new int[][]{new int[numTrans]});
            markingList.add(0);

            forks.add(new int[][][]{plusMatrix, minusMatrix});
        }

        return forks;
    }

    /**
     * Fork/Join con tren inicial.
     *   - Un tren + un fork/join simple,
     *   - Conecta la plaza idle del tren con el fork/join,
     *   - Agrega una transición final y su plaza idle.
     */
    private List<int[][][]> generateForkJoinWithInitTrain(int quantity) {
        List<int[][][]> result = new ArrayList<>();

        for (int idx = 0; idx < quantity; idx++) {
            // Tren
            List<int[][][]> trainList = generateTrains(1);
            int[][] tPlus = trainList.get(0)[0];
            int[][] tMinus = trainList.get(0)[1];

            // Fork/Join simple
            List<int[][][]> fjList = generateForksJoins(1);
            int[][] fjPlus = fjList.get(0)[0];
            int[][] fjMinus = fjList.get(0)[1];

            // Concatenar bloques
            int[][] plusMatrix = blockDiag(tPlus, fjPlus);
            int[][] minusMatrix = blockDiag(tMinus, fjMinus);

            int tCols = tPlus[0].length;
            int tRows = tPlus.length;
            int fjRows = fjPlus.length;

            // Conectar tren con fork/join: agregar 1 en plusMatrix en fila (tRows+fjRows-1), columna tCols
            plusMatrix[tRows + fjRows - 1][tCols] = 1;

            // Agregar transición final (nueva columna)
            int numPlaces = plusMatrix.length;
            int oldCols = plusMatrix[0].length;
            int[][] newPlus = new int[numPlaces][oldCols + 1];
            int[][] newMinus = new int[numPlaces][oldCols + 1];
            for (int i = 0; i < numPlaces; i++) {
                System.arraycopy(plusMatrix[i], 0, newPlus[i], 0, oldCols);
                System.arraycopy(minusMatrix[i], 0, newMinus[i], 0, oldCols);
            }
            // La plaza idle existente del tren (última fila de tPlus) corresponde a índice tRows-1
            // En newMinus, asignar 1 en la fila (numPlaces-1) para conectar plaza idle con nueva transición
            newMinus[numPlaces - 1][oldCols] = 1;

            // Registrar plaza idle: tokens aleatorios
            int tokens = rnd.nextInt(maxTokensIdle - minTokensIdle + 1) + minTokensIdle;
            markingList.add(tokens);

            result.add(new int[][][]{newPlus, newMinus});
        }

        return result;
    }

    /**
     * Fork/Join con tren final.
     *   - Un fork/join simple + un tren,
     *   - Conecta fork/join con tren,
     *   - Agrega una transición inicial y su plaza idle.
     */
    private List<int[][][]> generateForkJoinWithFinalTrain(int quantity) {
        List<int[][][]> result = new ArrayList<>();

        for (int idx = 0; idx < quantity; idx++) {
            // Fork/Join simple
            List<int[][][]> fjList = generateForksJoins(1);
            int[][] fjPlus = fjList.get(0)[0];
            int[][] fjMinus = fjList.get(0)[1];

            // Tren
            List<int[][][]> trainList = generateTrains(1);
            int[][] tPlus = trainList.get(0)[0];
            int[][] tMinus = trainList.get(0)[1];

            // Concatenar bloques
            int[][] plusMatrix = blockDiag(fjPlus, tPlus);
            int[][] minusMatrix = blockDiag(fjMinus, tMinus);

            int fjCols = fjPlus[0].length;
            int fjRows = fjPlus.length;

            // Conectar fork/join con tren: en minusMatrix, fila (fjRows-1), columna fjCols
            minusMatrix[fjRows - 1][fjCols] = 1;

            // Agregar transición inicial (nueva columna)
            int numPlaces = plusMatrix.length;
            int oldCols = plusMatrix[0].length;
            int[][] newPlus = new int[numPlaces][oldCols + 1];
            int[][] newMinus = new int[numPlaces][oldCols + 1];
            for (int i = 0; i < numPlaces; i++) {
                System.arraycopy(plusMatrix[i], 0, newPlus[i], 0, oldCols);
                System.arraycopy(minusMatrix[i], 0, newMinus[i], 0, oldCols);
            }
            // Plaza idle inicial: fila (fjRows-2) de newPlus, asignar 1 en nueva columna
            newPlus[fjRows - 2][oldCols] = 1;

            // Registrar plaza idle: tokens aleatorios
            int tokens = rnd.nextInt(maxTokensIdle - minTokensIdle + 1) + minTokensIdle;
            markingList.add(tokens);

            result.add(new int[][][]{newPlus, newMinus});
        }

        return result;
    }

    /**
     * Fork/Join con tren inicial y final.
     *   - Tren inicial + fork/join simple + tren final,
     *   - Conecta tren inicial con fork/join y fork/join con tren final,
     *   - Agrega plaza idle al final.
     */
    private List<int[][][]> generateForkJoinWithInitialAndFinalTrains(int quantity) {
        List<int[][][]> result = new ArrayList<>();

        for (int idx = 0; idx < quantity; idx++) {
            // Tren inicial
            List<int[][][]> initTrainList = generateTrains(1);
            int[][] initPlus = initTrainList.get(0)[0];
            int[][] initMinus = initTrainList.get(0)[1];

            // Fork/Join simple
            List<int[][][]> fjList = generateForksJoins(1);
            int[][] fjPlus = fjList.get(0)[0];
            int[][] fjMinus = fjList.get(0)[1];

            // Tren final
            List<int[][][]> finalTrainList = generateTrains(1);
            int[][] finPlus = finalTrainList.get(0)[0];
            int[][] finMinus = finalTrainList.get(0)[1];

            // Concatenar bloques: initTrain, fork/join, finalTrain
            int[][] tmp1 = blockDiag(initPlus, fjPlus);
            int[][] tmp2 = blockDiag(initMinus, fjMinus);
            int[][] plusMatrix = blockDiag(tmp1, finPlus);
            int[][] minusMatrix = blockDiag(tmp2, finMinus);

            int initCols = initPlus[0].length;
            int initRows = initPlus.length;
            int fjCols = fjPlus[0].length;
            int fjRows = fjPlus.length;

            // Conectar tren inicial con fork/join: plusMatrix[initRows+fjRows-1][initCols] = 1
            plusMatrix[initRows + fjRows - 1][initCols] = 1;
            // Conectar fork/join con tren final: minusMatrix[initRows+fjRows][initCols + fjCols + ?]
            minusMatrix[initRows + fjRows][initCols + fjCols + 1] = 1;

            // Agregar plaza idle final: 1 en última transición
            int numTrans = plusMatrix[0].length;
            int[] idle = new int[numTrans];
            idle[numTrans - 1] = 1;
            plusMatrix = vstack(plusMatrix, new int[][]{idle});
            int[] idleMinus = rollLeft(idle);
            minusMatrix = vstack(minusMatrix, new int[][]{idleMinus});

            int tokens = rnd.nextInt(maxTokensIdle - minTokensIdle + 1) + minTokensIdle;
            markingList.add(tokens);

            result.add(new int[][][]{plusMatrix, minusMatrix});
        }

        return result;
    }

    /**
     * Circuitos Fork/Join (fork/join + transición inicial + transición final + plaza idle).
     */
    private List<int[][][]> generateCircuitsForkJoin(int quantity) {
        List<int[][][]> result = new ArrayList<>();

        for (int idx = 0; idx < quantity; idx++) {
            // Fork/Join simple
            List<int[][][]> fjList = generateForksJoins(1);
            int[][] fjPlus = fjList.get(0)[0];
            int[][] fjMinus = fjList.get(0)[1];

            int numTrans = fjPlus[0].length;
            int numPlaces = fjPlus.length;

            // Agregar transición inicial (nueva columna)
            int oldCols = fjPlus[0].length;
            int[][] plus1 = new int[numPlaces][oldCols + 1];
            int[][] minus1 = new int[numPlaces][oldCols + 1];
            for (int i = 0; i < numPlaces; i++) {
                System.arraycopy(fjPlus[i], 0, plus1[i], 0, oldCols);
                System.arraycopy(fjMinus[i], 0, minus1[i], 0, oldCols);
            }
            // En plus1, fila (numPlaces-2) (anteúltima de fjPlus) pone 1 en nueva columna
            plus1[numPlaces - 2][oldCols] = 1;

            // Agregar transición final (otra columna)
            int[][] plus2 = new int[numPlaces][oldCols + 2];
            int[][] minus2 = new int[numPlaces][oldCols + 2];
            for (int i = 0; i < numPlaces; i++) {
                System.arraycopy(plus1[i], 0, plus2[i], 0, plus1[0].length);
                System.arraycopy(minus1[i], 0, minus2[i], 0, minus1[0].length);
            }
            // En minus2, fila (numPlaces-1), columna oldCols+1 = 1
            minus2[numPlaces - 1][oldCols + 1] = 1;

            // Agregar plaza idle (nueva fila)
            int[][] finalPlus = vstack(plus2, new int[][]{new int[plus2[0].length]});
            int[] idle = new int[plus2[0].length];
            idle[plus2[0].length - 1] = 1;
            int[][] finalMinus = vstack(minus2, new int[][]{idle});

            int tokens = rnd.nextInt(maxTokensIdle - minTokensIdle + 1) + minTokensIdle;
            markingList.add(tokens);

            result.add(new int[][][]{finalPlus, finalMinus});
        }

        return result;
    }

    /**
     * Genera recursos compartidos simples entre invariantes.
     * Cada recurso = plaza (fila) con 1s en transiciones seleccionadas de dos invariantes.
     * Retorna { [I_plus], [I_minus] }.
     */
    private int[][][] generateShareResources(int numTrans, int minQuantity, int maxQuantity) {
        int q = rnd.nextInt(maxQuantity - minQuantity + 1) + minQuantity;
        int[][] plusMatrix = new int[0][numTrans];
        int[][] minusMatrix = new int[0][numTrans];

        for (int i = 0; i < q; i++) {
            if (invariants.size() < 2) break;
            int[] newShare = new int[numTrans];
            // Tomar 2 invariantes aleatorias
            List<List<Integer>> invSample = pickRandomLists(invariants, 2);
            for (List<Integer> inv : invSample) {
                if (inv.size() < 2) continue;
                int tr = inv.get(rnd.nextInt(inv.size() - 1) + 1);
                newShare[tr - 1] = 1;
            }
            plusMatrix = vstack(plusMatrix, new int[][]{Arrays.copyOf(newShare, numTrans)});
            minusMatrix = vstack(minusMatrix, new int[][]{rollLeft(newShare)});
            markingList.add(1);
        }

        return new int[][][]{plusMatrix, minusMatrix};
    }

    /**
     * Genera recursos compartidos complejos.
     * Cada recurso = plaza con 1s en transiciones de al menos 3 invariantes.
     * Retorna { [I_plus], [I_minus] }.
     */
    private int[][][] generateCmpxResources(int numTrans, int quantity) {
        int[][] plusMatrix = new int[0][numTrans];
        int[][] minusMatrix = new int[0][numTrans];

        for (int idx = 0; idx < quantity; idx++) {
            if (invariants.size() < 3) break;
            int[] newShare = new int[numTrans];
            int k = rnd.nextInt(invariants.size() - 2) + 3; // entre 3 y invariants.size()
            List<List<Integer>> invSample = pickRandomLists(invariants, k);
            for (List<Integer> inv : invSample) {
                if (inv.size() < 2) continue;
                int tr = inv.get(rnd.nextInt(inv.size() - 1) + 1);
                newShare[tr - 1] = 1;
            }
            plusMatrix = vstack(plusMatrix, new int[][]{Arrays.copyOf(newShare, numTrans)});
            minusMatrix = vstack(minusMatrix, new int[][]{rollLeft(newShare)});
            markingList.add(1);
        }

        return new int[][][]{plusMatrix, minusMatrix};
    }

    // -------------------- Herramientas de matrices --------------------

    /** Crea una matriz identidad n×n. */
    private static int[][] identity(int n) {
        int[][] I = new int[n][n];
        for (int i = 0; i < n; i++) I[i][i] = 1;
        return I;
    }

    /** Retorna identidad desplazada a la derecha. */
    private static int[][] rollIdentity(int n) {
        int[][] I = identity(n);
        int[][] R = new int[n][n];
        for (int i = 0; i < n; i++) {
            R[i] = Arrays.copyOf(I[(i + 1) % n], n);
        }
        return R;
    }

    /** Elimina la última fila de M (M debe tener al menos una fila). */
    private static int[][] deleteLastRow(int[][] M) {
        int r = M.length - 1;
        int c = M[0].length;
        int[][] R = new int[r][c];
        for (int i = 0; i < r; i++) {
            R[i] = Arrays.copyOf(M[i], c);
        }
        return R;
    }

    /** Rueda un vector a la izquierda una posición. */
    private static int[] rollLeft(int[] v) {
        int n = v.length;
        int first = v[0];
        int[] r = new int[n];
        for (int i = 0; i < n - 1; i++) {
            r[i] = v[i + 1];
        }
        r[n - 1] = first;
        return r;
    }

    /** Toma k listas de invariantes al azar (sin repetición). */
    private static List<List<Integer>> pickRandomLists(List<List<Integer>> invariants, int k) {
        List<List<Integer>> copy = new ArrayList<>(invariants);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(k, copy.size()));
    }

    /** Concatena A y B en bloque diagonal. */
    private static int[][] blockDiag(int[][] A, int[][] B) {
        int r1 = A.length, c1 = (r1 > 0 ? A[0].length : 0);
        int r2 = B.length, c2 = (r2 > 0 ? B[0].length : 0);
        int[][] C = new int[r1 + r2][c1 + c2];
        for (int i = 0; i < r1; i++) {
            System.arraycopy(A[i], 0, C[i], 0, c1);
        }
        for (int i = 0; i < r2; i++) {
            System.arraycopy(B[i], 0, C[r1 + i], c1, c2);
        }
        return C;
    }

    /** Apila A sobre B verticalmente (misma cantidad de columnas). */
    private static int[][] vstack(int[][] A, int[][] B) {
        if (A.length == 0) return B;
        int[][] C = new int[A.length + B.length][A[0].length];
        for (int i = 0; i < A.length; i++) {
            C[i] = Arrays.copyOf(A[i], A[0].length);
        }
        for (int i = 0; i < B.length; i++) {
            C[A.length + i] = Arrays.copyOf(B[i], A[0].length);
        }
        return C;
    }
}