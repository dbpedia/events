# DBpedia Events

The English Wikipedia has more than a hundred edits per minute. A large part of the knowledge in Wikipedia is not static, but frequently updated, e.g., new movies or sports and political events. This makes Wikipedia an extremely rich, crowdsourced information hub for events. We have created a dataset based on DBpedia Live. Therefore, events are extracted not based on resource description them selves, but on the changes that happen to resource descriptions. The dataset gets daily updated and provides a list of headlined events linked to the actual update and resource snapshots.

## Event Extraction

Notable changes in DBpedia Live are extracted by application of SPARQL queries on an RDF model of the DBpedia Live changesets.
These queries can be configured as digest templates in digest.ttl, e.g.

    dig:DEADPEOPLE a dbe:DigestTemplate ;
        dcterms:identifier "DEADPEOPLE" ;
        dcterms:description """Finds people who died within last three weeks."""@en ;
        dbe:queryString """SELECT ?u ?res ?deathdate ?deathplace
            { ?u guo:target_subject ?res ;
                guo:insert [
                    dbo:deathDate ?deathdate ;
                    dbo:deathPlace ?deathplace ] .
                FILTER (xsd:date(?deathdate) > NOW()-1814400) }""" ;
        dbe:contextQueryString """SELECT ?label ?labeldeathplace
            { %%res%% rdfs:label ?label .
              %%deathplace%% rdfs:label ?labeldeathplace . }""" ;
        dbe:descriptionTemplate """%%res%% died on %%deathdate%% in %%deathplace%%.""" ;
        .

## Data Access

The daily dumps can be accessed at http://events.dbpedia.org/dataset

A SPARQL endpoint is provided at http://events.dbpedia.org/sparql