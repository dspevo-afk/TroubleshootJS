package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Literal relay courtyards and declared isolation sides, independent of names. */
public final class PcbVacancyDomainContractTest {
    private static int checks;
    public static void main(String[] args) {
        TroubleshootBoard board=new TroubleshootBoard("vacancy-domain-fixture");
        add(board,"L",PhysicalPackages.RELAY_SPDT);add(board,"R",PhysicalPackages.RELAY_SPDT);
        add(board,"S",PhysicalPackages.RELAY_SPDT);add(board,"M",PhysicalPackages.OPTOCOUPLER_4);
        Rectangle outline=new Rectangle(0,0,2000,1000);
        PcbFootprint source=PcbFootprint.fromPhysicalPackage(board.getComponent("S"),0,0);
        PcbFootprint left=PcbFootprint.fromPhysicalPackage(board.getComponent("L"),100,100);
        PcbFootprint right=PcbFootprint.fromPhysicalPackage(board.getComponent("R"),1000,100);
        Vector<PcbFootprint> peers=new Vector<PcbFootprint>();peers.add(left);peers.add(right);
        PcbPlacementConstraints primary=constraints(false,false);
        check(primary.getBarriers().get(0).firstDomain.equals("A_FOREIGN"),"barrier is lexically sorted");
        same(PcbPlacementPlanner.vacancyDomainBox(source,peers,primary,outline),10,10,920,980,
            "left domain uses foreign courtyard1010 minus80, not lexically first domain");
        same(PcbPlacementPlanner.vacancyDomainBox(source,peers,constraints(false,true),outline),10,10,920,980,
            "barrier declaration order cannot affect advisory side");
        same(PcbPlacementPlanner.vacancyDomainBox(source,peers,constraints(true,false),outline),470,10,1520,980,
            "right domain uses left courtyard end390 plus80");
        Vector<PcbFootprint> vertical=new Vector<PcbFootprint>();vertical.add(left);
        vertical.add(right.translated(100,600));
        same(PcbPlacementPlanner.vacancyDomainBox(source,vertical,primary,outline),10,10,1980,520,
            "upper domain follows the actual vertical axis");
        same(PcbPlacementPlanner.vacancyDomainBox(source,vertical,constraints(true,false),outline),10,370,1980,620,
            "lower domain preserves vertical clearance");
        Vector<PcbFootprint> foreignOnly=new Vector<PcbFootprint>();foreignOnly.add(right);
        check(PcbPlacementPlanner.vacancyDomainBox(source,foreignOnly,primary,outline)==null,
            "unestablished isolated side gains no invented target");
        Vector<PcbFootprint> ownOnly=new Vector<PcbFootprint>();ownOnly.add(left);
        same(PcbPlacementPlanner.vacancyDomainBox(source,ownOnly,primary,outline),10,10,1980,980,
            "missing foreign side leaves normal board margin");
        Vector<PcbFootprint> invalid=new Vector<PcbFootprint>();invalid.add(left);invalid.add(right.translated(100,100));
        check(PcbPlacementPlanner.vacancyDomainBox(source,invalid,primary,outline)==null,
            "overlapping isolated courtyards do not establish an axis");
        PcbFootprint mixed=PcbFootprint.fromPhysicalPackage(board.getComponent("M"),500,100);
        check(PcbPlacementPlanner.vacancyDomainBox(mixed,peers,primary,outline)==null,
            "mixed-domain body remains on its established boundary placement path");
        String prior=mixed.geometryFingerprint();peers.add(mixed);
        same(PcbPlacementPlanner.vacancyDomainBox(source,peers,primary,outline),10,10,635,980,
            "mixed peer contributes actual foreign terminal courtyard725, not entire inert body");
        check(prior.equals(mixed.geometryFingerprint()) && outline.width==2000 && source.getPlacement().getX()==0,
            "advisory clipping is pure");
        Rectangle occupied=new Rectangle(-20,40,100,60),pocket=new Rectangle(50,20,80,100);
        check(PcbPlacementPlanner.vacancyOverlapArea(occupied,pocket)==1800,
            "literal30-by60 envelope occupancy, independent of center proximity");
        check(PcbPlacementPlanner.vacancyOverlapArea(new Rectangle(-50,20,100,100),pocket)==0,
            "touching pocket boundary reserves no pocket area");
        check(PcbPlacementPlanner.vacancyOverlapArea(new Rectangle(60,50,20,10),pocket)==200,
            "fully contained envelope reserves exactly its own area");
        System.out.println("PASS: PCB vacancy domain contracts assertions="+checks);
    }
    private static PcbPlacementConstraints constraints(boolean sourceForeign,boolean reverse) {
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>();
        for(String id:new String[]{"L","R","S"})parts.add(new PcbPlacementConstraints.Part(id,"region","region",
            id.equals("R") || id.equals("S") && sourceForeign?"A_FOREIGN":"Z_OWN",PcbPlacementConstraints.Anchor.NONE,20));
        Vector<PcbPlacementConstraints.TerminalDomain> terminals=new Vector<PcbPlacementConstraints.TerminalDomain>();
        for(String terminal:new String[]{"A","K","C","E"})terminals.add(new PcbPlacementConstraints.TerminalDomain(
            terminal,terminal.equals("A") || terminal.equals("K")?"Z_OWN":"A_FOREIGN"));
        parts.add(new PcbPlacementConstraints.Part("M","region","region","Z_OWN",
            PcbPlacementConstraints.Anchor.NONE,20,terminals));
        Vector<PcbPlacementConstraints.Barrier> barriers=new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier(reverse?"A_FOREIGN":"Z_OWN",reverse?"Z_OWN":"A_FOREIGN",80));
        return new PcbPlacementConstraints(parts,barriers);
    }
    private static void add(TroubleshootBoard board,String id,PhysicalPackage physical) {
        BoardComponent component=new BoardComponent(id,"fixture",physical);board.addComponent(component);
        for(String terminal:physical.getTerminalIds()) {
            String net=id+"_"+terminal;board.addNet(new BoardNet(net));
            board.addPad(new BoardPad(id+"."+terminal,id,terminal,net));
        }
    }
    private static void same(Rectangle actual,int x,int y,int width,int height,String why) {
        check(actual!=null && actual.x==x && actual.y==y && actual.width==width && actual.height==height,why);
    }
    private static void check(boolean ok,String why) {checks++;if(!ok)throw new AssertionError(why);}
}
