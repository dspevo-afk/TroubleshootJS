package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Shared JVM/GWT boundary tests; never grants production admission. */
final class P09EnvelopeChecks {
    static int verifyColdAdmission(CirSim sim) {
        int checks=0;
        for(String family:new String[]{"LED_INDICATOR","RC_DELAY","RELAY_OUTPUT"}) {
            GeneratedBoardInstance owner=GenerationRequest.leaf(family,0,false)
                .resolve(new GenerationRequest.PlanCache()).construct().instance;
            try {
                if(GeneratedChallengeController.requiresInitialHealthCheck(owner,false))
                    throw new IllegalStateException("Non-healthy phase requested startup health check");
                if(GeneratedChallengeController.requiresInitialHealthCheck(owner,true)!=(owner.getTemporalBehavior()==null))
                    throw new IllegalStateException("Cold temporal output judged before startup profile: "+family);
                checks+=2;
            } finally { for(CircuitElm element:owner.getSimulationElements()) element.delete(); }
        }
        return checks;
    }
    private int assertions;
    private final SupportedEnvelope envelope=SupportedEnvelope.current();
    static int verify(GeneratedBoardInstance owner) { return new P09EnvelopeChecks().run(owner); }
    private void check(boolean value,String message) {
        assertions++; if(!value) throw new IllegalStateException("P09: "+message);
    }
    private void rejects(SupportedEnvelope.Reason reason,Runnable action) {
        try { action.run(); throw new IllegalStateException("P09 accepted negative "+reason); }
        catch(SupportedEnvelope.Rejected expected) { check(expected.reason==reason,"wrong rejection reason"); }
    }
    private int run(GeneratedBoardInstance owner) {
        final TroubleshootBoard board=owner.getBoard(); final PcbBoardLayout layout=owner.getPcbLayout();
        String untouched=layout.geometryFingerprint(); envelope.requireNormal(owner); assertions++;
        check(SupportedEnvelope.resolve(SupportedEnvelope.ID,SupportedEnvelope.VERSION)==envelope,"registry identity");
        rejects(SupportedEnvelope.Reason.UNKNOWN_ENVELOPE,new Runnable(){public void run(){SupportedEnvelope.resolve("anything",1);}});
        rejects(SupportedEnvelope.Reason.UNKNOWN_ENVELOPE,new Runnable(){public void run(){SupportedEnvelope.resolve(SupportedEnvelope.ID,0);}});
        rejects(SupportedEnvelope.Reason.MISSING_LAYOUT,new Runnable(){public void run(){envelope.requireBounds(board,null);}});
        envelope.requireBoardSize(new Rectangle(0,0,2048,1000));
        envelope.requireBoardSize(new Rectangle(0,0,1500,1500));
        envelope.requireBoardSize(new Rectangle(0,0,384,128)); assertions+=3;
        for(final Rectangle r:new Rectangle[]{new Rectangle(0,0,2049,1000),new Rectangle(0,0,1500,1501),
                new Rectangle(0,0,385,128),new Rectangle(0,0,128,385),new Rectangle(0,0,127,128)})
            rejects(SupportedEnvelope.Reason.BOARD_SIZE,new Runnable(){public void run(){envelope.requireBoardSize(r);}});
        for(PcbPadPlacement pad:layout.getPads()) { envelope.requireAccess(pad); assertions++; }
        final PcbPadPlacement pad=layout.getPads().firstElement();
        for(final PcbPadPlacement bad:new PcbPadPlacement[]{alterPad(pad,25,30,PcbCopperAccess.Exposure.EXPOSED),
                alterPad(pad,26,29,PcbCopperAccess.Exposure.EXPOSED),alterPad(pad,26,30,PcbCopperAccess.Exposure.COVERED)})
            rejects(SupportedEnvelope.Reason.ACCESS_FLOOR,new Runnable(){public void run(){envelope.requireAccess(bad);}});
        for(int count:new int[]{1,3}) {
            final PcbBoardLayout holes=copy(layout,null,null,false);
            for(int i=0;i<count;i++) holes.addHole(PcbTwoLayerRules.via("unqualified-via-"+i,board.getNetIds().firstElement(),60+20*i,60));
            rejects(SupportedEnvelope.Reason.HOLE_POLICY,new Runnable(){public void run(){envelope.requireBounds(board,holes);}});
        }
        PcbCopperLayer visible=board.getPlacementConstraints().routingLayer;
        final PcbCopperLayer other=visible==PcbCopperLayer.TOP?PcbCopperLayer.BOTTOM:PcbCopperLayer.TOP;
        final PcbBoardLayout mixed=copy(layout,null,null,false);
        Vector<PcbTraceGeometry> wrong=new Vector<PcbTraceGeometry>(mixed.getTraces());
        PcbTraceGeometry t=wrong.firstElement();
        wrong.set(0,new PcbTraceGeometry(t.getSourceId(),t.getNetId(),t.getStartPadId(),t.getEndPadId(),
            other,t.getExposure(),t.getXPoints(),t.getYPoints())); mixed.replaceTraces(wrong);
        rejects(SupportedEnvelope.Reason.LAYER_POLICY,new Runnable(){public void run(){envelope.requireBounds(board,mixed);}});
        final PcbBoardLayout covered=copy(layout,null,null,false);
        Vector<PcbTraceGeometry> masked=covered.getTraces();
        masked.set(0,new PcbTraceGeometry(t.getSourceId(),t.getNetId(),t.getStartPadId(),t.getEndPadId(),
            visible,PcbCopperAccess.Exposure.COVERED,t.getXPoints(),t.getYPoints())); covered.replaceTraces(masked);
        rejects(SupportedEnvelope.Reason.ACCESS_FLOOR,new Runnable(){public void run(){envelope.requireBounds(board,covered);}});
        for(PcbTraceGeometry trace:layout.getTraces()) {
            check(!PcbCopperAccess.canProbe(trace.getLayer(),trace.getExposure(),other.getFace()),"hidden copper not ordinarily selectable");
            check(PcbCopperAccess.canProbe(trace.getLayer(),trace.getExposure(),visible.getFace()),"visible copper target retained");
        }
        for(PhysicalPackage replacement:new PhysicalPackage[]{PhysicalPackages.DEV_SMD_0805,PhysicalPackages.RAISED_FACTORY_LINK}) {
            PcbComponentPlacement old=layout.getComponents().firstElement();
            PcbComponentPlacement changed=PcbComponentPlacement.fromPhysicalGeometry(old.getComponentId(),old.getX(),old.getY(),replacement,replacement.getGeometry());
            final PcbBoardLayout altered=copy(layout,changed,null,false);
            rejects(replacement==PhysicalPackages.RAISED_FACTORY_LINK?SupportedEnvelope.Reason.LINK_POLICY:SupportedEnvelope.Reason.PACKAGE_MIX,
                new Runnable(){public void run(){envelope.requireBounds(board,altered);}});
        }
        final PcbBoardLayout narrow=copy(layout,null,alterPad(pad,25,30,PcbCopperAccess.Exposure.EXPOSED),false);
        rejects(SupportedEnvelope.Reason.ACCESS_FLOOR,new Runnable(){public void run(){envelope.requireBounds(board,narrow);}});
        for(final int[] work:new int[][]{{81,1,1},{1,801,1},{1,1,160000001},{-1,0,0}}) {
            final PcbBoardLayout exhausted=copy(layout,null,null,false);
            exhausted.setGenerationStatistics(work[0],work[1],work[2],0,0,0);
            rejects(SupportedEnvelope.Reason.WORK_BUDGET,new Runnable(){public void run(){envelope.requireBounds(board,exhausted);}});
        }
        final PcbBoardLayout excessive=copy(layout,null,null,false);
        Vector<PcbTraceGeometry> repeated=excessive.getTraces();
        for(int i=0;i<161;i++) repeated.add(t); excessive.replaceTraces(repeated);
        rejects(SupportedEnvelope.Reason.ROUTE_BUDGET,new Runnable(){public void run(){envelope.requireBounds(board,excessive);}});
        PcbBoardLayout disconnected=copy(layout,null,null,true);
        envelope.requireBounds(board,disconnected); // A bounds check is deliberately not a connectivity certificate.
        boolean geometryRejected=false;
        try { disconnected.validateGeometry(board); } catch(RuntimeException expected) { geometryRejected=true; }
        check(geometryRejected,"independent physical truth must reject missing copper despite passing size/count bounds");
        check(untouched.equals(layout.geometryFingerprint()),"negative tests never mutate the original layout");
        check(envelope.canonical().contains("hiddenCopperSelectable=false"),"view policy bound into proof dependencies");
        return assertions;
    }
    private static PcbPadPlacement alterPad(PcbPadPlacement pad,int land,int probe,PcbCopperAccess.Exposure exposure) {
        return new PcbPadPlacement(pad.getPadId(),pad.getX(),pad.getY(),pad.getEscapeDx(),pad.getEscapeDy(),pad.getEscapeLength(),
            new Rectangle(pad.getX()-land/2,pad.getY()-land/2,land,land),
            new Rectangle(pad.getX()-probe/2,pad.getY()-probe/2,probe,probe),pad.getAttachment(),pad.getMountingSide(),exposure);
    }
    static PcbBoardLayout copy(PcbBoardLayout source,PcbComponentPlacement replacement,PcbPadPlacement pad,boolean omitCopper) {
        PcbBoardLayout result=new PcbBoardLayout(source.getWidth(),source.getHeight(),source.getBoardOutline(),source.getPartsTray(),source.getLayoutAlgorithmVersion());
        for(PcbComponentPlacement p:source.getComponents()) result.addComponent(replacement!=null && replacement.getComponentId().equals(p.getComponentId())?replacement:p);
        for(PcbPadPlacement p:source.getPads()) result.addPad(pad!=null && pad.getPadId().equals(p.getPadId())?pad:p);
        for(PcbSilkscreenLabel label:source.getSilkscreenLabels()) result.addSilkscreenLabel(label);
        for(PcbLayoutRegion region:source.getRegions()) result.addRegion(region);
        for(PcbBoardHole hole:source.getHoles()) result.addHole(hole);
        if(!omitCopper) for(PcbTraceGeometry trace:source.getTraces()) result.addTrace(trace);
        return result;
    }
}
