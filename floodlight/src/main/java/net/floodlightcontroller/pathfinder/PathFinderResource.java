package net.floodlightcontroller.pathfinder;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PathFinderResource extends ServerResource {
    protected static Logger logger = LoggerFactory.getLogger(PathFinderResource.class);

    @Get("json")
    public Object retrieve() {
        String startNode = (String) getRequestAttributes().get("start");
        String endNode = (String) getRequestAttributes().get("end");

        logger.info("New request received to find best path between nodes {} and {}", startNode, endNode);
        PathFinder pathFinder = PathFinder.getInstance();

        return pathFinder.findPath(startNode, endNode);
    }
}

