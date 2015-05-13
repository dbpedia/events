package org.dbpedia.events;

import com.google.common.collect.*;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Node_Literal;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.sparql.util.Context;
import com.hp.hpl.jena.sparql.util.Symbol;
import com.hp.hpl.jena.vocabulary.DCTerms;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.dbpedia.events.model.DigestItem;
import org.dbpedia.events.model.DigestTemplate;
import org.dbpedia.events.vocabs.EventsOntology;
import org.dbpedia.events.vocabs.GuoOntology;
import org.dbpedia.events.vocabs.ProvOntology;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by magnus on 16.02.15.
 */
public class DBpediaLiveDigest {

    private DateTime start;
    private DateTime end;

    private Configuration config;

    private File folder;
    private String dbpediaLiveLang;
    private String changesetsBaseUrl;
    private String lastPublishedUrl;

    private String sparqlEndpoint;
    private String sparqlDefaultgraph;
    private String sparqlPagerankEndpoint;
    private String sparqlPagerankDefaultgraph;
    private QueryExecutionFactory queryFactory;
    private QueryExecutionFactory pagerankQueryFactory;

    private String datasetBase;
    private String datasetTargetfolder;

    public static Logger L = LoggerFactory.getLogger(DBpediaLiveDigest.class);

    public DBpediaLiveDigest(DateTime start, DateTime end) throws ConfigurationException {
        this();

        this.start = start;
        this.end = end;
    }

    private DBpediaLiveDigest() throws ConfigurationException {
        config = new PropertiesConfiguration("application.conf");

        folder = new File(config.getString("dbpediadigest.cacheFolder"));
        dbpediaLiveLang = config.getString("dbpedia.live.lang");
        changesetsBaseUrl = config.getString("dbpedia.live.changesets.base");
        lastPublishedUrl = changesetsBaseUrl + config.getString("dbpedia.live.changesets.lastPublished");

        sparqlEndpoint = config.getString("dbpedia.live.sparql.endpoint");
        sparqlDefaultgraph = config.getString("dbpedia.live.sparql.defaultgraph");
        sparqlPagerankEndpoint = config.getString("dbpedia.pagerank.sparql.endpoint");
        sparqlPagerankDefaultgraph = config.getString("dbpedia.pagerank.sparql.defaultgraph");

        datasetBase = config.getString("dbpediadigest.dataset.base");
        datasetTargetfolder = config.getString("dbpediadigest.dataset.targetfolder");

        queryFactory = new QueryExecutionFactoryHttp(sparqlEndpoint, sparqlDefaultgraph);
        pagerankQueryFactory = new QueryExecutionFactoryHttp(sparqlPagerankEndpoint, sparqlPagerankDefaultgraph);
    }

