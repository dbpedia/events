package controllers;

import org.dbpedia.events.DBpediaLiveDigest;
import play.api.inject.Injector;
import play.mvc.*;

import views.html.*;

import javax.inject.Inject;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

    @Inject
    Injector injector;

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {

        DBpediaLiveDigest digest = injector.instanceOf(DBpediaLiveDigest.class);

        return ok(index.render("Your new application is ready."));
    }

}
