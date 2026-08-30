package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

/**
 * Developer-only cross-boundary verifier for Task 43P.  The terminal manifest
 * in this class is intentionally authored independently of the generated
 * board bindings and renderer geometry.  It joins raw PCB endpoints, provider
 * render surfaces, and live CircuitJS posts without becoming a runtime owner.
 */
final class Task43PPhysicalTruthDeveloperVerifier {
    private static final class TerminalExpectation {
        final String padId;
        final String componentId;
        final String terminalId;
        final String netId;
        final String solverClass;
        final int solverPost;

        TerminalExpectation(String padId, String componentId, String terminalId,
                String netId, String solverClass, int solverPost) {
            this.padId = padId;
            this.componentId = componentId;
            this.terminalId = terminalId;
            this.netId = netId;
            this.solverClass = solverClass;
            this.solverPost = solverPost;
        }
    }

    private static final class PackageExpectation {
        final String componentId;
        final String packageId;
        final String[] variants;
        final String[] transforms;

        PackageExpectation(String componentId, String packageId, String[] variants,
                String[] transforms) {
            this.componentId = componentId;
            this.packageId = packageId;
            this.variants = variants;
            this.transforms = transforms;
        }

        boolean accepts(String variant, String transform) {
            return contains(variants, variant) && contains(transforms, transform);
        }
    }

    private static final class Manifest {
        final Vector<TerminalExpectation> terminals = new Vector<TerminalExpectation>();
        final Vector<PackageExpectation> packages = new Vector<PackageExpectation>();

        PackageExpectation getPackage(String componentId) {
            for (PackageExpectation expected : packages)
                if (expected.componentId.equals(componentId))
                    return expected;
            return null;
        }
    }

    private static final class TriadObservation {
        String padId;
        String rawNetId;
        int rawX;
        int rawY;
        String renderedPadId;
        String renderedTerminalId;
        int renderedX;
        int renderedY;
        String packageId;
        String variant;
        String transform;
        String solverNetId;
        String solverClass;
        int solverPost;

        TriadObservation copy() {
            TriadObservation result = new TriadObservation();
            result.padId = padId;
            result.rawNetId = rawNetId;
            result.rawX = rawX;
            result.rawY = rawY;
            result.renderedPadId = renderedPadId;
            result.renderedTerminalId = renderedTerminalId;
            result.renderedX = renderedX;
            result.renderedY = renderedY;
            result.packageId = packageId;
            result.variant = variant;
            result.transform = transform;
            result.solverNetId = solverNetId;
            result.solverClass = solverClass;
            result.solverPost = solverPost;
            return result;
        }

        boolean matches(TerminalExpectation expected, TriadObservation baseline) {
            return expected.padId.equals(padId) && expected.netId.equals(rawNetId) &&
                rawX == baseline.rawX && rawY == baseline.rawY &&
                expected.padId.equals(renderedPadId) &&
                expected.terminalId.equals(renderedTerminalId) &&
                renderedX == baseline.renderedX && renderedY == baseline.renderedY &&
                baseline.packageId.equals(packageId) && baseline.variant.equals(variant) &&
                baseline.transform.equals(transform) &&
                expected.netId.equals(solverNetId) &&
                expected.solverClass.equals(solverClass) &&
                expected.solverPost == solverPost;
        }
    }

    private static final class NegativeCanaryResult {
        final String id;
        final boolean caught;

        NegativeCanaryResult(String id, boolean caught) {
            this.id = id;
            this.caught = caught;
        }
    }

    private Task43PPhysicalTruthDeveloperVerifier() { }

