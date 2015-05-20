#!/usr/bin/env bash
# @(#) A shell script for load bulk files.

isql-v 1111 exec="ld_dir_all('./dumps/', '*.ttl', 'http://events.dbpedia.org');"
isql-v 1111 exec="rdf_loader_run();"
isql-v 1111 exec="DB.DBA.RDF_VOID_STORE('http://events.dbpedia.org', 'http://events.dbpedia.org/void');"
isql-v 1111 exec="checkpoint;"
