package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/**
 * Versioned physical eligibility evidence for a normal two-layer medium board.
 * The policy receipt proves bounded candidate selection; this separate value
 * binds that receipt to the declared board and exact routed geometry.
 */
final class MediumBoardNormalAdmission implements GeneratedPhysicalAdmission {
    static final String ID = "MEDIUM_BOARD_NORMAL";
    static final int VERSION = 1;
    static final String IDENTITY = ID + "@" + VERSION;
    private static final int MIN_PARTS = 20;
    private static final int MAX_PARTS = 40;
    private static final int MAX_VIAS = PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias;
    private static final int MAX_ROUTING_EXPANSIONS =
        MediumBoardPhysicalPolicy.ROUTING_CANDIDATES * 2 *
            PcbRoutingWork.Limits.MAX_EXPANSIONS;

    private final long placementSeed;
    private final long routingSeed;
    private final String routeIdentity;
    private final String routeReceipt;
    private final String boardDeclaration;
    private final String layoutGeometry;
    private final String canonical;

    private MediumBoardNormalAdmission(long placementSeed, long routingSeed,
            String routeIdentity, String routeReceipt, String boardDeclaration,
            String layoutGeometry) {
        this.placementSeed = placementSeed;
        this.routingSeed = routingSeed;
        this.routeIdentity = routeIdentity;
        this.routeReceipt = routeReceipt;
        this.boardDeclaration = boardDeclaration;
        this.layoutGeometry = layoutGeometry;
        StringBuilder value = new StringBuilder(IDENTITY);
        field(value, "route", routeIdentity);
        field(value, "route-receipt", routeReceipt);
        field(value, "placement-seed", Long.toString(placementSeed));
        field(value, "routing-seed", Long.toString(routingSeed));
        field(value, "board-declaration", boardDeclaration);
        field(value, "layout-geometry", layoutGeometry);
        field(value, "bounds", "parts=" + MIN_PARTS + ".." + MAX_PARTS +
            ";vias=1.." + MAX_VIAS + ";factory-links=0;both-faces=EXPOSED" +
            ";pad-floor=" + SupportedEnvelope.MIN_PAD +
            ";probe-floor=" + SupportedEnvelope.MIN_PROBE +
            ";route-expansions=" + MAX_ROUTING_EXPANSIONS +
            ";medium-routing-policy=" + MediumBoardPhysicalPolicy.canonical());
        canonical = value.toString();
    }

    /**
     * Issues physical eligibility only for an accepted bounded P07 route.
     * The returned token retains no board, layout, or policy-result objects.
     */
    static GeneratedPhysicalAdmission fromAcceptedRoute(TroubleshootBoard board,
            MediumBoardPhysicalPolicy.Result result, long placementSeed, long routingSeed) {
        if (board == null || result == null || !result.accepted() ||
                result.getFailure() != null || result.getLayout() == null)
            throw new IllegalArgumentException("Normal medium admission requires an accepted routed candidate");
        MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
        requireAcceptedReceipt(statistics, result.getLayout());
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        if (!MediumBoardPhysicalPolicy.selected(constraints))
            throw new IllegalArgumentException("Normal medium admission requires MEDIUM_BOARD@1 routing constraints");
        PcbBoardLayout layout = result.getLayout();
        if (!layout.matchesGenerationSeeds(placementSeed, routingSeed))
            throw new IllegalArgumentException("Normal medium route seeds do not match the routed layout");
        PcbTwoLayerRules.requireNormalMediumGeometry(board, layout, MAX_VIAS);
        return new MediumBoardNormalAdmission(placementSeed, routingSeed,
            constraints.getPhysicalPolicyIdentity(), statistics.toCanonical(),
            boardDeclarationFingerprint(board), layout.geometryFingerprint());
    }

