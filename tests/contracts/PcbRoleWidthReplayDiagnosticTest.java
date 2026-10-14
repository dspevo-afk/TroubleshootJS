package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Measurement-only width substitution on frozen saved P07 placement geometry. */
public final class PcbRoleWidthReplayDiagnosticTest {
    private static final long DEFAULT_SEED=-1022L;
    private static final String NEG1022_SHA=
        "1216897c60853a35008c3d2fd62965d6489ad997c2e3a62cb578b081dedd5be9";
    private static final String SEED42_SHA=
        "df2494c31141ac8c6ccb5065454ba70e2190d29d9690a195f518c1c268c5fb01";
    private PcbRoleWidthReplayDiagnosticTest() { }

    public static void main(String[] args) {
        if(args.length==0) {
            runSavedPlacement(DEFAULT_SEED);
            runSavedPlacement(42L);
        } else for(String value:args) runSavedPlacement(Long.parseLong(value));
        pointGoalBoundaryCanaries();
        System.out.println("PASS: PCB role width replay diagnostic complete");
    }

    private static void runSavedPlacement(long seed) {
        Rb56Plan savedPlan=Rb56Plan.resolve(seed,11);
        final TroubleshootBoard savedBoard=savedPlan.board();
        MediumBoardPhysicalPolicy.Result generated=new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(savedBoard,savedPlan.layoutSeed,savedPlan.routingSeed);
        require(generated.accepted(),"saved11 source placement regenerates for diagnostic seed "+seed+
            ": "+generated.toCanonical());
        final PcbBoardLayout saved=generated.getLayout();
        String expected=seed==-1022L?NEG1022_SHA:seed==42L?SEED42_SHA:null;
        String savedHash=PlayerSessionFingerprint.of(saved.geometryFingerprint());
        require(expected!=null && expected.equals(savedHash),
            "saved11 source geometry is the frozen candidate hash for seed "+seed+
            " actual="+savedHash+" expected="+expected);
        int expectedAttempt=seed==-1022L?0:1;
        require(generated.getStatistics().selectedPlacementAttempt==expectedAttempt,
            "saved11 source retains selected attempt "+expectedAttempt+" for seed "+seed);
        for(PcbTraceGeometry trace:saved.getTraces())
            require(trace.getWidth()==9,"saved11 source trace remains uniform9: "+trace.getNetId());

        Rb56Plan rolePlan=Rb56Plan.resolve(seed,12);
        final TroubleshootBoard roleBoard=rolePlan.board();
        require(savedPlan.layoutSeed==rolePlan.layoutSeed && savedPlan.routingSeed==rolePlan.routingSeed,
            "placement interpretation leaves seeded layout and routing streams unchanged");
        require(sameElectricalIdentity(savedBoard,roleBoard),
            "saved11 and private12 role board retain exact electrical identities");
        final PcbBoardLayout roleLayout=copyWithRoleWidths(saved,roleBoard);
        require(savedHash.equals(PlayerSessionFingerprint.of(saved.geometryFingerprint())),
            "the frozen saved11 layout remains byte-exact after diagnostic copy");
        require(nonRouteGeometry(saved).equals(nonRouteGeometry(roleLayout)),
            "diagnostic changes trace widths only and retains placements, pads, regions, holes and outline");
        require(tracePathSignature(saved).equals(tracePathSignature(roleLayout)),
            "diagnostic retains every original P07 route path and endpoint identity");

        System.out.println("PCB_ROLE_WIDTH_REPLAY_SOURCE seed="+seed+" hash="+savedHash+
            " attempt="+expectedAttempt+" traces="+saved.getTraces().size()+
            " vias="+saved.getHoleCount());
        report("validateRoutingGeometry",new Check() {
            public void run() { roleLayout.validateRoutingGeometry(roleBoard); }
        });
        report("validateGeometry",new Check() {
            public void run() { roleLayout.validateGeometry(roleBoard); }
        });
        report("PcbTwoLayerRules",new Check() {
            public void run() { new PcbTwoLayerRules(roleBoard,roleLayout).validate(roleLayout); }
        });
        report("PcbTraceClearance",new Check() {
            public void run() { PcbTraceClearance.validate(roleLayout.getTraces()); }
        });
        report("conductorGraphConnectivity",new Check() {
            public void run() {
                roleLayout.captureConductorGraph(roleBoard).pristine()
                    .requirePristineNetConnectivity(roleBoard);
            }
        });
        System.out.println("PCB_ROLE_WIDTH_REPLAY_PATH seed="+seed+
            " status=NOT_RUN detail=first-step-search-replay-deferred");
        System.out.println("PCB_ROLE_WIDTH_REPLAY_ACCEPTANCE seed="+seed+" status=NOT_PROVEN");
    }

    /** Shows whether the old symmetric inclusive goal envelope blocks a clear odd-width stroke. */
    private static void pointGoalBoundaryCanaries() {
        reportGoalBoundary(PcbRoleWidthRoutingContractTest
            .measureCourtyardGoalBoundary(11,105,245));
        reportGoalBoundary(PcbRoleWidthRoutingContractTest
            .measureCourtyardGoalBoundary(13,104,244));
        reportGoalBoundary(PcbRoleWidthRoutingContractTest
            .measureCourtyardGoalBoundary(9,106,246));
    }