    public static void main(String[] args) throws Exception {
        String start = args[0];
        String end   = args[1];

        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd-HH");
        DateTime timeStart = fmt.parseDateTime(start);
        DateTime timeEnd = fmt.parseDateTime(end);

        DBpediaLiveDigest dig = new DBpediaLiveDigest(timeStart, timeEnd);
        Collection<DigestTemplate> digestTemplates = dig.readDigestTemplates();
        for (DigestTemplate t: digestTemplates) { L.info("DigestTemplate loaded: " + t.getId()); }

//        File xfolder = dig.downloadLatest(start);
        Path modelFilePath = Paths.get(dig.folder.getAbsolutePath(), "models", dig.getDigestId() + ".ttl");
        File modelFile = modelFilePath.toFile();

        Model updateModel = null;

        if (modelFile.exists()) {
            L.info("ModelFile " + modelFilePath + " exists.");
            updateModel = RDFDataMgr.loadModel(modelFilePath.toString());
        } else {
            L.info("Create " + modelFilePath + ".");
            modelFile.getParentFile().mkdirs();
            modelFile.createNewFile();
            updateModel = dig.createUpdateModel();
            L.info("Write ModelFile to: " + modelFilePath);
            FileOutputStream modelFileOS = new FileOutputStream(modelFile);
            updateModel.write(modelFileOS, "TURTLE");
        }

        L.info("Model has " + updateModel.listStatements().toList().size() + " statements.");

        QueryExecutionFactory updateQueryExecutionFactory = new QueryExecutionFactoryModel(updateModel);

        /**

        QueryExecution qe = updateQueryExecutionFactory.createQueryExecution(PrefixService.getSparqlPrefixDecl() +
                "SELECT DISTINCT ?deathDate (NOW() AS ?now) (NOW()-\"P21D\"^^xsd:duration AS ?latest) WHERE {?x dbo:deathDate ?deathDate}");
        // Set the query execution date to the last second of the day
        dig.setQueryExecutionDatetime(qe, dig.getEnd().plusMinutes(59).plusSeconds(59));

        ResultSet rs = qe.execSelect();

        while (rs.hasNext()) {
            QuerySolution r = rs.nextSolution();

            for (String var : rs.getResultVars()) {
                L.info("VAR " + var + " = " + r.get(var));
            }
        }

        //**/

        Collection<DigestItem> digestItems = new ArrayList<DigestItem>();

        for (DigestTemplate digestTemplate : digestTemplates) {
            digestItems.addAll(dig.testQuery(updateQueryExecutionFactory, digestTemplate));
        }

        dig.serializeDataset(digestItems);
    }

    private Collection<DigestTemplate> readDigestTemplates() {
        Model model = RDFDataMgr.loadModel(config.getString("dbpediadigest.config"));

        QueryExecutionFactory digestQueryFactory = new QueryExecutionFactoryModel(model);

        return DigestTemplate.instantiateDigestsFromModel(digestQueryFactory);
    }

    private Collection<DigestItem> testQuery(QueryExecutionFactory updateModelQef, DigestTemplate digestTemplate) {
        Set<Property> insertDeletePropSet = new HashSet<>(2);
        insertDeletePropSet.add(GuoOntology.delete);
        insertDeletePropSet.add(GuoOntology.insert);

        Collection<DigestItem> digestItems = new ArrayList<DigestItem>();

        String queryString = digestTemplate.getSparqlQuery();

        L.info("Test " + digestTemplate.getDescription());

        QueryExecution qe = updateModelQef.createQueryExecution(PrefixService.getSparqlPrefixDecl() + queryString);

        // Set the query execution date to the last second of the day
        setQueryExecutionDatetime(qe, getEnd());

        ResultSet rs = qe.execSelect();

        if (!rs.hasNext()) {
            L.info("NO NEWS");
        }
        while (rs.hasNext()) {
            String descriptionTemplate = digestTemplate.getDescriptionTemplate();

            QuerySolution r = rs.nextSolution();
            String res = r.get("res").asResource().getURI();
            String u = r.get("u").asResource().getURI();

            L.info("HIT " + u);

            Map<String, RDFNode> bindings = new HashMap<>();
            for (String var : rs.getResultVars()) {
                bindings.put(var, r.get(var));
            }

            // initialize digestItem
            DigestItem digestItem = new DigestItem(this, digestTemplate, bindings);

            if (validateDigestItem(digestItem)) {
                // create description from template
                for (String var : bindings.keySet()) {
                    if (descriptionTemplate.contains("%%" + var + "%%")) {
                        L.debug("    " + var + " " + bindings.get(var).toString());
                        if (bindings.get(var).isLiteral())
                            descriptionTemplate = descriptionTemplate.replaceAll("%%" + var + "%%", bindings.get(var).asLiteral().getLexicalForm().replace("$", "\\$"));
                        else if (bindings.get(var).isURIResource())
                            descriptionTemplate = descriptionTemplate.replaceAll("%%" + var + "%%", getLabelForResource(bindings.get(var).asResource().getURI()).replace("$", "\\$"));
                        else
                            L.warn("Unhandled type " + bindings.get(var));
                    }
                }
                digestItem.setDescription(descriptionTemplate);
                L.info("*** " + descriptionTemplate + " *** ");

                // get changesetFiles
                digestItem.setChangesetFiles(getChangesetFilesForUpdate(updateModelQef, u));

                // get updateInstruction
                Model uModel = getModelForResource(updateModelQef, u, u.replace("http://live.dbpedia.org/changesets/", ":update/"), insertDeletePropSet);
                //uModel.write(System.out, "TURTLE");
                digestItem.setUpdateInstruction(u.replace("http://live.dbpedia.org/changesets/", ":update/"), uModel);

                String snapshotUri = datasetBase + "snaphot/" + getDigestId() + res.replace("http://dbpedia.org/resource/", "/");
                digestItem.addSnapshot(snapshotUri, getModelForResource(queryFactory, res, snapshotUri));

                digestItems.add(digestItem);
            } else {
                L.info("Invalid digest item: " + digestItem.getId());
            }
        }

        return digestItems;
    }