    private static void requireAcceptedReceipt(MediumBoardPhysicalPolicy.Statistics statistics,
            PcbBoardLayout layout) {
        if (statistics == null || layout == null ||
                !MediumBoardPhysicalPolicy.identity().equals(statistics.policyIdentity) ||
                !"SUCCESS".equals(statistics.outcome) ||
                !MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                    statistics.selectedRoutePolicy) ||
                statistics.placementCandidates < 1 ||
                statistics.placementCandidates > MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES ||
                statistics.placementRejections < 0 ||
                statistics.placementRejections > MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES ||
                statistics.routeAttempts < 1 ||
                statistics.routeAttempts > MediumBoardPhysicalPolicy.ROUTING_CANDIDATES * 2 ||
                statistics.oneFaceAttempts < 0 ||
                statistics.oneFaceAttempts > MediumBoardPhysicalPolicy.ROUTING_CANDIDATES ||
                statistics.oneFaceSuccesses < 0 ||
                statistics.oneFaceSuccesses > statistics.oneFaceAttempts ||
                statistics.twoLayerAttempts < 1 ||
                statistics.twoLayerAttempts > MediumBoardPhysicalPolicy.ROUTING_CANDIDATES ||
                statistics.twoLayerSuccesses < 1 ||
                statistics.twoLayerSuccesses > statistics.twoLayerAttempts ||
                statistics.selectedPlacementAttempt < 0 ||
                statistics.selectedPlacementAttempt >= MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES ||
                statistics.selectedPlacementScore < 0 ||
                statistics.selectedRouteQualityMilli < 0 ||
                statistics.routingOrders < 0 || statistics.routingExpansions < 0 ||
                statistics.routingOrders > SupportedEnvelope.MAX_ROUTE_ATTEMPTS ||
                statistics.routingExpansions > MAX_ROUTING_EXPANSIONS ||
                layout.getGenerationPlacementAttempts() < 1 ||
                layout.getGenerationPlacementAttempts() > MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES ||
                layout.getGenerationRoutingAttempts() < 0 ||
                layout.getGenerationRoutingAttempts() > SupportedEnvelope.MAX_ROUTE_ATTEMPTS ||
                layout.getGenerationRoutingExpansions() < 0 ||
                layout.getGenerationRoutingExpansions() > MAX_ROUTING_EXPANSIONS ||
                statistics.routingOrders != layout.getGenerationRoutingAttempts() ||
                statistics.routingExpansions != layout.getGenerationRoutingExpansions())
            throw new IllegalArgumentException("Normal medium admission rejected an invalid route receipt");

