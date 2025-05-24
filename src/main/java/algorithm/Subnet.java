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
 * Representa una subred de la red de Petri original.
 */
class Subnet {
    private int id;
    private int[] placeIndices;
    private int[] transIndices;
    private int[][] subnetIMinus;
    private int[][] subnetIPlus;

    public Subnet(int id, int[] placeIndices, int[] transIndices, int[][] originalIMinus, int[][] originalIPlus) {
        this.id = id;
        this.placeIndices = placeIndices;
        this.transIndices = transIndices;

        // Construir las matrices de incidencia para la subred
        this.subnetIMinus = new int[placeIndices.length][transIndices.length];
        this.subnetIPlus = new int[placeIndices.length][transIndices.length];

        for (int i = 0; i < placeIndices.length; i++) {
            for (int j = 0; j < transIndices.length; j++) {
                this.subnetIMinus[i][j] = originalIMinus[placeIndices[i]][transIndices[j]];
                this.subnetIPlus[i][j] = originalIPlus[placeIndices[i]][transIndices[j]];
            }
        }
    }

    public int getId() {
        return id;
    }

    public int[] getPlaceIndices() {
        return placeIndices;
    }

    public int[] getTransIndices() {
        return transIndices;
    }

    public int[][] getSubnetIMinus() {
        return subnetIMinus;
    }

    public int[][] getSubnetIPlus() {
        return subnetIPlus;
    }

    /**
     * Verifica si la subred contiene una transición específica.
     */
    public boolean containsTransition(int transIndex) {
        for (int t : transIndices) {
            if (t == transIndex) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtiene el índice local de una transición global.
     */
    public int getLocalTransIndex(int globalTransIndex) {
        for (int i = 0; i < transIndices.length; i++) {
            if (transIndices[i] == globalTransIndex) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Extrae el marcado de la subred desde un marcado global.
     */
    public int[] extractSubnetMarking(int[] globalMarking) {
        int[] subnetMarking = new int[placeIndices.length];
        for (int i = 0; i < placeIndices.length; i++) {
            subnetMarking[i] = globalMarking[placeIndices[i]];
        }
        return subnetMarking;
    }

    /**
     * Dispara una transición en la subred.
     */
    public int[] fireTransition(int localTransIndex, int[] subnetMarking) {
        int[] newSubnetMarking = Arrays.copyOf(subnetMarking, subnetMarking.length);

        for (int i = 0; i < placeIndices.length; i++) {
            if (subnetMarking[i] == -1) {
                newSubnetMarking[i] = -1; // Omega stays omega
            } else {
                newSubnetMarking[i] -= subnetIMinus[i][localTransIndex];
                newSubnetMarking[i] += subnetIPlus[i][localTransIndex];
            }
        }

        return newSubnetMarking;
    }
}