    static String verify(CirSim sim) {
        if (sim == null)
            throw new IllegalArgumentException("Task43P physical verifier requires a simulation");
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        if (instance == null || instance.getPcbLayout() == null)
            throw new IllegalStateException("task43p-physical-triad-missing-board");
        if (sim.pcbWorkbenchController == null)
            throw new IllegalStateException("task43p-physical-triad-missing-renderer");

        Manifest manifest = manifestFor(instance.getCircuitFamilyId());
        PcbBoardLayout layout = instance.getPcbLayout();
        TroubleshootBoard board = instance.getBoard();
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        Vector<CircuitElm> ownedElements = instance.getSimulationElements();
        HashMap<String, TriadObservation> observations =
            new HashMap<String, TriadObservation>();
        Vector<String> liveReadings = new Vector<String>();
        int rawTraceChecks = 0;
        int renderedSurfaceChecks = 0;
        int solverChecks = 0;

        verifyPackageCatalog(instance, manifest);
        for (TerminalExpectation expected : manifest.terminals) {
            BoardPad boardPad = board.getPad(expected.padId);
            if (boardPad == null || !expected.componentId.equals(boardPad.getComponentId()) ||
                    !expected.terminalId.equals(boardPad.getTerminalId()) ||
                    !expected.netId.equals(boardPad.getNetId()))
                throw new IllegalStateException("task43p-physical-manifest-board-mismatch:" +
                    expected.padId);

            PcbPadPlacement pad = layout.getPad(expected.padId);
            if (pad == null)
                throw new IllegalStateException("task43p-physical-missing-pad:" +
                    expected.padId);
            PcbTraceGeometry trace = findTraceForPad(layout, expected.padId);
            if (trace == null || !expected.netId.equals(trace.getNetId()))
                throw new IllegalStateException("task43p-physical-raw-trace-mismatch:" +
                    expected.padId);
            int[] tracePoint = endpointForPad(trace, expected.padId);
            if (tracePoint[0] != pad.getX() || tracePoint[1] != pad.getY())
                throw new IllegalStateException("task43p-physical-raw-endpoint-gap:" +
                    expected.padId);
            rawTraceChecks++;

            PcbComponentPlacement placement = layout.getComponent(expected.componentId);
            if (placement == null)
                throw new IllegalStateException("task43p-physical-missing-component:" +
                    expected.componentId);
            PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime()
                .getSlot(expected.componentId);
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime()
                .getInstalledPart(expected.componentId);
            if (slot == null || part == null || slot.getInstalledPart() != part ||
                    !part.isInstalled() || part.getBoardSlot() != slot ||
                    part.getId() == null || part.getId().length() == 0)
                throw new IllegalStateException("task43p-physical-part-identity-mismatch:" +
                    expected.componentId);
            PhysicalPartRenderGeometry geometry = renderer
                .getInstalledGeometryForDeveloperVerification(expected.componentId);
            PhysicalPartRenderTerminal terminal = findTerminal(geometry, expected.padId,
                expected.terminalId);
            if (terminal == null || !expected.padId.equals(terminal.getBoardPadId()) ||
                    !expected.terminalId.equals(terminal.getTerminalId()))
                throw new IllegalStateException("task43p-physical-render-terminal-mismatch:" +
                    expected.padId);
            Point renderedPadPoint = renderer.getPadPoint(expected.padId);
            Point terminalPadPoint = terminal.getBoardPadPoint();
            if (renderedPadPoint == null || terminalPadPoint == null ||
                    renderedPadPoint.x != pad.getX() || renderedPadPoint.y != pad.getY() ||
                    renderedPadPoint.x != terminalPadPoint.x ||
                    renderedPadPoint.y != terminalPadPoint.y ||
                    !terminal.containsProbe(renderedPadPoint.x, renderedPadPoint.y) ||
                    !geometry.contains(renderedPadPoint.x, renderedPadPoint.y))
                throw new IllegalStateException("task43p-physical-render-pad-surface-mismatch:" +
                    expected.padId);
            ProbeTarget target = renderer.findProbeTarget(sim, renderedPadPoint.x,
                renderedPadPoint.y);
            if (!(target instanceof BoardPadProbeTarget) || !target.isValid() ||
                    !expected.padId.equals(((BoardPadProbeTarget) target).getPadId()))
                throw new IllegalStateException("task43p-physical-probe-target-mismatch:" +
                    expected.padId);
            renderedSurfaceChecks++;

            CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings()
                .getEndpoint(expected.padId);
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("task43p-physical-nonpost-binding:" +
                    expected.padId);
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            CircuitElm element = post.getElement();
            String solverNetId = instance.getSimulationBindings().getNetIdForEndpoint(post);
            if (!expected.netId.equals(solverNetId))
                throw new IllegalStateException("task43p-physical-solver-net-mismatch:" +
                    expected.padId);
            if (element == null || !ownedElements.contains(element) ||
                    element.getPostCount() <= post.getPostIndex() ||
                    post.getPostIndex() < 0 || !expected.solverClass.equals(
                        element.getClass().getSimpleName()))
                throw new IllegalStateException("task43p-physical-solver-post-mismatch:" +
                    expected.padId);
            double voltage = element.getPostVoltage(post.getPostIndex());
            if (Double.isNaN(voltage) || Double.isInfinite(voltage) ||
                    Math.abs(voltage) > 1e12)
                throw new IllegalStateException("task43p-physical-live-reading-invalid:" +
                    expected.padId);
            solverChecks++;
            liveReadings.add(expected.padId + "=" + Double.toString(voltage));

            TriadObservation observation = new TriadObservation();
            observation.padId = expected.padId;
            observation.rawNetId = trace.getNetId();
            observation.rawX = tracePoint[0];
            observation.rawY = tracePoint[1];
            observation.renderedPadId = terminal.getBoardPadId();
            observation.renderedTerminalId = terminal.getTerminalId();
            observation.renderedX = renderedPadPoint.x;
            observation.renderedY = renderedPadPoint.y;
            observation.packageId = placement.getPhysicalPackage().getId();
            observation.variant = placement.getGeometryVariantKey();
            observation.transform = placement.getGeometryTransformKey();
            observation.solverNetId = solverNetId;
            observation.solverClass = element.getClass().getSimpleName();
            observation.solverPost = post.getPostIndex();
            observations.put(expected.padId, observation);
        }

        Vector<NegativeCanaryResult> negativeCanaries = runNegativeCanaries(manifest,
            observations);
        for (NegativeCanaryResult result : negativeCanaries)
            if (!result.caught)
                throw new IllegalStateException("task43p-negative-canary-not-caught:" + result.id);

        return buildEvidence(instance, manifest, observations, liveReadings, rawTraceChecks,
            renderedSurfaceChecks, solverChecks, negativeCanaries);
    }

