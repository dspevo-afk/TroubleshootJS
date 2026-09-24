package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Vector;

/** Bounded, developer-only two-face comparison. Never invoked by normal generation. */
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
    static Result route(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return route(board,placement,policy,observer,MAX_EXPANSIONS);
    }
    static Result route(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum) {
        if(board==null || placement==null || policy==null || observer==null ||
                maximum<1 || maximum>MAX_EXPANSIONS) throw new IllegalArgumentException("Invalid P07 request");
        if(!placement.getHoles().isEmpty()) throw new IllegalArgumentException("P07 requires an undrilled candidate");
        placement.validateAgainst(board);
        // The matched prototype qualifies plated-through-hole packages only. A surface
        // pad must never acquire a second land merely because the search has two faces.
        for(PcbPadPlacement pad:placement.getPads())
            if(pad.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE)
                throw new IllegalArgumentException("P07 routing prototype requires plated-through-hole pads");
        PcbBoardLayout template=placement.copyForRouting();
        int links=PcbFactoryLinkPolicy.validateLayout(template);
        Budget budget=new Budget(maximum); String reason="NO_PATH"; int passes=0;
        for(int pass=0;pass<ORDERINGS && budget.expanded<maximum;pass++) {
            observer.check(pass); passes++;
            PcbBoardLayout candidate=template.copyForRouting();
            try {
                Search search=new Search(board,candidate,policy,observer,budget,pass);
                search.allNets();
                candidate.validateRoutingGeometry(board);
                search.rules.validate(candidate);
                candidate.validateRouteQuality();
                observer.check(pass); // No partial routes or holes can escape a failed/cancelled attempt.
                candidate.setRoutingStatistics(budget.expanded,search.segments,0);
                return new Result(candidate,"SUCCESS",budget.expanded,passes,links);
            } catch(Exhausted failure) { reason=failure.getMessage(); }
            catch(PcbBoardLayout.RouteQualityRejectedException quality) { reason="QUALITY_LIMIT"; }
        }
        return new Result(null,reason,budget.expanded,passes,links);
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
                faces[layer]=new PcbNetRouter.Router(layout,board,r,pass,observer,PcbCopperLayer.values()[layer]);
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
        void allNets() {
            Vector<String> nets=board.getNetIds();
            Collections.sort(nets,new Comparator<String>() { public int compare(String a,String b) {
                int ap=pass==1?-board.getNet(a).getPadIds().size():PcbNetRouter.priority(board,a);
                int bp=pass==1?-board.getNet(b).getPadIds().size():PcbNetRouter.priority(board,b);
                return ap==bp?a.compareTo(b):ap<bp?-1:1;
            }});
            if(pass==2) Collections.reverse(nets);
            for(String net:nets) {
                Vector<String> ids=board.getNet(net).getPadIds(); Collections.sort(ids);
                if(ids.size()<2) continue;
                PcbPadPlacement root=layout.getPad(ids.get(0));
                for(int i=1;i<ids.size();i++) {
                    try { branch(net,layout.getPad(ids.get(i)),root); }
                    catch(Exhausted failure) { throw new Exhausted(net+":"+failure.getMessage()); }
                }
            }
        }
        boolean[] goals(String net,PcbPadPlacement root) {
            boolean[] result=new boolean[cells*2];
            for(int layer=0;layer<2;layer++) if(layer==primary || policy.transitions>0) {
                result[layer*cells+cell(root)]=true;
                for(int c=0;c<cells;c++) if(faces[layer].ownsLayerCell(x(c),y(c),net)) result[layer*cells+c]=true;
            }
            if(policy==Policy.RESTRICTED_TWO_LAYER)
                result[(1-primary)*cells+cell(root)]=false;
            return result;
        }
        int[] lowerBound(boolean[] goals) {
            int[] distance=new int[cells],queue=new int[cells]; int head=0,tail=0;
            java.util.Arrays.fill(distance,-1);
            for(int c=0;c<cells;c++) if(goals[c] || goals[c+cells]) { distance[c]=0; queue[tail++]=c; }
            while(head<tail) {
                int c=queue[head++],cx=c%width,cy=c/width;
                int[] next={cx>0?c-1:-1,cx+1<width?c+1:-1,cy>0?c-width:-1,cy+1<height?c+width:-1};
                for(int n:next) if(n>=0 && distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; }
            }
            return distance;
        }
        void branch(String net,PcbPadPlacement start,PcbPadPlacement root) {
            branches++;
            boolean[] goal=goals(net,root); int[] distance=lowerBound(goal);
            HashMap<Integer,Node> best=new HashMap<Integer,Node>();
            PriorityQueue<Node> queue=new PriorityQueue<Node>();
            for(int layer=0;layer<2;layer++) if(layer==primary || policy==Policy.FULLER_TWO_LAYER)
                offer(queue,best,new Node(cell(start),layer,4,0,layer==primary?0:policy.secondaryCost,
                    distance[cell(start)],serial++,cells,null));
            int expanded=0; Node found=null;
            while(!queue.isEmpty()) {
                Node current=queue.poll(); if(best.get(current.key)!=current) continue;
                if((expanded%128)==0) observer.check(pass);
                if(budget.expanded>=budget.maximum) throw new Exhausted("TOTAL_SEARCH_LIMIT");
                if(expanded>=MAX_BRANCH_EXPANSIONS) throw new Exhausted("BRANCH_SEARCH_LIMIT");
                expanded++; budget.expanded++;
                if(goal[current.layer*cells+current.cell]) { found=current; break; }
                int cx=current.cell%width,cy=current.cell/width;
                int[] next={cy>0?current.cell-width:-1,cx+1<width?current.cell+1:-1,
                    cy+1<height?current.cell+width:-1,cx>0?current.cell-1:-1};
                for(int direction=0;direction<4;direction++) {
                    int n=next[direction]; if(n<0) continue;
                    if(!faces[current.layer].permitsLayerStep(x(current.cell),y(current.cell),x(n),y(n),net,start,root) ||
                            !rules.permits(net,PcbConductorBuilder.stroke(x(current.cell),y(current.cell),x(n),y(n)))) continue;
                    int cost=current.cost+GRID+(current.layer==primary?0:policy.secondaryCost)+
                        (current.direction!=4 && current.direction!=direction?35:0);
                    offer(queue,best,new Node(n,current.layer,direction,current.transitions,cost,
                        distance[n],serial++,cells,current));
                }
                if(current.direction!=4 && current.transitions<policy.transitions &&
                        pathViaFits(current) && viaFits(net,x(current.cell),y(current.cell)))
                    offer(queue,best,new Node(current.cell,1-current.layer,4,current.transitions+1,
                        current.cost+policy.viaCost,distance[current.cell],serial++,cells,current));
            }
            if(found==null) throw new Exhausted("NO_PATH");
            publishBranch(net,start,root,found);
        }
        void offer(PriorityQueue<Node> queue,HashMap<Integer,Node> best,Node node) {
            Node previous=best.get(node.key);
            if(previous!=null && previous.cost<=node.cost) return;
            best.put(node.key,node); queue.add(node);
        }
        boolean viaFits(String net,int x,int y) {
            if(policy.transitions==0) return false;
            for(PcbBoardHole hole:layout.getHoles())
                if(hole.x==x && hole.y==y) return net.equals(hole.netId);
            if(layout.getHoles().size()>=policy.vias) return false;
            Rectangle land=new Rectangle(x-PcbTwoLayerRules.VIA_LAND,y-PcbTwoLayerRules.VIA_LAND,
                PcbTwoLayerRules.VIA_LAND*2,PcbTwoLayerRules.VIA_LAND*2);
            if(!PcbTwoLayerRules.inside(layout.getBoardOutline(),land) || !rules.permits(net,land) ||
                    !faces[0].freeLayerCell(x,y,net) || !faces[1].freeLayerCell(x,y,net)) return false;
            for(PcbComponentPlacement part:parts)
                if(PcbConductorBuilder.touch(land,part.getRoutingCourtyard())) return false;
            Rectangle clearance=PcbTwoLayerRules.expand(land,PcbTraceRules.MIN_VISIBLE_CLEARANCE);
            for(PcbPadPlacement pad:pads)
                if(PcbConductorBuilder.touch(clearance,pad.getPadBounds())) return false;
            for(PcbBoardHole hole:layout.getHoles())
                if(PcbConductorBuilder.touch(clearance,hole.getBounds())) return false;
            return true;
        }
        boolean pathViaFits(Node current) {
            if(layout.getHoles().size()+current.transitions+1>policy.vias) return false;
            for(Node node=current;node.previous!=null;node=node.previous)
                if(node.layer!=node.previous.layer && Math.abs(x(node.cell)-x(current.cell))<20 &&
                        Math.abs(y(node.cell)-y(current.cell))<20) return false;
            return true;
        }
        void publishBranch(String net,PcbPadPlacement start,PcbPadPlacement root,Node end) {
            Vector<Node> nodes=new Vector<Node>();
            for(Node node=end;node!=null;node=node.previous) nodes.add(node);
            Collections.reverse(nodes);
            if(nodes.size()==1) return;
            int length=0,bends=0,lastDirection=4;
            for(int i=1;i<nodes.size();i++) {
                Node a=nodes.get(i-1),b=nodes.get(i);
                if(a.layer!=b.layer) { lastDirection=4; continue; }
                length+=GRID;
                if(lastDirection!=4 && lastDirection!=b.direction) bends++;
                lastDirection=b.direction;
            }
            int direct=Math.abs(start.getX()-x(end.cell))+Math.abs(start.getY()-y(end.cell));
            if(direct==0 || length>direct*3 || bends>16) throw new Exhausted("BRANCH_QUALITY_LIMIT");
            int first=0,piece=0;
            for(int i=1;i<=nodes.size();i++) {
                boolean transition=i<nodes.size() && nodes.get(i).layer!=nodes.get(i-1).layer;
                if(i!=nodes.size() && !transition) continue;
                Node begin=nodes.get(first),last=nodes.get(i-1);
                Vector<Point> path=new Vector<Point>();
                for(int j=first;j<i;j++) path.add(new Point(x(nodes.get(j).cell),y(nodes.get(j).cell)));
                if(path.size()>1) {
                    faces[begin.layer].occupyLayerPath(path,net);
                    Vector<Point> compact=new Vector<Point>();
                    for(Point point:path) {
                        int n=compact.size();
                        if(n>=2) {
                            Point a=compact.get(n-2),b=compact.get(n-1);
                            if(a.x==b.x && b.x==point.x || a.y==b.y && b.y==point.y) compact.remove(n-1);
                        }
                        compact.add(point);
                    }
                    int[] xs=new int[compact.size()],ys=new int[compact.size()];
                    for(int j=0;j<compact.size();j++) { xs[j]=compact.get(j).x; ys[j]=compact.get(j).y; }
                    String endPad=i==nodes.size() && end.cell==cell(root)?root.getPadId():null;
                    layout.addTrace(new PcbTraceGeometry("p07/"+net+"/"+branches+"/"+(piece++),net,
                        first==0?start.getPadId():null,endPad,PcbCopperLayer.values()[begin.layer],
                        PcbCopperAccess.Exposure.EXPOSED,xs,ys));
                    segments+=path.size()-1;
                }
                if(transition) {
                    int vx=x(last.cell),vy=y(last.cell); boolean exists=false;
                    for(PcbBoardHole hole:layout.getHoles()) if(hole.x==vx && hole.y==vy) exists=true;
                    if(!exists) layout.addHole(PcbTwoLayerRules.via("p07-via/"+net+"/"+branches+"/"+piece,net,vx,vy));
                    Vector<Point> point=new Vector<Point>(); point.add(new Point(vx,vy));
                    faces[0].occupyLayerPath(point,net); faces[1].occupyLayerPath(point,net);
                }
                first=i;
            }
        }
    }
    private PcbLayerRoutingPrototype() { }
}
