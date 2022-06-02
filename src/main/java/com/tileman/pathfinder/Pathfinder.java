package com.tileman.pathfinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

public class Pathfinder {

    public final CollisionMap map;
    public final Map<WorldPoint, List<WorldPoint>> transports;

    public Pathfinder(CollisionMap map, Map<WorldPoint, List<WorldPoint>> transports) {
        this.map = map;
        this.transports = transports;
    }

    public class Path {
        private final Node start;
        private final WorldPoint target;
        private final List<Node> boundary = new LinkedList<>();
        private final Set<WorldPoint> visited = new HashSet<>();

        public Node nearest;
        private Set<WorldPoint> path = new HashSet<>();

        public boolean loading;

        public Path(WorldPoint start, WorldPoint target) {
            this.target = target;
            this.start = new Node(start, null);
            this.nearest = null;
            this.loading = true;

            this.calculate();
        }

        private void addNeighbors(Node node) {
            if (map.w(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() - 1, node.position.getY(), node.position.getPlane()));
            }

            if (map.e(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() + 1, node.position.getY(), node.position.getPlane()));
            }

            if (map.s(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX(), node.position.getY() - 1, node.position.getPlane()));
            }

            if (map.n(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX(), node.position.getY() + 1, node.position.getPlane()));
            }

            if (map.sw(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() - 1, node.position.getY() - 1, node.position.getPlane()));
            }

            if (map.se(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() + 1, node.position.getY() - 1, node.position.getPlane()));
            }

            if (map.nw(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() - 1, node.position.getY() + 1, node.position.getPlane()));
            }

            if (map.ne(node.position.getX(), node.position.getY(), node.position.getPlane())) {
                addNeighbor(node, new WorldPoint(node.position.getX() + 1, node.position.getY() + 1, node.position.getPlane()));
            }

            for (WorldPoint transport : transports.getOrDefault(node.position, new ArrayList<>())) {
                addNeighbor(node, transport);
            }
        }

        public Set<WorldPoint> currentBest() {
            return nearest == null ? null : nearest.path();
        }

        public Set<WorldPoint> getPath() {
            return this.path;
        }

        public WorldPoint getStart() {
            return start.position;
        }

        public WorldPoint getTarget() {
            return target;
        }

        private void addNeighbor(Node node, WorldPoint neighbor) {
            if (!visited.add(neighbor)) {
                return;
            }

            boundary.add(new Node(neighbor, node));
        }

        public void calculate() {
            boundary.add(start);

            int bestDistance = Integer.MAX_VALUE;

            while (!boundary.isEmpty()) {
                Node node = boundary.remove(0);

                if (node.position.equals(target)) {
                    this.path = node.path();
                    this.loading = false;
                    return;
                }

                int distance = node.position.distanceTo(target);
                if (nearest == null || distance < bestDistance) {
                    nearest = node;
                    bestDistance = distance;
                }

                addNeighbors(node);
            }

            if (nearest != null) {
                this.path = nearest.path();
            }

            this.loading = false;
        }
    }

    private static class Node {
        public final WorldPoint position;
        public final Node previous;

        public Node(WorldPoint position, Node previous) {
            this.position = position;
            this.previous = previous;
        }

        public Set<WorldPoint> path() {
            Set<WorldPoint> path = new HashSet<>();
            Node node = this;

            while (node != null) {
                path.add(node.position);
                node = node.previous;
            }

            return path;
        }
    }
}