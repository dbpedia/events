package org.dbpedia.events;

import com.hp.hpl.jena.rdf.model.Model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by magnus on 17.03.15.
 */
public class PrefixService {

    private static Map<String, String> prefixes = new HashMap<String, String>();

    static {
        prefixes.put("dig", "http://events.dbpedia.org/data/digests#");
        prefixes.put("dbo", "http://dbpedia.org/ontology/");
        prefixes.put("dbprop", "http://dbpedia.org/property/");
        prefixes.put("dbp", "http://dbpedia.org/resource/");
        prefixes.put("dbc", "http://dbpedia.org/resource/Category:");
        prefixes.put("dbe", "http://events.dbpedia.org/ns/core#");

        prefixes.put("dc", "http://purl.org/dc/elements/1.1/");
        prefixes.put("dcterms", "http://purl.org/dc/terms/");
        prefixes.put("foaf", "http://xmlns.com/foaf/0.1/");
        prefixes.put("guo", "http://webr3.org/owl/guo#");
        prefixes.put("owl", "http://www.w3.org/2002/07/owl#");
        prefixes.put("pat", "http://purl.org/hpi/patchr#");
        prefixes.put("prov", "http://www.w3.org/ns/prov#");
        prefixes.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        prefixes.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        prefixes.put("sd", "http://www.w3.org/ns/sparql-service-description#");
        prefixes.put("void", "http://rdfs.org/ns/void#");
        prefixes.put("xsd", "http://www.w3.org/2001/XMLSchema#");
    }

    public static String getAllPrefixes() {
        String r = "";
        for (String key : prefixes.keySet()) {
            r += key + ": <" + prefixes.get(key) + ">\n";
        }
        return r;
    }

    public static void addPrefixes(Model model) {
        prefixes.putAll(model.getNsPrefixMap());
    }

    public static void addPrefix(String prefix, String uri) {
        prefixes.put(prefix, uri);
    }

    public static String getNamespaceForPrefix(String id) {
        return prefixes.get(id);
    }

    public static Set<String> getPrefixList() {
        return prefixes.keySet();
    }

    public static Map<String, String> getPrefixMap() {
        return prefixes;
    }

    public static String getSparqlPrefixDecl() {
        StringBuffer sb = new StringBuffer();

        for (String key : prefixes.keySet()) {
            sb.append("PREFIX " + key + ": <" + prefixes.get(key) + ">\n");
        }

        return sb.toString();
    }
}