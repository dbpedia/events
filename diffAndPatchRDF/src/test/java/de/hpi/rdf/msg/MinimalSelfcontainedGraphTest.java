package de.hpi.rdf.msg;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Test;

import java.util.Collection;
import java.util.logging.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by magnus on 27.05.16.
 */
public class MinimalSelfcontainedGraphTest {
    private static Logger L = Logger.getLogger(MinimalSelfcontainedGraphTest.class.getName());

    @Test
    public void testEmptyGraph() {
        Graph graph = Graph.emptyGraph;
        assertTrue(MinimalSelfcontainedGraphUtil.decompose(graph).isEmpty());
    }

    @Test
    public void testFoafGraph() {
        Graph graph = RDFDataMgr.loadGraph("http://magnus.13mm.de/foaf.tt", Lang.TURTLE);
        Collection<MinimalSelfcontainedGraph> msgs = MinimalSelfcontainedGraphUtil.decompose(graph);
        assertFalse(msgs.isEmpty());

        for (MinimalSelfcontainedGraph msg: msgs) {
            assertTrue(msg.isFinalized());
            assertTrue(msg.isUnified());
            assertNotNull(msg.getHash());
            if (!msg.isGroundTriple())
                L.info("== " + msg.getHash() + " ==\n" + msg.toString());
        }
    }

    @Test
    public void testFoafVocabGraph() {
        Graph graph = RDFDataMgr.loadGraph("http://www.w3.org/2006/time", Lang.RDFXML);
        Collection<MinimalSelfcontainedGraph> msgs = MinimalSelfcontainedGraphUtil.decompose(graph);
        assertFalse(msgs.isEmpty());

        for (MinimalSelfcontainedGraph msg: msgs) {
            assertTrue(msg.isFinalized());
            assertTrue(msg.isUnified());
            assertNotNull(msg.getHash());
            if (!msg.isGroundTriple())
                L.info("== " + msg.getHash() + " ==\n" + msg.toString());
        }
    }

}
