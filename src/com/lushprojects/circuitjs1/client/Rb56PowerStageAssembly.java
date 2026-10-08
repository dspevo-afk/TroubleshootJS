package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** Adopts one actual power graph; package movement preserves its stationary copper. */
final class Rb56PowerStageAssembly {
    final Rb56PowerStage stage;
    private final RelayOutputGenerator.Assembly assembly;
    private final Rb56Plan plan;
    private final TreeMap<String, Bundle> bundles = new TreeMap<String, Bundle>();
    private final Vector<CircuitElm> allPhysicalBacking = new Vector<CircuitElm>();

    private static final class Bundle {
        final Vector<CircuitElm> elements;
        final CircuitPostMeasurementEndpoint[] terminals;
        Bundle(Vector<CircuitElm> elements, CircuitPostMeasurementEndpoint[] terminals) {
            this.elements = elements; this.terminals = terminals;
        }
    }

    Rb56PowerStageAssembly(RelayOutputGenerator.Assembly assembly, Rb56Plan plan) {
        if (assembly == null || plan == null)
            throw new IllegalArgumentException("Missing RB56 power assembly");
        this.assembly = assembly; this.plan = plan;
        stage = new Rb56PowerStage(0, 20000);
        assembly.elements.addAll(stage.elements());
        // Pin logical nets to the graph's existing junctions before moving any backing.
        anchor("AC_LINE", stage.line); anchor("AC_RETURN", stage.returned);
        anchor("AC_FUSED", stage.fuse.getPost(1));
        anchor("HV_POS", stage.inPlus); anchor("HV_RETURN", stage.inMinus);
        anchor("PRE_L", stage.preLPlus); anchor("CTRL_RETURN", stage.outMinus);
        anchor("RAIL12", stage.outputPlus); anchor("ENABLE_AC", stage.enablePoint);
        anchor("FB_AC", stage.feedbackPoint); anchor("BIAS_AC", stage.biasPoint);
        anchor("SENSE_ZENER", stage.senseResistor.getPost(1));
        anchor("SENSE_LED", stage.senseZener.getPost(0));

        single("F1", stage.fuse);
        for (int i = 0; i < 4; i++) single("D" + (i + 1), stage.bridge[i]);
        single("CBULK", stage.bulk); single("RPRIMARY", stage.primaryBleed);
        CircuitPostMeasurementEndpoint[] moduleTerminals = new CircuitPostMeasurementEndpoint[7];
        for (int i = 0; i < moduleTerminals.length; i++) moduleTerminals[i] = stage.module.terminal(i);
        put("UAC", stage.module.elements(), moduleTerminals);
        single("RENAC", stage.enablePullup); single("CBIAS", stage.biasCap);
        single("DFREE", stage.freewheel);
        put("L1", pair(stage.outputInductor, stage.windingResistance),
            new CircuitPostMeasurementEndpoint[] {post(stage.outputInductor, 0), post(stage.windingResistance, 1)});
        put("COUT", pair(stage.outputCap, stage.capacitorEsr),
            new CircuitPostMeasurementEndpoint[] {post(stage.capacitorEsr, 0), post(stage.outputCap, 1)});
        single("ROUT", stage.outputBleed); single("UFB", stage.opto);
        single("DZ1", stage.senseZener); single("RSENSE", stage.senseResistor);
        single("RFB", stage.feedbackPullup); single("CFB", stage.feedbackComp);
        if (bundles.size() + 1 != Rb56Plan.POWER_PACKAGES)
            throw new IllegalStateException("Incomplete RB56 power package census");

        assembly.connector("JAC", "120 VAC RMS / 60 Hz input", "AC_LINE", "AC_RETURN",
            stage.input.linePole, 1, stage.input.returnPole, 1);
        assembly.connections.declareConnectorHarness("JAC", stage.input.linePole, 1, stage.input.returnPole, 1);
        assembly.power.bindPowerInput("MAINAC", stage.input.binding);
        assembly.specifications.addPowerInputNameplate(PowerInputNameplate.acRms("MAINAC",
            E05AcInputModel.RMS_VOLTS, E05AcInputModel.FREQUENCY_HZ));
        for (Rb56Plan.Part part : plan.powerParts())
            if (!"JAC".equals(part.id)) adopt(part, bundles.get(part.id));
    }