        boolean selectedTwoLayerSuccess = false;
        for (String outcome : statistics.routeOutcomes) {
            String expected = MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@" +
                statistics.selectedPlacementAttempt + "=SUCCESS";
            if (expected.equals(outcome)) selectedTwoLayerSuccess = true;
        }
        if (!selectedTwoLayerSuccess)
            throw new IllegalArgumentException("Selected P07 route is absent from its accepted receipt");
    }

    public String identity() { return IDENTITY; }
    public String canonical() { return canonical; }

    public void requireConstruction(TroubleshootBoard board, PcbBoardLayout layout) {
        requireBoundRealization(board, layout, false);
    }

    public void requireNormal(GeneratedBoardInstance owner) {
        if (owner == null || owner.isDeveloperOnlyFaultRoute() ||
                owner.getPhysicalAdmission() != this)
            throw new IllegalArgumentException("Normal medium admission is not attached to this player owner");
        PcbBoardLayout layout = owner.getPcbLayout();
        if (layout == null || !layout.isSealed())
            throw new IllegalArgumentException("Normal medium admission requires a sealed routed layout");
        requireBoundRealization(owner.getBoard(), layout, true);
    }

    private void requireBoundRealization(TroubleshootBoard board, PcbBoardLayout layout,
            boolean requireSealed) {
        if (board == null || layout == null ||
                !MediumBoardPhysicalPolicy.identity().equals(routeIdentity) ||
                !MediumBoardPhysicalPolicy.selected(board.getPlacementConstraints()) ||
                !routeIdentity.equals(board.getPlacementConstraints().getPhysicalPolicyIdentity()) ||
                !layout.matchesGenerationSeeds(placementSeed, routingSeed) ||
                (requireSealed && !layout.isSealed()) ||
                !boardDeclaration.equals(boardDeclarationFingerprint(board)) ||
                !layoutGeometry.equals(layout.geometryFingerprint()))
            throw new IllegalArgumentException("Normal medium physical admission does not match its board realization");
        PcbTwoLayerRules.requireNormalMediumGeometry(board, layout, MAX_VIAS);
    }

    private static String boardDeclarationFingerprint(TroubleshootBoard board) {
        StringBuilder out = new StringBuilder();
        field(out, "board", board.getId());
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String id : componentIds) {
            BoardComponent component = board.getComponent(id);
            PhysicalPackage physical = component.getPhysicalPackage();
            field(out, "component", id);
            field(out, "component-type", component.getType());
            field(out, "component-marking", component.getDisplayName());
            field(out, "component-package", physical.getId());
            field(out, "component-package-terminals", join(physical.getTerminalIds()));
            field(out, "component-package-geometry-version",
                Integer.toString(physical.getGeometryContractVersionValue()));
            field(out, "component-package-geometry", physical.getGeometry().getWidth() + "x" +
                physical.getGeometry().getHeight());
            field(out, "component-package-developer-generic",
                Boolean.toString(physical.isDeveloperGeneric()));
            Vector<String> connected = new Vector<String>();
            Vector<String> terminals = physical.getTerminalIds();
            for (int first = 0; first < terminals.size(); first++)
                for (int second = first + 1; second < terminals.size(); second++)
                    if (physical.isInternallyConnected(terminals.get(first), terminals.get(second)))
                        connected.add(terminals.get(first) + "=" + terminals.get(second));
            field(out, "component-package-internal", join(connected));
        }

        Vector<String> padIds = board.getPadIds();
        Collections.sort(padIds);
        for (String id : padIds) {
            BoardPad pad = board.getPad(id);
            field(out, "pad", id + "|" + pad.getComponentId() + "|" +
                pad.getTerminalId() + "|" + pad.getNetId());
        }

        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        for (String id : netIds) {
            BoardNet net = board.getNet(id);
            Vector<String> attached = net.getPadIds();
            Collections.sort(attached);
            field(out, "net", id + "|" + net.getRoutingRole() + "|" + join(attached));
        }

        Vector<String> powerIds = board.getPowerInputIds();
        Collections.sort(powerIds);
        for (String id : powerIds) {
            ExternalBoardPowerInput input = board.getPowerInput(id);
            field(out, "power-input", id + "|" + input.getPositivePadId() + "|" +
                input.getReturnPadId() + "|" + input.getPositiveNetId() + "|" +
                input.getReturnNetId());
        }

        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        field(out, "placement-policy", constraints.getPhysicalPolicyIdentity());
        field(out, "placement-layer", String.valueOf(constraints.routingLayer));
        Vector<PcbPlacementConstraints.Part> parts = constraints.getParts();
        Collections.sort(parts, new java.util.Comparator<PcbPlacementConstraints.Part>() {
            public int compare(PcbPlacementConstraints.Part first,
                    PcbPlacementConstraints.Part second) {
                return first.componentId.compareTo(second.componentId);
            }
        });
        for (PcbPlacementConstraints.Part part : parts)
            field(out, "placement-part", part.componentId + "|" + part.regionId + "|" +
                part.regionLabel + "|" + part.domainId + "|" + part.anchor + "|" +
                part.accessMargin);
        Vector<PcbPlacementConstraints.Barrier> barriers = constraints.getBarriers();
        for (PcbPlacementConstraints.Barrier barrier : barriers)
            field(out, "placement-barrier", barrier.firstDomain + "|" +
                barrier.secondDomain + "|" + barrier.clearance);
        return out.toString();
    }

    private static String join(Vector<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) field(out, "value", value);
        return out.toString();
    }

    private static void field(StringBuilder out, String name, String value) {
        out.append(name.length()).append(':').append(name).append('=');
        out.append(value == null ? -1 : value.length()).append(':');
        if (value != null) out.append(value);
        out.append(';');
    }
}
