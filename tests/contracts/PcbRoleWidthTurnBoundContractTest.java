package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Vector;

/** Independent shortest-path oracle for the role-width routing turn heuristic. */
public final class PcbRoleWidthTurnBoundContractTest {
    private static final int GRID = 10;
    private static final int TURN_COST = 35;
    private static final int DIRECTIONS = 5;
    private static final int INFINITY = Integer.MAX_VALUE / 4;
    private static int assertions;

    private static final SeededPcbLayoutGenerator.AttemptObserver NO_OP_OBSERVER =
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };

    private PcbRoleWidthTurnBoundContractTest() { }

    public static void main(String[] args) {
        literalExpectations();
        independentDijkstraChecks();
        workspaceReuseChecks();
        cancelledWorkspaceReinitializes();
        negativeOriginTranslation();
        System.out.println("PASS: PCB role width turn bound contracts assertions=" + assertions);
    }

    private static void literalExpectations() {
        Fixture straight = fixture(5, 5, 0, 0,
            new Goal[] { new Goal(0, 4, 2) }, 60, false);
        int center = straight.cell(2, 2);
        check(straight.bound.value(center, 1) == 20,
            "east-facing state uses the literal20-unit forward ray");
        check(straight.bound.value(center, 3) == 55,
            "west-facing state adds one35-unit turn before the20-unit route");
        check(straight.bound.value(center, 0) == 55,
            "north-facing state adds one35-unit turn before the20-unit route");
        check(straight.bound.value(center, 4) == 20,
            "sentinel direction takes the minimum of the four axial rays");

        Fixture oneBend = fixture(5, 5, 0, 0,
            new Goal[] { new Goal(0, 4, 0) }, 60, false);
        int bendStart = oneBend.cell(2, 2);
        check(oneBend.bound.value(bendStart, 1) == 75,
            "two east and two north steps plus one turn total75");
        check(oneBend.bound.value(bendStart, 3) == 110,
            "west-facing state needs 40 travel plus two turns for a behind-diagonal goal");
        check(oneBend.bound.value(bendStart, 2) == 110,
            "south-facing state needs 40 travel plus two turns for a behind-diagonal goal");
        check(oneBend.bound.value(bendStart, 4) == 75,
            "sentinel starts freely then still requires one turn for a diagonal goal");

        Fixture multipleGoals = fixture(6, 5, 0, 0,
            new Goal[] { new Goal(0, 3, 3), new Goal(1, 5, 2) }, 60, false);
        int multiStart = multipleGoals.cell(2, 2);
        check(multipleGoals.distance[multiStart] == 20,
            "nearest Manhattan goal is the diagonal20-unit goal");
        check(multipleGoals.bound.value(multiStart, 1) == 30,
            "forward ray selects the farther30-unit goal on the other face");

        Fixture otherFaceAtSameCell = fixture(5, 5, 0, 0,
            new Goal[] { new Goal(1, 2, 2) }, 60, false);
        check(otherFaceAtSameCell.bound.value(otherFaceAtSameCell.cell(2, 2), 1) == 0,
            "a goal on either face makes the shared geometric distance zero");
    }

    private static void independentDijkstraChecks() {
        for (int viaCost : new int[] { 60, 160 }) {
            Fixture straight = fixture(5, 5, 0, 0,
                new Goal[] { new Goal(0, 4, 2), new Goal(1, 1, 4) }, viaCost, false);
            verifyAgainstDijkstra(straight, "straight/multiface via=" + viaCost);

            Fixture diagonal = fixture(6, 5, 0, 0,
                new Goal[] { new Goal(0, 3, 3), new Goal(1, 5, 2) }, viaCost, false);
            verifyAgainstDijkstra(diagonal, "diagonal/competing goals via=" + viaCost);

            Fixture obstructed = fixture(4, 3, 0, 0,
                new Goal[] { new Goal(0, 0, 1), new Goal(1, 3, 1) }, viaCost, true);
            verifyAgainstDijkstra(obstructed, "blocked face with open second face via=" + viaCost);
        }
    }

    private static void workspaceReuseChecks() {
        PcbLayerRoutingPrototype.TurnBoundWorkspace workspace=
            new PcbLayerRoutingPrototype.TurnBoundWorkspace(5,5);
        Fixture first=fixture(5,5,0,0,new Goal[] { new Goal(0,4,2) },60,false,workspace);
        verifyAgainstDijkstra(first,"reused workspace first goal map");

        Fixture second=fixture(5,5,0,0,
            new Goal[] { new Goal(0,0,4), new Goal(1,4,0) },160,false,workspace);
        requireSameRayArrays(first.bound,second.bound,
            "sequential bounds reuse only this workspace's ray arrays");
        verifyAgainstDijkstra(second,"reused workspace opposite-corner goals");

        Fixture third=fixture(5,5,0,0,new Goal[] { new Goal(1,2,2) },60,false,workspace);
        requireSameRayArrays(second.bound,third.bound,
            "a third goal map reuses the fully overwritten workspace");
        verifyAgainstDijkstra(third,"reused workspace same-cell opposite-face goal");

        boolean mismatchedGridRejected=false;
        try {
            new PcbLayerRoutingPrototype.TurnLowerBound(1,25,third.goals,third.distance,
                NO_OP_OBSERVER,0,workspace);
        } catch(IllegalArgumentException expected) {
            mismatchedGridRejected=true;
        }
        check(mismatchedGridRejected,
            "workspace rejects a same-cell-count grid with a different shape before filling");
        verifyAgainstDijkstra(third,"shape rejection leaves the active bound unchanged");

        PcbLayerRoutingPrototype.TurnBoundWorkspace separate=
            new PcbLayerRoutingPrototype.TurnBoundWorkspace(5,5);
        Fixture independent=fixture(5,5,0,0,new Goal[] { new Goal(0,4,2) },60,false,separate);
        for(String name:new String[] {"north","east","south","west","axisMin"})
            check(fieldValue(third.bound,name)!=fieldValue(independent.bound,name),
                "independent workspace owns a separate "+name+" array");
        check(fieldValue(workspace,"rowMin")!=fieldValue(separate,"rowMin"),
            "independent workspace owns a separate row-minimum array");
        verifyAgainstDijkstra(independent,"independent workspace has no cross-owner alias");
    }

    private static void cancelledWorkspaceReinitializes() {
        final int width=4097,height=1,cells=width*height;
        final PcbLayerRoutingPrototype.TurnBoundWorkspace workspace=
            new PcbLayerRoutingPrototype.TurnBoundWorkspace(width,height);
        boolean[] incompleteGoals=new boolean[cells*2];
        int[] incompleteDistance=new int[cells];
        Arrays.fill(incompleteDistance,INFINITY);
        final RuntimeException cancelled=new RuntimeException("partial turn-bound workspace fill");
        final int[] checks={0};
        boolean propagated=false;
        try {
            new PcbLayerRoutingPrototype.TurnLowerBound(width,height,incompleteGoals,
                incompleteDistance,new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int attempt) {
                        if(++checks[0]==2) throw cancelled;
                    }
                },0,workspace);
        } catch(RuntimeException failure) {
            propagated=failure==cancelled;
        }
        check(propagated && checks[0]==2,
            "cancellation during a ray sweep propagates before publishing a partial bound");

        Fixture reinitialized=fixture(width,height,0,0,
            new Goal[] { new Goal(0,width-1,0) },60,false,workspace);
        verifyAgainstDijkstra(reinitialized,"partial workspace is fully overwritten after cancellation");
    }

    private static void negativeOriginTranslation() {
        Fixture zeroOrigin = fixture(5, 4, 0, 0,
            new Goal[] { new Goal(0, 4, 2) }, 60, false);
        Fixture negativeOrigin = fixture(5, 4, -7, -11,
            new Goal[] { new Goal(0, -3, -9) }, 60, false);
        for (int cell = 0; cell < zeroOrigin.cells; cell++) {
            for (int direction = 0; direction < DIRECTIONS; direction++)
                check(zeroOrigin.bound.value(cell, direction) ==
                        negativeOrigin.bound.value(cell, direction),
                    "translation by a negative origin preserves value at cell " + cell +
                        " direction " + direction);
        }
        int start = negativeOrigin.cell(-5, -9);
        check(negativeOrigin.bound.value(start, 1) == 20,
            "negative-coordinate east ray retains its literal20-unit bound");
        check(negativeOrigin.bound.value(start, 3) == 55,
            "negative-coordinate reverse ray retains its literal55-unit bound");
        verifyAgainstDijkstra(negativeOrigin, "negative-origin translation");
    }

    private static void verifyAgainstDijkstra(Fixture fixture, String label) {
        int[] exact = fixture.graph.distancesToAnyGoal();
        int reachable = 0;
        for (int state = 0; state < fixture.graph.stateCount; state++) {
            int distance = exact[state];
            if (distance >= INFINITY) continue;
            reachable++;
            int cell = fixture.graph.cellOf(state);
            int direction = fixture.graph.directionOf(state);
            int estimate = fixture.bound.value(cell, direction);
            check(estimate >= 0 && estimate <= distance,
                label + " heuristic is admissible at state " + state +
                    " estimate=" + estimate + " exact=" + distance);
        }
        check(reachable == fixture.graph.stateCount,
            label + " every bounded-grid state can reach a goal on some face");

        for (Edge edge : fixture.graph.edges) {
            int fromCell = fixture.graph.cellOf(edge.from);
            int fromDirection = fixture.graph.directionOf(edge.from);
            int toCell = fixture.graph.cellOf(edge.to);
            int toDirection = fixture.graph.directionOf(edge.to);
            int from = fixture.bound.value(fromCell, fromDirection);
            int to = fixture.bound.value(toCell, toDirection);
            check(from <= edge.cost + to,
                label + " heuristic is consistent across " + edge.from + "->" + edge.to +
                    " cost=" + edge.cost + " h=" + from + " next=" + to);
        }
    }

    private static Fixture fixture(int width, int height, int originX, int originY,
            Goal[] goalSpecs, int viaCost, boolean wallOnFirstFace) {
        return fixture(width,height,originX,originY,goalSpecs,viaCost,wallOnFirstFace,null);
    }

    private static Fixture fixture(int width,int height,int originX,int originY,
            Goal[] goalSpecs,int viaCost,boolean wallOnFirstFace,
            PcbLayerRoutingPrototype.TurnBoundWorkspace workspace) {
        int cells = width * height;
        boolean[] goals = new boolean[cells * 2];
        for (Goal goal : goalSpecs) {
            int cell = relativeCell(width, height, originX, originY, goal.x, goal.y);
            goals[goal.layer * cells + cell] = true;
        }
        int[] distance = bruteForceManhattan(width, height, originX, originY, goalSpecs);
        PcbLayerRoutingPrototype.TurnLowerBound bound=workspace==null?
            new PcbLayerRoutingPrototype.TurnLowerBound(width,height,goals,distance,NO_OP_OBSERVER,0):
            new PcbLayerRoutingPrototype.TurnLowerBound(width,height,goals,distance,
                NO_OP_OBSERVER,0,workspace);
        OracleGraph graph = new OracleGraph(width, height, goals, viaCost,
            new int[] { 0, 7 }, wallOnFirstFace);
        return new Fixture(width, height, originX, originY, cells, goals, distance, bound, graph);
    }

    private static void requireSameRayArrays(PcbLayerRoutingPrototype.TurnLowerBound first,
            PcbLayerRoutingPrototype.TurnLowerBound second,String label) {
        for(String name:new String[] {"north","east","south","west","axisMin"})
            check(fieldValue(first,name)==fieldValue(second,name),label+": "+name);
    }

    private static Object fieldValue(Object target,String name) {
        try {
            Field field=target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch(Exception failure) {
            throw new AssertionError("Cannot inspect "+name+" workspace array: "+failure);
        }
    }

    /** Independent brute-force coordinate oracle; it does not call production lowerBound(). */
    private static int[] bruteForceManhattan(int width, int height, int originX, int originY,
            Goal[] goals) {
        int[] distance = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int best = INFINITY;
                int absoluteX = originX + x, absoluteY = originY + y;
                for (Goal goal : goals) {
                    int steps = Math.abs(absoluteX - goal.x) + Math.abs(absoluteY - goal.y);
                    best = Math.min(best, steps * GRID);
                }
                distance[y * width + x] = best;
            }
        }
        return distance;
    }

    private static int relativeCell(int width, int height, int originX, int originY,
            int absoluteX, int absoluteY) {
        int x = absoluteX - originX, y = absoluteY - originY;
        if (x < 0 || x >= width || y < 0 || y >= height)
            throw new IllegalArgumentException("Fixture goal lies outside its grid");
        return y * width + x;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Goal {
        final int layer, x, y;
        Goal(int layer, int x, int y) {
            if (layer < 0 || layer > 1) throw new IllegalArgumentException("Invalid goal face");
            this.layer = layer; this.x = x; this.y = y;
        }
    }

    private static final class Fixture {
        final int width, height, originX, originY, cells;
        final boolean[] goals;
        final int[] distance;
        final PcbLayerRoutingPrototype.TurnLowerBound bound;
        final OracleGraph graph;
        Fixture(int width, int height, int originX, int originY, int cells, boolean[] goals,
                int[] distance, PcbLayerRoutingPrototype.TurnLowerBound bound,
                OracleGraph graph) {
            this.width = width; this.height = height; this.originX = originX;
            this.originY = originY; this.cells = cells; this.goals = goals;
            this.distance = distance; this.bound = bound; this.graph = graph;
        }
        int cell(int x, int y) {
            return relativeCell(width, height, originX, originY, x, y);
        }
    }

    private static final class Edge {
        final int from, to, cost;
        Edge(int from, int to, int cost) { this.from = from; this.to = to; this.cost = cost; }
    }

    private static final class DistanceLabel implements Comparable<DistanceLabel> {
        final int state, distance;
        DistanceLabel(int state, int distance) { this.state = state; this.distance = distance; }
        public int compareTo(DistanceLabel other) {
            if (distance != other.distance) return distance < other.distance ? -1 : 1;
            return state < other.state ? -1 : state == other.state ? 0 : 1;
        }
    }

    /** Explicit two-face state graph and reverse-edge Dijkstra, independent of production search. */
    private static final class OracleGraph {
        private static final int[] DX = new int[] { 0, 1, 0, -1 };
        private static final int[] DY = new int[] { -1, 0, 1, 0 };
        final int width, height, cells, stateCount;
        final boolean[] goals;
        final Vector<Edge> edges = new Vector<Edge>();
        final Vector<Vector<Edge>> incoming = new Vector<Vector<Edge>>();

        OracleGraph(int width, int height, boolean[] goals, int viaCost,
                int[] secondaryByLayer, boolean wallOnFirstFace) {
            this.width = width; this.height = height; cells = width * height;
            stateCount = 2 * cells * DIRECTIONS; this.goals = goals;
            if (viaCost != 60 && viaCost != 160)
                throw new IllegalArgumentException("Fixture uses only the two declared via costs");
            if (secondaryByLayer == null || secondaryByLayer.length != 2 ||
                    secondaryByLayer[0] < 0 || secondaryByLayer[1] < 0)
                throw new IllegalArgumentException("Secondary edge costs must be nonnegative");
            for (int state = 0; state < stateCount; state++) incoming.add(new Vector<Edge>());

            for (int layer = 0; layer < 2; layer++) {
                for (int cell = 0; cell < cells; cell++) {
                    int x = cell % width, y = cell / width;
                    for (int direction = 0; direction < DIRECTIONS; direction++) {
                        int from = state(layer, cell, direction);
                        for (int move = 0; move < 4; move++) {
                            int nextX = x + DX[move], nextY = y + DY[move];
                            if (nextX < 0 || nextX >= width || nextY < 0 || nextY >= height)
                                continue;
                            if (wallOnFirstFace && layer == 0 &&
                                    ((x == 1 && move == 1) || (x == 2 && move == 3)))
                                continue;
                            int nextCell = nextY * width + nextX;
                            int cost = GRID + secondaryByLayer[layer] +
                                (direction != 4 && direction != move ? TURN_COST : 0);
                            addEdge(from, state(layer, nextCell, move), cost);
                        }
                        if (direction != 4)
                            addEdge(from, state(1 - layer, cell, 4), viaCost);
                    }
                }
            }
        }

        int[] distancesToAnyGoal() {
            int[] distance = new int[stateCount];
            Arrays.fill(distance, INFINITY);
            PriorityQueue<DistanceLabel> queue = new PriorityQueue<DistanceLabel>();
            for (int layer = 0; layer < 2; layer++) {
                for (int cell = 0; cell < cells; cell++) {
                    if (!goals[layer * cells + cell]) continue;
                    for (int direction = 0; direction < DIRECTIONS; direction++) {
                        int goalState = state(layer, cell, direction);
                        distance[goalState] = 0;
                        queue.add(new DistanceLabel(goalState, 0));
                    }
                }
            }
            while (!queue.isEmpty()) {
                DistanceLabel current = queue.remove();
                if (current.distance != distance[current.state]) continue;
                Vector<Edge> predecessors = incoming.get(current.state);
                for (Edge edge : predecessors) {
                    int candidate = current.distance + edge.cost;
                    if (candidate < distance[edge.from]) {
                        distance[edge.from] = candidate;
                        queue.add(new DistanceLabel(edge.from, candidate));
                    }
                }
            }
            return distance;
        }

        int cellOf(int state) { return state % (cells * DIRECTIONS) / DIRECTIONS; }
        int directionOf(int state) { return state % DIRECTIONS; }

        private int state(int layer, int cell, int direction) {
            return (layer * cells + cell) * DIRECTIONS + direction;
        }

        private void addEdge(int from, int to, int cost) {
            Edge edge = new Edge(from, to, cost);
            edges.add(edge);
            incoming.get(to).add(edge);
        }
    }
}
