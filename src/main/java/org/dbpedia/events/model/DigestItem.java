package org.dbpedia.events.model;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.DCTerms;
import org.dbpedia.events.DBpediaLiveDigest;
import org.dbpedia.events.PrefixService;
import org.dbpedia.events.vocabs.EventsOntology;
import org.dbpedia.events.vocabs.ProvOntology;
import org.joda.time.DateTime;

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
    private Map<String, Model> snaphots;

    public DigestItem(DBpediaLiveDigest digest, DigestTemplate digestTemplate, String res, String u) {
        this.digest = digest;
        this.digestTemplate = digestTemplate;
        this.resource = res;
        this.update = u;

        this.changesetFiles = new HashSet<Resource>();
        this.snaphots = new HashMap<String, Model>();
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
        this.snaphots.put(res, model);
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

    public Map<String, Model> getSnaphots() {
        return snaphots;
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
        Resource event = model.createResource(digest.getDatasetBase() + "item/" + this.getId(),
                EventsOntology.Event);

        // add timestamp
        DateTime time = new DateTime(Calendar.getInstance().getTime());
        event.addProperty(ProvOntology.generatedAtTime, model.createTypedLiteral(time, XSDDatatype.XSDdateTime));

        // add derivedFroms
        event.addProperty(ProvOntology.wasDerivedFrom, PrefixService.getNamespaceForPrefix("dig") + this.getDigestTemplate().getId());
        for (Resource changesetFile: this.changesetFiles) {
            event.addProperty(ProvOntology.wasDerivedFrom, changesetFile);
        }

        // add update instruction
        for (String key: this.updateInstruction.keySet()) {
            event.addProperty(EventsOntology.update, this.updateInstruction.get(key).getResource(key));
            model.add(this.updateInstruction.get(key));
        }

        // add snapshots
        for (String key: this.snaphots.keySet()) {
            event.addProperty(EventsOntology.context, this.snaphots.get(key).getResource(key));
            // model.add(this.snaphots.get(key));
        }

        // add description
        event.addProperty(DCTerms.description, model.createLiteral(this.getDescription(), "en"));

        return event;
    }

}
