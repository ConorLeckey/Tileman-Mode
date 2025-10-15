package com.tileman;

import net.runelite.api.Constants;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import java.util.*;

public class TilemanPath {
    private final TilemanModePlugin plugin;
    private final List<Node> unevaluatedNodes = new LinkedList<>();
    private final Set<WorldPoint> evaluatedNodes = new HashSet<>();

    public TilemanPath(TilemanModePlugin plugin) {
        this.plugin = plugin;
    }

    public List<WorldPoint> findPath(WorldPoint start, WorldPoint end) {

        // check for silly requests
        Node shortestPathTail = null;
        if (start == null || end == null || start.equals(end)) {
            return Collections.emptyList(); // no path available
        }

        // cleanse working states
        unevaluatedNodes.clear();
        evaluatedNodes.clear();

        // add the starting point to the evaluate list
        unevaluatedNodes.add(new Node(start, null));

        int bestDistance = Integer.MAX_VALUE;
        int searchLimiter = Constants.EXTENDED_SCENE_SIZE * Constants.EXTENDED_SCENE_SIZE;

        while (!unevaluatedNodes.isEmpty() && searchLimiter > 0) {
            searchLimiter -= 1; // used to exit the loop if search gets too large
            Node node = unevaluatedNodes.remove(0);

            if (node.point.equals(end)) {
                return node.getPathToRootHead();
            }

            // distance heuristic for node comparison
            int distance = Math.max(Math.abs(node.point.getX() - end.getX()), Math.abs(node.point.getY() - end.getY()));
            if (shortestPathTail == null || distance < bestDistance) {
                shortestPathTail = node;
                bestDistance = distance;
            }

            addTraversableNeighbors(node);
        }

        // dont return a near path, only an actual path
        if (!shortestPathTail.point.equals(end)){
            return Collections.emptyList();
        }

        // don't return single tile paths
        List<WorldPoint> returnPath = shortestPathTail.getPathToRootHead();
        if (returnPath.size() <= 1){
            return Collections.emptyList();
        }

        return returnPath;
    }

    private void addTraversableNeighbors(Node head) {

        // don't operate on garbage data
        if (head.point == null){
            return;
        }

        int staticAxis = 0;
        int southOnYAxis = -1;
        int northOnYAxis = 1;
        int eastOnXAxis = 1;
        int westOnXAxis = -1;
        int plane = head.point.getPlane();

        int x = head.point.getX();
        int y = head.point.getY();

        WorldView wv = plugin.getClient().getWorldView(-1);
        if (wv == null){
            return;
        }

        // collision checks work on WorldArea, so convert WorldPoint to 1x1 WorldArea
        WorldArea travelCheck = head.point.toWorldArea();
        if (travelCheck == null){
            return;
        }

        // Axis aligned

        if (travelCheck.canTravelInDirection(wv, staticAxis, northOnYAxis)){
            WorldPoint north = new WorldPoint(x, y + northOnYAxis, plane);
            addNeighbor(head, north);
        }

        if (travelCheck.canTravelInDirection(wv, eastOnXAxis, staticAxis)){
            WorldPoint east = new WorldPoint(x + eastOnXAxis, y, plane);
            addNeighbor(head, east);
        }

        if (travelCheck.canTravelInDirection(wv, staticAxis, southOnYAxis)){
            WorldPoint south = new WorldPoint(x, y + southOnYAxis, plane);
            addNeighbor(head, south);
        }

        if (travelCheck.canTravelInDirection(wv, westOnXAxis, staticAxis)){
            WorldPoint west = new WorldPoint(x + westOnXAxis, y, plane);
            addNeighbor(head, west);
        }

        // Diagonal north

        if (travelCheck.canTravelInDirection(wv, eastOnXAxis, northOnYAxis)){
            WorldPoint northEast = new WorldPoint(x + eastOnXAxis, y + northOnYAxis, plane);
            addNeighbor(head, northEast);
        }

        if (travelCheck.canTravelInDirection(wv, westOnXAxis, northOnYAxis)){
            WorldPoint northWest = new WorldPoint(x + westOnXAxis, y + northOnYAxis, plane);
            addNeighbor(head, northWest);
        }

        // Diagonal south

        if (travelCheck.canTravelInDirection(wv, eastOnXAxis, southOnYAxis)){
            WorldPoint southEast = new WorldPoint(x + eastOnXAxis, y + southOnYAxis, plane);
            addNeighbor(head, southEast);
        }

        if (travelCheck.canTravelInDirection(wv, westOnXAxis, southOnYAxis)){
            WorldPoint southWest = new WorldPoint(x + westOnXAxis, y + southOnYAxis, plane);
            addNeighbor(head, southWest);
        }

    }

    private void addNeighbor(Node head, WorldPoint neighbor) {

        // Exit early if we didn't successfully add the evaluated node to the tracking set
        if (!evaluatedNodes.add(neighbor)) {
            return; // do nothing deliberately
        }

        Node newTail = new Node(neighbor, head);
        unevaluatedNodes.add(newTail);
    }

    private static class Node {
        public final WorldPoint point;
        public final Node head;
        public Node(WorldPoint currentNodePoint, Node headToPointTo) {
            this.point = currentNodePoint;
            this.head = headToPointTo;
        }

        public List<WorldPoint> getPathToRootHead() {
            List<WorldPoint> path = new LinkedList<>();
            Node node = this;

            // walk the linked list all the way back to the original head
            while (node != null) {
                path.add(0, node.point);
                node = node.head;
            }

            // Ordered list representing the WorldPoint path from tail to the head
            return new ArrayList<>(path);
        }
    }
}