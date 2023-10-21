package net.floodlightcontroller.pathfinder;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.routing.Router;

public class PathFinderWebRoutable implements RestletRoutable {
    @Override
    public String basePath() {
        return "/find-path";
    }

    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/{start}/{end}", PathFinderResource.class);
        return router;
    }
}
