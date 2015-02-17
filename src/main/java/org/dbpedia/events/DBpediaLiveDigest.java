package org.dbpedia.events;

import com.google.common.collect.*;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by magnus on 16.02.15.
 */
public class DBpediaLiveDigest {

    private final File folder = new File("/Users/magnus/Datasets/live.de.dbpedia.org/changesets/");
    private final String lastPublishedUrl = "http://live.de.dbpedia.org/changesets/lastPublishedFile.txt";
    private static final String prefixes =
            "PREFIX dbo: <http://dbpedia.org/ontology/>\n" +
            "PREFIX dc: <http://purl.org/dc/elements/1.1/>\n" +
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
            "PREFIX prov: <http://www.w3.org/ns/prov#>\n" +
            "PREFIX pat: <http://purl.org/hpi/patchr#>\n" +
            "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n" +
            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
            "PREFIX sd: <http://www.w3.org/ns/sparql-service-description#>\n" +
            "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "PREFIX void: <http://rdfs.org/ns/void#>\n" +
            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "PREFIX guo: <http://webr3.org/owl/guo#>\n" +
            "PREFIX dcterms: <http://purl.org/dc/terms/>\n";

    public static void main(String[] args) throws Exception {

        DBpediaLiveDigest dig = new DBpediaLiveDigest();
        File xfolder = dig.downloadLatest("2015-02-16-22-000293");

        Model updateModel = dig.createUpdateModel(xfolder);

        updateModel.write(System.out, "TURTLE");

//        Map<String, Model> models = dig.createModels(xfolder);

        String queryString = "SELECT ?u ?res ?deathdate ?deathplace WHERE { " +
                "?u guo:targetSubject ?res ; " +
                "guo:insert [ " +
                "dbo:deathDate ?deathdate ; " +
                "dbo:deathPlace ?deathplace ] . " +
                "FILTER (xsd:date(?deathdate) > \"2015-02-09\"^^xsd:date) " +
                "}";

        dig.testQuery(updateModel, queryString);

        queryString = "SELECT ?u ?res ?releasedate WHERE { " +
                "?u guo:targetSubject ?res ;" +
                "guo:insert [ " +
                "dbo:releaseDate ?releasedate ] . " +
                "FILTER (xsd:date(?releasedate) > \"2015-02-09\"^^xsd:date) " +
                "}";

        dig.testQuery(updateModel, queryString);

        queryString = "SELECT ?u ?res ?introductiondate WHERE { " +
                "?u guo:targetSubject ?res ;" +
                "guo:insert [ " +
                "dbo:introductionDate ?introductiondate ] . " +
                "FILTER (xsd:date(?introductiondate) > \"2015-02-09\"^^xsd:date) " +
                "}";

        dig.testQuery(updateModel, queryString);

        queryString = "SELECT ?u ?res ?p ?old ?new WHERE { " +
                "?u guo:targetSubject ?res ; " +
                "guo:delete [ " +
                "?p ?old ] ; " +
                "guo:insert [ " +
                "?p ?new ] . " +
                "FILTER (isNumeric(?old) && isNumeric(?new) && ?new > ?old) " +
                "FILTER (STRSTARTS(STR(?p), \"http://dbpedia.org/ontology/\")) " +
                "FILTER (?p != dbo:wikiPageRevisionID) " +
                "FILTER (?p != dbo:wikiPageID) " +
                "}";

        dig.testQuery(updateModel, queryString);
    }

    private void testQuery(Model model, String queryString) {

        System.out.println(queryString);

        QuerySolutionMap initialBindings = new QuerySolutionMap();
        Literal now = model.createLiteral(Calendar.getInstance().getTime().toString());
        initialBindings.add("now", now);

        Query query = QueryFactory.create(prefixes + queryString);
        QueryExecution exec = QueryExecutionFactory.create(query, model, initialBindings);
        ResultSet rs = exec.execSelect();

        if (!rs.hasNext()) {
            //
        }
        while (rs.hasNext()) {
            QuerySolution r = rs.nextSolution();
            String res = r.get("res").asResource().getURI();

            System.out.println("HIT " + res);

            for (String var : rs.getResultVars()) {
                System.out.println("    " + var + " " + r.get(var).toString());
            }
        }
    }