    private static void verifyPackageCatalog(GeneratedBoardInstance instance, Manifest manifest) {
        TroubleshootBoard board = instance.getBoard();
        PcbBoardLayout layout = instance.getPcbLayout();
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        if (componentIds.size() != manifest.packages.size() ||
                layout.getComponents().size() != manifest.packages.size())
            throw new IllegalStateException("task43p-physical-package-manifest-incomplete");
        for (String componentId : componentIds) {
            PackageExpectation expected = manifest.getPackage(componentId);
            BoardComponent component = board.getComponent(componentId);
            PcbComponentPlacement placement = layout.getComponent(componentId);
            if (expected == null || component == null || placement == null ||
                    component.getPhysicalPackage() == null ||
                    !expected.packageId.equals(component.getPhysicalPackage().getId()) ||
                    !expected.packageId.equals(placement.getPhysicalPackage().getId()) ||
                    !expected.accepts(placement.getGeometryVariantKey(),
                        placement.getGeometryTransformKey()))
                throw new IllegalStateException("task43p-physical-package-variant-mismatch:" +
                    componentId);
            if (placement.getGeometryRealization() == null ||
                    !placement.getGeometryVariantKey().equals(
                        placement.getGeometryRealization().getVariantKey()) ||
                    !placement.getGeometryTransformKey().equals(
                        placement.getGeometryRealization().getTransformKey()))
                throw new IllegalStateException("task43p-physical-transform-mismatch:" +
                    componentId);
        }
    }

