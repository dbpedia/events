package org.dbpedia.events;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * User: Dimitris Kontokostas
 * Description
 * Created: 6/2/14 11:42 AM
 */
public class DBpediaItem {

    private final String language;

    private final String titleDecoded;

    private final long revision;

    // DBpedia does not produce blank nodes
    private final SortedSet<String> triples;

    private final List<String> excludeWhenStartsWithList;
    private final List<String> excludeWhenContainsList;

    public final String serverURL = "http://localhost:9999";

    public DBpediaItem(String language, String titleDecoded, long revision) {
        this.language = language;
        this.titleDecoded = titleDecoded;
        this.revision = revision;
        triples = getTriplesFromServer(language, revision);

        excludeWhenStartsWithList = Arrays.asList(
                "# completed",
                "# started",
                "<http://" + language + ".dbpedia.org/property/",
                "<http://" + language + ".wikipedia.org/wiki/"
        );

        excludeWhenContainsList = Arrays.asList(
                "<http://dbpedia.org/ontology/wikiPageRevisionID>",
                "<http://dbpedia.org/ontology/wikiPageID>" ,
                "<http://www.w3.org/ns/prov#wasDerivedFrom>",
                "<http://xmlns.com/foaf/0.1/isPrimaryTopicOf>"
        );

    }

    private String getExtractionURL(String language, long revisionId) {
        return serverURL + "/server/extraction/" + language + "/extract?title=&revid=" + revisionId + "&format=turtle-triples&extractors=custom";
    }

    private SortedSet<String> getTriplesFromServer(String language, long revision) {

        SortedSet<String> revisionTriples = new TreeSet<String>();

        try {
            URL website = new URL(getExtractionURL(language, revision));

            URLConnection connection = website.openConnection();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream()));

            String inputLine;

            while ((inputLine = in.readLine()) != null)
                revisionTriples.add(inputLine);

            in.close();

        } catch (IOException e) {
            e.printStackTrace();
        }


        return  revisionTriples;
    }

    private boolean startsWithExcludes(String triple){
        for (String exclude: excludeWhenStartsWithList) {
            if (triple.startsWith(exclude))
                return true;
        }
        return false;
    }

    private boolean containsExcludes(String triple){
        for (String exclude: excludeWhenContainsList) {
            if (triple.contains(exclude))
                return true;
        }
        return false;
    }

    public SortedSet<String> getTriples() {
        return triples;
    }

    public SortedSet<String> getTriplesCleaned() {
        SortedSet<String> cleanedTriples = new TreeSet<String>();
        for (String triple: triples) {
            // add only if it's not in excludes
            if (!startsWithExcludes(triple) && !containsExcludes(triple)) {
                cleanedTriples.add(triple);
            }
        }
        return cleanedTriples;
    }
}
