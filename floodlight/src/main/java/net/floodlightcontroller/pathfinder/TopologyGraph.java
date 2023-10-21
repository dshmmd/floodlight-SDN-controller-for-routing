package net.floodlightcontroller.pathfinder;

import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TopologyGraph {
    private static TopologyGraph instance;
    private static Boolean weighted;
    private static Map<DatapathId, Set<DatapathId>> graph;
    private static Map<DatapathId, Map<DatapathId, Integer>> weightedGraph;
    private static Random random;

    private TopologyGraph() {
        weighted = false;
        graph = new HashMap<>();
        weightedGraph = new HashMap<>();
        random = new Random();
    }

    public static TopologyGraph getInstance() {
        if (instance == null) {
            instance = new TopologyGraph();
        }
        return instance;
    }


    public void addNodeOrEdge(DatapathId... nodes) {
        if (nodes.length == 1) {
            if (nodes[0] == null) return;

            DatapathId node = nodes[0];
            graph.putIfAbsent(node, new HashSet<>());

        } else if (nodes.length == 2) {
            if (nodes[0] == null || nodes[1] == null) return;

            DatapathId node1 = nodes[0];
            DatapathId node2 = nodes[1];
            graph.putIfAbsent(node1, new HashSet<>());
            graph.get(node1).add(node2);
            graph.putIfAbsent(node2, new HashSet<>());
            graph.get(node2).add(node1);

        } else {
            throw new IllegalArgumentException("Invalid number of arguments. Expected 1 or 2 nodes.");
        }
    }


    public List<DatapathId> findShortestPath(DatapathId start, DatapathId end) {
        if (!weighted) {
            weightGraph();
            weighted= true;
            printWeightedGraph();
        }

        Map<DatapathId, DatapathId> previousNodes = new HashMap<>();
        Map<DatapathId, Integer> shortestDistances = new HashMap<>();
        PriorityQueue<DatapathId> queue = new PriorityQueue<>(
                Comparator.comparingInt(node -> shortestDistances.getOrDefault(node, Integer.MAX_VALUE)));

        shortestDistances.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty()) {
            DatapathId currentNode = queue.poll();
            int currentNodeDistance = shortestDistances.get(currentNode);

            for (DatapathId adjacentNode : weightedGraph.get(currentNode).keySet()) {
                int edgeWeight = weightedGraph.get(currentNode).get(adjacentNode);
                int distanceThroughCurrentNode = currentNodeDistance + edgeWeight;

                if (!shortestDistances.containsKey(adjacentNode) || distanceThroughCurrentNode < shortestDistances.get(adjacentNode)) {
                    shortestDistances.put(adjacentNode, distanceThroughCurrentNode);
                    previousNodes.put(adjacentNode, currentNode);
                    queue.add(adjacentNode);
                }
            }
        }

        List<DatapathId> shortestPath = new ArrayList<>();
        DatapathId currentNode = end;

        while (currentNode != null) {
            shortestPath.add(currentNode);
            currentNode = previousNodes.get(currentNode);
        }
        Collections.reverse(shortestPath);
        return shortestPath;
    }


    private void weightGraph() {
        for (DatapathId node : graph.keySet()) {
            weightedGraph.put(node, new HashMap<>());
            for (DatapathId adjacentNode : graph.get(node)) {
                if (weightedGraph.containsKey(adjacentNode) && weightedGraph.get(adjacentNode).containsKey(node)) {
                    int weight = weightedGraph.get(adjacentNode).get(node);
                    weightedGraph.get(node).put(adjacentNode, weight);
                } else {
                    int weight = random.nextInt(10) + 1;
                    weightedGraph.get(node).put(adjacentNode, weight);
                }
            }
        }
    }

    public void printWeightedGraph() {
        Logger logger = LoggerFactory.getLogger(TopologyGraph.class);
        logger.info("Printing weighted graph...");
        Set<Pair> printedEdges = new HashSet<>();
        for (DatapathId node : weightedGraph.keySet()) {
            for (DatapathId adjacentNode : weightedGraph.get(node).keySet()) {
                Pair edge = new Pair(node, adjacentNode);
                if (!printedEdges.contains(edge)) {
                    int weight = weightedGraph.get(node).get(adjacentNode);
                    System.out.println(node + " --" + weight + "-- " + adjacentNode);
                    printedEdges.add(edge);
                }
            }
        }
    }


    public static class Pair {
        DatapathId node1;
        DatapathId node2;

        public Pair(DatapathId node1, DatapathId node2) {
            this.node1 = node1;
            this.node2 = node2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair pair = (Pair) o;
            return (Objects.equals(node1, pair.node1) && Objects.equals(node2, pair.node2)) ||
                    (Objects.equals(node1, pair.node2) && Objects.equals(node2, pair.node1));
        }

        @Override
        public int hashCode() {
            return Objects.hash(node1, node2) + Objects.hash(node2, node1);
        }
    }

}
