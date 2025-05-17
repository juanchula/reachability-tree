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
 * Representa una tarea de disparo de una transición en una subred.
 */
class FiringTask {
    private String parentMarkingId;
    private int transitionIndex;
    private Subnet subnet;

    public FiringTask(String parentMarkingId, int transitionIndex, Subnet subnet) {
        this.parentMarkingId = parentMarkingId;
        this.transitionIndex = transitionIndex;
        this.subnet = subnet;
    }

    public String getParentMarkingId() {
        return parentMarkingId;
    }

    public int getTransitionIndex() {
        return transitionIndex;
    }

    public Subnet getSubnet() {
        return subnet;
    }

    public String getChildMarkingId() {
        return parentMarkingId + "_t" + transitionIndex;
    }
}