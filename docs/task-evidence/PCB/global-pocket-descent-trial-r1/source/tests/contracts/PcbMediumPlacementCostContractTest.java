package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Independent literal fixture for the existing complete-plan medium placement cost. */
public final class PcbMediumPlacementCostContractTest {
    private static int assertions;

    public static void main(String[] args) {
        final Fixture fixture = fixture();
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            fixture.plan.outline, fixture.plan.footprints);
        String boardBefore = boardFingerprint(fixture.board);
        String planBefore = planFingerprint(fixture.plan);

        rejectIllegalArgument(new Attempt() {
            public void run() {
                MediumBoardPhysicalPolicy.scorePlan(null, fixture.plan);
            }
        }, "whole-plan adapter rejects a null board with IllegalArgumentException");
        rejectIllegalArgument(new Attempt() {
            public void run() {
                MediumBoardPhysicalPolicy.scorePlan(fixture.board, null);
            }
        }, "whole-plan adapter rejects a null plan with IllegalArgumentException");

        MediumBoardPhysicalPolicy.PlacementScore first =
            MediumBoardPhysicalPolicy.scorePlan(fixture.board, fixture.plan);
        check(first.outlineArea == 2880000L,
            "literal 2400x1200 outline area is 2880000");
        check(first.courtyardArea == 123456L && first.demandArea == 789012L,
            "fixed planner courtyard and demand areas remain independently represented");
        long signalSpan = 3 * 400;
        long supplySpan = 1 * 400 * 6;
        long returnSpan = 1 * 400 * 6;
        long highCurrentSpan = 1 * 400 * 4;
        long controlSpan = 1 * 400 * 2;
        long roleWeightedSpan = signalSpan + supplySpan + returnSpan +
            highCurrentSpan + controlSpan;
        check(signalSpan == 1200 && supplySpan == 2400 && returnSpan == 2400 &&
                highCurrentSpan == 1600 && controlSpan == 800 &&
                roleWeightedSpan == 8400 && first.electricalSpan == roleWeightedSpan,
            "literal SIGNAL/SUPPLY/RETURN/HIGH_CURRENT/CONTROL contributions sum to 8400");
        check(3 * 400 == 1200 && 4 * 400 + 2 * 800 == 3200 &&
                1200 < 3200,
            "four-corner multi-pad net has a 1200 MST, distinct from 3200 all-pairs span");
        check(first.regionCohesion == 2000L,
            "literal region-center bbox spans are core800, power600, and IO600");
        check(first.connectorDistance == 400L,
            "stock connector pad uses its nearest same-net nonconnector at distance400");
        check(first.corridorPenalty == 6L,
            "literal obstacle overlaps only the tie-selected MST corridor by600 area");
        check(first.total == 81464L,
            "literal total is 8400*8 + 2000*4 + 400*6 + 6*12 + 2880 + 123 + 789");

