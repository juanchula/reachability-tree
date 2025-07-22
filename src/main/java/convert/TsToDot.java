package convert;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class TsToDot {
    private static class TransitionEdge {
        String tname;
        int dst;
        TransitionEdge(String tname, int dst) {
            this.tname = tname;
            this.dst = dst;
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java TsToReachabilityTree <input.ts> <output.dot>");
            System.exit(1);
        }
        Path inputFile = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);

        try {
            List<String> lines = Files.readAllLines(inputFile);

            // Parse markings
            Map<Integer, List<Integer>> markingPlaces = new LinkedHashMap<>();
            Pattern markPat = Pattern.compile("^\\s*(\\d+)\\s*:\\s*(.*)$");
            Set<Integer> allPlaces = new TreeSet<>();

            for (String line : lines) {
                Matcher m = markPat.matcher(line);
                if (m.matches()) {
                    int id = Integer.parseInt(m.group(1));
                    String rest = m.group(2).trim();
                    List<Integer> places = new ArrayList<>();
                    if (!rest.isEmpty()) {
                        for (String p : rest.split("\\s+")) {
                            if (p.startsWith("p")) {
                                try {
                                    int pi = Integer.parseInt(p.substring(1));
                                    places.add(pi);
                                    allPlaces.add(pi);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                    markingPlaces.put(id, places);
                }
            }
            int maxPlace = allPlaces.isEmpty() ? 0 : Collections.max(allPlaces);
            int placeCount = maxPlace + 1;

            // Parse reachability edges
            Map<Integer, List<TransitionEdge>> adj = new HashMap<>();
            Pattern edgeLinePat = Pattern.compile("^\\s*(\\d+)\\s*->\\s*(.+)$");
            Pattern edgePat = Pattern.compile("^(t\\d+)/(.+)$");
            Pattern digitPat = Pattern.compile("(\\d+)");

            for (String line : lines) {
                Matcher lineMatcher = edgeLinePat.matcher(line);
                if (!lineMatcher.matches()) continue;
                int src = Integer.parseInt(lineMatcher.group(1));
                String rest = lineMatcher.group(2);
                for (String part : rest.split(",")) {
                    String pr = part.trim();
                    Matcher e = edgePat.matcher(pr);
                    if (!e.matches()) continue;
                    String tname = e.group(1);
                    String dstRaw = e.group(2);
                    Matcher dm = digitPat.matcher(dstRaw);
                    if (!dm.find()) continue;
                    int dst = Integer.parseInt(dm.group(1));
                    adj.computeIfAbsent(src, k -> new ArrayList<>())
                            .add(new TransitionEdge(tname, dst));
                }
            }

            // Generate DOT
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
                writer.write("digraph ReachabilityTree {"); writer.newLine();
                writer.write("  node [shape=box];"); writer.newLine();

                // Start DFS from initial marking 0
                dfs(0, "m0", new LinkedHashSet<>(Collections.singletonList(0)),
                        markingPlaces, placeCount, adj, writer);

                writer.write("}"); writer.newLine();
            }

            System.out.println("DOT file generated at: " + outputFile.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void dfs(int currentId,
                            String nodeName,
                            Set<Integer> visited,
                            Map<Integer, List<Integer>> markingPlaces,
                            int placeCount,
                            Map<Integer, List<TransitionEdge>> adj,
                            BufferedWriter writer) throws IOException {
        // Write node with marking vector label
        List<Integer> places = markingPlaces.getOrDefault(currentId, Collections.emptyList());
        StringBuilder vb = new StringBuilder("[");
        for (int i = 0; i < placeCount; i++) {
            vb.append(places.contains(i) ? "1" : "0");
            if (i < placeCount - 1) vb.append(", ");
        }
        vb.append("]");
        writer.write(String.format("  \"%s\" [label=\"%s\"];", nodeName, vb));
        writer.newLine();

        // Recurse on outgoing edges
        for (TransitionEdge e : adj.getOrDefault(currentId, Collections.emptyList())) {
            int dst = e.dst;
            if (visited.contains(dst)) continue;  // avoid cycles
            String childName = nodeName + "_" + e.tname;
            writer.write(String.format("  \"%s\" -> \"%s\" [label=\"%s\"];",
                    nodeName, childName, e.tname));
            writer.newLine();

            Set<Integer> newVisited = new LinkedHashSet<>(visited);
            newVisited.add(dst);
            dfs(dst, childName, newVisited, markingPlaces, placeCount, adj, writer);
        }
    }
}
