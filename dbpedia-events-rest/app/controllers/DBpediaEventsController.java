package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.limit.QueryExecutionFactoryLimit;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.dbpedia.events.DBpediaLiveDigest;
import org.dbpedia.events.model.DigestItem;
import org.dbpedia.events.model.DigestTemplate;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import play.api.inject.*;
import play.mvc.*;
import play.Logger;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by magnus on 18.05.16.
 */
public class DBpediaEventsController extends Controller {

    @Inject
    Injector injector;

    public Result testConfig() {

        DBpediaLiveDigest dig = injector.instanceOf(DBpediaLiveDigest.class);

        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd-HH");
        DateTime start = fmt.parseDateTime("2015-09-01-00");
        DateTime end = fmt.parseDateTime("2015-09-01-11");

        JsonNode json = request().body().asJson();

        if (json == null) {
            return badRequest("Expecting Json data");
        }
        String templateText = json.findPath("templateText").textValue();
        String queryText = json.findPath("query").textValue();
        String startArg = json.findPath("start").textValue();
        String endArg = json.findPath("end").textValue();
        if (startArg != null && endArg != null) {
            start = fmt.parseDateTime(startArg);
            end = fmt.parseDateTime(endArg);
        } else {
            Logger.info("Use default start/end.");
        }
        Logger.info("Start " + start + ", end " + end);

        if (templateText == null) {
            return badRequest("Missing parameter [templateText]");
        } else {
            Logger.info("templateText: " + templateText);

            String hash = DigestUtils.sha1Hex(dig.getDigestId(start, end) + templateText);
            Logger.info("hash: " + hash);

            Path modelFilePath = Paths.get(dig.folder.getAbsolutePath(), "models", dig.getDigestId(start, end) + ".ttl");
            File modelFile = modelFilePath.toFile();

            Path digestFilePath = Paths.get(dig.folder.getAbsolutePath(), "digests", hash + ".ttl");
            File digestFile = digestFilePath.toFile();

            if (!digestFile.exists()) {
                Collection<DigestTemplate> digestTemplates = null;
                try {
                    digestTemplates = dig.readDigestTemplate(templateText);
                } catch (RiotException e) {
                    Logger.error(e.getMessage());
                    return badRequest("Error while parsing [templateText]: " + e.getLocalizedMessage());
                }

                for (DigestTemplate t : digestTemplates) {
                    Logger.info("DigestTemplate loaded: " + t.getId());
                }

                Model updateModel = null;

                if (modelFile.exists()) {
                    Logger.info("ModelFile " + modelFilePath + " exists.");
                    updateModel = RDFDataMgr.loadModel(modelFilePath.toString());
                } else {
                    Logger.info("Create " + modelFilePath + ".");
                    try {
                        modelFile.getParentFile().mkdirs();
                        modelFile.createNewFile();
                        updateModel = dig.createUpdateModel(start, end);
                        Logger.info("Write ModelFile to: " + modelFilePath);
                        FileOutputStream modelFileOS = new FileOutputStream(modelFile);
                        updateModel.write(modelFileOS, "TURTLE");
                    } catch (IOException e) {
                        Logger.error(e.getMessage());
                        return badRequest(e.getLocalizedMessage());
                    }
                }

                Logger.info("Model has " + updateModel.listStatements().toList().size() + " statements.");

                QueryExecutionFactory updateQueryExecutionFactory = new QueryExecutionFactoryModel(updateModel);
                updateQueryExecutionFactory = new QueryExecutionFactoryLimit(updateQueryExecutionFactory, false, (long) 50);

                Collection<DigestItem> digestItems = new ArrayList<DigestItem>();

                for (DigestTemplate digestTemplate : digestTemplates) {
                    digestItems.addAll(dig.testQuery(updateQueryExecutionFactory, digestTemplate, start, end));
                }

                dig.serializeDataset(digestItems, start, end, digestFile);
            }

            if (queryText != null) {
                Model model = RDFDataMgr.loadModel(digestFile.getAbsolutePath());
                Model tModel = ModelFactory.createDefaultModel();
                tModel.read(new ByteArrayInputStream(templateText.getBytes()), null, "TTL");
                model.add(tModel);

                QueryExecutionFactory queryQueryExecutionFactory = new QueryExecutionFactoryModel(model);
                Query query = QueryFactory.create(queryText);
                QueryExecution queryQueryExecution = queryQueryExecutionFactory.createQueryExecution(query);
                ResultSet rs = queryQueryExecution.execSelect();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                ResultSetFormatter.outputAsJSON(os, rs);

                //response().setHeader("Content-Disposition", "attachment; filename=\""+digestFile.toString()+"\"");
                return ok(os.toByteArray());
            }

            response().setHeader("Content-Disposition", "attachment; filename=\""+digestFile.toString()+"\"");
            return ok(digestFile);
        }
    }
}