        MediumBoardPhysicalPolicy.PlacementCandidate candidate =
            new MediumBoardPhysicalPolicy.PlacementCandidate(fixture.board, 2, fixture.plan);
        check(first.toCanonical().equals(candidate.score.toCanonical()),
            "whole-plan adapter equals the existing board-aware candidate score");
        MediumBoardPhysicalPolicy.PlacementScore repeated =
            MediumBoardPhysicalPolicy.scorePlan(fixture.board, fixture.plan);
        check(first.toCanonical().equals(repeated.toCanonical()),
            "repeated whole-plan scoring returns the exact deterministic score");
        Vector<PcbFootprint> reversedFootprints =
            new Vector<PcbFootprint>(fixture.plan.footprints);
        Collections.reverse(reversedFootprints);
        PcbPlacementPlanner.Plan reversedPlan = new PcbPlacementPlanner.Plan(
            fixture.plan.outline, reversedFootprints, fixture.plan.regions,
            fixture.plan.courtyardArea, fixture.plan.demandArea,
            fixture.plan.candidate, fixture.plan.evaluations);
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            reversedPlan.outline, reversedPlan.footprints);
        String reversedBefore = planFingerprint(reversedPlan);
        MediumBoardPhysicalPolicy.PlacementScore reversed =
            MediumBoardPhysicalPolicy.scorePlan(fixture.board, reversedPlan);
        check(first.toCanonical().equals(reversed.toCanonical()),
            "complete reversed footprint input order preserves the canonical whole-plan score");
        check(reversedBefore.equals(planFingerprint(reversedPlan)),
            "scoring leaves the reversed complete plan unchanged as well");
        check(boardBefore.equals(boardFingerprint(fixture.board)) &&
                planBefore.equals(planFingerprint(fixture.plan)),
            "scoring and candidate parity leave board nets, pads, constraints and full plan unchanged");

        System.out.println("PASS: PCB medium placement cost contracts assertions=" + assertions +
            " fixtures=1 evaluator=mediumWholePlanCost");
    }

    private static Fixture fixture() {
        TroubleshootBoard board = new TroubleshootBoard("MEDIUM_PLACEMENT_COST_LITERAL");
        board.addNet(new BoardNet("N_SIGNAL", BoardNet.RoutingRole.SIGNAL));
        board.addNet(new BoardNet("N_SUPPLY", BoardNet.RoutingRole.SUPPLY));
        board.addNet(new BoardNet("N_RETURN", BoardNet.RoutingRole.RETURN));
        board.addNet(new BoardNet("N_HIGH", BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet("N_CONTROL", BoardNet.RoutingRole.CONTROL));
        board.addNet(new BoardNet("N_OBSTACLE", BoardNet.RoutingRole.SIGNAL));
        board.addNet(new BoardNet("N_AUX", BoardNet.RoutingRole.SIGNAL));

        PhysicalPackage pointPackage = pointPackage();
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        addPoint(board, footprints, parts, pointPackage, "A", "N_SIGNAL",
            100, 100, "CORE");
        addPoint(board, footprints, parts, pointPackage, "B", "N_SIGNAL",
            500, 100, "CORE");
        addPoint(board, footprints, parts, pointPackage, "C", "N_SIGNAL",
            100, 500, "CORE");
        addPoint(board, footprints, parts, pointPackage, "D", "N_SIGNAL",
            500, 500, "CORE");
        // Its courtyard intersects the B-D corridor but none of the other
        // square's tied shortest edges or the other nets' corridors.
        addPoint(board, footprints, parts, pointPackage, "X", "N_OBSTACLE",
            500, 300, "OBSTACLE");
        addConnector(board, footprints, parts, "J1", 1000, 700);
        addPoint(board, footprints, parts, pointPackage, "S", "N_SUPPLY",
            1400, 700, "POWER");
        addPoint(board, footprints, parts, pointPackage, "R1", "N_RETURN",
            1000, 900, "POWER");
        addPoint(board, footprints, parts, pointPackage, "R2", "N_RETURN",
            1400, 900, "POWER");
        addPoint(board, footprints, parts, pointPackage, "H1", "N_HIGH",
            1800, 700, "IO");
        addPoint(board, footprints, parts, pointPackage, "H2", "N_HIGH",
            2200, 700, "IO");
        addPoint(board, footprints, parts, pointPackage, "K1", "N_CONTROL",
            1800, 900, "IO");
        addPoint(board, footprints, parts, pointPackage, "K2", "N_CONTROL",
            2200, 900, "IO");

        PcbPlacementConstraints constraints = new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
            PcbTraceWidthProfile.ROLE_WIDTHS);
        board.setPlacementConstraints(constraints);
        constraints.validate(board);
        board.validate();
        Rectangle outline = new Rectangle(20, 20, 2400, 1200);
        PcbPlacementPlanner.validate(board, constraints, outline, footprints);
        PcbPlacementPlanner.Plan plan = new PcbPlacementPlanner.Plan(outline, footprints,
            new Vector<PcbLayoutRegion>(), 123456L, 789012L, 2, 13);
        return new Fixture(board, constraints, plan);
    }

    private static PhysicalPackage pointPackage() {
        PhysicalPackage template = P06FactoryLinkFixtures.TEST_POINT;
        PhysicalPackageGeometry source = template.getGeometry();
        PhysicalPackageGeometry.Terminal original = source.getTerminal(0);
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(new PhysicalPackageGeometry.Terminal(original.getTerminalId(),
            original.getPadCenter(), original.getPadBounds(), original.getBoardPadProbeCenter(),
            original.getBoardPadProbeBounds(), original.getConnectedLead(),
            original.getLiftedLead(), -1, 0, 30, original.getAttachment()));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(source.getWidth(),
            source.getHeight(), terminals, source.getBodyBounds(), source.getBodyKeepOut(),
            source.getRoutingCourtyard(), source.getSelectionEnvelope(),
            source.getDragEnvelope(), source.getGeometryContractVersion());
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        Vector<PcbRotation> rotations = new Vector<PcbRotation>();
        rotations.add(PcbRotation.DEG_0);
        Vector<PcbBoardSide> sides = new Vector<PcbBoardSide>();
        sides.add(PcbBoardSide.TOP);
        return new PhysicalPackage("MEDIUM_SCORE_POINT", template.getTerminalIds(),
            new Vector<String>(), false, geometry, variants, "DEFAULT",
            PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT, rotations, sides);
    }

    private static void addPoint(TroubleshootBoard board, Vector<PcbFootprint> footprints,
            Vector<PcbPlacementConstraints.Part> parts, PhysicalPackage physical,
            String id, String netId, int padX, int padY, String region) {
        BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
        board.addComponent(component);
        board.addPad(new BoardPad(id + ".1", id, "1", netId));
        PcbPackagePose reference = new PcbPackagePose(100, 100, PcbRotation.DEG_0,
            PcbBoardSide.TOP);
        Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose = new PcbPackagePose(100 + padX - local.x,
            100 + padY - local.y, PcbRotation.DEG_0, PcbBoardSide.TOP);
        footprints.add(PcbFootprint.fromPhysicalPackage(component, pose,
            physical.getGeometry()));
        parts.add(new PcbPlacementConstraints.Part(id, region, region, "BOARD",
            PcbPlacementConstraints.Anchor.NONE, 20));
    }

    private static void addConnector(TroubleshootBoard board,
            Vector<PcbFootprint> footprints, Vector<PcbPlacementConstraints.Part> parts,
            String id, int firstPadX, int firstPadY) {
        PhysicalPackage physical = PhysicalPackages.THROUGH_HOLE_CONNECTOR_2;
        BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
        board.addComponent(component);
        Vector<String> terminalIds = physical.getTerminalIds();
        for (int index = 0; index < terminalIds.size(); index++) {
            String terminalId = terminalIds.get(index);
            String netId = "1".equals(terminalId) ? "N_SUPPLY" : "N_AUX";
            board.addPad(new BoardPad(id + "." + terminalId, id, terminalId, netId));
        }
        PcbPackagePose reference = new PcbPackagePose(100, 100, PcbRotation.DEG_0,
            PcbBoardSide.TOP);
        Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose = new PcbPackagePose(100 + firstPadX - local.x,
            100 + firstPadY - local.y, PcbRotation.DEG_0, PcbBoardSide.TOP);
        footprints.add(PcbFootprint.fromPhysicalPackage(component, pose,
            physical.getGeometry()));
        parts.add(new PcbPlacementConstraints.Part(id, "JACK", "JACK", "BOARD",
            PcbPlacementConstraints.Anchor.NONE, 20));
    }

    private static void rejectIllegalArgument(Attempt attempt, String message) {
        boolean rejected = false;
        try { attempt.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }

    private static String boardFingerprint(TroubleshootBoard board) {
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        StringBuilder value = new StringBuilder(board.getId()).append("|POLICY:")
            .append(constraints.getPhysicalPolicyIdentity()).append("|WIDTHS:")
            .append(constraints.getTraceWidthProfile().canonical()).append("|DENSITY:")
            .append(constraints.placementDensityVersion).append("|LAYER:")
            .append(constraints.routingLayer).append("|TWO_LAYER:")
            .append(constraints.requiresTwoLayerRouting);
        for (PcbPlacementConstraints.Barrier barrier : constraints.getBarriers())
            value.append("|B:").append(barrier.firstDomain).append(':')
                .append(barrier.secondDomain).append(':').append(barrier.clearance);
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            BoardComponent component = board.getComponent(componentId);
            value.append("|C:").append(componentId).append(':')
                .append(component.getPhysicalPackage().getId()).append(':')
                .append(component.getPhysicalPackage().isConnector());
            Vector<String> padIds = component.getPadIds();
            Collections.sort(padIds);
            for (String padId : padIds) value.append(':').append(padId);
        }
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        for (String netId : netIds) {
            BoardNet net = board.getNet(netId);
            value.append("|N:").append(netId).append(':')
                .append(net.getRoutingRole());
            Vector<String> padIds = net.getPadIds();
            Collections.sort(padIds);
            for (String padId : padIds) value.append(':').append(padId);
        }
        Vector<String> padIds = board.getPadIds();
        Collections.sort(padIds);
        for (String padId : padIds) {
            BoardPad pad = board.getPad(padId);
            value.append("|P:").append(padId).append(':')
                .append(pad.getComponentId()).append(':').append(pad.getTerminalId())
                .append(':').append(pad.getNetId());
        }
        for (PcbPlacementConstraints.Part part : constraints.getParts()) {
            value.append("|D:").append(part.componentId).append(':').append(part.regionId)
                .append(':').append(part.regionLabel).append(':').append(part.domainId)
                .append(':').append(part.anchor).append(':').append(part.accessMargin);
            for (PcbPlacementConstraints.TerminalDomain domain : part.getTerminalDomains())
                value.append("|TD:").append(part.componentId).append(':')
                    .append(domain.terminalId).append(':').append(domain.domainId);
        }
        return value.toString();
    }

    private static String planFingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder value = new StringBuilder();
        value.append(plan.outline.x).append(',').append(plan.outline.y).append(',')
            .append(plan.outline.width).append(',').append(plan.outline.height).append('|')
            .append(plan.candidate).append('|').append(plan.evaluations).append('|')
            .append(plan.courtyardArea).append('|').append(plan.demandArea).append('|')
            .append(plan.footprints.size()).append('|').append(plan.regions.size()).append(';');
        for (PcbFootprint footprint : plan.footprints)
            value.append(footprint.geometryFingerprint()).append(';');
        return value.toString();
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private interface Attempt { void run(); }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbPlacementConstraints constraints;
        final PcbPlacementPlanner.Plan plan;
        Fixture(TroubleshootBoard board, PcbPlacementConstraints constraints,
                PcbPlacementPlanner.Plan plan) {
            this.board = board;
            this.constraints = constraints;
            this.plan = plan;
        }
    }

    private PcbMediumPlacementCostContractTest() { }
}