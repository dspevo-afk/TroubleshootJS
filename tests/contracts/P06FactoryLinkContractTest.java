package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent P06 geometry/routing negatives. Native topology is not claimed as a solver result. */
public final class P06FactoryLinkContractTest {
    private static int checks;
    private static void require(boolean value, String reason) {
        checks++; if (!value) throw new AssertionError(reason);
    }
    private static void reject(Runnable action, String reason) {
        boolean rejected=false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected=true; }
        catch (IllegalStateException expected) { rejected=true; }
        require(rejected,reason);
    }
    public static void main(String[] args) {
        CirSim sim = new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        geometry(); backing(); policy(); routedNegatives(); construction(); matchedCorpus();
        System.out.println("PASS: P06 factory-link contracts assertions="+checks);
    }
    private static void geometry() {
        final PhysicalPackage pkg=PhysicalPackages.RAISED_FACTORY_LINK;
        final PhysicalPackageGeometry geometry=pkg.getGeometry();
        RaisedCrossoverGeometry declaration=geometry.getRaisedCrossover();
        require(!pkg.isEquivalentTo(PhysicalPackages.AXIAL_RESISTOR) && !pkg.isInternallyConnected("1","2"),
            "link is a real two-terminal component, not an ideal board-copper union");
        Rectangle copy=declaration.getUnderpass(); copy.x=0; copy.width=220;
        require(declaration.getUnderpass().equals(new Rectangle(92,5,36,60)),"declaration defensively copies geometry");
        require(geometry.isEquivalentTo(geometry.mirroredHorizontally().mirroredHorizontally()),"mirrored declaration round trip");
        final PhysicalPackageGeometry ordinary=PhysicalPackages.AXIAL_RESISTOR.getGeometry();
        PhysicalPackageGeometry taller=ordinary.withRaisedCrossover(new RaisedCrossoverGeometry(new Rectangle(92,5,36,60),18,2,2));
        require(!geometry.isEquivalentTo(taller),"height difference participates in geometry identity");
        for (PcbRotation rotation:pkg.getAllowedRotations()) for(PcbBoardSide side:pkg.getAllowedMountingSides()) {
            PcbPackagePose pose=new PcbPackagePose(400,400,rotation,side);
            PcbComponentPlacement placement=PcbComponentPlacement.fromPhysicalGeometry("FL",pose,pkg,geometry);
            require(placement.geometryFingerprint().contains("raisedCrossover="),"sealed identity records underpass dimensions and height");
            require(placement.getRoutingCourtyard().equals(pose.toBoardRectangle(ordinary.getRoutingCourtyard(),220,70)),
                "raising body never shrinks the physical placement courtyard");
            // Independent local-coordinate oracle includes all 9 units of swept copper, not its centerline alone.
            for(int x=60;x<=160;x++) {
                Rectangle stroke=pose.toBoardRectangle(new Rectangle(x-4,-5,9,90),220,70);
                boolean expected=x>=96 && x<=123;
                require(placement.permitsUnderpass(PcbCopperLayer.forFace(side),stroke)==expected,
                    "full copper width in all eight poses, local x="+x+" "+rotation+" "+side);
                require(!placement.permitsUnderpass(PcbCopperLayer.forFace(side.opposite()),stroke),
                    "opposite face gets no same-face underpass exception");
            }
            Rectangle moved=placement.translatedBy(20,30).getUnderpass(), original=placement.getUnderpass();
            require(moved.x==original.x+20 && moved.y==original.y+30 && moved.width==original.width && moved.height==original.height,
                "underpass follows immutable package translation");
        }
        reject(new Runnable(){public void run(){new RaisedCrossoverGeometry(new Rectangle(92,5,36,60),4,2,2);}},"no zero vertical clearance");
        reject(new Runnable(){public void run(){new RaisedCrossoverGeometry(new Rectangle(92,5,36,60),16,2,0);}},"no uninsulated crossover");
        reject(new Runnable(){public void run(){ordinary.withRaisedCrossover(new RaisedCrossoverGeometry(new Rectangle(20,5,160,60),16,2,2));}},
            "portal cannot include exposed pads or component leads");
        reject(new Runnable(){public void run(){ordinary.withRaisedCrossover(new RaisedCrossoverGeometry(new Rectangle(92,10,36,20),16,2,2));}},
            "portal must span opposite courtyard edges");
        reject(new Runnable(){public void run(){geometry.withRaisedCrossover(geometry.getRaisedCrossover());}},"cannot overwrite installed declaration");
        PhysicalPackageGeometry shortEnvelope=ordinary.withRaisedCrossover(new RaisedCrossoverGeometry(new Rectangle(92,5,36,60),16,1,2));
        PhysicalPackage shortPkg=new PhysicalPackage("TOO_SHORT_COPPER",pkg.getTerminalIds(),new Vector<String>(),false,shortEnvelope);
        PcbComponentPlacement insufficient=PcbComponentPlacement.fromPhysicalGeometry("X",400,400,shortPkg,shortEnvelope);
        require(!insufficient.permitsUnderpass(PcbCopperLayer.TOP,new Rectangle(506,400,9,80)),"declaration must support nominal copper height");
    }
    private static void backing() {
        final Vector<CircuitElm> backing=new Vector<CircuitElm>();
        final ResistorElm resistor=FactoryLinkSpecification.STANDARD.createElement(100,100); backing.add(resistor);
        FactoryLinkSpecification.requireBacking(FactoryLinkSpecification.STANDARD,PhysicalPackages.RAISED_FACTORY_LINK,backing);
        require(resistor.resistance==.05 && resistor.getClass()==ResistorElm.class,"actual finite resistor stamp");
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,1}) {
            resistor.resistance=invalid;
            reject(new Runnable(){public void run(){FactoryLinkSpecification.requireBacking(FactoryLinkSpecification.STANDARD,
                PhysicalPackages.RAISED_FACTORY_LINK,backing);}},"foreign or non-finite resistance rejected");
        }
        resistor.resistance=.05;
        reject(new Runnable(){public void run(){FactoryLinkSpecification.requireBacking(new BasicPhysicalSpecification("FAKE"),
            PhysicalPackages.RAISED_FACTORY_LINK,backing);}},"specification cannot be replaced with renamed resistor metadata");
        reject(new Runnable(){public void run(){FactoryLinkSpecification.requireBacking(FactoryLinkSpecification.STANDARD,
            PhysicalPackages.AXIAL_RESISTOR,backing);}},"ordinary package cannot claim factory link backing");
        final Vector<CircuitElm> fuseBacking=new Vector<CircuitElm>();
        fuseBacking.add(new ProtectionFuseElm(100,100));
        reject(new Runnable(){public void run(){new PhysicalServicePart("FORGED",new BasicPhysicalSpecification("FAKE"),
            new PhysicalNameplate("FORGED","Disguised fuse"),PhysicalPackages.RAISED_FACTORY_LINK,fuseBacking,
            new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,"FORGED"));}},
            "service inventory cannot smuggle another model inside a raised package");
        backing.clear(); backing.add(new WireElm(100,100));
        reject(new Runnable(){public void run(){FactoryLinkSpecification.requireBacking(FactoryLinkSpecification.STANDARD,
            PhysicalPackages.RAISED_FACTORY_LINK,backing);}},"ideal wire is not a supported link stamp");
    }
    private static PcbBoardLayout policyLayout(int links,int ordinary,int width,int height) {
        PcbBoardLayout layout=new PcbBoardLayout(2000,2000,new Rectangle(100,100,width,height),new Rectangle(1500,100,300,500));
        for(int i=0;i<links+ordinary;i++) {
            PhysicalPackage pkg=i<links?PhysicalPackages.RAISED_FACTORY_LINK:P06FactoryLinkFixtures.TEST_POINT;
            layout.addComponent(PcbComponentPlacement.fromPhysicalGeometry("P"+i,200,200+i*80,pkg,pkg.getGeometry()));
        }
        return layout;
    }
    private static void policy() {
        require(PcbFactoryLinkPolicy.MAX_LINKS==2 && PcbFactoryLinkPolicy.MAX_CANDIDATES==32 &&
            PcbFactoryLinkPolicy.ADDED_ROUTE_COST==400,"explicit finite prototype policy proposal");
        for(int i=0;i<32;i++) PcbFactoryLinkPolicy.requireCandidateIndex(i);
        reject(new Runnable(){public void run(){PcbFactoryLinkPolicy.requireCandidateIndex(32);}},"candidate cap enforced");
        reject(new Runnable(){public void run(){PcbFactoryLinkPolicy.requireCandidateIndex(-1);}},"negative candidate rejected");
        require(PcbFactoryLinkPolicy.validateLayout(policyLayout(2,12,1000,1000))==2,"two links need enough area and supporting parts");
        reject(new Runnable(){public void run(){PcbFactoryLinkPolicy.validateLayout(policyLayout(3,40,1000,1000));}},"absolute link cap");
        reject(new Runnable(){public void run(){PcbFactoryLinkPolicy.validateLayout(policyLayout(2,11,1000,1000));}},"physical count density cap");
        reject(new Runnable(){public void run(){PcbFactoryLinkPolicy.validateLayout(policyLayout(1,20,200,200));}},"area density cap");
    }
    private static void routedNegatives() {
        P06FactoryLinkFixtures.Fixture good=new P06FactoryLinkFixtures.Fixture(true,0,0); good.route();
        good.layout.validateGeometry(good.board);
        require(good.layout.getTraces().size()==3,"three copper routes with an elevated component between the outer nets");
        PcbConductorGraph.Snapshot copper=good.layout.captureConductorGraph(good.board).pristine();
        require(copper.padsConnected("L.1","FL1.1") && copper.padsConnected("R.1","FL1.2") && copper.padsConnected("A.1","B.1"),
            "all intended physical copper endpoints connected");
        require(!copper.padsConnected("A.1","FL1.1") && !copper.padsConnected("A.1","FL1.2") && !copper.padsConnected("FL1.1","FL1.2"),
            "elevated component and underpass never merge board copper");
        final P06FactoryLinkFixtures.Fixture disguised=new P06FactoryLinkFixtures.Fixture(false,0,0);
        for(PcbTraceGeometry trace:good.layout.getTraces()) disguised.layout.addTrace(trace);
        reject(new Runnable(){public void run(){disguised.layout.validateRoutingGeometry(disguised.board);}},
            "independent validator rejects the same path under an ordinary axial body");
        final P06FactoryLinkFixtures.Fixture narrow=new P06FactoryLinkFixtures.Fixture(true,0,0);
        for(PcbTraceGeometry trace:good.layout.getTraces()) {
            if("UNDER".equals(trace.getNetId()))
                trace=trace.withPath(new int[]{310,294,294,310},new int[]{150,150,450,450});
            narrow.layout.addTrace(trace);
        }
        reject(new Runnable(){public void run(){narrow.layout.validateRoutingGeometry(narrow.board);}},
            "inside centerline but overlapping copper stroke rejected");
        good.layout.seal(); final PcbBoardLayout sealed=good.layout;
        require(good.layout.geometryFingerprint().equals(good.layout.copySealed().geometryFingerprint()),"frozen underpass copies preserve identity");
        reject(new Runnable(){public void run(){sealed.addComponent(PcbComponentPlacement.fromPhysicalGeometry("LATE",200,200,
            PhysicalPackages.RAISED_FACTORY_LINK,PhysicalPackages.RAISED_FACTORY_LINK.getGeometry()));}},"no late insert into already-proved layout");
    }
    private static void construction() {
        final P06FactoryLinkPrototype prototype=new P06FactoryLinkPrototype();
        final GeneratedBoardInstance owner=prototype.instance;
        PhysicalPart<?> link=owner.getPhysicalBoardRuntime().getInstalledPart("FL1");
        require(link instanceof PhysicalServicePart && ((PhysicalServicePart)link).isFactoryLink(),"construction creates real service ownership");
        require(owner.getConnectionBindings().getForComponent("FL1").size()==2 && owner.getPcbLayout().isSealed(),"two detachable leads and frozen board owner");
        require(owner.getPhysicalBoardRuntime().getScopedMutationCapability("FL1")!=null,"link has the production scoped mutation provider");
        reject(new Runnable(){public void run(){PcbFactoryLinkPolicy.validateConstruction(owner.getBoard(),owner.getPcbLayout(),
            owner.getPhysicalSpecifications(),owner.getComponentBindings(),false);}},"factory link cannot enter normal construction before P09");
        reject(new Runnable(){public void run(){owner.getDiagnosticSolvabilityContract().validate(owner);}},"developer evidence is not normal diagnostic proof");
        GeneratedBoardInstance repeated=new P06FactoryLinkPrototype().instance;
        require(owner.getPcbLayout().geometryFingerprint().equals(repeated.getPcbLayout().geometryFingerprint()),"deterministic fresh physical realization");
        require(owner.getPhysicalBoardRuntime().getInstalledPart("FL1")!=repeated.getPhysicalBoardRuntime().getInstalledPart("FL1"),"fresh graph never reuses mutable link identity");
    }
    private static long length(PcbBoardLayout layout) {
        long length=0;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++) length+=Math.abs(x[i]-x[i-1])+Math.abs(y[i]-y[i-1]);
        }
        return length;
    }
    private static void matchedCorpus() {
        for(int shift:new int[]{-20,0,20}) {
            P06FactoryLinkFixtures.Fixture raised=new P06FactoryLinkFixtures.Fixture(true,0,shift); raised.route();
            raised.layout.validateGeometry(raised.board);
            P06FactoryLinkFixtures.Fixture repeated=new P06FactoryLinkFixtures.Fixture(true,0,shift); repeated.route();
            require(raised.layout.geometryFingerprint().equals(repeated.layout.geometryFingerprint()),"matched raised routing deterministic");
            P06FactoryLinkFixtures.Fixture copperOnly=null;
            int expansion=-1, attempts=0, rejected=0;
            for(int width:new int[]{0,10,20,30,40,60,80}) {
                P06FactoryLinkFixtures.Fixture control=new P06FactoryLinkFixtures.Fixture(false,width,shift,true); attempts++;
                try { control.route(); copperOnly=control; expansion=width; break; }
                catch(PcbNetRouter.Rejected expected) {
                    rejected++; require(control.layout.getTraces().isEmpty(),"failed no-link controls publish no partial route");
                }
            }
            require(copperOnly!=null,"true no-link matched control actually routes within frozen envelope");
            copperOnly.layout.validateGeometry(copperOnly.board);
            require(raised.board.getComponentIds().size()==copperOnly.board.getComponentIds().size()+1,"link costs one real physical component");
            for(String id:new String[]{"L.1","R.1","A.1","B.1","G.1"}) {
                PcbPadPlacement a=raised.layout.getPad(id),b=copperOnly.layout.getPad(id);
                require(a.getX()==b.getX() && a.getY()==b.getY(),"matched external endpoints have identical coordinates");
            }
            Rectangle a=raised.layout.getBoardOutline(),b=copperOnly.layout.getBoardOutline();
            System.out.println("P06_MATCH shift="+shift+" linkParts="+raised.board.getComponentIds().size()+
                " noLinkParts="+copperOnly.board.getComponentIds().size()+" linkArea="+(a.width*a.height)+
                " noLinkArea="+(b.width*b.height)+" firstPassingExpansion="+expansion+
                " noLinkAttempts="+attempts+" noLinkRejections="+rejected+
                " linkCopperLength="+length(raised.layout)+" noLinkCopperLength="+length(copperOnly.layout)+
                " linkExpansions="+raised.layout.getRoutingExpansions()+" noLinkExpansions="+copperOnly.layout.getRoutingExpansions());
        }
    }
}
