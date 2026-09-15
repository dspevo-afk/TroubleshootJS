package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Independent coordinate/clearance expectations; no canvas or solver doubles. */
public final class WorkbenchLiftedLeadAppearanceContractTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); checks++;
    }
    public static void main(String[] args) {
        PhysicalPackageGeometry resistor = resistor(8, 8);
        check(resistor.getBodyBounds().contains(resistor.getTerminal(0).getLiftedFreeEndPoint().x,
            resistor.getTerminal(0).getLiftedFreeEndPoint().y), "fixture independently reproduces the old hidden tip");
        WorkbenchLiftedLeadAppearance left = project(resistor, 0, pads(resistor), empty(), empty());
        check(left.getBodyPoint().equals(new Point(75,30)), "body attachment retained exactly");
        check(left.getBendPoint().equals(new Point(58,30)) && left.getTipPoint().equals(new Point(58,6)), "left resistor bends outward then up, clear of its old pad");
        check(left.getProbeBounds().equals(new Rectangle(54,2,8,8)), "probe is the original-size visible free tip");
        check(!left.getProbeBounds().intersects(resistor.getBodyBounds()) && left.getPadOverlaps() == 0, "tip is outside body and both board-pad probe surfaces");
        check(left.getPath().size() == 3 && left.getLeadSegments().size() == 2, "paint uses two real lead segments");
        Vector<Rectangle> obstruction = pads(resistor); obstruction.add(new Rectangle(53,1,10,10));
        WorkbenchLiftedLeadAppearance down = project(resistor,0,obstruction,empty(),empty());
        check(down.getTipPoint().equals(new Point(58,54)) && down.getPadOverlaps() == 0, "a pad at the preferred free tip selects the clear opposite bend");
        Vector<Rectangle> trace = empty(); trace.add(new Rectangle(53,1,10,10));
        check(project(resistor,0,pads(resistor),trace,empty()).getTipPoint().equals(new Point(58,54)), "a trace at the first tip is avoided when the opposite bend is clear");
        Vector<Rectangle> allTraces = empty(); allTraces.add(new Rectangle(-500,-500,2000,2000));
        WorkbenchLiftedLeadAppearance unavoidable = project(resistor,0,obstruction,allTraces,empty());
        check(unavoidable.getTipPoint().equals(new Point(58,54)) && unavoidable.getPadOverlaps() == 0 && unavoidable.getTraceOverlaps() == 1,
            "unavoidable trace overlap retains a pad-clear visible tip and reports its bounded approximation");
        // All space outside the first L-shaped lead corridor contains trace.
        // Its first tip is occupied by a pad: the clear-pad bend must win even
        // though the overlapping-pad candidate has strictly fewer trace hits.
        Vector<Rectangle> corridor = empty();
        corridor.add(new Rectangle(-500,-500,554,2000));
        corridor.add(new Rectangle(-500,-500,2000,502));
        corridor.add(new Rectangle(62,2,1000,25));
        corridor.add(new Rectangle(-500,33,2000,1000));
        corridor.add(new Rectangle(78,27,1000,6));
        check(project(resistor,0,pads(resistor),corridor,empty()).getTraceOverlaps() == 0, "independent trace corridor leaves the original up-bend clear");
        WorkbenchLiftedLeadAppearance priority = project(resistor,0,obstruction,corridor,empty());
        check(priority.getPadOverlaps() == 0 && priority.getTraceOverlaps() > 0,
            "pad clearance takes precedence over a trace-free candidate that overlaps a pad");
        Vector<Rectangle> neighbor = empty(); neighbor.add(new Rectangle(52,-2,12,16));
        check(project(resistor,0,pads(resistor),empty(),neighbor).getTipPoint().equals(new Point(58,54)), "neighboring component body does not hide the free tip");
        Collections.reverse(obstruction);
        check(project(resistor,0,obstruction,allTraces,empty()).getTipPoint().equals(unavoidable.getTipPoint()), "obstacle iteration order cannot affect the canonical candidate");
        Vector<Rectangle> crowded = empty(); crowded.add(new Rectangle(-500,-500,2000,2000));
        WorkbenchLiftedLeadAppearance fallback = project(resistor,0,crowded,allTraces,empty());
        check(fallback.getPadOverlaps() == 1 && !fallback.getProbeBounds().intersects(resistor.getBodyBounds()), "fully obstructed bounded fallback is explicit and does not return the hidden original tip");
        Point copy = left.getTipPoint(); copy.x = 999;
        Rectangle copyBounds = left.getProbeBounds(); copyBounds.width = 999;
        left.getPath().get(0).x = 999;
        check(left.getTipPoint().equals(new Point(58,6)) && left.getProbeBounds().width == 8 && left.getBodyPoint().x == 75,
            "appearance geometry is immutable across renderer consumers");

        int poses = 0;
        for (PhysicalPackageGeometry geometry : new PhysicalPackageGeometry[] {resistor, transistor(), relay()})
            for (int terminal = 0; terminal < geometry.getTerminalIds().size(); terminal++) {
                WorkbenchLiftedLeadAppearance base = project(geometry,terminal,pads(geometry),empty(),empty());
                for (PcbBoardSide side : PcbBoardSide.values()) for (PcbRotation rotation : PcbRotation.values()) {
                    PcbPackagePose pose = new PcbPackagePose(600,900,rotation,side);
                    Vector<Rectangle> boardPads = empty();
                    for (int i=0;i<geometry.getTerminalIds().size();i++) boardPads.add(geometry.placedAt(pose).getBoardPadProbeBounds(i));
                    WorkbenchLiftedLeadAppearance result = WorkbenchLiftedLeadAppearance.project(geometry,pose,terminal,boardPads,empty(),empty());
                    check(result.getTipPoint().equals(expected(base.getTipPoint(),geometry,rotation,side)), "rotation/mount reflection preserves chosen terminal tip");
                    check(result.getBendPoint().equals(expected(base.getBendPoint(),geometry,rotation,side)), "rotation/mount reflection preserves bend");
                    check(result.getPadOverlaps() == 0 && !result.getProbeBounds().intersects(geometry.placedAt(pose).getBodyBounds()), "two-, three- and five-terminal packages have visible pad-clear tips in every pose");
                    check(result.getProbeBounds().width == 8 && result.getProbeBounds().height == 8, "pose never inflates invisible probe target");
                    poses++;
                }
            }
        PhysicalPackageGeometry asymmetric = resistor(10,6);
        WorkbenchLiftedLeadAppearance rotated = WorkbenchLiftedLeadAppearance.project(asymmetric,
            new PcbPackagePose(600,900,PcbRotation.DEG_90,PcbBoardSide.BOTTOM),0,empty(),empty(),empty());
        check(rotated.getProbeBounds().width == 6 && rotated.getProbeBounds().height == 10, "non-square declared probe dimensions rotate without enlargement");
        check(resistor.getTerminal(0).getLiftedFreeEndPoint().equals(new Point(82,30)) && resistor.getTerminal(0).getPadCenter().equals(new Point(30,30)),
            "projection never mutates canonical package or board-pad geometry");
        System.out.println("PASS: lifted lead appearance contracts assertions=" + checks + " poses=" + poses);
    }
    private static Point expected(Point point, PhysicalPackageGeometry g, PcbRotation rotation, PcbBoardSide side) {
        int x = side == PcbBoardSide.TOP ? point.x : g.getWidth() - point.x, y = point.y;
        int rx = x, ry = y;
        if(rotation == PcbRotation.DEG_90) {rx=g.getHeight()-y;ry=x;}
        if(rotation == PcbRotation.DEG_180) {rx=g.getWidth()-x;ry=g.getHeight()-y;}
        if(rotation == PcbRotation.DEG_270) {rx=y;ry=g.getWidth()-x;}
        return new Point(600+rx,900+ry);
    }
    private static Vector<Rectangle> empty() { return new Vector<Rectangle>(); }
    private static WorkbenchLiftedLeadAppearance project(PhysicalPackageGeometry g,int terminal,
            Vector<Rectangle> pads,Vector<Rectangle> traces,Vector<Rectangle> bodies) {
        return WorkbenchLiftedLeadAppearance.project(g,PcbPackagePose.top(0,0),terminal,pads,traces,bodies);
    }
    private static Vector<Rectangle> pads(PhysicalPackageGeometry g) {
        Vector<Rectangle> result=empty();
        for(PhysicalPackageGeometry.Terminal t:g.getTerminals()) result.add(t.getBoardPadProbeBounds());
        return result;
    }
    private static PhysicalPackageGeometry resistor(int probeWidth,int probeHeight) {
        Vector<PhysicalPackageGeometry.Terminal> t=new Vector<PhysicalPackageGeometry.Terminal>();
        t.add(terminal("1",30,30,75,30,82,30,probeWidth,probeHeight));
        t.add(terminal("2",190,30,145,30,138,30,probeWidth,probeHeight));
        return geometry(220,70,t,new Rectangle(70,18,80,34));
    }
    private static PhysicalPackageGeometry transistor() {
        Vector<PhysicalPackageGeometry.Terminal> t=new Vector<PhysicalPackageGeometry.Terminal>();
        t.add(terminal("B",20,90,44,70,44,64,8,8));
        t.add(terminal("C",60,90,60,70,60,64,8,8));
        t.add(terminal("E",100,90,76,70,76,64,8,8));
        return geometry(130,125,t,new Rectangle(28,26,64,56));
    }
    private static PhysicalPackageGeometry relay() {
        Vector<PhysicalPackageGeometry.Terminal> t=new Vector<PhysicalPackageGeometry.Terminal>();
        t.add(terminal("A1",30,120,90,120,70,140,8,8));
        t.add(terminal("A2",270,120,210,120,230,140,8,8));
        t.add(terminal("COM",70,30,100,70,100,54,8,8));
        t.add(terminal("NC",150,30,150,70,150,54,8,8));
        t.add(terminal("NO",230,30,200,70,200,54,8,8));
        return geometry(300,210,t,new Rectangle(90,70,120,100));
    }
    private static PhysicalPackageGeometry geometry(int width,int height,Vector<PhysicalPackageGeometry.Terminal> t,Rectangle body) {
        return new PhysicalPackageGeometry(width,height,t,body,body,new Rectangle(-30,-30,width+60,height+60),
            new Rectangle(-40,-40,width+80,height+80),new Rectangle(-50,-50,width+100,height+100));
    }
    private static PhysicalPackageGeometry.Terminal terminal(String id,int px,int py,int bx,int by,int lx,int ly,int w,int h) {
        Point pad=new Point(px,py),body=new Point(bx,by),lift=new Point(lx,ly);
        return new PhysicalPackageGeometry.Terminal(id,pad,new Rectangle(px-13,py-13,26,26),pad,
            new Rectangle(px-15,py-15,30,30),lead(pad,body,body,w,h),lead(lift,body,lift,w,h),0,0,0);
    }
    private static PhysicalPackageGeometry.Lead lead(Point end,Point body,Point probe,int w,int h) {
        return new PhysicalPackageGeometry.Lead(end,body,new Rectangle(Math.min(end.x,body.x)-3,Math.min(end.y,body.y)-3,
            Math.abs(end.x-body.x)+6,Math.abs(end.y-body.y)+6),probe,new Rectangle(probe.x-w/2,probe.y-h/2,w,h));
    }
}
