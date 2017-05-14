package serialization.graph;

import microtrafficsim.core.convenience.parser.DefaultParserConfig;
import microtrafficsim.core.exfmt.Container;
import microtrafficsim.core.exfmt.ExchangeFormat;
import microtrafficsim.core.exfmt.extractor.streetgraph.StreetGraphExtractor;
import microtrafficsim.core.logic.nodes.Node;
import microtrafficsim.core.logic.streetgraph.Graph;
import microtrafficsim.core.logic.streetgraph.StreetGraph;
import microtrafficsim.core.logic.streets.DirectedEdge;
import microtrafficsim.core.logic.streets.Lane;
import microtrafficsim.core.map.style.impl.DarkStyleSheet;
import microtrafficsim.core.serialization.ExchangeFormatSerializer;
import microtrafficsim.core.simulation.configs.SimulationConfig;
import microtrafficsim.utils.resources.PackagedResource;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class StreetGraphExchangeFormatTest {

    /**
     * If not null, this file is used instead of the default 'map.osm'.
     */
    private static final File OPTIONAL_TEST_FILE = null;


    private static Graph osm;
    private static Graph xfm;

    @BeforeClass
    public static void initializeTestData() throws Exception {
        File osmxml;

        //noinspection ConstantConditions
        if (OPTIONAL_TEST_FILE == null)
            osmxml = new PackagedResource(StreetGraphExchangeFormatTest.class, "/preprocessing/graph/map.osm").asTemporaryFile();
        else
            osmxml = OPTIONAL_TEST_FILE;

        osm = loadGraphOsm(osmxml);
        xfm = serializeDeserialize(osm);
    }

    private static Graph loadGraphOsm(File file) throws Exception {
        return DefaultParserConfig.get(getConfig()).build().parse(file).streetgraph;
    }

    private static SimulationConfig getConfig() {
        SimulationConfig config = new SimulationConfig();

        config.maxVehicleCount                            = 1000;
        config.speedup                                    = Integer.MAX_VALUE;
        config.seed                                       = 0;
        config.multiThreading.nThreads                    = 8;
        config.crossingLogic.drivingOnTheRight            = true;
        config.crossingLogic.edgePriorityEnabled          = true;
        config.crossingLogic.priorityToTheRightEnabled    = true;
        config.crossingLogic.friendlyStandingInJamEnabled = true;
        config.crossingLogic.onlyOneVehicleEnabled        = false;
        config.visualization.style                        = new DarkStyleSheet();

        return config;
    }

    private static Graph serializeDeserialize(Graph graph) throws Exception {
        ExchangeFormat fmt = ExchangeFormat.getDefault();
        fmt.getConfig().set(new StreetGraphExtractor.Config(getConfig()));
        ExchangeFormatSerializer ser = ExchangeFormatSerializer.create();

        File tmp = File.createTempFile("map", "mtsm");

        // store
        Container container = fmt.manipulator()
                .inject(graph)
                .getContainer();

        ser.write(tmp, container);

        // load
        return fmt.manipulator(ser.read(tmp)).extract(StreetGraph.class);
    }


    /*
    |=======|
    | by id |
    |=======|
    */
    @Test
    public void testFullSerializationAndDeserializationByIds() {
        assertEquals(osm.getNodes().size(), xfm.getNodes().size());
        assertEquals(osm.getEdges().size(), xfm.getEdges().size());

        // check nodes
        for (Node osmNode : osm.getNodes()) {
            Node xfmNode = nodeById(xfm.getNodes(), osmNode);
            assertNotNull(xfmNode);

            // check incoming edges
            assertEquals(osmNode.getIncomingEdges().size(), xfmNode.getIncomingEdges().size());
            for (DirectedEdge osmEdge : osmNode.getIncomingEdges()) {
                DirectedEdge xfmEdge = edgeByIds(xfmNode.getIncomingEdges(), osmEdge);
                assertNotNull(xfmEdge);
            }

            // check leaving edges
            assertEquals(osmNode.getLeavingEdges().size(), xfmNode.getLeavingEdges().size());
            for (DirectedEdge osmEdge : osmNode.getLeavingEdges()) {
                DirectedEdge xfmEdge = edgeByIds(xfmNode.getLeavingEdges(), osmEdge);
                assertNotNull(xfmEdge);
            }

            // check connectors
            assertEquals(osmNode.getConnectors().size(), xfmNode.getConnectors().size());
            for (Map.Entry<Lane, ArrayList<Lane>> osmConnector : osmNode.getConnectors().entrySet()) {
                Map.Entry<Lane, ArrayList<Lane>> xfmConnector = connectorByIds(xfmNode.getConnectors().entrySet(), osmConnector);
                assertNotNull(xfmConnector);
            }
        }

        // check edges
        for (DirectedEdge osmEdge : osm.getEdges()) {
            DirectedEdge xfmEdge = edgeByIds(xfm.getEdges(), osmEdge);
            assertNotNull(xfmEdge);

            // TODO: check other properties
        }
    }


    private Node nodeById(Set<Node> nodes, Node query) {
        for (Node node : nodes)
            if (node.getId() == query.getId())
                return node;

        return null;
    }

    private DirectedEdge edgeByIds(Set<DirectedEdge> edges, DirectedEdge query) {
        for (DirectedEdge edge : edges)
            if (edgeEqualsByIds(edge, query))
                return edge;

        return null;
    }

    private Map.Entry<Lane, ArrayList<Lane>> connectorByIds(Set<Map.Entry<Lane, ArrayList<Lane>>> connectors, Map.Entry<Lane, ArrayList<Lane>> query) {
        for (Map.Entry<Lane, ArrayList<Lane>> connector : connectors) {
            if (connectorEqualsById(connector, query))
                return connector;
        }

        return null;
    }


    private boolean edgeEqualsByIds(DirectedEdge a, DirectedEdge b) {
        return a.getId() == b.getId()
                && a.getOrigin().getId() == b.getOrigin().getId()
                && a.getDestination().getId() == b.getDestination().getId();
    }

    private boolean laneEqualsByIds(Lane a, Lane b) {
        return edgeEqualsByIds(a.getAssociatedEdge(), b.getAssociatedEdge()) && a.getIndex() == b.getIndex();
    }

    private boolean lanesEqualsByIds(Collection<Lane> a, Collection<Lane> b) {
        for (Lane la : a) {
            boolean found = false;

            for (Lane lb : b) {
                if (laneEqualsByIds(la, lb)) {
                    found = true;
                    break;
                }
            }

            if (!found)
                return false;
        }

        return true;
    }

    private boolean connectorEqualsById(Map.Entry<Lane, ArrayList<Lane>> a, Map.Entry<Lane, ArrayList<Lane>> b) {
        return laneEqualsByIds(a.getKey(), b.getKey()) && lanesEqualsByIds(a.getValue(), b.getValue());
    }


    /*
    |=============|
    | by hashcode |
    |=============|
    */
    @Test
    public void testFullSerializationAndDeserializationByHashCodes() {
        assertEquals(osm.getNodes().size(), xfm.getNodes().size());
        assertEquals(osm.getEdges().size(), xfm.getEdges().size());

        // check nodes
        for (Node osmNode : osm.getNodes()) {
            Node xfmNode = nodeByHashCode(xfm.getNodes(), osmNode);
            assertNotNull(xfmNode);

            // check incoming edges
            assertEquals(osmNode.getIncomingEdges().size(), xfmNode.getIncomingEdges().size());
            for (DirectedEdge osmEdge : osmNode.getIncomingEdges()) {
                DirectedEdge xfmEdge = edgeByHashCodes(xfmNode.getIncomingEdges(), osmEdge);
                assertNotNull(xfmEdge);
            }

            // check leaving edges
            assertEquals(osmNode.getLeavingEdges().size(), xfmNode.getLeavingEdges().size());
            for (DirectedEdge osmEdge : osmNode.getLeavingEdges()) {
                DirectedEdge xfmEdge = edgeByHashCodes(xfmNode.getLeavingEdges(), osmEdge);
                assertNotNull(xfmEdge);
            }

            // check connectors
            assertEquals(osmNode.getConnectors().size(), xfmNode.getConnectors().size());
            for (Map.Entry<Lane, ArrayList<Lane>> osmConnector : osmNode.getConnectors().entrySet()) {
                Map.Entry<Lane, ArrayList<Lane>> xfmConnector = connectorByHashCodes(xfmNode.getConnectors().entrySet(),
                        osmConnector);
                assertNotNull(xfmConnector);
            }
        }

        // check edges
        for (DirectedEdge osmEdge : osm.getEdges()) {
            DirectedEdge xfmEdge = edgeByHashCodes(xfm.getEdges(), osmEdge);
            assertNotNull(xfmEdge);

            // TODO: check other properties
        }
    }


    private Node nodeByHashCode(Set<Node> nodes, Node query) {
        for (Node node : nodes)
            if (node.hashCode() == query.hashCode())
                return node;

        return null;
    }

    private DirectedEdge edgeByHashCodes(Set<DirectedEdge> edges, DirectedEdge query) {
        for (DirectedEdge edge : edges)
            if (edgeEqualsByHashCodes(edge, query))
                return edge;

        return null;
    }

    private Map.Entry<Lane, ArrayList<Lane>> connectorByHashCodes(Set<Map.Entry<Lane, ArrayList<Lane>>> connectors, Map
            .Entry<Lane, ArrayList<Lane>> query) {
        for (Map.Entry<Lane, ArrayList<Lane>> connector : connectors) {
            if (connectorEqualsByHashCode(connector, query))
                return connector;
        }

        return null;
    }


    private boolean edgeEqualsByHashCodes(DirectedEdge a, DirectedEdge b) {
        return a.hashCode() == b.hashCode()
                && a.getOrigin().hashCode() == b.getOrigin().hashCode()
                && a.getDestination().hashCode() == b.getDestination().hashCode();
    }

    private boolean laneEqualsByHashCodes(Lane a, Lane b) {
        return edgeEqualsByHashCodes(a.getAssociatedEdge(), b.getAssociatedEdge()) && a.getIndex() == b.getIndex();
    }

    private boolean lanesEqualsByHashCodes(Collection<Lane> a, Collection<Lane> b) {
        for (Lane la : a) {
            boolean found = false;

            for (Lane lb : b) {
                if (laneEqualsByHashCodes(la, lb)) {
                    found = true;
                    break;
                }
            }

            if (!found)
                return false;
        }

        return true;
    }

    private boolean connectorEqualsByHashCode(Map.Entry<Lane, ArrayList<Lane>> a, Map.Entry<Lane, ArrayList<Lane>> b) {
        return laneEqualsByHashCodes(a.getKey(), b.getKey()) && lanesEqualsByHashCodes(a.getValue(), b.getValue());
    }
}