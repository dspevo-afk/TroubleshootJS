package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Vector;

/** Current bounded single-layer tree router; P06 owns optional layer transitions. */
final class PcbNetRouter {
    private static final int GRID=10, ROUTING_CHECK_INTERVAL=128;
    static final int MINIMUM_STEP_COST=2;
    static final class Rejected extends RuntimeException {
        final String blockedNet;
        Rejected(String reason) { this(reason,null); }
        Rejected(String reason,String blockedNet) { super(reason);this.blockedNet=blockedNet; }
    }
    private static Rejected reject(PcbRoutingRejectedException.Kind kind,String message) { return new Rejected(message); }
    private static Rectangle traceCollisionEnvelope(Rectangle r) {
        int margin=PcbTraceRules.TRACE_WIDTH/2;
        return new Rectangle(r.x-margin,r.y-margin,r.width+margin*2,r.height+margin*2);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        route(layout,board,outline,attempt,observer,true);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical) {
        route(layout,board,outline,attempt,observer,canonical,0);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical,long routingSeed) {
        route(layout,board,outline,attempt,observer,canonical,routingSeed,0);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical,long routingSeed,int treeVariant) {
        route(layout,board,outline,attempt,observer,canonical,routingSeed,treeVariant,null);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical,long routingSeed,int treeVariant,Vector<String> blocked) {
        Router router=new Router(layout,board,outline,attempt,observer);
        Vector<String> nets=board.getNetIds();
        final TroubleshootBoard definition=board;
        final int order=board.getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM?(attempt/2)%4:0;
        Collections.sort(nets,new Comparator<String>() { public int compare(String a,String b) {
            int ap=priority(definition,a),bp=priority(definition,b);
            if(order==1) { ap=-ap;bp=-bp; }
            if(order>=2) {
                ap=definition.getNet(a).getPadIds().size();bp=definition.getNet(b).getPadIds().size();
                if(order==2) {ap=-ap;bp=-bp;}
            }
            return ap==bp?a.compareTo(b):ap<bp?-1:1;
        }});
        if(board.getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM && attempt>=8)
            NamedRandomStreams.shuffle(nets,new java.util.Random(routingSeed ^ (0x632be59bd9b4e019L*(attempt+1))));
        if(treeVariant>=2) Collections.reverse(nets);
        if(blocked!=null)for(String net:blocked) {nets.remove(net);nets.add(0,net);}
        try {
        for(String net:nets) {
            Vector<String> remaining=board.getNet(net).getPadIds(); Collections.sort(remaining);
            // A declared unused contact remains a physical pad without invented copper.
            if(remaining.size()==1) continue;
            if(remaining.isEmpty()) throw new IllegalStateException("Cannot route a net without pads: "+net);
            Vector<String> reached=new Vector<String>();
            // Bounded medoid/farthest candidates. Every tie ends in the canonical pad ID.
            String root=chooseRoot(layout,remaining,attempt%2); remaining.remove(root); reached.add(root);
            java.util.Random branchOrder=new java.util.Random(routingSeed ^ net.hashCode() ^ (0x9e3779b97f4a7c15L*(attempt+1)));
            boolean trunk=false;
            while(!remaining.isEmpty()) {
                String next=treeVariant%2==1 ?
                    remaining.get(branchOrder.nextInt(remaining.size())) :
                    treeVariant>=2?chooseFarthest(layout,remaining,reached):chooseNext(layout,remaining,reached);
                remaining.remove(next);
                if(internallyReached(board,next,reached)) { reached.add(next); continue; }
                PcbTraceGeometry trace;
                try { trace=router.route(net,next,trunk?null:root); }
                catch(Rejected failure) {throw new Rejected(failure.getMessage(),net);}
                layout.addTrace(trace); reached.add(next); trunk=true;
            }
        }
        if(canonical) PcbRouteCanonicalizer.canonicalize(layout);
        } finally {
            layout.setRoutingStatistics(router.expansions,router.rawSegments,router.congestionRejections);
        }
    }
    static int priority(TroubleshootBoard board,String net) {
        for(String id:board.getPowerInputIds()) {
            ExternalBoardPowerInput input=board.getPowerInput(id);
            if(net.equals(input.getReturnNetId())) return 0;
            if(net.equals(input.getPositiveNetId())) return 1;
        }
        return board.getNet(net).getRoutingRole().priority;
    }
    private static String chooseRoot(PcbBoardLayout layout,Vector<String> pads,int variant) {
        String best=pads.get(0); long bestCost=variant==0?Long.MAX_VALUE:Long.MIN_VALUE;
        for(String id:pads) {
            PcbPadPlacement a=layout.getPad(id); long sum=0;
            for(String other:pads) { PcbPadPlacement b=layout.getPad(other);sum+=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY()); }
            if(variant==0?sum<bestCost:sum>bestCost) { best=id;bestCost=sum; }
        }
        return best;
    }
    private static String chooseNext(PcbBoardLayout layout,Vector<String> remaining,Vector<String> reached) {
        String best=remaining.get(0); long distance=Long.MAX_VALUE;
        for(String id:remaining) for(String other:reached) {
            PcbPadPlacement a=layout.getPad(id),b=layout.getPad(other);
            long d=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY());
            if(d<distance) {best=id;distance=d;}
        }
        return best;
    }
    private static String chooseFarthest(PcbBoardLayout layout,Vector<String> remaining,Vector<String> reached) {
        String best=remaining.get(0);long farthest=-1;
        for(String id:remaining) {
            long nearest=Long.MAX_VALUE;PcbPadPlacement a=layout.getPad(id);
            for(String other:reached) {
                PcbPadPlacement b=layout.getPad(other);
                nearest=Math.min(nearest,Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY()));
            }
            if(nearest>farthest) {best=id;farthest=nearest;}
        }
        return best;
    }
    private static boolean internallyReached(TroubleshootBoard board,String id,Vector<String> reached) {
        BoardPad pad=board.getPad(id);
        for(String other:reached) {
            BoardPad previous=board.getPad(other);
            if(pad.getComponentId().equals(previous.getComponentId()) && board.getComponent(pad.getComponentId()).getPhysicalPackage()
                    .isInternallyConnected(pad.getTerminalId(),previous.getTerminalId())) return true;
        }
        return false;
    }
    private static class Router {
        int expansions,rawSegments,congestionRejections;
        private final PcbBoardLayout layout;
        private final TroubleshootBoard board;
        private final Rectangle outline;
        private final int minX;
        private final int minY;
        private final int gridWidth;
        private final int gridHeight;
        private final String[][] occupiedNet;
        private final String[][] clearanceNet;
        // Placement is immutable for this routing attempt. Take one private
        // snapshot instead of copying collections and physical bounds per edge.
        private final PcbPadPlacement[] pads;
        private final String[] padNets;
        private final Rectangle[] escapeReservations;
        private final PcbComponentPlacement[] components;
        private final Rectangle[] courtyards, collisionCourtyards;
        private final int attempt;
        private final SeededPcbLayoutGenerator.AttemptObserver observer;
        private final PcbCopperLayer layer;
        private boolean emptyComponentFace=true;
        private String[][] horizontalReservation,verticalReservation,padAt;

        Router(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline,
                int attempt, SeededPcbLayoutGenerator.AttemptObserver observer) {
            this.layout = layout;
            this.board = board;
            this.outline = outline;
            this.attempt = attempt;
            this.observer = observer;
            layer=board.getPlacementConstraints().routingLayer;
            minX = outline.x + GRID;
            minY = outline.y + GRID;
            gridWidth = (outline.width - 2 * GRID) / GRID + 1;
            gridHeight = (outline.height - 2 * GRID) / GRID + 1;
            if (gridWidth < 1 || gridHeight < 1 || (long)gridWidth*gridHeight > 700000)
                throw new Rejected("ROUTING_GRID_BUDGET");
            occupiedNet = new String[gridWidth][gridHeight];
            clearanceNet = new String[gridWidth][gridHeight];
            pads = layout.getPads().toArray(new PcbPadPlacement[0]);
            padNets = new String[pads.length];
            escapeReservations = new Rectangle[pads.length];
            for (int index = 0; index < pads.length; index++) {
                PcbPadPlacement pad = pads[index];
                if(!PcbCopperAccess.hasCopper(pad,layer))
                    throw new IllegalArgumentException("Requested routing layer has no pad land: "+pad.getPadId());
                padNets[index] = board.getPad(pad.getPadId()).getNetId();
                int length = ((pad.getEscapeLength() + GRID - 1) / GRID) * GRID;
                int ex = pad.getX() + pad.getEscapeDx() * length;
                int ey = pad.getY() + pad.getEscapeDy() * length;
                int margin = PcbTraceRules.MIN_CENTERLINE_CLEARANCE - 1;
                escapeReservations[index] = new Rectangle(Math.min(pad.getX(), ex) - margin,
                    Math.min(pad.getY(), ey) - margin, Math.abs(pad.getX() - ex) + margin * 2,
                    Math.abs(pad.getY() - ey) + margin * 2);
                if(pad.getMountingSide()!=layer.getFace())
                    escapeReservations[index]=new Rectangle(pad.getX()-margin,pad.getY()-margin,margin*2,margin*2);
            }
            components = layout.getComponents().toArray(new PcbComponentPlacement[0]);
            courtyards = new Rectangle[components.length];
            collisionCourtyards = new Rectangle[components.length];
            for (int index = 0; index < components.length; index++) {
                courtyards[index] = components[index].getRoutingCourtyard();
                if(components[index].getMountingSide()==layer.getFace()) emptyComponentFace=false;
                collisionCourtyards[index] = traceCollisionEnvelope(courtyards[index]);
            }
            if(emptyComponentFace) {
                horizontalReservation=new String[gridWidth][gridHeight];verticalReservation=new String[gridWidth][gridHeight];padAt=new String[gridWidth][gridHeight];
                for(int i=0;i<pads.length;i++) {
                    Rectangle r=escapeReservations[i];
                    int px=gridX(pads[i].getX()),py=gridY(pads[i].getY());
                    if(px>=0&&py>=0&&px<gridWidth&&py<gridHeight)padAt[px][py]=pads[i].getPadId();
                    int x0=Math.max(0,(r.x-minX)/GRID-1),x1=Math.min(gridWidth-1,(r.x+r.width-minX)/GRID+1);
                    int y0=Math.max(0,(r.y-minY)/GRID-1),y1=Math.min(gridHeight-1,(r.y+r.height-minY)/GRID+1);
                    for(int x=x0;x<=x1;x++) for(int y=y0;y<=y1;y++) {
                        if(r.intersects(new Rectangle(minX+x*GRID,minY+y*GRID,GRID,1))) reserve(horizontalReservation,x,y,padNets[i]);
                        if(r.intersects(new Rectangle(minX+x*GRID,minY+y*GRID,1,GRID))) reserve(verticalReservation,x,y,padNets[i]);
                    }
                }
            }
        }
        private void reserve(String[][] cells,int x,int y,String net) {
            cells[x][y]=cells[x][y]==null || cells[x][y].equals(net)?net:"";
        }

        PcbTraceGeometry route(String netId, String startPadId, String endPadId) {
            PcbPadPlacement startPad = layout.getPad(startPadId);
            PcbPadPlacement endPad = layout.getPad(endPadId);
            int startX = gridX(startPad.getX());
            int startY = gridY(startPad.getY());
            int endX = endPad == null ? -1 : gridX(endPad.getX());
            int endY = endPad == null ? -1 : gridY(endPad.getY());
            if (startX < 0 || startY < 0 || startX >= gridWidth || startY >= gridHeight ||
                    (endPad != null && (endX < 0 || endY < 0 || endX >= gridWidth || endY >= gridHeight)))
                throw new IllegalStateException("Pad is not aligned to PCB routing grid: " +
                    netId);
            boolean[] goals=new boolean[gridWidth*gridHeight];
            if(endPad!=null) goals[endY*gridWidth+endX]=true;
            else for(int gx=0;gx<gridWidth;gx++) for(int gy=0;gy<gridHeight;gy++)
                goals[gy*gridWidth+gx]=netId.equals(occupiedNet[gx][gy]) && canJoinTree(gx,gy);
            int[] distances=distanceField(goals);

            final int noneDirection = 4;
            final int[] directionX = { 0, 1, 0, -1 };
            final int[] directionY = { -1, 0, 1, 0 };
            // Flat, zero-initialized storage avoids four forests of per-cell
            // arrays in JavaScript. Previous uses zero for unseen, -1 for the
            // root, and prior state + 1 for a discovered predecessor.
            double[] bestCost = new double[gridWidth*gridHeight*5];
            int[] previous = new int[bestCost.length];
            PriorityQueue<SearchNode> open = new PriorityQueue<SearchNode>(128,
                new Comparator<SearchNode>() {
                    public int compare(SearchNode first, SearchNode second) {
                        return first.compareTo(second);
                    }
                });
            int sequence = 0;
            previous[stateKey(startX,startY,noneDirection)] = -1;
            open.add(new SearchNode(startX, startY, noneDirection, 0,
                lowerBound(distances[startY*gridWidth+startX],endPad), sequence++));
            SearchNode goal = null;
            int expanded = 0;
            while (!open.isEmpty()) {
                if ((expanded++ % ROUTING_CHECK_INTERVAL) == 0)
                    observer.check(attempt);
                if (expanded > (long)gridWidth*gridHeight*20) throw new Rejected("SEARCH_WORK_BUDGET");
                SearchNode current = open.poll();
                expansions++;
                int currentKey=stateKey(current.x,current.y,current.direction);
                if (current.cost != bestCost[currentKey])
                    continue;
                if (goals[current.y*gridWidth+current.x]) {
                    goal = current;
                    break;
                }
                for (int direction = 0; direction < 4; direction++) {
                    int nextX = current.x + directionX[direction];
                    int nextY = current.y + directionY[direction];
                    if (nextX < 0 || nextY < 0 || nextX >= gridWidth || nextY >= gridHeight ||
                            !isLegalMove(current, nextX, nextY, direction, startX, startY,
                                endX, endY, startPad, endPad) ||
                            !canTraverse(current.x, current.y, nextX, nextY, startPad, endPad,netId) ||
                            !canOccupy(nextX, nextY, netId, startPad, endPad))
                        continue;
                    double stepCost = GRID;
                    if (netId.equals(occupiedNet[nextX][nextY]))
                        stepCost = MINIMUM_STEP_COST;
                    else if (netId.equals(clearanceNet[nextX][nextY]))
                        stepCost = 7;
                    double cost = current.cost + stepCost;
                    if (current.direction != noneDirection && current.direction != direction)
                        cost += 35;
                    int nextKey=stateKey(nextX,nextY,direction);
                    if (previous[nextKey]!=0 && cost >= bestCost[nextKey])
                        continue;
                    bestCost[nextKey] = cost;
                    previous[nextKey] = currentKey+1;
                    open.add(new SearchNode(nextX, nextY, direction, cost,
                        lowerBound(distances[nextY*gridWidth+nextX],endPad), sequence++));
                }
            }
            if (goal == null)
                throw reject(PcbRoutingRejectedException.Kind.ROUTING,
                    "Unable to route net " + netId + " from " +
                    startPadId + "@" + startPad.getX() + "," + startPad.getY() + " to " +
                    (endPad==null ? "connected tree" : endPadId + "@" + endPad.getX() + "," + endPad.getY()));

            Vector<Point> points = new Vector<Point>();
            int currentX = goal.x;
            int currentY = goal.y;
            int currentDirection = goal.direction;
            while (currentX >= 0 && currentY >= 0) {
                points.add(new Point(minX + currentX * GRID, minY + currentY * GRID));
                int prior=previous[stateKey(currentX,currentY,currentDirection)];
                if(prior==-1) break;
                if(prior==0) throw new IllegalStateException("Missing routing predecessor");
                prior--;
                currentDirection=prior%5;
                currentX=(prior/5)%gridWidth;
                currentY=(prior/5)/gridWidth;
            }
            Collections.reverse(points);
            if(points.size()<2) throw new Rejected("ZERO_LENGTH_BRANCH");
            rawSegments+=points.size()-1;
            markCopper(points, netId);
            // Keep the searched path for independent comparison. The canonicalizer
            // removes only collinear subdivisions and retains physical witnesses.
            Vector<Point> routedPoints = points;
            int[] xPoints = new int[routedPoints.size()];
            int[] yPoints = new int[routedPoints.size()];
            for (int index = 0; index < routedPoints.size(); index++) {
                xPoints[index] = routedPoints.get(index).x;
                yPoints[index] = routedPoints.get(index).y;
            }
            return new PcbTraceGeometry(endPadId==null ? "tree/"+netId+"/branch/"+startPadId :
                PcbTraceGeometry.defaultSourceId(startPadId,endPadId,layer),netId,
                startPadId,endPadId,layer,PcbCopperAccess.Exposure.EXPOSED,xPoints,yPoints);
        }

        private boolean canJoinTree(int x,int y) {
            if(emptyComponentFace) return true;
            int px=minX+x*GRID,py=minY+y*GRID;
            for(int i=0;i<collisionCourtyards.length;i++)
                if(components[i].getMountingSide()==layer.getFace() && containsInclusive(collisionCourtyards[i],px,py)) return false;
            return true;
        }
        private int lowerBound(int distance,PcbPadPlacement end) {
            if(layer!=PcbCopperLayer.BOTTOM || !emptyComponentFace) return distance*MINIMUM_STEP_COST;
            // All parts in the bottom-layer envelope mount on top. Thus every
            // cell of existing same-net copper is a goal: the cost-2 step can
            // occur only once, at arrival; preceding fresh/clearance steps cost
            // at least 7. The initial two-pad branch has no same-net copper yet.
            return end!=null ? distance*10 : Math.max(0,distance*7-5);
        }
        /** Manhattan lower bound to ANY connected trunk cell, multiplied by the true minimum step cost. */
        private int[] distanceField(boolean[] goals) {
            int[] distance=new int[goals.length],queue=new int[goals.length]; int head=0,tail=0;
            for(int i=0;i<distance.length;i++) { distance[i]=Integer.MAX_VALUE; if(goals[i]) { distance[i]=0;queue[tail++]=i; } }
            if(tail==0) throw new Rejected("ORPHAN_TREE");
            while(head<tail) {
                int key=queue[head++],x=key%gridWidth,y=key/gridWidth;
                for(int next:new int[]{x>0?key-1:-1,x+1<gridWidth?key+1:-1,y>0?key-gridWidth:-1,y+1<gridHeight?key+gridWidth:-1})
                    if(next>=0&&distance[next]==Integer.MAX_VALUE) { distance[next]=distance[key]+1;queue[tail++]=next; }
            }
            return distance;
        }

        private boolean isLegalMove(SearchNode current, int nextX, int nextY, int direction,
                int startX, int startY, int endX, int endY, PcbPadPlacement startPad,
                PcbPadPlacement endPad) {
            int startDx=current.x-startX,startDy=current.y-startY;
            int startDistance=(startDx*startPad.getEscapeDx()+startDy*startPad.getEscapeDy())*GRID;
            if (startPad.getMountingSide()==layer.getFace() && startPad.getEscapeLength() > 0 && startDistance>=0 && startDistance<startPad.getEscapeLength() &&
                    startDx*startPad.getEscapeDy()==startDy*startPad.getEscapeDx() &&
                    (directionX(direction) != startPad.getEscapeDx() ||
                    directionY(direction) != startPad.getEscapeDy()))
                return false;
            if (endPad!=null && endPad.getMountingSide()==layer.getFace() && endPad.getEscapeLength() > 0 &&
                    (nextX-endX)*endPad.getEscapeDy()==(nextY-endY)*endPad.getEscapeDx() &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID>=0 &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID<endPad.getEscapeLength() &&
                    (directionX(direction) != -endPad.getEscapeDx() ||
                    directionY(direction) != -endPad.getEscapeDy()))
                return false;
            return true;
        }

        private boolean canOccupy(int x, int y, String netId, PcbPadPlacement startPad,
                PcbPadPlacement endPad) {
            int physicalX = minX + x * GRID;
            int physicalY = minY + y * GRID;
            if ((occupiedNet[x][y] != null && !netId.equals(occupiedNet[x][y])) ||
                    (clearanceNet[x][y] != null && !netId.equals(clearanceNet[x][y]))) {
                congestionRejections++;
                return false;
            }
            if(emptyComponentFace) return padAt[x][y]==null || padAt[x][y].equals(startPad.getPadId()) ||
                endPad!=null && padAt[x][y].equals(endPad.getPadId());
            if (isPadAtOtherNet(physicalX, physicalY, startPad.getPadId(),
                        endPad==null?null:endPad.getPadId()))
                return false;
            if ((physicalX == startPad.getX() && physicalY == startPad.getY()) ||
                    (endPad!=null && physicalX == endPad.getX() && physicalY == endPad.getY()))
                return true;
            for (int index = 0; index < components.length; index++) {
                PcbComponentPlacement component = components[index];
                if(component.getMountingSide()!=layer.getFace()) continue;
                if (!containsInclusive(courtyards[index], physicalX, physicalY))
                    continue;
                boolean startEscape = component.getComponentId().equals(
                    board.getPad(startPad.getPadId()).getComponentId()) &&
                    startPad.isInEscapeCorridor(physicalX, physicalY);
                boolean endEscape = endPad!=null && component.getComponentId().equals(
                    board.getPad(endPad.getPadId()).getComponentId()) &&
                    endPad.isInEscapeCorridor(physicalX, physicalY);
                if (!startEscape && !endEscape)
                    return false;
            }
            return true;
        }

        private boolean canTraverse(int fromX, int fromY, int toX, int toY,
                PcbPadPlacement startPad, PcbPadPlacement endPad,String net) {
            // Cache the exact existing rectangle/step intersection predicate on
            // the unpopulated face, avoiding a per-search-edge scan of every pad.
            if(emptyComponentFace) {
                String reserved=fromY==toY?horizontalReservation[Math.min(fromX,toX)][fromY]:verticalReservation[fromX][Math.min(fromY,toY)];
                return reserved==null || reserved.equals(net);
            }
            int startPhysicalX = minX + fromX * GRID;
            int startPhysicalY = minY + fromY * GRID;
            int endPhysicalX = minX + toX * GRID;
            int endPhysicalY = minY + toY * GRID;
            String startComponentId = board.getPad(startPad.getPadId()).getComponentId();
            String endComponentId = endPad==null?null:board.getPad(endPad.getPadId()).getComponentId();
            Rectangle move = new Rectangle(Math.min(startPhysicalX,endPhysicalX),Math.min(startPhysicalY,endPhysicalY),
                Math.max(1,Math.abs(startPhysicalX-endPhysicalX)),Math.max(1,Math.abs(startPhysicalY-endPhysicalY)));
            for(int index = 0; index < pads.length; index++) {
                if(net.equals(padNets[index])) continue;
                // Reserve every terminal's escape before routing any net. An early rail
                // cannot occupy the only way out of a later signal's courtyard.
                if(escapeReservations[index].intersects(move)) return false;
            }
            Rectangle stroke = traceStroke(startPhysicalX, startPhysicalY, endPhysicalX, endPhysicalY);
            for (int index = 0; index < components.length; index++) {
                PcbComponentPlacement component = components[index];
                if(component.getMountingSide()!=layer.getFace()) continue;
                if (!courtyards[index].intersects(stroke))
                    continue;
                boolean startEscape = component.getComponentId().equals(startComponentId) &&
                    courtyardIntersectionIsEscape(collisionCourtyards[index], startPad,
                        startPhysicalX, startPhysicalY, endPhysicalX, endPhysicalY);
                boolean endEscape = component.getComponentId().equals(endComponentId) &&
                    courtyardIntersectionIsEscape(collisionCourtyards[index], endPad,
                        startPhysicalX, startPhysicalY, endPhysicalX, endPhysicalY);
                if (!startEscape && !endEscape)
                    return false;
            }
            return true;
        }

        private boolean courtyardIntersectionIsEscape(Rectangle courtyard,
                PcbPadPlacement pad, int x1, int y1, int x2, int y2) {
            if (pad.getEscapeLength() > 0 &&
                    ((x1 == pad.getX() && y1 == pad.getY() &&
                        pad.isInEscapeCorridor(x2, y2)) ||
                    (x2 == pad.getX() && y2 == pad.getY() &&
                        pad.isInEscapeCorridor(x1, y1))))
                return true;
            if (y1 == y2) {
                int left = Math.max(Math.min(x1, x2), courtyard.x);
                int right = Math.min(Math.max(x1, x2), courtyard.x + courtyard.width);
                return left <= right && pad.isInEscapeCorridor(left, y1) &&
                    pad.isInEscapeCorridor(right, y1);
            }
            if (x1 == x2) {
                int top = Math.max(Math.min(y1, y2), courtyard.y);
                int bottom = Math.min(Math.max(y1, y2), courtyard.y + courtyard.height);
                return top <= bottom && pad.isInEscapeCorridor(x1, top) &&
                    pad.isInEscapeCorridor(x1, bottom);
            }
            return false;
        }

        private void markCopper(Vector<Point> points, String netId) {
            for (Point point : points) {
                int centerX = gridX(point.x);
                int centerY = gridY(point.y);
                occupiedNet[centerX][centerY] = netId;
                for (int x = Math.max(0, centerX - PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS);
                        x <= Math.min(gridWidth - 1,
                            centerX + PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS); x++) {
                    for (int y = Math.max(0, centerY - PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS);
                            y <= Math.min(gridHeight - 1,
                                centerY + PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS); y++)
                        if (clearanceNet[x][y] == null)
                            clearanceNet[x][y] = netId;
                }
            }
        }

        private boolean isPadAtOtherNet(int x, int y, String startPadId, String endPadId) {
            for (PcbPadPlacement pad : pads) {
                if (pad.getPadId().equals(startPadId) || pad.getPadId().equals(endPadId))
                    continue;
                if (pad.getX() == x && pad.getY() == y)
                    return true;
            }
            return false;
        }

        private int directionX(int direction) {
            return direction == 1 ? 1 : direction == 3 ? -1 : 0;
        }

        private int directionY(int direction) {
            return direction == 0 ? -1 : direction == 2 ? 1 : 0;
        }

        private int manhattan(int x, int y, int otherX, int otherY) {
            return Math.abs(x - otherX) + Math.abs(y - otherY);
        }

        private int gridX(int x) {
            return (x - minX) % GRID == 0 ? (x - minX) / GRID : -1;
        }
        private int stateKey(int x,int y,int direction) { return (y*gridWidth+x)*5+direction; }
        private int gridY(int y) {
            return (y - minY) % GRID == 0 ? (y - minY) / GRID : -1;
        }

        private static boolean containsInclusive(Rectangle rectangle, int x, int y) {
            return x >= rectangle.x && y >= rectangle.y &&
                x <= rectangle.x + rectangle.width && y <= rectangle.y + rectangle.height;
        }

        private static Rectangle traceStroke(int firstX, int firstY, int secondX, int secondY) {
            int half = PcbTraceRules.TRACE_WIDTH / 2;
            if (firstX == secondX)
                return new Rectangle(firstX - half, Math.min(firstY, secondY) - half,
                    PcbTraceRules.TRACE_WIDTH,
                    Math.abs(secondY - firstY) + PcbTraceRules.TRACE_WIDTH);
            if (firstY == secondY)
                return new Rectangle(Math.min(firstX, secondX) - half, firstY - half,
                    Math.abs(secondX - firstX) + PcbTraceRules.TRACE_WIDTH,
                    PcbTraceRules.TRACE_WIDTH);
            throw new IllegalStateException("PCB router encountered a non-Manhattan move");
        }

        private static class SearchNode {
            private final int x;
            private final int y;
            private final int direction;
            private final double cost;
            private final double heuristic;
            private final int sequence;

            SearchNode(int x, int y, int direction, double cost, double heuristic,
                    int sequence) {
                this.x = x;
                this.y = y;
                this.direction = direction;
                this.cost = cost;
                this.heuristic = heuristic;
                this.sequence = sequence;
            }

            int compareTo(SearchNode other) {
                double total = cost + heuristic;
                double otherTotal = other.cost + other.heuristic;
                if (total < otherTotal)
                    return -1;
                if (total > otherTotal)
                    return 1;
                if (heuristic < other.heuristic)
                    return -1;
                if (heuristic > other.heuristic)
                    return 1;
                if (y != other.y)
                    return y - other.y;
                if (x != other.x)
                    return x - other.x;
                if (direction != other.direction)
                    return direction - other.direction;
                return sequence - other.sequence;
            }
        }
    }
}
