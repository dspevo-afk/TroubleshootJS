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
    private static void check(boolean value,String reason) {checks++;if(!value)throw new AssertionError(reason);}
}
