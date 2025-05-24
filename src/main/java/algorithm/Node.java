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

class Node {
    private String markingId;
    private Map<Integer, int[]> subnetMarkings;
    private AtomicInteger completionCounter;
    private int[] finalGlobalMarking;

    public Node(String markingId, Map<Integer, int[]> subnetMarkings, int completionCount) {
        this.markingId = markingId;
        this.subnetMarkings = subnetMarkings;
        this.completionCounter = new AtomicInteger(completionCount);
    }

    public String getMarkingId() {
        return markingId;
    }

    public Map<Integer, int[]> getSubnetMarkings() {
        return subnetMarkings;
    }

    public void setSubnetMarking(int subnetId, int[] marking) {
        subnetMarkings.put(subnetId, marking);
    }

    public int decrementAndGetCompletionCounter() {
        return completionCounter.decrementAndGet();
    }

    /**
     * Construye el marcado global a partir de los marcados de las subredes.
     */
    public int[] buildGlobalMarking(PetriNet petriNet) {
        int[] globalMarking = new int[petriNet.getInitialMarking().length];

        for (Subnet subnet : petriNet.getSubnets()) {
            int[] subnetMarking = subnetMarkings.get(subnet.getId());
            int[] placeIndices = subnet.getPlaceIndices();

            for (int i = 0; i < placeIndices.length; i++) {
                globalMarking[placeIndices[i]] = subnetMarking[i];
            }
        }

        return globalMarking;
    }

    /**
     * Set the given places as omega (-1) in the marking.
     */
    public static int[] setOmegas(int[] marking, boolean[] omegaPlaces) {
        int[] result = Arrays.copyOf(marking, marking.length);
        for (int i = 0; i < result.length; i++) {
            if (omegaPlaces[i]) {
                result[i] = -1;
            }
        }
        return result;
    }

    /**
     * Checks if this marking is omega with respect to another marking.
     * Returns an array of booleans indicating which places should become omega.
     *
     * A marking m is omega with respect to ancestor a if for all i: m[i] >= a[i] (or m[i] == -1),
     * and for at least one i: m[i] > a[i] (and a[i] != -1), and a[i] != -1.
     *
     * contextMarkingId: the marking id of the current context (e.g., ancestorId in the chain)
     */
    public static boolean[] getOmegaPlaces(int[] ancestor, int[] current, String contextMarkingId) {
        boolean[] omega = new boolean[ancestor.length];
        boolean strictlyGreater = false;
        boolean shouldLog = false;
        if (algorithm.Main.DEBUG) {
            if (algorithm.Main.DEBUG_MARKING_ID == null) {
                shouldLog = true;
            } else if (contextMarkingId != null && isPrefixOf(contextMarkingId, algorithm.Main.DEBUG_MARKING_ID)) {
                shouldLog = true;
            }
        }
        if (shouldLog) {
            System.out.println("[" + contextMarkingId + "] Comparing ancestor=" + Arrays.toString(ancestor) + " with current=" + Arrays.toString(current));
        }
        for (int i = 0; i < ancestor.length; i++) {
            String msg = null;
            if (ancestor[i] == -1) {
                continue; // already omega in ancestor
            }
            if (current[i] == -1) {
                continue; // already omega in current
            }
            if (current[i] > ancestor[i]) {
                omega[i] = true;
                strictlyGreater = true;
            } else if (current[i] < ancestor[i]) {
                if (shouldLog) {
                    System.out.println("[" + contextMarkingId + "] ABORT: current[" + i + "]=" + current[i] + " < ancestor[" + i + "]=" + ancestor[i]);
                }
                return new boolean[ancestor.length];
            }
        }
        if (shouldLog) {
            System.out.println("[" + contextMarkingId + "] Result omega=" + Arrays.toString(strictlyGreater ? omega : new boolean[ancestor.length]));
        }
        return strictlyGreater ? omega : new boolean[ancestor.length];
    }

    // Helper to check if ancestorId is an ancestor of markingId (prefix match)
    private static boolean isAncestor(String ancestorId, String markingId) {
        if (ancestorId == null || markingId == null) return false;
        return markingId.startsWith(ancestorId);
    }

    // Helper to check if contextMarkingId is a prefix of markingId (i.e., markingId startsWith contextMarkingId)
    private static boolean isPrefixOf(String contextMarkingId, String markingId) {
        if (contextMarkingId == null || markingId == null) return false;
        return markingId.startsWith(contextMarkingId);
    }

    /**
     * Returns a copy of the marking with omegas (-1) propagated from the given omega mask.
     */
    public static int[] propagateOmegas(int[] marking, boolean[] omegaPlaces) {
        int[] result = Arrays.copyOf(marking, marking.length);
        for (int i = 0; i < result.length; i++) {
            if (omegaPlaces[i]) {
                result[i] = -1;
            }
        }
        return result;
    }

    public void setFinalGlobalMarking(int[] marking) { this.finalGlobalMarking = marking; }
    public int[] getFinalGlobalMarking() { return finalGlobalMarking; }
}