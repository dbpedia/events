package org.dbpedia.events.model;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.DCTerms;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.dbpedia.events.DBpediaLiveDigest;
import org.dbpedia.events.PrefixService;
import org.dbpedia.events.vocabs.EventsOntology;
import org.dbpedia.events.vocabs.ProvOntology;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.*;

/**
 * Created by magnus on 22.04.15.
 */
public class DigestItem {
    private DBpediaLiveDigest digest;

    private final DigestTemplate digestTemplate;
    private final String update;
    private final String resource;
    private Map<String, RDFNode> bindings;

    private String description;
    private Collection<Resource> changesetFiles;
    private Map<String, Model> updateInstruction;
    private Map<String, Model> snapshots;

    public DigestItem(DBpediaLiveDigest digest, DigestTemplate digestTemplate, String res, String u) {
        this.digest = digest;
        this.digestTemplate = digestTemplate;
        this.resource = res;
        this.update = u;

        this.changesetFiles = new HashSet<Resource>();
        this.snapshots = new HashMap<String, Model>();
        this.updateInstruction = new HashMap<String, Model>();
        this.bindings = new HashMap<String, RDFNode>();
    }

    public DigestItem(DBpediaLiveDigest digest, DigestTemplate digestTemplate, Map<String, RDFNode> bindings) {
        this(digest, digestTemplate, bindings.get("res").asResource().getURI(), bindings.get("u").asResource().getURI());

        this.bindings = bindings;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setChangesetFiles(Collection<Resource> changesetFiles) {
        this.changesetFiles = changesetFiles;
    }

    public void addChangesetFiles(Resource changesetFile) {
        this.changesetFiles.add(changesetFile);
    }

    public void addSnapshot(String res, Model model) {
        this.snapshots.put(res, model);
    }

    public void setUpdateInstruction(String uri, Model updateInstruction) {
        this.updateInstruction.clear();
        this.updateInstruction.put(uri, updateInstruction);
    }

    public DigestTemplate getDigestTemplate() {
        return digestTemplate;
    }

    public String getUpdate() {
        return update;
    }

    public String getResource() {
        return resource;
    }

    public String getDescription() {
        return description;
    }

    public Collection<Resource> getChangesetFiles() {
        return changesetFiles;
    }

    public Map<String, Model> getUpdateInstruction() {
        return updateInstruction;
    }

    public Map<String, Model> getSnapshots() {
        return snapshots;
    }

    public Map<String, RDFNode> getBindings() { return bindings; }

    public DBpediaLiveDigest getDigest() { return digest; }

    public String getId() {
        return this.digest.getDigestId() +
                this.resource.replace("http://dbpedia.org/resource/", "/") +
                "-" +
                this.digestTemplate.getId();
    }

    public Resource getAsResource(Model model) {
        //String uri = PatchrUtils.generateUri(prefix, "patch");
        Resource event = model.createResource(this.digest.getDatasetBase() + "item/" + this.getId(),
                EventsOntology.Event);

        // add timestamp
        //DateTime time = new DateTime(Calendar.getInstance().getTime());
        event.addProperty(ProvOntology.generatedAtTime, model.createTypedLiteral(this.digest.getEnd(), XSDDatatype.XSDdateTime));

        // add derivedFroms
        event.addProperty(ProvOntology.wasDerivedFrom, model.getResource(PrefixService.getNamespaceForPrefix("dig") + this.getDigestTemplate().getId()));
        for (Resource changesetFile: this.changesetFiles) {
            event.addProperty(ProvOntology.wasDerivedFrom, changesetFile);
        }

        // add update instruction
        for (String key: this.updateInstruction.keySet()) {
            event.addProperty(EventsOntology.update, this.updateInstruction.get(key).getResource(key));
            model.add(this.updateInstruction.get(key));
        }

        // add snapshots
        for (String key: this.snapshots.keySet()) {
            event.addProperty(EventsOntology.context, this.snapshots.get(key).getResource(key));
            // model.add(this.snaphots.get(key));
        }

        // add statistics
        int unmodifiedDays = 0;
        int outdegree = 0;
        int indegree = 0;

        // get unmodified days
        QueryExecutionFactory qef = new QueryExecutionFactoryModel(this.updateInstruction.get(this.updateInstruction.keySet().toArray()[0]));
        QueryExecution qe = qef.createQueryExecution(PrefixService.getSparqlPrefixDecl() +
                "SELECT DISTINCT ((xsd:date(NOW()) - xsd:date(?mod)) AS ?days) {?u guo:delete/dbo:wikiPageModified ?mod}");
        digest.setQueryExecutionDatetime(qe, digest.getEnd());
        ResultSet rs = qe.execSelect();
        if (!rs.hasNext()) {
            this.digest.L.debug("No modified date.");
        } else while (rs.hasNext()) {
            QuerySolution r = rs.nextSolution();
            String days = r.get("days").asLiteral().getString();
            this.digest.L.debug("Unmodified days = " + days);
            if (!days.startsWith("-")) {
                Period unmodified = Period.parse(days);
                unmodifiedDays = unmodified.getDays();
            } else {
                unmodifiedDays = 0;
            }
        }

        // get indegree and outdegree
        Map<String, Integer> inoutdegree = digest.getInOutDegree(this.resource);
        indegree = inoutdegree.get("indegree");
        outdegree = inoutdegree.get("outdegree");

        event.addProperty(EventsOntology.numberOfChangesetFiles, model.createTypedLiteral(changesetFiles.size(), XSDDatatype.XSDinteger));
        event.addProperty(EventsOntology.daysSinceLastWikipageModified, model.createTypedLiteral(unmodifiedDays, XSDDatatype.XSDinteger));
        event.addProperty(EventsOntology.resourceIndegree, model.createTypedLiteral(indegree, XSDDatatype.XSDinteger));
        event.addProperty(EventsOntology.resourceOutdegree, model.createTypedLiteral(outdegree, XSDDatatype.XSDinteger));

        float rankValue = computeRankValue(changesetFiles.size(), unmodifiedDays, indegree, outdegree, digestTemplate.getRankWeight());
        event.addProperty(EventsOntology.rankValue, model.createTypedLiteral(rankValue, XSDDatatype.XSDfloat));

        // add description
        event.addProperty(DCTerms.description, model.createLiteral(this.getDescription(), "en"));

        return event;
    }

    private float computeRankValue(int numberOfChangesetFiles, int daysSinceLastWikipageModified, int resourceIndegree, int resourceOutdegree, float rankWeight) {
        float result = 0;

        result += ((float) Math.min(numberOfChangesetFiles, 48)) / 48;
        result += ((float) 356 - Math.min(daysSinceLastWikipageModified, 356)) / 356;
        // avg indegree from http://jens-lehmann.org/files/2009/dbpedia_jws.pdf
        result += ((float) resourceIndegree) / 11.03;
        result += ((float) resourceOutdegree) / 55.15;
        result += ((float) Math.min(rankWeight, 1.));

        return result;
    }

}
