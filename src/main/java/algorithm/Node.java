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
}