    private boolean validateDigestItem(DigestItem digestItem) {
        String queryString = digestItem.getDigestTemplate().getContextQuery();

        String label = null;
        QueryExecution qe = null;
        try {
            Map<String, RDFNode> bindings = digestItem.getBindings();

            for (String var : bindings.keySet()) {
                if (queryString.contains("%%" + var + "%%")) {
                    if (bindings.get(var).isURIResource())
                        queryString = queryString.replaceAll("%%" + var + "%%", "<" + bindings.get(var).asResource().getURI() + ">");
                }
            }

            qe = queryFactory.createQueryExecution(PrefixService.getSparqlPrefixDecl() + queryString);
            ResultSet rs = qe.execSelect();

            if (rs.hasNext()) {
                // TODO add to digestbindings and reuse labels for descriptiontemplate
                while (rs.hasNext()) {
                    QuerySolution r = rs.nextSolution();

                    for (String var : rs.getResultVars()) {
                        L.debug("VAR " + var + " = " + r.get(var));
                        bindings.put(var, r.get(var));
                    }
                }

                return true;
            }
        } catch(Exception e) {
            L.error("Exception while excuting \"" + queryString + "\".", e);
        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        return false;
    }

    private Model getModelForResource(QueryExecutionFactory qef, String uri) {
        return getModelForResource(qef, uri, uri, null);
    }

    private Model getModelForResource(QueryExecutionFactory qef, String uri, Set<Property> properties) {
        return getModelForResource(qef, uri, uri, properties);
    }

    private Model getModelForResource(QueryExecutionFactory qef, String uriFrom, String uriTo, Set<Property> properties) {
        Model model = getModelForResource(qef, uriFrom, uriTo);

/*        Resource res = model.getResource(uriTo);

        // TODO making iterative queries seems suspective to me, should be possible more efficient
        for (Property p: properties) {
            StmtIterator stmts = model.listStatements(res, p, (RDFNode) null);
            for (Statement stmt: stmts.toList()) {
                String queryString = "DESCRIBE ?node WHERE { %%URI%% <" + p.getURI() + "> ?node . }";

                Model pModel = null;

                QueryExecution qe = null;
                try {
                    qe = qef.createQueryExecution(PrefixService.getSparqlPrefixDecl() + queryString.replaceAll("%%URI%%", "<" + uriFrom + ">"));

                    pModel = qe.execDescribe();
                } catch (RiotException e) {
                    L.error("RiotException when querying <" + uriFrom + ">: " + e.getMessage());
                } finally {
                    if (qe != null) {
                        qe.close();
                    }
                }

                model.add(pModel);
            }
        }
*/
        return model;
    }

    public Map<String, Integer> getInOutDegree(String uri) {
        Map<String, Integer> result = new HashMap<>();

        String queryString = "SELECT (COUNT(DISTINCT ?s) AS ?indegree) (COUNT(DISTINCT ?o) AS ?outdegree) { " +
            "{?s ?p %%URI%% . FILTER( REGEX(?s, \"^http://dbpedia.org/resource/\") ) } " +
            "  UNION " +
            "{%%URI%% ?p ?o . FILTER( ISURI(?o) && REGEX(?o, \"^http://dbpedia.org/resource/\") ) } " +
            "}";

        QueryExecution qe = this.queryFactory.createQueryExecution(PrefixService.getSparqlPrefixDecl() +
                queryString.replaceAll("%%URI%%", "<" + uri + ">"));
        ResultSet rs = qe.execSelect();

        if (rs.hasNext()) {
            QuerySolution r = rs.nextSolution();
            result.put("indegree", r.get("indegree").asLiteral().getInt());
            result.put("outdegree", r.get("outdegree").asLiteral().getInt());
        }

        return result;
    }

    private Model getModelForResource(QueryExecutionFactory qef, String uriFrom, String uriTo) {
        String queryString = "DESCRIBE %%URI%%";

        Model model = ModelFactory.createDefaultModel();

        Resource resFrom = model.createResource(uriFrom);
        Resource resTo = model.createResource(uriTo);

        QueryExecution qe = null;
        try {
            qe = qef.createQueryExecution(PrefixService.getSparqlPrefixDecl() + queryString.replaceAll("%%URI%%", "<" + uriFrom + ">"));

            model = qe.execDescribe();

/*            Model result = qe.execDescribe();

            StmtIterator stmts = result.listStatements();

            while (stmts.hasNext()) {
                Statement t = stmts.next();
                // TODO include linked blanknodes !!!
                if (t.getSubject().equals(resFrom)) {
                    model.add(resTo, t.getPredicate(), t.getObject());
                } else {
                    //model.add(t);
                }
            }
*/
        } catch (RiotException e) {
            L.error("RiotException when querying <" + uriFrom + ">: " + e.getMessage());
        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        return model;
    }

    private Collection<Resource> getChangesetFilesForUpdate(QueryExecutionFactory updateModelQef, String u) {
        String queryString = "SELECT DISTINCT ?file WHERE { " +
                "%%U%% prov:wasDerivedFrom ?file . }";

        Collection<Resource> result = new HashSet<Resource>();
        QueryExecution qe = null;
        try {
            qe = updateModelQef.createQueryExecution(PrefixService.getSparqlPrefixDecl() + queryString.replaceAll("%%U%%", "<" + u + ">"));
            ResultSet results = qe.execSelect();

            while (results.hasNext()) {
                QuerySolution qs = results.next();

                result.add(qs.get("file").asResource());
            }
        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        return result;
    }

    private String getLabelForResource(String uri) {
        String queryString = "SELECT DISTINCT ?label WHERE { " +
                "{ %%URI%% foaf:name ?label . } " +
                "UNION { %%URI%% rdfs:label ?label . FILTER langMatches( lang(?label), \"" + dbpediaLiveLang + "\" ) } " +
                ((dbpediaLiveLang.equalsIgnoreCase("en")) ? "" : "  UNION { %%URI%% rdfs:label ?label . FILTER langMatches( lang(?label), \"en\" ) } ") +
                "}";

        String label = null;
        QueryExecution qe = null;
        try {
            qe = queryFactory.createQueryExecution(PrefixService.getSparqlPrefixDecl() + queryString.replaceAll("%%URI%%", "<" + uri + ">"));
            ResultSet results = qe.execSelect();

            if (results.hasNext()) {
                QuerySolution qs = results.next();

                label = qs.get("label").asLiteral().getLexicalForm();

                if (results.hasNext()) {
                    L.debug("Multiple labels for " + uri);
                }
            }
        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        return (label != null) ? label : uri;
    }

    @Deprecated
    private float getPagerankForResource(String uri) {
        String queryString = "SELECT ?pagerank WHERE { " +
                "%%URI%% dbo:wikiPageRank ?pagerank . }";

        float pagerank = (float) -1;
        QueryExecution qe = null;
        try {
            qe = pagerankQueryFactory.createQueryExecution(PrefixService.getSparqlPrefixDecl() + queryString.replaceAll("%%URI%%", "<" + uri + ">"));
            ResultSet results = qe.execSelect();

            if (results.hasNext()) {
                QuerySolution qs = results.next();

                pagerank = qs.getLiteral("pagerank").getFloat();

                if (results.hasNext()) {
                    L.warn("Multiple pageranks for " + uri);
                }
            }
        } catch(QueryException e) {
            L.error("Exception while getting Pagerank. ", e);
        } finally {
            if (qe != null) {
                qe.close();
            }
        }

        return (pagerank > 0.) ? pagerank : (float) 0.;
    }

    public String getDigestId() {
        if (start.getDayOfYear() == end.getDayOfYear()) {
            if (start.getHourOfDay() == end.getHourOfDay()) {
                DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy/MM/dd/HH");
                return fmt.print(start);
            } else if (start.getHourOfDay() == 0 && end.getHourOfDay() == 23) {
                DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy/MM/dd");
                return fmt.print(start);
            } else {
                DateTimeFormatter fmtS = DateTimeFormat.forPattern("yyyy/MM/dd/HH");
                DateTimeFormatter fmtE = DateTimeFormat.forPattern("HH");
                return fmtS.print(start) + "--" + fmtE.print(end);
            }
        }

        return datesToString(this.start, this.end);
    }

    private String datesToString(DateTime start, DateTime end) {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy/MM/dd/HH");
        return fmt.print(start) + "/--/" + fmt.print(end);
    }

    public void setQueryExecutionDatetime(QueryExecution qe, DateTime time) {
        // Set the query execution date to the last second of the day
        Context qc = qe.getContext();
        for (Symbol s: qc.keys()) {
            if (s.getSymbol().equals("http://jena.hpl.hp.com/ARQ/system#now")) {
                DateTimeFormatter isofmt = ISODateTimeFormat.dateTime();
                qc.set(s, NodeFactory.createLiteral(time.toString(isofmt), XSDDatatype.XSDdateTime));
                L.debug("Set QueryExecution time http://jena.hpl.hp.com/ARQ/system#now to: " + ((Node_Literal) qc.get(s)).toString());
                return;
            }
        }

        L.warn("QueryExecution time not set.");
    }

    public DateTime getStart() {
        return start;
    }

    public DateTime getEnd() {
        return end.plusMinutes(59).plusSeconds(59);
    }

    public String getDatasetBase() {
        return datasetBase;
    }

    private Model createUpdateModel() {
        Model updateModel = ModelFactory.createDefaultModel();

        Multimap<Resource, Statement> insert = HashMultimap.create();
        Multimap<Resource, Statement> delete = HashMultimap.create();
        Multimap<Resource, Resource> changesets = HashMultimap.create();

        List<File> files = getChangesetFiles();

        L.info("Changeset files to process: " + files.size());

        for (File f: files) {
            if (f.isFile() && f.canRead()) {
                processFile(insert, delete, changesets, f);
            }
        }

        for (Resource res : changesets.keySet()) {
            Collection<Statement> insertStmts = insert.get(res);
            Collection<Statement> deleteStmts = delete.get(res);
            Collection<Resource> changesetFiles = changesets.get(res);

            L.debug("Resource " + res.getURI() + ": " +
                    insertStmts.size() + " inserts, " +
                    deleteStmts.size() + " deletes, " +
                    changesetFiles.size() + " files");

            if (insertStmts.isEmpty() && deleteStmts.isEmpty()) {
                continue;
            }

            Resource update = updateModel.createResource(
                    datasetBase + "update/" + getDigestId() + "/" + res.getURI().replaceFirst("http://dbpedia.org/resource/", ""),
                    GuoOntology.UpdateInstruction);
            update.addProperty(GuoOntology.target_subject, res);

            Resource insertGraph = updateModel.createResource();
            Resource deleteGraph = updateModel.createResource();

            for (Resource file: changesetFiles) {
                update.addProperty(ProvOntology.wasDerivedFrom, file);
            }
            for (Statement stmt : insertStmts) {
                insertGraph.addProperty(stmt.getPredicate(), stmt.getObject());
            }
            for (Statement stmt : deleteStmts) {
                deleteGraph.addProperty(stmt.getPredicate(), stmt.getObject());
            }

            update.addProperty(GuoOntology.insert, insertGraph);
            update.addProperty(GuoOntology.delete, deleteGraph);
        }

        return updateModel;
    }

    private List<File> getChangesetFiles() {
        File startFolder = getFolderForDateTime(this.start);
        File endFolder = getFolderForDateTime(this.end);

        List<File> files = new ArrayList<File>();

        DateTime c = new DateTime(this.start);
        File cFolder = null;
        while (true) {
            cFolder = getFolderForDateTime(c);

            if (cFolder.isDirectory()) {
                L.debug("DIR  " + cFolder.getAbsolutePath());

                for (File f : cFolder.listFiles()) {
                    if (f.getName().endsWith(".added.nt.gz") || f.getName().endsWith(".removed.nt.gz")) {
                        files.add(f);
                    } else {
                        // IGNORE
                        continue;
                    }
                }

            } else {
                L.debug("No DIR " + cFolder.getAbsolutePath());
            }

            if (c.compareTo(end) >= 0) {
                break;
            } else {
                c = c.plusHours(1);
                cFolder = getFolderForDateTime(c);
            }
        }

        return files;
    }

    private File getFolderForDateTime(DateTime date) {
        int year = date.getYear();
        int month = date.getMonthOfYear();
        int day = date.getDayOfMonth();
        int hour = date.getHourOfDay();

        File f = Paths.get(folder.getAbsolutePath(),
                String.format("%04d", year),
                String.format("%02d", month),
                String.format("%02d", day),
                String.format("%02d", hour)).toFile();

        return f;
    }

    private void processFile(Multimap<Resource, Statement> insert, Multimap<Resource, Statement> delete, Multimap<Resource, Resource> changesets, File f) {
        Model changesetModel = ModelFactory.createDefaultModel();
        changesetModel.read(f.getAbsolutePath());
        StmtIterator iter = changesetModel.listStatements();

        Resource changesetFile = changesetModel.createResource(
                changesetsBaseUrl + f.getAbsolutePath().split("/changesets/")[1]);

        if (f.getName().endsWith(".added.nt.gz")) {
            L.debug("READ FILE " + f.getAbsolutePath());

            try {
                while (iter.hasNext()) {
                    Statement stmt = iter.next();
/* could improve performance
                    if (stmt.getPredicate().hasURI("http://dbpedia.org/ontology/wikiPageExtracted")) {
                    http://dbpedia.org/meta/contributorID
                    http://dbpedia.org/meta/contributor
                    http://dbpedia.org/ontology/wikiPageRevisionLink
                    http://dbpedia.org/ontology/wikiPageRevisionID
                        continue;
                    }
*/
                    Resource s = stmt.getSubject();
                    // skip the wikipedia resources
                    if (!s.getURI().startsWith("http://dbpedia.org/resource/")) {
                        L.debug("Skip " + s.getURI());
                        continue;
                    }

                    if (delete.containsEntry(s, stmt)) {
                        L.debug("Undelete " + stmt);
                        delete.remove(s, stmt);
                    } else {
                        insert.put(s, stmt);
                    }
                    changesets.put(s, changesetFile);
                }
            } finally {
                if (iter != null) iter.close();
            }

        } else if (f.getName().endsWith(".removed.nt.gz")) {
            L.debug("READ FILE " + f.getAbsolutePath());

            try {
                while (iter.hasNext()) {
                    Statement stmt = iter.next();

                    Resource s = stmt.getSubject();
                    // skip the wikipedia resources
                    if (!s.getURI().startsWith("http://dbpedia.org/resource/")) {
                        L.debug("Skip " + s.getURI());
                        continue;
                    }

                    if (insert.containsEntry(s, stmt)) {
                        L.debug("Uninsert " + stmt);
                        insert.remove(s, stmt);
                    } else {
                        delete.put(s, stmt);
                    }
                    changesets.put(s, changesetFile);
                }
            } finally {
                if (iter != null) iter.close();
            }

        } else {
            L.debug("IGNORE FILE " + f.getAbsolutePath());
        }
    }

    /**
     * @Deprecated
     **/
    private File downloadLatest(String lastPublished) {
        if (lastPublished == null || lastPublished.isEmpty()) {
            BufferedReader in = null;
            try {
                URLConnection conn = new URL(lastPublishedUrl).openConnection();
                in = new BufferedReader(new InputStreamReader(
                        conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    lastPublished = line;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        L.info("lastPublished " + lastPublished);

        String[] lp = lastPublished.split("-");
        String year = lp[0];
        String month = lp[1];
        String day = lp[2];
        String hour = lp[3];
        String hindex = lp[4];

        File xfolder = Paths.get(folder.getAbsolutePath(), year, month, day, hour).toFile();

        if (!xfolder.exists()) {
            L.debug("MKDIRS " + xfolder);
            xfolder.mkdirs();
        } else {
            L.debug("EXISTS " + xfolder);
        }

        // Download files

        // save lastPublished

        return xfolder;
    }

    private void serializeDataset(Collection<DigestItem> digestItems) {
        Path datasetFilePath = null;
        File datasetFile = null;

        try {
            Model model = ModelFactory.createDefaultModel();

            Resource digest = model.createResource(this.getDatasetBase() + "digest/" + this.getDigestId(),
                    EventsOntology.Digest);

            digest.addProperty(DCTerms.identifier, model.createLiteral(this.getDigestId()));
            digest.addProperty(ProvOntology.startedAtTime, model.createTypedLiteral(this.getStart(), XSDDatatype.XSDdateTime));
            digest.addProperty(ProvOntology.endedAtTime, model.createTypedLiteral(this.getEnd(), XSDDatatype.XSDdateTime));

            for (DigestItem item: digestItems) {
                Resource event = item.getAsResource(model);
                event.addProperty(EventsOntology.digest, digest);
                serializeSnapshots(item.getSnapshots());
            }

            datasetFilePath = Paths.get(datasetTargetfolder, "dataset", this.getDigestId() + ".ttl");
            datasetFile = datasetFilePath.toFile();

            if (datasetFile.exists()) {
                L.warn("Dataset " + datasetFilePath + " exists.");
            } else {
                datasetFile.getParentFile().mkdirs();
                datasetFile.createNewFile();
            }

            L.info("Dataset has " + digestItems.size() + " items.");
            L.info("Dataset has " + model.listStatements().toList().size() + " statements.");
            L.info("Write Dataset to: " + datasetFilePath);
            FileOutputStream modelFileOS = new FileOutputStream(datasetFile);
            model.write(modelFileOS, "TURTLE");
        } catch (IOException e) {
            L.error("Error while writing " + datasetFilePath + ".", e);
        }
    }

    private void serializeSnapshots(Map<String, Model> snaphots) {
        Path datasetFilePath = null;
        File datasetFile = null;

        try {
            for (String key : snaphots.keySet()) {
                Model model = snaphots.get(key);

                datasetFilePath = Paths.get(datasetTargetfolder, "snapshot",
                        this.getDigestId(),
                        key.split(this.getDigestId(), 2)[1] + ".ttl");
                datasetFile = datasetFilePath.toFile();

                if (datasetFile.exists()) {
                    L.warn("Snapshot " + datasetFilePath + " exists.");
                    return;
                } else {
                    datasetFile.getParentFile().mkdirs();
                    datasetFile.createNewFile();
                }

                L.info("Snapshot has " + model.listStatements().toList().size() + " statements.");
                L.info("Write Snapshot to: " + datasetFilePath);
                FileOutputStream modelFileOS = new FileOutputStream(datasetFile);
                model.write(modelFileOS, "TURTLE");
            }
        } catch (IOException e) {
            L.error("Error while writing " + datasetFilePath + ".", e);
        }
    }

}