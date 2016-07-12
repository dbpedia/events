# DBpedia Events REST Interface

## Usage

```curl --header "Content-type: application/json" --request POST --data @PATH_TO_TEST_FILE http://127.0.0.1:9000/api/testconfig```

The test file is a JSON such as:
```
{"templateText": "\n
@prefix dig:        <http://events.dbpedia.org/data/digests#> .\n
@prefix dbe:        <http://events.dbpedia.org/ns/core#> .\n
@prefix dcterms:    <http://purl.org/dc/terms/> .\n
@prefix rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n
@prefix xsd:        <http://www.w3.org/2001/XMLSchema#> .\n

dig:RISINGNUMBERS a dbe:DigestTemplate ;\n
    dcterms:identifier \"RISINGNUMBERS\" ;\n
    dcterms:description \"\"\"Numbers are rising.\"\"\"@en ;\n
    dbe:queryString \"\"\"SELECT ?u ?res ?p ?old ?new\n
        { ?u guo:target_subject ?res ;\n
            guo:delete [\n
                ?p ?old ] ;\n
            guo:insert [\n
                ?p ?new ] .\n
            FILTER (isNumeric(?old) && isNumeric(?new) && ?new > ?old)
            FILTER (STRSTARTS(STR(?p), \"http://dbpedia.org/ontology/\"))
            FILTER (?p != dbo:wikiPageID)
            FILTER (?p != dbo:wikiPageLength)
            FILTER (?p != dbo:wikiPageOutDegree)
            FILTER (?p != dbo:wikiPageRevisionID) }\"\"\" ;\n
    dbe:contextQueryString \"\"\"SELECT ?labelres\n
        { %%res%% rdfs:label ?labelres . }\"\"\" ;\n
    dbe:descriptionTemplate \"\"\"%%labelres%%'s %%p%% raised from %%old%% to %%new%%.\"\"\" ;\n
    dbe:rankWeight \"0.1\"^^xsd:float ."}
```

Test file examples can be found at ...

## Deploy with Docker
```docker run -it -v "<YOUR_DBPEDIALIVE_CHANGESET_FOLDER>:/data" -v "<$pwd>:/home/play/Code" -p 9000:9000 semanticmultimedia/playjava```