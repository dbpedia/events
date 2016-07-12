package de.hpi.rdf.msg;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.graph.*;
import org.apache.jena.graph.impl.CollectionGraph;

import java.util.*;

/**
 * Created by magnus on 27.05.16.
 */
public class MinimalSelfcontainedGraph {

    public Graph graph;
    private boolean finalized;
    private boolean unified;
    private String hash;
    public Collection<Node> bnodes;


    public MinimalSelfcontainedGraph() {
        graph = new CollectionGraph();
        finalized = false;
        unified = false;
        hash = null;
        bnodes = new HashSet<Node>();
    }

    public MinimalSelfcontainedGraph(Triple triple) {
        this();

        graph.add(triple);

        if (isGroundTriple(triple)) {
            finalize();
        } else {
            if (triple.getSubject().isBlank())
                bnodes.add(triple.getSubject());
            if (triple.getObject().isBlank())
                bnodes.add(triple.getObject());
        }
    }

    public void merge(MinimalSelfcontainedGraph merger) throws IllegalArgumentException {
        if (finalized || merger.finalized)
            throw new IllegalArgumentException("Can not merge finalized MinimalSelfcontainedGraphs.");

        if (this.equals(merger))
            throw new IllegalArgumentException("Can not merge self.");

        GraphUtil.addInto(graph, merger.graph);
        bnodes.addAll(merger.bnodes);
    }

    public String toString() {
        String s = (finalized ? "FINALIZED " : "") + (unified ? "UNIFIED " : "") + graph.size() + " TRIPLES\n"
                + graphToString();
        return s;
    }

    public String graphToString() {
        List <Triple> triples = GraphUtil.findAll(graph).toList();

        Collections.sort(triples, new Comparator<Triple>() {
            public int compare(Triple o1, Triple o2) {
                String s1 = o1.getSubject().toString() + " "
                        + o1.getPredicate().toString() + " "
                        + o1.getObject().toString();
                String s2 = o2.getSubject().toString() + " "
                        + o2.getPredicate().toString() + " "
                        + o2.getObject().toString();
                return s1.compareTo(s2);
            }
        });

        String graphAsString = "";

        for (Triple triple: triples) {
            graphAsString = graphAsString + (graphAsString.length() > 0 ? "\n" : "")
                    + triple.getSubject() + " "
                    + triple.getPredicate() + " "
                    + triple.getObject();
        }

        return graphAsString;
    }

    public String getHash() {
        return hash;
    }

    public void finalize() {
        finalized = true;
        unify();
        byte[] bytes = DigestUtils.sha256(graphToString());
        hash = Hex.encodeHexString(bytes);
    }

    private void unify() {
        if (!finalized)
            throw new IllegalArgumentException("Can only unify finalized MinimalSelfcontainedGraphs.");

        if (isGroundTriple()) {
            unified = true;
            return;
        }

        // contains bnodes
        List<Triple> triples = GraphUtil.findAll(graph).toList();

        // sort bnode-id agnostic
        Collections.sort(triples, new Comparator<Triple>() {
            public int compare(Triple o1, Triple o2) {
                String s1 = (o1.getSubject().isBlank() ? "~" : o1.getSubject().toString()) + " "
                        + o1.getPredicate().toString() + " "
                        + (o1.getObject().isBlank() ? "~" : o1.getObject().toString()) + " "
                        + (o1.getSubject().isBlank() ? "# " + o1.getSubject() : "") + " "
                        + (o1.getObject().isBlank() ? "# " + o1.getObject() : "") + " ";
                String s2 = (o2.getSubject().isBlank() ? "~" : o2.getSubject().toString()) + " "
                        + o2.getPredicate().toString() + " "
                        + (o2.getObject().isBlank() ? "~" : o2.getObject().toString()) + " "
                        + (o2.getSubject().isBlank() ? "# " + o2.getSubject() : "") + " "
                        + (o2.getObject().isBlank() ? "# " + o2.getObject() : "") + " ";
                return s1.compareTo(s2);
            }
        });

        Map<Node, Node> bnodeMap = new HashMap<Node, Node>();
        Integer counter = 0;

        graph.clear();

        // replace bnodes with bnodes having a subsequent id
        for (Triple triple: triples) {
            Node subject;
            Node object;

            if (triple.getSubject().isBlank()) {
                subject = bnodeMap.get(triple.getSubject());
                if (subject == null) {
                    subject = NodeFactory.createBlankNode(Integer.toString(counter++));
                    bnodeMap.put(triple.getSubject(), subject);
                }
            } else {
                subject = triple.getSubject();
            }

            if (triple.getObject().isBlank()) {
                object = bnodeMap.get(triple.getObject());
                if (object == null) {
                    object = NodeFactory.createBlankNode(Integer.toString(counter++));
                    bnodeMap.put(triple.getObject(), object);
                }
            } else {
                object = triple.getObject();
            }

            graph.add(new Triple(subject, triple.getPredicate(), object));
        }

        unified = true;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public boolean isUnified() {
        return unified;
    }

    public boolean isGroundTriple() {
        if (graph.size() == 1) {
            Triple triple = GraphUtil.findAll(graph).next();
            return isGroundTriple(triple);
        }
        return false;
    }

    private static boolean isGroundTriple(Triple triple) {
        return !(triple.getSubject().isBlank() || triple.getSubject().isBlank());
    }

}