    private static Vector<NegativeCanaryResult> runNegativeCanaries(Manifest manifest,
            HashMap<String, TriadObservation> observations) {
        Vector<NegativeCanaryResult> result = new Vector<NegativeCanaryResult>();
        if (manifest.terminals.isEmpty())
            throw new IllegalStateException("task43p-negative-canary-empty-manifest");
        TerminalExpectation expected = manifest.terminals.firstElement();
        TriadObservation baseline = observations.get(expected.padId);
        if (baseline == null)
            throw new IllegalStateException("task43p-negative-canary-no-baseline");

        TriadObservation rendererOffset = baseline.copy();
        rendererOffset.renderedX++;
        result.add(new NegativeCanaryResult("renderer-only-pad-offset",
            !rendererOffset.matches(expected, baseline)));

        TriadObservation rawGap = baseline.copy();
        rawGap.rawX++;
        result.add(new NegativeCanaryResult("raw-endpoint-displacement-gap",
            !rawGap.matches(expected, baseline)));

        TriadObservation wrongNet = baseline.copy();
        wrongNet.rawNetId = "TASK43P_WRONG_NET";
        result.add(new NegativeCanaryResult("raw-endpoint-wrong-net",
            !wrongNet.matches(expected, baseline)));

        TriadObservation solverSwap = baseline.copy();
        solverSwap.solverPost = baseline.solverPost == 0 ? 1 : 0;
        result.add(new NegativeCanaryResult("solver-binding-post-swap",
            !solverSwap.matches(expected, baseline)));

        TriadObservation transformMismatch = baseline.copy();
        transformMismatch.transform = "TASK43P_MIRROR_MISMATCH";
        result.add(new NegativeCanaryResult("mirror-transform-mismatch",
            !transformMismatch.matches(expected, baseline)));

        TriadObservation selfConsistentWrongMapping = baseline.copy();
        selfConsistentWrongMapping.padId = "TASK43P_WRONG_PAD";
        selfConsistentWrongMapping.rawNetId = "TASK43P_WRONG_NET";
        selfConsistentWrongMapping.renderedPadId = "TASK43P_WRONG_PAD";
        selfConsistentWrongMapping.renderedTerminalId = "TASK43P_WRONG_TERMINAL";
        selfConsistentWrongMapping.packageId = "TASK43P_WRONG_PACKAGE";
        selfConsistentWrongMapping.variant = "TASK43P_WRONG_VARIANT";
        selfConsistentWrongMapping.transform = "TASK43P_WRONG_TRANSFORM";
        selfConsistentWrongMapping.solverClass = "TASK43P_WRONG_SOLVER";
        result.add(new NegativeCanaryResult("internally-self-consistent-wrong-mapping",
            !selfConsistentWrongMapping.matches(expected, baseline)));
        return result;
    }

    private static String buildEvidence(GeneratedBoardInstance instance, Manifest manifest,
            HashMap<String, TriadObservation> observations, Vector<String> liveReadings,
            int rawTraceChecks, int renderedSurfaceChecks, int solverChecks,
            Vector<NegativeCanaryResult> negativeCanaries) {
        StringBuilder result = new StringBuilder();
        result.append("{\"status\":\"PASS\",\"manifestAuthoring\":\"independent\",");
        result.append("\"family\":").append(q(instance.getCircuitFamilyId())).append(',');
        result.append("\"topology\":").append(q(instance.getTopologyVariantId())).append(',');
        result.append("\"seed\":").append(instance.getSeed()).append(',');
        result.append("\"fault\":").append(q(instance.getFaultBinding().getFault().getId()))
            .append(',');
        result.append("\"faultOwner\":").append(q(instance.getFaultLocus() == null ? null :
            instance.getFaultLocus().getOwnerId())).append(',');
        result.append("\"terminalCount\":").append(manifest.terminals.size()).append(',');
        result.append("\"rawTraceChecks\":").append(rawTraceChecks).append(',');
        result.append("\"renderedSurfaceChecks\":").append(renderedSurfaceChecks).append(',');
        result.append("\"solverChecks\":").append(solverChecks).append(',');
        result.append("\"productionMutation\":\"none\",\"experimentResidue\":\"in_memory_only\",");
        result.append("\"liveReadings\":[");
        for (int index = 0; index < liveReadings.size(); index++) {
            if (index > 0) result.append(',');
            result.append(q(liveReadings.get(index)));
        }
        result.append("],\"negativeCanaries\":[");
        for (int index = 0; index < negativeCanaries.size(); index++) {
            if (index > 0) result.append(',');
            NegativeCanaryResult canary = negativeCanaries.get(index);
            result.append("{\"id\":").append(q(canary.id)).append(",\"caught\":")
                .append(canary.caught).append('}');
        }
        result.append("],\"terminals\":[");
        Vector<String> padIds = new Vector<String>(observations.keySet());
        Collections.sort(padIds);
        for (int index = 0; index < padIds.size(); index++) {
            if (index > 0) result.append(',');
            TriadObservation observation = observations.get(padIds.get(index));
            result.append("{\"padId\":").append(q(observation.padId))
                .append(",\"rawNetId\":").append(q(observation.rawNetId))
                .append(",\"rawEndpoint\":[").append(observation.rawX).append(',')
                .append(observation.rawY).append("]")
                .append(",\"renderedPadId\":").append(q(observation.renderedPadId))
                .append(",\"renderedTerminalId\":").append(q(observation.renderedTerminalId))
                .append(",\"renderedPadPoint\":[").append(observation.renderedX).append(',')
                .append(observation.renderedY).append("]")
                .append(",\"packageId\":").append(q(observation.packageId))
                .append(",\"variant\":").append(q(observation.variant))
                .append(",\"transform\":").append(q(observation.transform))
                .append(",\"solverNetId\":").append(q(observation.solverNetId))
                .append(",\"solverClass\":").append(q(observation.solverClass))
                .append(",\"solverPost\":").append(observation.solverPost).append('}');
        }
        result.append("]}");
        return result.toString();
    }

