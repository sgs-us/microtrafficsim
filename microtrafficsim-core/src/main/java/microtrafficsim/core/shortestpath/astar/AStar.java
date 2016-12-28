package microtrafficsim.core.shortestpath.astar;

import microtrafficsim.core.shortestpath.ShortestPathAlgorithm;
import microtrafficsim.core.shortestpath.ShortestPathEdge;
import microtrafficsim.core.shortestpath.ShortestPathNode;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;


/**
 * This class represents an A* algorithm. You can use the constructor for own implementations of the weight and
 * estimation function, but you can also use {@link #createShortestWayDijkstra()} for a standard implementation of
 * Dijkstra's algorithm.
 *
 * @author Jan-Oliver Schmidt, Dominic Parga Cacheiro
 */
public class AStar implements ShortestPathAlgorithm {

    private final Function<ShortestPathEdge, Float> edgeWeightFunction;
    private final BiFunction<ShortestPathNode, ShortestPathNode, Float> estimationFunction;

    /**
     * Standard constructor which sets its edge weight and estimation function to the given ones. This constructor
     * should only be used if you want to define the weight and estimation function on your own. There is a factory
     * method in this class for an implementation of Dijkstra's algorithm.
     *
     * @param edgeWeightFunction The A* algorithm uses a node N from the priority queue for actualizing (if necessary)
     *                           the weight of each node D, that is a destination of an edge starting in N. For this,
     *                           it needs the current weight of N plus the weight of the mentioned edge. <br>
     *                           <p>
     *                           Invariants: <br>
     *                           All edge weights has to be >= 0
     * @param estimationFunction In addition, the A* algorithm estimates the distance from this destination D to the end
     *                           of the route to find the shortest path faster by reducing the search area. <br>
     *                           <p>
     *                           Invariants: <br>
     *                           1) This estimation must be lower or equal to the real shortest path from destination
     *                           to the route's end. So it is not allowed to be more pessimistic than the correct
     *                           shortest path. Otherwise, it is not guaranteed, that the A* algorithm returns correct
     *                           results. <br>
     *                           2) This estimation has to be >= 0
     *
     */
    public AStar(Function<ShortestPathEdge, Float> edgeWeightFunction,
                 BiFunction<ShortestPathNode, ShortestPathNode, Float> estimationFunction) {
        this.edgeWeightFunction = edgeWeightFunction;
        this.estimationFunction = estimationFunction;
    }

    /**
     * @return Standard implementation of Dijkstra's algorithm for calculating the shortest (not necessarily fastest)
     * path using {@link ShortestPathEdge#getLength()}
     */
    public static AStar createShortestWayDijkstra() {
        return new AStar(
                edge -> (float)edge.getLength(),
                (destination, routeDestination) -> 0f
        );
    }

    /*
    |===========================|
    | (i) ShortestPathAlgorithm |
    |===========================|
    */
    /**
     * This method is not needed in this algorithm and thus its empty.
     */
    @Override
    public void preprocess() {

    }

    @Override
    public void findShortestPath(ShortestPathNode start, ShortestPathNode end, Stack<ShortestPathEdge> shortestPath) {

        if (start == end)
            return;
        HashMap<ShortestPathNode, WeightedNode> visitedNodes = new HashMap<>();
        PriorityQueue<WeightedNode>             queue        = new PriorityQueue<>();
        queue.add(new WeightedNode(start, null, null, 0f, estimationFunction.apply(start, end)));

        while (!queue.isEmpty()) {
            WeightedNode current = queue.poll();

            if (current.node == end) { // shortest path found
                // create shortest path
                while (current.predecessor != null) {
                    shortestPath.push(current.predecessor);
                    current = visitedNodes.get(current.predecessor.getOrigin());
                }

                return;
            }

            if (visitedNodes.keySet().contains(current.node))
                continue;
            visitedNodes.put(current.node, current);

            // iterate over all leaving edges
            for (ShortestPathEdge leaving : current.node.getLeavingEdges(current.predecessor)) {
                ShortestPathNode dest = leaving.getDestination();
                float            g    = current.g + edgeWeightFunction.apply(leaving);

                // push new node into priority queue
                if (!visitedNodes.keySet().contains(dest))
                    queue.add(new WeightedNode(dest, leaving, null, g, estimationFunction.apply(dest, end)));
            }
        }
    }
}