package org.dbpedia.events.model;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.dbpedia.events.PrefixService;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by magnus on 17.03.15.
 */
public class DigestTemplate {

    private final String id;
    private final String description;
    private final String sparqlQuery;
    private final String contextQuery;
    private final String paramQuery;
    private final String descriptionTemplate;
    private final float rankWeight;

    public DigestTemplate(String id, String description, String sparqlQuery, String contextQuery, String paramQuery, String descriptionTemplate, float rankWeight) {
        assert id != null;
        this.id = id;

        assert description != null;
        this.description = description;

        assert sparqlQuery != null;
        this.sparqlQuery = sparqlQuery;

        assert contextQuery != null;
        this.contextQuery = contextQuery;

        this.paramQuery = paramQuery;

        assert descriptionTemplate != null;
        this.descriptionTemplate = descriptionTemplate;

        this.rankWeight = rankWeight;
    }

    public String getId() { return id; }

    public String getDescription() {
        return description;
    }

    public String getSparqlQuery() { return sparqlQuery; }

    public String getContextQuery() { return contextQuery; }

    public String getParamQuery() { return paramQuery; }

    public String getDescriptionTemplate() { return descriptionTemplate; }

    public float getRankWeight() { return rankWeight; }

    public static Collection<DigestTemplate> instantiateDigestsFromModel(QueryExecutionFactory queryFactory) {
        final String sparqlSelect = PrefixService.getSparqlPrefixDecl() +
                "SELECT DISTINCT ?digest WHERE { " +
                " ?digest a dbe:DigestTemplate . " +
                " NOT EXISTS { ?digest dbe:ignore true . } }";

        Collection<DigestTemplate> digestTemplates = new ArrayList<>();
        Collection<String> digestURIs = new ArrayList<>();
        QueryExecution qe = null;
        try {
            qe = queryFactory.createQueryExecution(sparqlSelect);
            ResultSet results = qe.execSelect();

            while (results.hasNext()) {
                QuerySolution qs = results.next();

                digestURIs.add(qs.get("digest").toString());
            }

            for (String digestURI : digestURIs) {
                digestTemplates.add(getDigest(queryFactory, digestURI));
            }
        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        return digestTemplates;
    }

    private static DigestTemplate getDigest(QueryExecutionFactory queryFactory, String digestURI) {
        final String sparqlSelect = PrefixService.getSparqlPrefixDecl() +
                "SELECT DISTINCT ?digest ?id ?desc ?query ?contextquery ?template ?rankweight WHERE { " +
                "  %%DIGESTURI%% a dbe:DigestTemplate ; " +
                "  dcterms:identifier ?id ; " +
                "  dcterms:description ?desc ; " +
                "  dbe:queryString ?query ;" +
                "  dbe:contextQueryString ?contextquery ;" +
                "  dbe:descriptionTemplate ?template ;" +
                "  dbe:rankWeight ?rankweight . }";

        DigestTemplate digestTemplate = null;
        QueryExecution qe = null;
        try {
            qe = queryFactory.createQueryExecution(sparqlSelect.replaceAll("%%DIGESTURI%%", "<" + digestURI + ">"));
            ResultSet results = qe.execSelect();

            if (results.hasNext()) {
                QuerySolution qs = results.next();

                String id = qs.get("id").toString();
                String desc = qs.get("desc").toString();
                String query = qs.get("query").toString();
                String contextQuery = qs.get("contextquery").toString();
                String template = qs.get("template").toString();
                float rankWeight = qs.get("rankweight").asLiteral().getFloat();

                digestTemplate = new DigestTemplate(id, desc, query, contextQuery, null, template, rankWeight);

                // if multiple results returns something is wrong
                if (results.hasNext()) {
                    throw new IllegalArgumentException("Digest not valid: " + digestTemplate.getId());
                }
            }
        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        return digestTemplate;
    }

    public String getAsResource(Model model) {
        return null;
    }
}
