package de.hpi.rdf.msg;

import com.sun.tools.javac.util.Assert;
import org.apache.jena.graph.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.*;
import java.util.logging.Logger;

/**
 * Created by magnus on 27.05.16.
 */
public class MinimalSelfcontainedGraphUtil {

    private static Logger L = Logger.getLogger(MinimalSelfcontainedGraphUtil.class.getName());

    public static List<Model> decompose(Model model) {

        return null;
    }

    public static Collection<MinimalSelfcontainedGraph> decompose(Graph graph) {

        List<MinimalSelfcontainedGraph> msgs = new ArrayList<MinimalSelfcontainedGraph>();

        L.info("Graph has " + graph.size() + " triples.");

        for (ExtendedIterator<Triple> it = GraphUtil.findAll(graph); it.hasNext();) {
            Triple triple = it.next();

            MinimalSelfcontainedGraph msg = new MinimalSelfcontainedGraph(triple);
            msgs.add(msg);
        }

        return mergeAll(msgs);
    }

    private static Collection<MinimalSelfcontainedGraph> mergeAll(List<MinimalSelfcontainedGraph> msgs) {

        Set<MinimalSelfcontainedGraph> finalMsgs = new HashSet<MinimalSelfcontainedGraph>();
        Set<MinimalSelfcontainedGraph> mergedMsgs = new HashSet<MinimalSelfcontainedGraph>();

        Map<Node, MinimalSelfcontainedGraph> bNodeMap = new HashMap<Node, MinimalSelfcontainedGraph>();

        while (!(msgs.isEmpty() && mergedMsgs.isEmpty())) {
            L.info("Todo: " + msgs.size() + ", Done: " + finalMsgs.size());

            for (Iterator<MinimalSelfcontainedGraph> it = msgs.iterator(); it.hasNext();) {
                MinimalSelfcontainedGraph msg = it.next();
                if (msg.isFinalized()) {
                    L.info("Final " + msg);

                    finalMsgs.add(msg);
                    it.remove();
                } else {
                    for (Node bnode : msg.bnodes) {
                        MinimalSelfcontainedGraph merger = bNodeMap.get(bnode);
                        if (merger == null) {
                            bNodeMap.put(bnode, msg);
                        } else {
                            L.info("Merge " + msg + " into " + merger);
                            merger.merge(msg);
                            it.remove();
                            mergedMsgs.add(merger);
                            break;
                        }
                    }
                }
            }

            msgs.removeAll(mergedMsgs);

            for (Iterator<MinimalSelfcontainedGraph> it = msgs.iterator(); it.hasNext();) {
                MinimalSelfcontainedGraph msg = it.next();
                msg.finalize();
                it.remove();
                finalMsgs.add(msg);
            }

            Assert.check(msgs.isEmpty());

            msgs.addAll(mergedMsgs);
            mergedMsgs.clear();
            bNodeMap.clear();
        }

        return finalMsgs;
    }


}