    private Map<String, Model> createModels(File folder) {
        Model delModel = ModelFactory.createDefaultModel();
        Model addModel = ModelFactory.createDefaultModel();

        for (File f: folder.listFiles()) {
            if (f.isDirectory()) {
                System.out.println("DIR  " + f.getAbsolutePath());
                Map<String, Model> subModels = createModels(f);
                addModel.add(subModels.get("added"));
                delModel.add(subModels.get("removed"));
            }
            if (f.isFile() && f.canRead()) {

                if (f.getName().endsWith(".added.nt.gz")) {
                    System.out.println("READ FILE " + f.getAbsolutePath());
                    try {
                        addModel.read(f.getAbsolutePath());
                    } catch (Exception e) {
                        System.out.println("BROKEN FILE " + f.getAbsolutePath() + ": " + e.getStackTrace());
                    }
                } else if (f.getName().endsWith(".removed.nt.gz")) {
                    System.out.println("READ FILE " + f.getAbsolutePath());
                    try {
                        delModel.read(f.getAbsolutePath());
                    } catch (Exception e) {
                        System.out.println("BROKEN FILE " + f.getAbsolutePath() + ": " + e.getStackTrace());
                    }
                } else {
                    System.out.println("IGNORE FILE " + f.getAbsolutePath());
                }
            }
        }

        Map<String, Model> models = new HashMap(2);
        models.put("removed", delModel);
        models.put("added", addModel);

        return models;
    }

    private Model createUpdateModel(File folder) {
        Model updateModel = ModelFactory.createDefaultModel();

        for (File f: folder.listFiles()) {
            if (f.isDirectory()) {
                System.out.println("DIR  " + f.getAbsolutePath());
                Model model = createUpdateModel(f);

                // TODO more intelligent merge
                updateModel.add(model);
            }
            if (f.isFile() && f.canRead()) {

                if (f.getName().endsWith(".added.nt.gz")) {
                    System.out.println("READ FILE " + f.getAbsolutePath());

                    Property pInsert = updateModel.createProperty("http://webr3.org/owl/guo#", "insert");
                    processFile(updateModel, f, pInsert);

                } else if (f.getName().endsWith(".removed.nt.gz")) {
                    System.out.println("READ FILE " + f.getAbsolutePath());

                    Property pDelete = updateModel.createProperty("http://webr3.org/owl/guo#", "delete");
                    processFile(updateModel, f, pDelete);

                } else {
                    System.out.println("IGNORE FILE " + f.getAbsolutePath());
                }
            }
        }

        return updateModel;
    }

    private void processFile(Model updateModel, File f, Property action) {
        try {
            Model addModel = ModelFactory.createDefaultModel();
            addModel.read(f.getAbsolutePath());

            Multimap<Resource, Statement> stmtMap = HashMultimap.create();

            StmtIterator iter = addModel.listStatements();
            try {
                while (iter.hasNext()) {
                    Statement stmt = iter.next();
                    Resource s = stmt.getSubject();
                    stmtMap.put(s, stmt);
                }
            } finally {
                if (iter != null) iter.close();
            }

            for (Resource res : stmtMap.keySet()) {
                Collection<Statement> stmts = stmtMap.get(res);

                Resource update = updateModel.createResource(f.getName().split("\\.")[0] + "/" + res.getNameSpace() + res.getLocalName());
                Property pTargetSubject = updateModel.createProperty("http://webr3.org/owl/guo#", "targetSubject");
                update.addProperty(pTargetSubject, res);

                Resource updateGraph = updateModel.createResource();

                for (Statement stmt : stmts) {
                    updateGraph.addProperty(stmt.getPredicate(), stmt.getObject());
                }

                update.addProperty(action, updateGraph);
            }
        } catch (Exception e) {
            System.out.println("BROKEN FILE " + f.getAbsolutePath() + ": " + e.getStackTrace());
        }
    }

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

        System.out.println("lastPublished " + lastPublished);

        String[] lp = lastPublished.split("-");
        String year = lp[0];
        String month = lp[1];
        String day = lp[2];
        String hour = lp[3];
        String hindex = lp[4];

        File xfolder = Paths.get(folder.getAbsolutePath(), year, month, day).toFile();

        if (!xfolder.exists()) {
            System.out.println("MKDIRS " + xfolder);
            xfolder.mkdirs();
        } else {
            System.out.println("EXISTS " + xfolder);
        }

        return xfolder;
    }
}