    private static void reportGoalBoundary(
            PcbRoleWidthRoutingContractTest.GoalBoundaryMeasurement measurement) {
        System.out.println("PCB_ROLE_WIDTH_GOAL_BOUNDARY width="+measurement.width+
            " court="+rect(measurement.courtyard)+" clearStroke="+
            rect(measurement.clearStroke)+" clearPhysicalOverlap="+
            measurement.clearPhysicalOverlap+" clearFreeCell="+measurement.clearSearchFreeCell+
            " clearSearchGoal="+measurement.clearSearchGoal+
            " overlapStroke="+rect(measurement.overlapStroke)+" overlapPhysical="+
            measurement.overlapPhysical+" overlapSearchGoal="+
            measurement.overlapSearchGoal);
    }

    private static PcbBoardLayout copyWithRoleWidths(PcbBoardLayout source,
            TroubleshootBoard roleBoard) {
        PcbBoardLayout copy=new PcbBoardLayout(source.getWidth(),source.getHeight(),
            source.getBoardOutline(),source.getPartsTray(),source.getLayoutAlgorithmVersion());
        for(PcbComponentPlacement component:source.getComponents()) copy.addComponent(component);
        for(PcbLayoutRegion region:source.getRegions()) copy.addRegion(region);
        for(PcbPadPlacement pad:source.getPads()) copy.addPad(pad);
        for(PcbSilkscreenLabel label:source.getSilkscreenLabels()) copy.addSilkscreenLabel(label);
        for(PcbBoardHole hole:source.getHoles()) copy.addHole(hole);
        for(PcbTraceGeometry trace:source.getTraces()) {
            BoardNet net=roleBoard.getNet(trace.getNetId());
            if(net==null) throw new IllegalStateException("Missing role net "+trace.getNetId());
            copy.addTrace(new PcbTraceGeometry(trace.getSourceId(),trace.getNetId(),
                trace.getStartPadId(),trace.getEndPadId(),trace.getLayer(),trace.getExposure(),
                trace.getXPoints(),trace.getYPoints(),roleBoard.getPlacementConstraints()
                    .getTraceWidthProfile().widthFor(net.getRoutingRole())));
        }
        return copy;
    }

    private static boolean sameElectricalIdentity(TroubleshootBoard first,TroubleshootBoard second) {
        Vector<String> firstNets=first.getNetIds(),secondNets=second.getNetIds();
        Vector<String> firstParts=first.getComponentIds(),secondParts=second.getComponentIds();
        Vector<String> firstPads=first.getPadIds(),secondPads=second.getPadIds();
        Collections.sort(firstNets); Collections.sort(secondNets);
        Collections.sort(firstParts); Collections.sort(secondParts);
        Collections.sort(firstPads); Collections.sort(secondPads);
        if(!firstNets.equals(secondNets) || !firstParts.equals(secondParts) ||
                !firstPads.equals(secondPads)) return false;
        for(String id:firstPads) {
            BoardPad a=first.getPad(id),b=second.getPad(id);
            if(!a.getComponentId().equals(b.getComponentId()) ||
                    !a.getTerminalId().equals(b.getTerminalId()) || !a.getNetId().equals(b.getNetId()))
                return false;
        }
        return true;
    }

    private static String nonRouteGeometry(PcbBoardLayout layout) {
        StringBuilder out=new StringBuilder(layout.getWidth()+"x"+layout.getHeight()+"|")
            .append(rect(layout.getBoardOutline())).append('|').append(rect(layout.getPartsTray()))
            .append('|').append(layout.componentGeometryFingerprint()).append('|')
            .append(layout.silkscreenGeometryFingerprint()).append('|');
        Vector<PcbPadPlacement> pads=layout.getPads();
        Collections.sort(pads,new Comparator<PcbPadPlacement>() {
            public int compare(PcbPadPlacement a,PcbPadPlacement b) {
                return a.getPadId().compareTo(b.getPadId());
            }
        });
        for(PcbPadPlacement pad:pads) out.append(pad.geometryFingerprint()).append(';');
        for(PcbLayoutRegion region:layout.getRegions()) {
            out.append("region=").append(region.id).append(':').append(region.label).append(':');
            for(String component:region.getComponentIds()) out.append(component).append(',');
        }
        for(PcbBoardHole hole:layout.getHoles()) out.append("hole=").append(hole.fingerprint()).append(';');
        return out.toString();
    }

    private static String tracePathSignature(PcbBoardLayout layout) {
        Vector<PcbTraceGeometry> traces=layout.getTraces();
        Collections.sort(traces,new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry a,PcbTraceGeometry b) {
                return a.getSourceId().compareTo(b.getSourceId());
            }
        });
        StringBuilder out=new StringBuilder();
        for(PcbTraceGeometry trace:traces)
            out.append(trace.getSourceId()).append(':').append(trace.getNetId()).append(':')
                .append(trace.getStartPadId()).append('-').append(trace.getEndPadId()).append(':')
                .append(trace.getLayer()).append(':').append(trace.getExposure()).append(':')
                .append(Arrays.toString(trace.getXPoints())).append(':')
                .append(Arrays.toString(trace.getYPoints())).append(';');
        return out.toString();
    }

    private static String rect(Rectangle value) {
        return value.x+","+value.y+","+value.width+","+value.height;
    }
    private static void report(String name,Check check) {
        try {
            check.run();
            System.out.println("PCB_ROLE_WIDTH_REPLAY_CHECK "+name+" status=LEGAL");
        } catch(RuntimeException failure) {
            System.out.println("PCB_ROLE_WIDTH_REPLAY_CHECK "+name+" status=REJECTED reason="+
                oneLine(failure));
        }
    }
    private static String oneLine(Throwable value) {
        String message=value.getMessage();
        return value.getClass().getSimpleName()+(message==null?"":":"+message.replace('\n',' '));
    }
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    private interface Check { void run(); }
}
