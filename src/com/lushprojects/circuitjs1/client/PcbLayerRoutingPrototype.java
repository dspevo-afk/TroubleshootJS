package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;

/** Bounded two-face routing engine; adoption remains controlled by the physical-policy owner. */
final class PcbLayerRoutingPrototype {
    enum Policy {
        ONE_LAYER(0,0,0,0,false), SPARSE_LINK(0,0,0,0,true),
        RESTRICTED_TWO_LAYER(2,8,160,8,true), FULLER_TWO_LAYER(4,48,60,2,true);
        final int transitions, vias, viaCost, secondaryCost;
        final boolean underpasses;
        Policy(int transitions,int vias,int viaCost,int secondaryCost,boolean underpasses) {
            this.transitions=transitions; this.vias=vias; this.viaCost=viaCost;
            this.secondaryCost=secondaryCost; this.underpasses=underpasses;
        }
    }
    static final int GRID=10, MAX_CELLS=250000, MAX_EXPANSIONS=1000000;
    static final int MAX_BRANCH_EXPANSIONS=100000, ORDERINGS=3;
    static final int WORK_QUANTUM=32768;
    private static final int PATH_OCCUPANCY_CHUNK=256;
    static final class Result {
        final PcbBoardLayout layout;
        final String outcome;
        final int expansions, orderings, vias, links;
        Result(PcbBoardLayout layout,String outcome,int expansions,int orderings,int links) {
            this.layout=layout; this.outcome=outcome; this.expansions=expansions;
            this.orderings=orderings; this.links=links; vias=layout==null?0:layout.getHoles().size();
        }
        boolean accepted() { return layout!=null; }
    }
    private static final class Exhausted extends RuntimeException {
        Exhausted(String reason) { super(reason); }
    }
    private static final class Budget {
        final int maximum;
        int expanded;
        Budget(int maximum) { this.maximum=maximum; }
    }
    static Session begin(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return begin(board,placement,policy,observer,MAX_EXPANSIONS);
    }
    static Session begin(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum) {
        return new Session(board,placement,policy,observer,maximum);
    }
    static Result route(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return route(board,placement,policy,observer,MAX_EXPANSIONS);
    }
    static Result route(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum) {
        Session session=begin(board,placement,policy,observer,maximum);
        while(!session.advanceSlice()) { }
        return session.result();
    }
    /** Retains one route candidate and its search state across counted generation turns. */
    static final class Session {
        private final TroubleshootBoard board;
        private final Policy policy;
        private final SeededPcbLayoutGenerator.AttemptObserver observer;
        private final PcbBoardLayout template;
        private final int links;
        private final Budget budget;
        private int pass,passes;
        private String reason="NO_PATH";
        private PcbBoardLayout candidate;
        private Search search;
        private Result result;
        private boolean complete;

