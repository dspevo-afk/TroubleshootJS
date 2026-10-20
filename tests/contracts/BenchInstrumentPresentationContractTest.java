package com.lushprojects.circuitjs1.client;

/** Literal small/wide/tall scenes and canonical package pins, independent of UI rendering. */
public final class BenchInstrumentPresentationContractTest {
    private static int checks;
    public static void main(String[] args) {
        home(new Rectangle(40,20,800,400), new Rectangle(-244,20,252,454));
        home(new Rectangle(0,0,3571,2071), new Rectangle(-971,259,862,1553));
        home(new Rectangle(0,0,3600,1284), new Rectangle(-675,102,599,1080));
        home(new Rectangle(100,-50,3571,2071), new Rectangle(-871,209,862,1553));
        pins(PhysicalPackages.TO220_REGULATOR_4,
            new String[]{"INPUT","OUTPUT","RETURN","ENABLE"}, new int[]{50,90,130,170}, new int[]{150,150,150,150});
        pins(PhysicalPackages.E04_DECISION_CONTROL_5,
            new String[]{"SENSOR","REFERENCE","RAIL","OUTPUT","RETURN"}, new int[]{30,80,130,180,230}, new int[]{150,150,150,150,150});
        pins(PhysicalPackages.OPTOCOUPLER_4,
            new String[]{"A","K","C","E"}, new int[]{30,30,310,310}, new int[]{50,130,50,130});
        pins(PhysicalPackages.ISOLATED_CONVERTER_7,
            new String[]{"IN+","IN-","PRE_L+","OUT-","EN","FB","BIAS"},
            new int[]{30,30,390,390,30,30,30}, new int[]{50,100,110,210,150,200,250});
        reference(PhysicalPackages.TO220_REGULATOR_4, "INPUT", new Rectangle(45,83,10,10));
        reference(PhysicalPackages.E04_DECISION_CONTROL_5, "SENSOR", new Rectangle(32,83,10,10));
        reference(PhysicalPackages.OPTOCOUPLER_4, "A", new Rectangle(97,45,10,10));
        reference(PhysicalPackages.ISOLATED_CONVERTER_7, "IN+", new Rectangle(97,45,10,10));
        for (PhysicalPackage p : new PhysicalPackage[]{PhysicalPackages.AXIAL_RESISTOR,
                PhysicalPackages.RELAY_SPDT,PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                PhysicalPackages.TO92_NPN,PhysicalPackages.E05_ISOLATION_TRANSFORMER_4}) {
            check(!ChipPinout.supports(p), "ordinary parts keep existing annotations and menu");
            try { ChipPinout.geometry(p); throw new AssertionError("nonchip accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        check(!ChipPinout.supports(null), "no missing-part pinout");
        System.out.println("PASS: bench instrument presentation contracts assertions="+checks);
    }
    private static void home(Rectangle board,Rectangle expected) {
        Rectangle actual=BenchInstrumentLayout.meterHome(board);
        check(actual.x==expected.x && actual.y==expected.y && actual.width==expected.width && actual.height==expected.height,
            "literal board-relative meter home");
        for (PcbBoardSide face:PcbBoardSide.values()) {
            PcbViewport camera=new PcbViewport(board);camera.resize(new Rectangle(0,0,1440,858));camera.setFace(face);
            camera.fitWorkbench(board.union(actual),24);
            PcbViewport.Transform t=camera.permanent();
            Rectangle m=new PcbViewport.Transform(t.scale,t.x,t.y,0,false).project(actual), b=t.project(board);
            check(m.x>=19 && m.x+m.width<b.x && m.y>=19 && m.y+m.height<=815,
                "full meter fits above drawer on either face without overlapping PCB");
            check(b.x>=19 && b.x+b.width<=1421 && b.y>=19 && b.y+b.height<=815,
                "full board remains fitted");
        }
    }
    private static void pins(PhysicalPackage p,String[] names,int[] xs,int[] ys) {
        check(ChipPinout.supports(p), "supported public chip family");
        PhysicalPackageGeometry g=ChipPinout.geometry(p);
        check(g.getTerminals().size()==names.length,"no invented or omitted pins");
        for(int i=0;i<names.length;i++) {
            PhysicalPackageGeometry.Terminal pin=g.getTerminal(i);Point pad=pin.getPadCenter();
            check(pin.getTerminalId().equals(names[i]) && pad.x==xs[i] && pad.y==ys[i],
                "public name is associated with its declared physical lead");
        }
    }
    private static void reference(PhysicalPackage p,String firstName,Rectangle expected) {
        PhysicalPackageGeometry g=ChipPinout.geometry(p);
        Rectangle mark=ChipPinout.referenceMark(g);
        rect(mark,expected,"literal first-terminal body mark");
        check(g.getTerminal(0).getTerminalId().equals(firstName),"reference names declared terminal0");
        contained(mark,g.getBodyBounds(),"reference ink stays inside inert body");
        Point center=new Point(mark.x+mark.width/2,mark.y+mark.height/2);
        Point first=g.getTerminal(0).getConnectedLead().getBodyPoint();
        for(int i=1;i<g.getTerminals().size();i++)
            check(distance(center,first)<distance(center,g.getTerminal(i).getConnectedLead().getBodyPoint()),
                "reference is nearest its named lead, not vector sorting or a screen corner");
        for(PcbRotation rotation:PcbRotation.values()) for(PcbBoardSide mount:PcbBoardSide.values()) {
            PcbPackagePose pose=new PcbPackagePose(700,900,rotation,mount);
            Rectangle projected=pose.toBoardRectangle(mark,g.getWidth(),g.getHeight());
            Rectangle oracle=P01PhysicalPoseChecks.expectedRectangle(expected,g.getWidth(),g.getHeight(),
                700,900,rotation,mount);
            rect(projected,oracle,"reference follows independent affine pose oracle");
            contained(projected,g.placedAt(pose).getBodyBounds(),"posed reference remains inside body");
            for(PcbBoardSide view:PcbBoardSide.values()) {
                PcbViewport.Transform camera=new PcbViewport.Transform(2,7,11,2000,view==PcbBoardSide.BOTTOM);
                int x=7+2*(view==PcbBoardSide.TOP?oracle.x:2000-oracle.x-oracle.width);
                rect(camera.project(projected),new Rectangle(x,11+2*oracle.y,oracle.width*2,oracle.height*2),
                    "view reflection preserves reference orientation");
            }
        }
        // These four canonical packages have no tray quarter-turn or polarity reversal.
        LoosePartPose loose=LoosePartPose.forCell(p,null,new Rectangle(100,200,500,250));
        check(!loose.isPolarityMirrored() && loose.getQuarterTurn()==LoosePartPose.QuarterTurn.NONE,
            "canonical public chip tray pose is truthful");
        double scale=loose.getScale();Point translation=loose.getTranslation();
        int x=(int)Math.floor(expected.x*scale+translation.x);
        int y=(int)Math.floor(expected.y*scale+translation.y);
        int right=(int)Math.ceil((expected.x+expected.width)*scale+translation.x);
        int bottom=(int)Math.ceil((expected.y+expected.height)*scale+translation.y);
        rect(loose.transformRectangle(mark),new Rectangle(x,y,right-x,bottom-y),
            "loose reference uses existing rigid tray pose and outward bounds");
        contained(loose.transformRectangle(mark),loose.getBodyBounds(),"tray reference stays on body");
        mark.x=-999;mark.width=999;
        rect(ChipPinout.referenceMark(g),expected,"returned ink rectangle owns no mutable package state");
        check(g.getTerminal(0).getTerminalId().equals(firstName),"reference does not mutate terminal order");
    }
    private static long distance(Point a,Point b) {long x=a.x-b.x,y=a.y-b.y;return x*x+y*y;}
    private static void rect(Rectangle a,Rectangle b,String reason) {
        check(a.x==b.x && a.y==b.y && a.width==b.width && a.height==b.height,reason);
    }
    private static void contained(Rectangle a,Rectangle b,String reason) {
        check(a.x>=b.x && a.y>=b.y && a.x+a.width<=b.x+b.width && a.y+a.height<=b.y+b.height,reason);
    }
    private static void check(boolean value,String reason) {checks++;if(!value)throw new AssertionError(reason);}
}