    private static PhysicalPartRenderTerminal findTerminal(PhysicalPartRenderGeometry geometry,
            String padId, String terminalId) {
        if (geometry == null)
            return null;
        for (PhysicalPartRenderTerminal terminal : geometry.getTerminals())
            if (padId.equals(terminal.getBoardPadId()) &&
                    terminalId.equals(terminal.getTerminalId()))
                return terminal;
        return null;
    }

    private static PcbTraceGeometry findTraceForPad(PcbBoardLayout layout, String padId) {
        for (PcbTraceGeometry trace : layout.getTraces())
            if (padId.equals(trace.getStartPadId()) || padId.equals(trace.getEndPadId()))
                return trace;
        return null;
    }

    private static int[] endpointForPad(PcbTraceGeometry trace, String padId) {
        int[] x = trace.getXPoints();
        int[] y = trace.getYPoints();
        int last = x.length - 1;
        if (padId.equals(trace.getStartPadId()))
            return new int[] { x[0], y[0] };
        if (padId.equals(trace.getEndPadId()))
            return new int[] { x[last], y[last] };
        throw new IllegalArgumentException("Trace does not expose pad endpoint: " + padId);
    }

    private static Manifest manifestFor(String family) {
        Manifest result = new Manifest();
        if ("LED_INDICATOR".equals(family)) {
            addPackage(result, "J1", "THROUGH_HOLE_CONNECTOR_2", true);
            addPackage(result, "R1", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addTerminal(result, "J1.1", "J1", "1", "VIN", "SwitchElm", 1);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "R1.1", "R1", "1", "VIN", "WireElm", 1);
            addTerminal(result, "R1.2", "R1", "2", "LED_NODE", "WireElm", 0);
            addTerminal(result, "LED1.A", "LED1", "A", "LED_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "GND", "GroundElm", 0);
        } else if (DiodeProtectedIndicatorGenerator.FAMILY_ID.equals(family)) {
            addPackage(result, "J1", "THROUGH_HOLE_CONNECTOR_2", true);
            addPackage(result, "D1", "AXIAL_DIODE", false, "SPAN_230", "SPAN_250");
            addPackage(result, "R1", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addTerminal(result, "J1.1", "J1", "1", "VIN", "SwitchElm", 1);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "D1.A", "D1", "A", "VIN", "WireElm", 1);
            addTerminal(result, "D1.K", "D1", "K", "DIODE_OUT", "WireElm", 0);
            addTerminal(result, "R1.1", "R1", "1", "DIODE_OUT", "ResistorElm", 0);
            addTerminal(result, "R1.2", "R1", "2", "LED_NODE", "ResistorElm", 1);
            addTerminal(result, "LED1.A", "LED1", "A", "LED_NODE", "LEDElm", 0);
            addTerminal(result, "LED1.K", "LED1", "K", "GND", "LEDElm", 1);
        } else if (RcDelayGenerator.FAMILY_ID.equals(family)) {
            addPackage(result, "J1", "THROUGH_HOLE_CONNECTOR_2", true);
            addPackage(result, "J2", "THROUGH_HOLE_OUTPUT_HEADER_2", false);
            addPackage(result, "R1", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "R2", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "C1", "RADIAL_ELECTROLYTIC_CAPACITOR", false);
            addPackage(result, "C2", "RADIAL_CERAMIC_CAPACITOR", false);
            addTerminal(result, "J1.1", "J1", "1", "VIN", "WireElm", 1);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "J2.1", "J2", "1", "RC_OUT", "WireElm", 1);
            addTerminal(result, "J2.2", "J2", "2", "GND", "GroundElm", 0);
            addTerminal(result, "R1.1", "R1", "1", "VIN", "WireElm", 1);
            addTerminal(result, "R1.2", "R1", "2", "RC_OUT", "ResistorElm", 1);
            addTerminal(result, "R2.1", "R2", "1", "RC_OUT", "WireElm", 1);
            addTerminal(result, "R2.2", "R2", "2", "GND", "ResistorElm", 1);
            addTerminal(result, "C1.+", "C1", "+", "RC_OUT", "WireElm", 1);
            addTerminal(result, "C1.-", "C1", "-", "GND", "GroundElm", 0);
            addTerminal(result, "C2.1", "C2", "1", "VIN", "CapacitorElm", 0);
            addTerminal(result, "C2.2", "C2", "2", "GND", "CapacitorElm", 1);
        } else if (NpnLowSideSwitchGenerator.FAMILY_ID.equals(family)) {
            addSwitchPackage(result, "J1");
            addSwitchPackage(result, "J2");
            addPackage(result, "RLOAD", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "RB", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "RPD", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addPackage(result, "Q1", "TO92_NPN", false);
            addSwitchTerminals(result, "J1", "LOAD_SUPPLY");
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "RLOAD.1", "RLOAD", "1", "LOAD_SUPPLY", "WireElm", 1);
            addTerminal(result, "RLOAD.2", "RLOAD", "2", "LOAD_NODE", "WireElm", 0);
            addTerminal(result, "LED1.A", "LED1", "A", "LOAD_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "DRAIN", "WireElm", 0);
            addTerminal(result, "J2.1", "J2", "1", "CONTROL_INPUT", "WireElm", 1);
            addTerminal(result, "J2.2", "J2", "2", "GND", "GroundElm", 0);
            addTerminal(result, "RB.1", "RB", "1", "CONTROL_INPUT", "WireElm", 1);
            addTerminal(result, "RB.2", "RB", "2", "BASE", "WireElm", 1);
            addTerminal(result, "RPD.1", "RPD", "1", "BASE", "WireElm", 1);
            addTerminal(result, "RPD.2", "RPD", "2", "GND", "GroundElm", 0);
            addTerminal(result, "Q1.B", "Q1", "B", "BASE", "WireElm", 1);
            addTerminal(result, "Q1.C", "Q1", "C", "COLLECTOR", "WireElm", 1);
            addTerminal(result, "Q1.E", "Q1", "E", "GND", "WireElm", 1);
        } else if (NmosLowSideSwitchGenerator.FAMILY_ID.equals(family)) {
            addSwitchPackage(result, "J1");
            addSwitchPackage(result, "J2");
            addPackage(result, "RLOAD", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "RPD", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addPackage(result, "Q1", "TO92_NMOS", false);
            addTerminal(result, "J1.1", "J1", "1", "LOAD_SUPPLY", "WireElm", 0);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "RLOAD.1", "RLOAD", "1", "LOAD_SUPPLY", "WireElm", 1);
            addTerminal(result, "RLOAD.2", "RLOAD", "2", "LOAD_NODE", "WireElm", 0);
            addTerminal(result, "LED1.A", "LED1", "A", "LOAD_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "DRAIN", "WireElm", 0);
            addTerminal(result, "J2.1", "J2", "1", "CONTROL_INPUT", "WireElm", 1);
            addTerminal(result, "J2.2", "J2", "2", "GND", "GroundElm", 0);
            addTerminal(result, "RPD.1", "RPD", "1", "CONTROL_INPUT", "WireElm", 0);
            addTerminal(result, "RPD.2", "RPD", "2", "GND", "GroundElm", 0);
            addTerminal(result, "Q1.G", "Q1", "G", "CONTROL_INPUT", "WireElm", 1);
            addTerminal(result, "Q1.D", "Q1", "D", "DRAIN", "WireElm", 1);
            addTerminal(result, "Q1.S", "Q1", "S", "GND", "WireElm", 1);
        } else if ("PARALLEL_DUAL_INDICATOR".equals(family)) {
            addSwitchPackage(result, "J1");
            addPackage(result, "R1", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "R2", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addPackage(result, "LED2", "THROUGH_HOLE_LED", false);
            addTerminal(result, "J1.1", "J1", "1", "VIN", "SwitchElm", 1);
            addTerminal(result, "J1.2", "J1", "2", "GND", "WireElm", 1);
            addTerminal(result, "R1.1", "R1", "1", "VIN", "WireElm", 1);
            addTerminal(result, "R1.2", "R1", "2", "BRANCH1_NODE", "WireElm", 0);
            addTerminal(result, "LED1.A", "LED1", "A", "BRANCH1_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "GND", "WireElm", 0);
            addTerminal(result, "R2.1", "R2", "1", "VIN", "WireElm", 1);
            addTerminal(result, "R2.2", "R2", "2", "BRANCH2_NODE", "WireElm", 0);
            addTerminal(result, "LED2.A", "LED2", "A", "BRANCH2_NODE", "WireElm", 1);
            addTerminal(result, "LED2.K", "LED2", "K", "GND", "WireElm", 0);
        } else {
            throw new IllegalStateException("task43p-physical-unknown-family:" + family);
        }
        return result;
    }

    private static void addSwitchPackage(Manifest manifest, String componentId) {
        addPackage(manifest, componentId, "THROUGH_HOLE_CONNECTOR_2", true);
    }

    private static void addSwitchTerminals(Manifest manifest, String componentId,
            String positiveNet) {
        addTerminal(manifest, componentId + ".1", componentId, "1", positiveNet,
            "SwitchElm", 1);
    }

    private static void addPackage(Manifest manifest, String componentId, String packageId,
            boolean connector) {
        addPackage(manifest, componentId, packageId, connector, "DEFAULT");
    }

    private static void addPackage(Manifest manifest, String componentId, String packageId,
            boolean connector, String firstVariant, String... additionalVariants) {
        String[] variants = new String[1 + additionalVariants.length];
        variants[0] = firstVariant;
        for (int index = 0; index < additionalVariants.length; index++)
            variants[index + 1] = additionalVariants[index];
        String[] transforms;
        if (connector)
            transforms = new String[] { "IDENTITY", "MIRROR_X" };
        else
            transforms = new String[] { "IDENTITY" };
        manifest.packages.add(new PackageExpectation(componentId, packageId, variants,
            transforms));
    }

    private static void addTerminal(Manifest manifest, String padId, String componentId,
            String terminalId, String netId, String solverClass, int solverPost) {
        manifest.terminals.add(new TerminalExpectation(padId, componentId, terminalId, netId,
            solverClass, solverPost));
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values)
            if (candidate.equals(value))
                return true;
        return false;
    }

    private static String q(String value) {
        if (value == null)
            return "null";
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\') result.append("\\\\");
            else if (character == '"') result.append("\\\"");
            else if (character == '\n') result.append("\\n");
            else if (character == '\r') result.append("\\r");
            else if (character == '\t') result.append("\\t");
            else result.append(character);
        }
        return result.append('"').toString();
    }
}