        Session(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,int maximum) {
            if(board==null || placement==null || policy==null || observer==null ||
                    maximum<1 || maximum>MAX_EXPANSIONS) throw new IllegalArgumentException("Invalid P07 request");
            this.board=board; this.policy=policy; this.observer=observer;
            observer.check(0);
            if(!placement.getHoles().isEmpty()) throw new IllegalArgumentException("P07 requires an undrilled candidate");
            placement.validateAgainst(board);
            // The matched prototype qualifies plated-through-hole packages only. A surface
            // pad must never acquire a second land merely because the search has two faces.
            int checked=0;
            for(PcbPadPlacement pad:placement.getPads()) {
                if((checked++&127)==0) observer.check(0);
                if(pad.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE)
                    throw new IllegalArgumentException("P07 routing prototype requires plated-through-hole pads");
            }
            template=placement.copyForRouting();
            links=PcbFactoryLinkPolicy.validateLayout(template);
            budget=new Budget(maximum);
            observer.check(0);
        }
        boolean advanceSlice() { return advanceSlice(WORK_QUANTUM); }
        /** Each queue pop or bounded route-state operation consumes one slice unit. */
        boolean advanceSlice(int maximumWork) {
            if(maximumWork<1) throw new IllegalArgumentException("P07 slice must make positive progress");
            if(complete) return true;
            observer.check(pass);
            int remaining=maximumWork;
            while(remaining>0 && !complete) {
                if(search==null) {
                    if(pass>=ORDERINGS || budget.expanded>=budget.maximum) {
                        finishRejected();
                        break;
                    }
                    observer.check(pass);
                    passes++;
                    candidate=template.copyForRouting();
                    try {
                        search=new Search(board,candidate,policy,observer,budget,pass);
                    } catch(Exhausted failure) {
                        reason=failure.getMessage();
                        candidate=null;
                        pass++;
                    }
                    observer.check(pass<ORDERINGS?pass:ORDERINGS-1);
                    remaining--;
                    continue;
                }
                try {
                    search.advanceOne();
                    remaining--;
                    if(search.complete) {
                        observer.check(pass);
                        candidate.validateRoutingGeometry(board);
                        search.rules.validate(candidate);
                        candidate.validateRouteQuality();
                        observer.check(pass); // No partial routes or holes can escape a failed/cancelled attempt.
                        candidate.setRoutingStatistics(budget.expanded,search.segments,0);
                        result=new Result(candidate,"SUCCESS",budget.expanded,passes,links);
                        complete=true;
                    }
                } catch(Exhausted failure) {
                    reason=failure.getMessage();
                    candidate=null; search=null; pass++;
                    remaining--;
                } catch(PcbBoardLayout.RouteQualityRejectedException quality) {
                    reason="QUALITY_LIMIT";
                    candidate=null; search=null; pass++;
                    remaining--;
                }
            }
            if(!complete) observer.check(pass<ORDERINGS?pass:ORDERINGS-1);
            return complete;
        }
        Result result() {
            if(!complete) throw new IllegalStateException("P07 route has not completed");
            return result;
        }
        int expansions() { return budget.expanded; }
        private void finishRejected() {
            result=new Result(null,reason,budget.expanded,passes,links);
            complete=true;
        }
    }
    private static final class Node implements Comparable<Node> {
        final int cell,layer,direction,transitions,cost,heuristic,serial,key;
        final Node previous;
        Node(int cell,int layer,int direction,int transitions,int cost,int heuristic,
                int serial,int cells,Node previous) {
            this.cell=cell; this.layer=layer; this.direction=direction; this.transitions=transitions;
            this.cost=cost; this.heuristic=heuristic; this.serial=serial; this.previous=previous;
            key=(((transitions*2+layer)*5+direction)*cells)+cell;
        }
        public int compareTo(Node other) {
            int total=cost+heuristic, theirs=other.cost+other.heuristic;
            if(total!=theirs) return total<theirs?-1:1;
            if(heuristic!=other.heuristic) return heuristic<other.heuristic?-1:1;
            if(key!=other.key) return key<other.key?-1:1;
            return serial<other.serial?-1:serial==other.serial?0:1;
        }
    }
    /** Typed heap uses Node.compareTo directly; each route Node serial breaks remaining ties. */
    private static final class NodeQueue {
        private Node[] values=new Node[128];
        private int size;
        void add(Node value) {
            if(value==null) throw new IllegalArgumentException("Missing search node");
            if(size==values.length) {
                Node[] larger=new Node[values.length*2];
                System.arraycopy(values,0,larger,0,size);
                values=larger;
            }
            int at=size++;
            while(at>0) {
                int parent=(at-1)>>>1;
                Node above=values[parent];
                if(value.compareTo(above)>=0) break;
                values[at]=above; at=parent;
            }
            values[at]=value;
        }
        Node poll() {
            if(size==0) return null;
            Node result=values[0],value=values[--size];
            values[size]=null;
            if(size>0) {
                int at=0,half=size>>>1;
                while(at<half) {
                    int child=(at<<1)+1,right=child+1;
                    if(right<size && values[right].compareTo(values[child])<0) child=right;
                    if(value.compareTo(values[child])<=0) break;
                    values[at]=values[child]; at=child;
                }
                values[at]=value;
            }
            return result;
        }
    }
    private static final class Search {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        final Policy policy;
        final SeededPcbLayoutGenerator.AttemptObserver observer;
        final Budget budget;
        final int pass,minX,minY,width,height,cells,primary;
        final PcbTwoLayerRules rules;
        final PcbNetRouter.Router[] faces=new PcbNetRouter.Router[2];
        final PcbPadPlacement[] pads;
        final PcbComponentPlacement[] parts;
        int serial,branches,segments;
        private Vector<String> nets;
        private int netIndex;
        private String currentNet;
        private Vector<String> currentPadIds;
        private Vector<String> reachedPadIds;
        private PcbPadPlacement currentRoot;
        private BranchSearch branch;
        private Publisher publisher;
        private boolean complete;
        Search(TroubleshootBoard board,PcbBoardLayout layout,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,Budget budget,int pass) {
            this.board=board; this.layout=layout; this.policy=policy;
            this.observer=observer; this.budget=budget; this.pass=pass;
            Rectangle r=layout.getBoardOutline(); minX=r.x+GRID; minY=r.y+GRID;
            width=(r.width-2*GRID)/GRID+1; height=(r.height-2*GRID)/GRID+1;
            if(width<1 || height<1 || (long)width*height>MAX_CELLS) throw new Exhausted("GRID_LIMIT");
            cells=width*height; primary=board.getPlacementConstraints().routingLayer.ordinal();
            rules=new PcbTwoLayerRules(board,layout);
            pads=layout.getPads().toArray(new PcbPadPlacement[0]);
            parts=layout.getComponents().toArray(new PcbComponentPlacement[0]);
            for(int layer=0;layer<2;layer++) {
                faces[layer]=new PcbNetRouter.Router(layout,board,r,pass,observer,PcbCopperLayer.values()[layer],
                    policy.transitions>0?PcbTwoLayerRules.VIA_LAND:0);
                faces[layer].factoryUnderpasses=policy.underpasses;
            }
        }
        int x(int cell) { return minX+(cell%width)*GRID; }
        int y(int cell) { return minY+(cell/width)*GRID; }
        int cell(PcbPadPlacement pad) {
            int x=pad.getX()-minX,y=pad.getY()-minY;
            if(x<0 || y<0 || x%GRID!=0 || y%GRID!=0 || x/GRID>=width || y/GRID>=height)
                throw new IllegalArgumentException("P07 pad is outside its routing grid");
            return (y/GRID)*width+x/GRID;
        }
        void advanceOne() {
            if(complete) return;
            if(publisher!=null) {
                if(publisher.advanceOne()) {
                    segments+=publisher.segments;
                    reachedPadIds.add(publisher.start.getPadId());
                    publisher=null;
                }
                return;
            }
            if(branch!=null) {
                branch.advanceOne();
                if(branch.found!=null) {
                    publisher=new Publisher(branch.net,branch.start,branch.root,branch.found);
                    branch=null;
                }
                return;
            }
            if(nets==null) {
                nets=board.getNetIds();
                Collections.sort(nets,new Comparator<String>() { public int compare(String a,String b) {
                    int ap=pass==1?-board.getNet(a).getPadIds().size():PcbNetRouter.priority(board,a);
                    int bp=pass==1?-board.getNet(b).getPadIds().size():PcbNetRouter.priority(board,b);
                    return ap==bp?a.compareTo(b):ap<bp?-1:1;
                }});
                if(pass==2) Collections.reverse(nets);
                return;
            }
            if(currentPadIds!=null && !currentPadIds.isEmpty()) {
                // Keep a different branch tree on the existing reverse-priority pass.
                // Wide return nets retain nearest-to-reached scheduling; lexical
                // return branches can exhaust the unchanged per-branch search cap.
                boolean lexical=pass==2 &&
                    board.getNet(currentNet).getRoutingRole()!=BoardNet.RoutingRole.RETURN;
                String next=lexical?currentPadIds.firstElement():
                    PcbNetRouter.chooseNext(layout,currentPadIds,reachedPadIds);
                currentPadIds.remove(next);
                PcbPadPlacement start=layout.getPad(next);
                branches++;
                branch=new BranchSearch(currentNet,start,currentRoot);
                return;
            }
            if(netIndex<nets.size()) {
                currentNet=nets.get(netIndex++);
                currentPadIds=board.getNet(currentNet).getPadIds();
                Collections.sort(currentPadIds);
                if(currentPadIds.size()<2) {
                    currentPadIds=null; currentRoot=null; reachedPadIds=null;
                } else {
                    currentRoot=layout.getPad(currentPadIds.remove(0));
                    reachedPadIds=new Vector<String>();
                    reachedPadIds.add(currentRoot.getPadId());
                }
                return;
            }
            complete=true;
        }
        boolean[] goals(String net,PcbPadPlacement root) {
            boolean[] result=new boolean[cells*2];
            for(int layer=0;layer<2;layer++) if(layer==primary || policy.transitions>0) {
                result[layer*cells+cell(root)]=true;
                for(int c=0;c<cells;c++) {
                    if((c&4095)==0) observer.check(pass);
                    if(faces[layer].ownsLayerCell(x(c),y(c),net)) result[layer*cells+c]=true;
                }
            }
            if(policy==Policy.RESTRICTED_TWO_LAYER)
                result[(1-primary)*cells+cell(root)]=false;
            return result;
        }
        int[] lowerBound(boolean[] goals) {
            int[] distance=new int[cells],queue=new int[cells]; int head=0,tail=0;
            java.util.Arrays.fill(distance,-1);
            for(int c=0;c<cells;c++) {
                if((c&4095)==0) observer.check(pass);
                if(goals[c] || goals[c+cells]) { distance[c]=0; queue[tail++]=c; }
            }
            while(head<tail) {
                if((head&4095)==0) observer.check(pass);
                int c=queue[head++],cx=c%width,cy=c/width;
                if(cx>0) { int n=c-1; if(distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; } }
                if(cx+1<width) { int n=c+1; if(distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; } }
                if(cy>0) { int n=c-width; if(distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; } }
                if(cy+1<height) { int n=c+width; if(distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; } }
            }
            return distance;
        }
        private final class BranchSearch {
            final String net;
            final PcbPadPlacement start,root;
            final boolean[] goal;
            final int[] distance;
            final PcbBoardHole[] holes;
            final Rectangle[] holeBounds;
            final HashMap<Integer,Node> best=new HashMap<Integer,Node>();
            final NodeQueue queue=new NodeQueue();
            int expanded;
            Node found;
            BranchSearch(String net,PcbPadPlacement start,PcbPadPlacement root) {
                this.net=net; this.start=start; this.root=root;
                observer.check(pass);
                // Publisher finishes before the next branch is constructed.
                // Its holes are immutable throughout this branch, including
                // yields. Refresh from the owner for every new branch.
                holes=layout.getHoles().toArray(new PcbBoardHole[0]);
                holeBounds=new Rectangle[holes.length];
                for(int i=0;i<holes.length;i++) holeBounds[i]=holes[i].getBounds();
                goal=goals(net,root); distance=lowerBound(goal);
                for(int layer=0;layer<2;layer++) if(layer==primary || policy==Policy.FULLER_TWO_LAYER)
                    offer(queue,best,new Node(cell(start),layer,4,0,layer==primary?0:policy.secondaryCost,
                        distance[cell(start)],serial++,cells,null));
                observer.check(pass);
            }
            void offerStep(Node current,int n,int direction) {
                // The face predicate already checks this exact domain corridor.
                // In-grid adjacent centers keep the 9-unit stroke inside the validated outline.
                if(!faces[current.layer].permitsLayerStep(x(current.cell),y(current.cell),x(n),y(n),net,start,root)) return;
                int cost=current.cost+GRID+(current.layer==primary?0:policy.secondaryCost)+
                    (current.direction!=4 && current.direction!=direction?35:0);
                offer(queue,best,new Node(n,current.layer,direction,current.transitions,cost,
                    distance[n],serial++,cells,current));
            }
            void advanceOne() {
                Node current=queue.poll();
                if(current==null) throw new Exhausted(net+":NO_PATH");
                if(best.get(current.key)!=current) return;
                if((expanded%128)==0) observer.check(pass);
                if(budget.expanded>=budget.maximum) throw new Exhausted(net+":TOTAL_SEARCH_LIMIT");
                if(expanded>=MAX_BRANCH_EXPANSIONS) throw new Exhausted(net+":BRANCH_SEARCH_LIMIT");
                expanded++; budget.expanded++;
                if(goal[current.layer*cells+current.cell]) { found=current; return; }
                int cx=current.cell%width,cy=current.cell/width;
                if(cy>0) offerStep(current,current.cell-width,0);
                if(cx+1<width) offerStep(current,current.cell+1,1);
                if(cy+1<height) offerStep(current,current.cell+width,2);
                if(cx>0) offerStep(current,current.cell-1,3);
                if(current.direction!=4 && current.transitions<policy.transitions &&
                        pathViaFits(current) && viaFits(net,x(current.cell),y(current.cell),holes,holeBounds))
                    offer(queue,best,new Node(current.cell,1-current.layer,4,current.transitions+1,
                        current.cost+policy.viaCost,distance[current.cell],serial++,cells,current));
            }
        }
        private final class Publisher {
            final String net;
            final PcbPadPlacement start,root;
            final Node end;
            final Vector<Node> reversed=new Vector<Node>();
            Node collect;
            int stage,qualityIndex=1,length,bends,lastDirection=4;
            int pieceStart,pieceScan=1,pieceEnd,pathIndex,pathCount,piece,compactIndex;
            boolean transition;
            Vector<Point> occupancy;
            Vector<Point> compact;
            int[] traceX,traceY;
            int segments;
            Publisher(String net,PcbPadPlacement start,PcbPadPlacement root,Node end) {
                this.net=net; this.start=start; this.root=root; this.end=end; collect=end;
            }
            Node nodeAt(int index) { return reversed.get(reversed.size()-1-index); }
            boolean advanceOne() {
                if(stage==0) {
                    if(collect!=null) {
                        reversed.add(collect); collect=collect.previous; return false;
                    }
                    stage=1; return false;
                }
                if(stage==1) {
                    if(qualityIndex<reversed.size()) {
                        Node a=nodeAt(qualityIndex-1),b=nodeAt(qualityIndex++);
                        if(a.layer!=b.layer) { lastDirection=4; return false; }
                        length+=GRID;
                        if(lastDirection!=4 && lastDirection!=b.direction) bends++;
                        lastDirection=b.direction;
                        return false;
                    }
                    int direct=Math.abs(start.getX()-x(end.cell))+Math.abs(start.getY()-y(end.cell));
                    if(direct==0 || length>direct*3 || bends>16) throw new Exhausted(net+":BRANCH_QUALITY_LIMIT");
                    stage=2; pieceStart=0; pieceScan=1; return false;
                }
                if(stage==2) {
                    if(pieceStart>=reversed.size()) { stage=6; return true; }
                    if(pieceScan<reversed.size() && nodeAt(pieceScan).layer==nodeAt(pieceScan-1).layer) {
                        pieceScan++; return false;
                    }
                    pieceEnd=pieceScan-1; transition=pieceScan<reversed.size();
                    pathIndex=pieceStart; pathCount=0; occupancy=new Vector<Point>(); compact=new Vector<Point>();
                    stage=3; return false;
                }
                if(stage==3) {
                    if(pathIndex<=pieceEnd) {
                        Node node=nodeAt(pathIndex++);
                        Point point=new Point(x(node.cell),y(node.cell));
                        occupancy.add(point); pathCount++;
                        int n=compact.size();
                        if(n>=2) {
                            Point a=compact.get(n-2),b=compact.get(n-1);
                            if(a.x==b.x && b.x==point.x || a.y==b.y && b.y==point.y)
                                compact.remove(n-1);
                        }
                        compact.add(point);
                        if(occupancy.size()>=PATH_OCCUPANCY_CHUNK) flushOccupancy();
                        return false;
                    }
                    flushOccupancy();
                    traceX=new int[compact.size()]; traceY=new int[compact.size()]; compactIndex=0;
                    stage=4; return false;
                }
                if(stage==4) {
                    if(compactIndex<compact.size()) {
                        Point point=compact.get(compactIndex);
                        traceX[compactIndex]=point.x; traceY[compactIndex]=point.y; compactIndex++;
                        return false;
                    }
                    stage=5; return false;
                }
                if(stage==5) {
                    Node begin=nodeAt(pieceStart),last=nodeAt(pieceEnd);
                    if(pathCount>1) {
                        String endPad=pieceEnd==reversed.size()-1 && end.cell==cell(root)?root.getPadId():null;
                        layout.addTrace(new PcbTraceGeometry("p07/"+net+"/"+branches+"/"+(piece++),net,
                            pieceStart==0?start.getPadId():null,endPad,PcbCopperLayer.values()[begin.layer],
                            PcbCopperAccess.Exposure.EXPOSED,traceX,traceY));
                        segments+=pathCount-1;
                    }
                    if(transition) {
                        int vx=x(last.cell),vy=y(last.cell); boolean exists=false;
                        for(PcbBoardHole hole:layout.getHoles()) if(hole.x==vx && hole.y==vy) exists=true;
                        if(!exists) layout.addHole(PcbTwoLayerRules.via("p07-via/"+net+"/"+branches+"/"+piece,net,vx,vy));
                        Vector<Point> point=new Vector<Point>(); point.add(new Point(vx,vy));
                        faces[0].occupyLayerPath(point,net); faces[1].occupyLayerPath(point,net);
                        pieceStart=pieceScan; pieceScan=pieceStart+1; stage=2;
                        return false;
                    }
                    stage=6; return true;
                }
                return stage==6;
            }
            private void flushOccupancy() {
                if(occupancy.isEmpty()) return;
                faces[nodeAt(pieceStart).layer].occupyLayerPath(occupancy,net);
                occupancy.clear();
                observer.check(pass);
            }
        }
        void offer(NodeQueue queue,HashMap<Integer,Node> best,Node node) {
            Node previous=best.get(node.key);
            if(previous!=null && previous.cost<=node.cost) return;
            best.put(node.key,node); queue.add(node);
        }
        boolean viaFits(String net,int x,int y,PcbBoardHole[] holes,Rectangle[] holeBounds) {
            if(policy.transitions==0) return false;
            for(PcbBoardHole hole:holes)
                if(hole.x==x && hole.y==y) return net.equals(hole.netId);
            if(holes.length>=policy.vias) return false;
            Rectangle land=new Rectangle(x-PcbTwoLayerRules.VIA_LAND,y-PcbTwoLayerRules.VIA_LAND,
                PcbTwoLayerRules.VIA_LAND*2,PcbTwoLayerRules.VIA_LAND*2);
            if(!PcbTwoLayerRules.inside(layout.getBoardOutline(),land) || !rules.permits(net,land) ||
                    !faces[0].freeLayerCell(x,y,net) || !faces[1].freeLayerCell(x,y,net)) return false;
            for(PcbComponentPlacement part:parts)
                if(PcbConductorBuilder.touch(land,part.getRoutingCourtyard())) return false;
            Rectangle clearance=PcbTwoLayerRules.expand(land,PcbTraceRules.MIN_VISIBLE_CLEARANCE);
            for(PcbPadPlacement pad:pads)
                if(PcbConductorBuilder.touch(clearance,pad.getPadBounds())) return false;
            for(Rectangle bounds:holeBounds)
                if(PcbConductorBuilder.touch(clearance,bounds)) return false;
            return true;
        }
        boolean pathViaFits(Node current) {
            if(layout.getHoleCount()+current.transitions+1>policy.vias) return false;
            int pathIndex=0;
            for(Node node=current;node.previous!=null;node=node.previous) {
                if((pathIndex++&255)==0) observer.check(pass);
                if(node.layer!=node.previous.layer && Math.abs(x(node.cell)-x(current.cell))<20 &&
                        Math.abs(y(node.cell)-y(current.cell))<20) return false;
            }
            return true;
        }
    }
    private PcbLayerRoutingPrototype() { }
}