    CircuitElm primary(String id) {
        if ("JAC".equals(id)) return stage.input.source;
        return bundle(id).elements.get(0);
    }
    Vector<CircuitElm> backing(String id) {
        if ("JAC".equals(id)) {
            Vector<CircuitElm> result = new Vector<CircuitElm>(); result.add(stage.input.source); return result;
        }
        return new Vector<CircuitElm>(bundle(id).elements);
    }
    private Bundle bundle(String id) {
        Bundle result = bundles.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown RB56 power part: " + id);
        return result;
    }
    private void anchor(String id, Point point) {
        if (!assembly.nets.containsKey(id) || point == null)
            throw new IllegalArgumentException("Undeclared RB56 power net: " + id);
        assembly.nets.put(id, new Point(point.x, point.y));
    }
    private void single(String id, CircuitElm element) {
        Vector<CircuitElm> backing = new Vector<CircuitElm>(); backing.add(element);
        CircuitPostMeasurementEndpoint[] terminals = new CircuitPostMeasurementEndpoint[element.getPostCount()];
        for (int i = 0; i < terminals.length; i++) terminals[i] = post(element, i);
        put(id, backing, terminals);
    }
    private void put(String id, Vector<CircuitElm> elements, CircuitPostMeasurementEndpoint[] terminals) {
        Rb56Plan.Part declaration = plan.part(id);
        if (declaration == null || declaration.kind != Rb56Plan.Kind.POWER ||
                declaration.physicalPackage.getTerminalCount() != terminals.length || bundles.containsKey(id))
            throw new IllegalArgumentException("Power backing disagrees with RB56 declaration: " + id);
        for (CircuitElm element : elements) {
            if (!assembly.elements.contains(element) || allPhysicalBacking.contains(element))
                throw new IllegalArgumentException("Power backing has a foreign or duplicate owner: " + id);
            allPhysicalBacking.add(element);
        }
        bundles.put(id, new Bundle(new Vector<CircuitElm>(elements), terminals));
    }
    private void adopt(Rb56Plan.Part declaration, Bundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("Missing RB56 backing: " + declaration.id);
        CircuitPostMeasurementEndpoint[] copper = new CircuitPostMeasurementEndpoint[bundle.terminals.length];
        boolean module = "UAC".equals(declaration.id);
        for (int i = 0; i < copper.length; i++) {
            Point old = module ? stage.moduleLeads[i].getPost(0) : point(bundle.terminals[i]);
            copper[i] = stationaryCopper(new Point(old.x, old.y));
        }
        // The module already owns an isolated island and exactly seven detachable leads.
        if (!module) translateWholeBundle(bundle.elements);
        WireElm[] leads = new WireElm[copper.length];
        if (module) System.arraycopy(stage.moduleLeads, 0, leads, 0, stage.moduleLeads.length);
        if (!module) for (int i = 0; i < leads.length; i++) {
            boolean reverse = i == 1 && leads.length == 2 &&
                ("RESISTOR".equals(declaration.type) || "CAPACITOR".equals(declaration.type) || "DIODE".equals(declaration.type));
            leads[i] = reverse ? assembly.wire(point(bundle.terminals[i]), point(copper[i])) :
                assembly.wire(point(copper[i]), point(bundle.terminals[i]));
        }
        Vector<CircuitElm> auxiliary = new Vector<CircuitElm>(bundle.elements); auxiliary.remove(0);
        assembly.registerExistingPart(declaration.id, declaration.type, declaration.physicalPackage,
            declaration.terminalIds(), declaration.netIds(), bundle.elements.get(0), auxiliary,
            copper, bundle.terminals, leads);
    }
    private CircuitPostMeasurementEndpoint stationaryCopper(Point at) {
        for (CircuitElm element : assembly.elements) {
            if (!(element instanceof WireElm) || allPhysicalBacking.contains(element) ||
                    assembly.connections.isConnectionElement(element) || isModuleLead(element)) continue;
            for (int post = 0; post < element.getPostCount(); post++)
                if (at.equals(element.getPost(post))) return new CircuitPostMeasurementEndpoint(element, post);
        }
        // Some series junctions previously consisted solely of two component posts.
        // Keep one independent copper endpoint there before either package moves.
        for (int trial = 1; trial <= 4096; trial++) {
            Point other = new Point(at.x + 16 * trial, at.y + 16);
            if (!occupied(other, null)) return post(assembly.wire(at, other), 0);
        }
        throw new IllegalStateException("No free RB56 copper anchor");
    }
    private boolean isModuleLead(CircuitElm element) {
        for (WireElm lead : stage.moduleLeads) if (lead == element) return true;
        return false;
    }
    private void translateWholeBundle(Vector<CircuitElm> elements) {
        CircuitElm primary = elements.get(0);
        for (int trial = 0; trial < 4096; trial++) {
            int dx = 100000 + (trial % 32) * 1024 - primary.x;
            int dy = 100000 + (trial / 32) * 1024 - primary.y;
            boolean free = true;
            for (CircuitElm element : elements) for (int i = 0; i < element.getPostCount() && free; i++) {
                Point at = element.getPost(i);
                if (occupied(new Point(at.x + dx, at.y + dy), elements)) free = false;
            }
            if (free) {
                for (CircuitElm element : elements) element.move(dx, dy);
                return;
            }
        }
        throw new IllegalStateException("No free RB56 package island");
    }
    private boolean occupied(Point at, Vector<CircuitElm> except) {
        for (CircuitElm element : assembly.elements) {
            if (except != null && except.contains(element)) continue;
            for (int i = 0; i < element.getPostCount(); i++)
                if (at.equals(element.getPost(i))) return true;
        }
        return false;
    }
    private static Point point(CircuitPostMeasurementEndpoint endpoint) {
        return endpoint.getElement().getPost(endpoint.getPostIndex());
    }
    private static CircuitPostMeasurementEndpoint post(CircuitElm element, int index) {
        return new CircuitPostMeasurementEndpoint(element, index);
    }
    private static Vector<CircuitElm> pair(CircuitElm first, CircuitElm second) {
        Vector<CircuitElm> result = new Vector<CircuitElm>(); result.add(first); result.add(second); return result;
    }
}
