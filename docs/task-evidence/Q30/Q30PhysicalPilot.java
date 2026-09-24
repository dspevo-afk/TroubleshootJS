package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Developer-only Q30 physical feasibility pilot.
 *
 * This is deliberately a logical/physical manifest.  It does not construct a
 * CircuitJS graph, prove rail behavior, admit a fault, or claim player
 * qualification.  It uses the existing P03 demand planner and P05 single-face
 * router, with a small P07 developer-only comparison on selected seeds.
 */
public final class Q30PhysicalPilot {
    private static final SeededPcbLayoutGenerator.AttemptObserver OBSERVER =
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };

    /* Representative and held-out values retain exact signed-long transport. */
    private static final long[] REPRESENTATIVE = { 0L, 1L, 3L, 17L, 42L, -1L };
    private static final long[] HELD_OUT = { 11L, 23L, 37L, 59L, 83L, -23L };

    private enum Driver { BJT, NMOS }
    private enum ReferenceArrangement { COMMON_5V_DIVIDER, LOCAL_SENSOR_DIVIDER }
    private enum RootPlacementVariant {
        EXACT_ROOT,
        NO_CONNECTOR_ANCHORS,
        COLLAPSED_REGIONS,
        WIDER_ACCESS_MARGIN
    }

    private static final class PartSpec {
        final String id;
        final PhysicalPackage physical;
        final String region;
        final PcbPlacementConstraints.Anchor anchor;
        PartSpec(String id, PhysicalPackage physical, String region,
                PcbPlacementConstraints.Anchor anchor) {
            this.id = id;
            this.physical = physical;
            this.region = region;
            this.anchor = anchor;
        }
    }

    private static final class Manifest {
        final long seed;
        final Driver driver;
        final ReferenceArrangement references;
        final TroubleshootBoard board;
        final PcbFootprintRegistry registry;
        final Vector<PcbPlacementConstraints.Part> demands =
            new Vector<PcbPlacementConstraints.Part>();
        final Set<String> connected = new HashSet<String>();
        final TreeMap<String, Integer> packageCounts = new TreeMap<String, Integer>();

        Manifest(long seed) {
            this.seed = seed;
            long selector = seed ^ (seed >>> 32);
            driver = (selector & 1L) == 0L ? Driver.BJT : Driver.NMOS;
            references = (selector & 2L) == 0L ?
                ReferenceArrangement.COMMON_5V_DIVIDER :
                ReferenceArrangement.LOCAL_SENSOR_DIVIDER;
            board = new TroubleshootBoard("Q30_RB32_" + seed);
            registry = StandardPcbFootprintProviders.createRegistry();
            declareNets();
            declareParts();
            wireManifest();
            verifyManifest();
            board.setPlacementConstraints(new PcbPlacementConstraints(
                demands, new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM));
            board.validate();
        }

        private void declareNets() {
            net("RETURN", BoardNet.RoutingRole.RETURN);
            net("VIN12_RAW", BoardNet.RoutingRole.SUPPLY);
            net("VIN12_PROTECTED", BoardNet.RoutingRole.SUPPLY);
            net("VIN12_SW", BoardNet.RoutingRole.HIGH_CURRENT);
            net("CONTROL_5V", BoardNet.RoutingRole.SUPPLY);
            net("SENSOR_A_RAW", BoardNet.RoutingRole.SIGNAL);
            net("SENSOR_A_SIG", BoardNet.RoutingRole.SIGNAL);
            net("SENSOR_A_REF", BoardNet.RoutingRole.CONTROL);
            net("SENSOR_B_RAW", BoardNet.RoutingRole.SIGNAL);
            net("SENSOR_B_SIG", BoardNet.RoutingRole.SIGNAL);
            net("SENSOR_B_REF", BoardNet.RoutingRole.CONTROL);
            net("DRIVE_A", BoardNet.RoutingRole.CONTROL);
            net("RELAY_A_LOW", BoardNet.RoutingRole.HIGH_CURRENT);
            net("OUTPUT_A", BoardNet.RoutingRole.HIGH_CURRENT);
            net("DRIVE_B", BoardNet.RoutingRole.CONTROL);
            net("RELAY_B_LOW", BoardNet.RoutingRole.HIGH_CURRENT);
            net("OUTPUT_B", BoardNet.RoutingRole.HIGH_CURRENT);
        }

        private void net(String id, BoardNet.RoutingRole role) {
            board.addNet(new BoardNet(id, role));
        }

        private void declareParts() {
            part("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "entry-protection", PcbPlacementConstraints.Anchor.RIGHT);
            part("F1", PhysicalPackages.AXIAL_FUSE, "entry-protection",
                PcbPlacementConstraints.Anchor.NONE);
            part("D1", PhysicalPackages.AXIAL_DIODE, "entry-protection",
                PcbPlacementConstraints.Anchor.NONE);
            part("CIN", PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
                "entry-protection", PcbPlacementConstraints.Anchor.NONE);

            part("U1", PhysicalPackages.TO220_REGULATOR_4, "regulator-filter",
                PcbPlacementConstraints.Anchor.NONE);
            part("CIN5", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
                "regulator-filter", PcbPlacementConstraints.Anchor.NONE);
            part("COUT5", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
                "regulator-filter", PcbPlacementConstraints.Anchor.NONE);
            part("CBYP5", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
                "regulator-filter", PcbPlacementConstraints.Anchor.NONE);

            sensorChannel("A");
            sensorChannel("B");

            outputChannel("A");
            outputChannel("B");

            part("LEDA", PhysicalPackages.THROUGH_HOLE_LED, "status",
                PcbPlacementConstraints.Anchor.NONE);
            part("LEDB", PhysicalPackages.THROUGH_HOLE_LED, "status",
                PcbPlacementConstraints.Anchor.NONE);
        }

        private void sensorChannel(String channel) {
            part("J" + channel + "S", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "sensor-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
            part("R" + channel + "SER", PhysicalPackages.AXIAL_RESISTOR,
                "sensor-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
            part("C" + channel + "FLT", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
                "sensor-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
            part("R" + channel + "REFH", PhysicalPackages.AXIAL_RESISTOR,
                "sensor-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
            part("R" + channel + "REFL", PhysicalPackages.AXIAL_RESISTOR,
                "sensor-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
            part("U2" + channel, PhysicalPackages.E04_DECISION_CONTROL_5,
                "sensor-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
        }

        private void outputChannel(String channel) {
            PhysicalPackage switchPackage = driver == Driver.BJT ?
                PhysicalPackages.TO92_NPN : PhysicalPackages.TO92_NMOS;
            part("Q" + channel, switchPackage, "output-" + channel.toLowerCase(),
                PcbPlacementConstraints.Anchor.NONE);
            part("R" + channel + "DRV", PhysicalPackages.AXIAL_RESISTOR,
                "output-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
            part("R" + channel + "BIAS", PhysicalPackages.AXIAL_RESISTOR,
                "output-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
            part("D" + channel + "FLY", PhysicalPackages.AXIAL_DIODE,
                "output-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
            part("K" + channel, PhysicalPackages.RELAY_SPDT,
                "output-" + channel.toLowerCase(), PcbPlacementConstraints.Anchor.NONE);
        }

        private void part(String id, PhysicalPackage physical, String region,
                PcbPlacementConstraints.Anchor anchor) {
            if (board.getComponent(id) != null)
                throw new IllegalArgumentException("Duplicate Q30 part " + id);
            board.addComponent(new BoardComponent(id, "Q30_PHYSICAL_PILOT", physical, id));
            demands.add(new PcbPlacementConstraints.Part(id, region, region,
                "LOW_VOLTAGE_BOARD", anchor, 20));
            Integer old = packageCounts.get(physical.getId());
            packageCounts.put(physical.getId(), old == null ? 1 : old + 1);
        }

        private void wireManifest() {
            connect("J1", "1", "VIN12_RAW");
            connect("J1", "2", "RETURN");
            connect("F1", "1", "VIN12_RAW");
            connect("F1", "2", "VIN12_PROTECTED");
            connect("D1", "A", "VIN12_PROTECTED");
            connect("D1", "K", "VIN12_SW");
            connect("CIN", "+", "VIN12_SW");
            connect("CIN", "-", "RETURN");

            connect("U1", "INPUT", "VIN12_SW");
            connect("U1", "OUTPUT", "CONTROL_5V");
            connect("U1", "RETURN", "RETURN");
            connect("U1", "ENABLE", "CONTROL_5V");
            connect("CIN5", "1", "VIN12_SW");
            connect("CIN5", "2", "RETURN");
            connect("COUT5", "1", "CONTROL_5V");
            connect("COUT5", "2", "RETURN");
            connect("CBYP5", "1", "CONTROL_5V");
            connect("CBYP5", "2", "RETURN");

            wireSensor("A");
            wireSensor("B");
            wireOutput("A");
            wireOutput("B");
            connect("LEDA", "A", "OUTPUT_A");
            connect("LEDA", "K", "RETURN");
            connect("LEDB", "A", "OUTPUT_B");
            connect("LEDB", "K", "RETURN");
        }

        private void wireSensor(String channel) {
            String upper = channel.toUpperCase();
            String raw = "SENSOR_" + upper + "_RAW";
            String signal = "SENSOR_" + upper + "_SIG";
            String reference = "SENSOR_" + upper + "_REF";
            String top = references == ReferenceArrangement.COMMON_5V_DIVIDER ?
                "CONTROL_5V" : signal;
            connect("J" + upper + "S", "1", raw);
            connect("J" + upper + "S", "2", "RETURN");
            connect("R" + upper + "SER", "1", raw);
            connect("R" + upper + "SER", "2", signal);
            connect("C" + upper + "FLT", "1", signal);
            connect("C" + upper + "FLT", "2", "RETURN");
            connect("R" + upper + "REFH", "1", top);
            connect("R" + upper + "REFH", "2", reference);
            connect("R" + upper + "REFL", "1", reference);
            connect("R" + upper + "REFL", "2", "RETURN");
            connect("U2" + upper, "SENSOR", signal);
            connect("U2" + upper, "REFERENCE", reference);
            connect("U2" + upper, "RAIL", "CONTROL_5V");
            connect("U2" + upper, "OUTPUT", "OUTPUT_" + upper);
            connect("U2" + upper, "RETURN", "RETURN");
        }

        private void wireOutput(String channel) {
            String upper = channel.toUpperCase();
            String drive = "DRIVE_" + upper;
            String low = "RELAY_" + upper + "_LOW";
            String output = "OUTPUT_" + upper;
            String switchBase = driver == Driver.BJT ? "B" : "G";
            String switchLow = driver == Driver.BJT ? "C" : "D";
            String switchReturn = driver == Driver.BJT ? "E" : "S";
            connect("Q" + upper, switchBase, drive);
            connect("Q" + upper, switchLow, low);
            connect("Q" + upper, switchReturn, "RETURN");
            connect("R" + upper + "DRV", "1", "OUTPUT_" + upper);
            connect("R" + upper + "DRV", "2", drive);
            connect("R" + upper + "BIAS", "1", drive);
            connect("R" + upper + "BIAS", "2", "RETURN");
            connect("D" + upper + "FLY", "A", "VIN12_SW");
            connect("D" + upper + "FLY", "K", low);
            connect("K" + upper, "A1", "VIN12_SW");
            connect("K" + upper, "A2", low);
            connect("K" + upper, "COM", "VIN12_SW");
            connect("K" + upper, "NC", "RETURN");
            connect("K" + upper, "NO", output);
        }

        private void connect(String componentId, String terminalId, String netId) {
            BoardComponent component = board.getComponent(componentId);
            if (component == null || board.getNet(netId) == null)
                throw new IllegalArgumentException("Unknown Q30 connection " + componentId +
                    "." + terminalId + " -> " + netId);
            if (!component.getPhysicalPackage().getTerminalIds().contains(terminalId))
                throw new IllegalArgumentException("Unknown terminal " + componentId + "." +
                    terminalId);
            String padId = componentId + "." + terminalId;
            if (!connected.add(padId)) throw new IllegalArgumentException("Duplicate Q30 pad " + padId);
            board.addPad(new BoardPad(padId, componentId, terminalId, netId));
        }

        private void verifyManifest() {
            if (board.getComponentIds().size() != 32)
                throw new AssertionError("Q30 manifest package count=" + board.getComponentIds().size());
            if (board.getPadIds().size() != 80)
                throw new AssertionError("Q30 manifest pad count=" + board.getPadIds().size());
            if (board.getNetIds().size() != 17)
                throw new AssertionError("Q30 manifest net count=" + board.getNetIds().size());
            for (String id : board.getComponentIds()) {
                BoardComponent component = board.getComponent(id);
                if (component.getPadIds().size() != component.getPhysicalPackage().getTerminalCount())
                    throw new AssertionError("Unwired Q30 component " + id);
                for (String pad : component.getPadIds()) if (!connected.contains(pad))
                    throw new AssertionError("Unwired Q30 pad " + pad);
            }
            if (countPackage(PhysicalPackages.E04_DECISION_CONTROL_5) != 2 ||
                    countPackage(PhysicalPackages.RELAY_SPDT) != 2)
                throw new AssertionError("Role-faithful repeated packages missing");
        }

        private int countPackage(PhysicalPackage physical) {
            Integer count = packageCounts.get(physical.getId());
            return count == null ? 0 : count;
        }
    }

    /**
     * Task-owned copy of the root RB30 physical board declaration.  This keeps
     * the route probe runnable from the accepted base, where Rb30Plan.java is
     * not present, while preserving its 33-package roles and named-stream
     * topology choices.  It remains a structural-only adapter: no solver
     * elements or player-facing qualification are constructed here.
     */
    private static final class RootPlanManifest {
        private static final String FAMILY_ID = "RB30_CONTROL";
        final long seed;
        final long layoutSeed;
        final long routingSeed;
        final boolean driverABjt;
        final boolean driverBBjt;
        final boolean sharedHystereticReference;
        final RootPlacementVariant placementVariant;
        final TroubleshootBoard board;
        final PcbFootprintRegistry registry;
        final TreeMap<String, Integer> packageCounts = new TreeMap<String, Integer>();

        RootPlanManifest(long seed) {
            this(seed, RootPlacementVariant.EXACT_ROOT);
        }

        RootPlanManifest(long seed, RootPlacementVariant placementVariant) {
            this.seed = seed;
            this.placementVariant = placementVariant;
            // P1 measures the actual provider plan. The earlier archived
            // corpus used the equivalent copied manifest; its bytes stay
            // frozen as the before result.
            Rb30Plan actual = Rb30Plan.resolve(seed);
            driverABjt = actual.driverABjt;
            driverBBjt = actual.driverBBjt;
            sharedHystereticReference = actual.sharedHystereticReference;
            layoutSeed = actual.layoutSeed;
            routingSeed = actual.routingSeed;
            board = actual.board();
            registry = StandardPcbFootprintProviders.createRegistry();
            if (placementVariant != RootPlacementVariant.EXACT_ROOT) {
                board.setPlacementConstraints(rootPlacement(board, placementVariant));
                board.validate();
            }
            for (String id : board.getComponentIds()) {
                String packageId = board.getComponent(id).getPhysicalPackage().getId();
                Integer old = packageCounts.get(packageId);
                packageCounts.put(packageId, old == null ? 1 : old + 1);
            }
            if (board.getComponentIds().size() != 33)
                throw new AssertionError("Root RB30 package accounting changed");
        }

        String topology() {
            return "RB30_" + (sharedHystereticReference ?
                "SHARED_HYSTERETIC" : "SEPARATE_DIRECT") + "_A_" +
                (driverABjt ? "BJT" : "NMOS") + "_B_" +
                (driverBBjt ? "BJT" : "NMOS");
        }

        private void declareNets() {
            for (String id : new String[] {
                    "RAW12", "FUSED12", "RAIL12", "EN5", "RAIL5", "LOAD12"
                }) net(id, BoardNet.RoutingRole.SUPPLY);
            for (String id : new String[] { "CTRL_RETURN", "LOAD_RETURN" })
                net(id, BoardNet.RoutingRole.RETURN);
            for (String id : new String[] { "A_RAW", "A_SENSE",
                    "A_CMD", "A_DRIVE", "B_RAW", "B_SENSE",
                    "B_CMD", "B_DRIVE" })
                net(id, BoardNet.RoutingRole.CONTROL);
            if (sharedHystereticReference)
                net("REF_SHARED", BoardNet.RoutingRole.CONTROL);
            else {
                net("A_REF", BoardNet.RoutingRole.CONTROL);
                net("B_REF", BoardNet.RoutingRole.CONTROL);
            }
            for (String id : new String[] { "A_COIL_LOW", "B_COIL_LOW",
                    "OUT_A", "OUT_B" })
                net(id, BoardNet.RoutingRole.HIGH_CURRENT);
            for (String id : new String[] { "NC_A", "NC_B", "LED_FEED" })
                net(id, BoardNet.RoutingRole.SIGNAL);
        }

        private void net(String id, BoardNet.RoutingRole role) {
            board.addNet(new BoardNet(id, role));
        }

        private void declareParts() {
            part("J1", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "entry", "RAW12", "CTRL_RETURN");
            part("F1", "FUSE", PhysicalPackages.AXIAL_FUSE, "entry",
                "RAW12", "FUSED12");
            part("DREV", "DIODE", PhysicalPackages.AXIAL_DIODE, "entry",
                "FUSED12", "RAIL12");
            part("C12", "CAPACITOR", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
                "entry", "FUSED12", "CTRL_RETURN");

            part("U1", "REGULATOR", PhysicalPackages.TO220_REGULATOR_4,
                "regulation", "RAIL12", "RAIL5", "CTRL_RETURN", "EN5");
            part("CIN", "CAPACITOR", PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
                "regulation", "RAIL12", "CTRL_RETURN");
            part("C5", "CAPACITOR", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
                "regulation", "RAIL5", "CTRL_RETURN");
            part("REN", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "regulation", "RAIL12", "EN5");

            sensor("A", sharedHystereticReference ? "REF_SHARED" : "A_REF");
            sensor("B", sharedHystereticReference ? "REF_SHARED" : "B_REF");
            if (sharedHystereticReference) {
                part("RREF_H", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                    "sensor-reference", "RAIL5", "REF_SHARED");
                part("RREF_L", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                    "sensor-reference", "REF_SHARED", "CTRL_RETURN");
                part("RFB_A", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                    "sensor-a", "A_CMD", "A_SENSE");
                part("RFB_B", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                    "sensor-b", "B_CMD", "B_SENSE");
            } else {
                for (String channel : new String[] { "A", "B" }) {
                    part("RREF_H" + channel, "RESISTOR",
                        PhysicalPackages.AXIAL_RESISTOR,
                        "sensor-" + channel.toLowerCase(),
                        "RAIL5", channel + "_REF");
                    part("RREF_L" + channel, "RESISTOR",
                        PhysicalPackages.AXIAL_RESISTOR,
                        "sensor-" + channel.toLowerCase(),
                        channel + "_REF", "CTRL_RETURN");
                }
            }

            output("A", driverABjt);
            output("B", driverBBjt);
            part("RLED", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "status", "RAIL5", "LED_FEED");
            part("LED1", "LED", PhysicalPackages.THROUGH_HOLE_LED, "status",
                "LED_FEED", "CTRL_RETURN");

            part("JSA", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "sensor-a", "A_RAW", "CTRL_RETURN");
            part("JSB", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "sensor-b", "B_RAW", "CTRL_RETURN");
            part("JLOAD", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "load", "LOAD12", "LOAD_RETURN");
            part("JOA", "OUTPUT_HEADER",
                PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2, "output-a",
                "OUT_A", "LOAD_RETURN");
            part("JOB", "OUTPUT_HEADER",
                PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2, "output-b",
                "OUT_B", "LOAD_RETURN");
        }

        private void sensor(String channel, String referenceNet) {
            part("U2" + channel, "SENSOR_CONTROL",
                PhysicalPackages.E04_DECISION_CONTROL_5, "sensor-" +
                    channel.toLowerCase(), channel + "_SENSE", referenceNet,
                    "RAIL5", channel + "_CMD", "CTRL_RETURN");
            part("RS" + channel, "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "sensor-" + channel.toLowerCase(), channel + "_RAW",
                channel + "_SENSE");
        }

        private void output(String channel, boolean bjt) {
            String upper = channel.toUpperCase();
            PhysicalPackage switchPackage = bjt ? PhysicalPackages.TO92_NPN :
                PhysicalPackages.TO92_NMOS;
            String type = bjt ? "BJT" : "NMOS";
            part("RD" + upper, "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "output-" + channel.toLowerCase(), upper + "_CMD",
                upper + "_DRIVE");
            part("RPD" + upper, "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "output-" + channel.toLowerCase(), upper + "_DRIVE",
                "CTRL_RETURN");
            part("Q" + upper, type, switchPackage,
                "output-" + channel.toLowerCase(), upper + "_DRIVE",
                upper + "_COIL_LOW", "CTRL_RETURN");
            part("D" + upper, "DIODE", PhysicalPackages.AXIAL_DIODE,
                "output-" + channel.toLowerCase(), upper + "_COIL_LOW",
                "RAIL5");
            part("K" + upper, "RELAY", PhysicalPackages.RELAY_SPDT,
                "output-" + channel.toLowerCase(), "RAIL5",
                upper + "_COIL_LOW", "LOAD12", "NC_" + upper,
                "OUT_" + upper);
        }

        private void addExternalInputs() {
            addPower("MAIN12", "J1.1", "J1.2", "RAW12", "CTRL_RETURN");
            addPower("SENSOR_A", "JSA.1", "JSA.2", "A_RAW", "CTRL_RETURN");
            addPower("SENSOR_B", "JSB.1", "JSB.2", "B_RAW", "CTRL_RETURN");
            addPower("LOAD12", "JLOAD.1", "JLOAD.2", "LOAD12", "LOAD_RETURN");
        }

        private void addPower(String id, String positivePad, String returnPad,
                String positiveNet, String returnNet) {
            board.addPowerInput(new ExternalBoardPowerInput(id, positivePad,
                returnPad, positiveNet, returnNet));
        }

        private void part(String id, String type, PhysicalPackage physical,
                String region, String... nets) {
            board.addComponent(new BoardComponent(id, type, physical));
            Vector<String> terminals = physical.getTerminalIds();
            if (terminals.size() != nets.length)
                throw new IllegalArgumentException("RB30 terminal count: " + id);
            for (int index = 0; index < terminals.size(); index++) {
                String terminal = terminals.get(index);
                board.addPad(new BoardPad(id + "." + terminal, id, terminal,
                    nets[index]));
            }
            Integer old = packageCounts.get(physical.getId());
            packageCounts.put(physical.getId(), old == null ? 1 : old + 1);
        }

        private PcbPlacementConstraints rootPlacement(TroubleshootBoard value,
                RootPlacementVariant variant) {
            Vector<PcbPlacementConstraints.Part> parts =
                new Vector<PcbPlacementConstraints.Part>();
            for (String id : value.getComponentIds()) {
                String region = id.equals("J1") || id.equals("F1") ||
                    id.equals("DREV") || id.equals("C12") ? "entry" :
                    id.equals("U1") || id.equals("CIN") || id.equals("C5") ||
                    id.equals("REN") ? "regulation" :
                    id.equals("JSA") || id.equals("U2A") || id.equals("RSA") ||
                    id.equals("RREFA") || id.equals("RFB_A") ||
                    id.equals("RREF_HA") || id.equals("RREF_LA") ? "sensor-a" :
                    id.equals("JSB") || id.equals("U2B") || id.equals("RSB") ||
                    id.equals("RREFB") || id.equals("RFB_B") ||
                    id.equals("RREF_HB") || id.equals("RREF_LB") ? "sensor-b" :
                    id.equals("RREF_H") || id.equals("RREF_L") ?
                    "sensor-reference" :
                    id.equals("RLED") || id.equals("LED1") ? "status" :
                    id.endsWith("A") || id.equals("JOA") ? "output-a" :
                    id.endsWith("B") || id.equals("JOB") ? "output-b" :
                    "output-common";
                PcbPlacementConstraints.Anchor anchor =
                    id.equals("J1") || id.equals("JSA") || id.equals("JSB") ?
                        PcbPlacementConstraints.Anchor.LEFT :
                    id.equals("JLOAD") ? PcbPlacementConstraints.Anchor.RIGHT :
                        PcbPlacementConstraints.Anchor.NONE;
                if (variant == RootPlacementVariant.NO_CONNECTOR_ANCHORS)
                    anchor = PcbPlacementConstraints.Anchor.NONE;
                String regionId = variant == RootPlacementVariant.COLLAPSED_REGIONS ?
                    "rb30-all" : region;
                String regionLabel = variant == RootPlacementVariant.COLLAPSED_REGIONS ?
                    "RB30 all packages" : region;
                int accessMargin = variant == RootPlacementVariant.WIDER_ACCESS_MARGIN ?
                    40 : 20;
                parts.add(new PcbPlacementConstraints.Part(id, regionId, regionLabel,
                    "low-voltage-board", anchor, accessMargin));
            }
            return new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM);
        }
    }

    private static final class RouteOutcome {
        final String cohort;
        final long seed;
        final Manifest manifest;
        PcbPlacementPlanner.Plan selectedPlan;
        PcbBoardLayout selectedLayout;
        String selectedOutcome = "NO_CANDIDATE";
        String selectedDetail = "";
        int placementCandidates;
        int routingCandidates;
        int successfulCandidates;
        boolean hardRouterObstacle;
        String firstHardObstacle = "";
        long elapsedNanos;
        final Vector<String> candidateOutcomes = new Vector<String>();

        RouteOutcome(String cohort, long seed) {
            this.cohort = cohort;
            this.seed = seed;
            manifest = new Manifest(seed);
        }
    }

    private static final class RootRouteOutcome {
        final String cohort;
        final long seed;
        final RootPlanManifest manifest;
        PcbPlacementPlanner.Plan selectedPlan;
        PcbBoardLayout selectedLayout;
        String selectedOutcome = "NO_CANDIDATE";
        String selectedDetail = "";
        int placementCandidates;
        int routingCandidates;
        int successfulCandidates;
        boolean hardRouterObstacle;
        String firstHardObstacle = "";
        long elapsedNanos;
        final Vector<String> candidateOutcomes = new Vector<String>();

        RootRouteOutcome(String cohort, long seed) {
            this(cohort, seed, RootPlacementVariant.EXACT_ROOT);
        }

        RootRouteOutcome(String cohort, long seed, RootPlacementVariant variant) {
            this.cohort = cohort;
            this.seed = seed;
            manifest = new RootPlanManifest(seed, variant);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Q30_PILOT_BEGIN {\"schema\":1,\"scope\":\"developer-only physical/logical manifest; no solver/player qualification\",\"policy\":\"THT_SINGLE_FACE@1\",\"face\":\"BOTTOM\"}");
        System.out.println("Q30_P09_LIMITS {\"maxParts\":16,\"maxPads\":40,\"maxNets\":16,\"maxNetDegree\":10,\"maxArea\":2250000,\"maxUniqueCopper\":16000,\"maxSegments\":160,\"normalAdmission\":\"REJECTED_FOR_Q30_POPULATION_OR_OTHER_UNQUALIFIED_BOUND\"}");
        System.out.println("Q30_P07_FROZEN_REFERENCE {\"source\":\"P07LayerCorpus prior receipt\",\"family\":\"RB30\",\"seed\":3,\"policy\":\"P05_REFERENCE\",\"parts\":30,\"area\":5062500,\"bottomRoutes\":58,\"uniqueCopper\":20380,\"expansions\":811642,\"elapsedMs\":403.0,\"structuralOnly\":true}");

        if (args.length > 0 && "--root-plan".equals(args[0])) {
            runRootPlanCorpus();
            return;
        }
        if (args.length > 0 && "--two-layer".equals(args[0])) {
            runTwoLayerComparison(args);
            return;
        }
        if (args.length > 0 && "--geometry".equals(args[0])) {
            exportGeometry(args);
            return;
        }
        if (args.length > 0 && "--p1-policy".equals(args[0])) {
            runP1Policy(args);
            return;
        }
        if (args.length > 0 && "--root-adjustments".equals(args[0])) {
            runRootAdjustmentPilot();
            return;
        }

        Vector<RouteOutcome> outcomes = new Vector<RouteOutcome>();
        for (long seed : REPRESENTATIVE) outcomes.add(run("representative", seed));
        for (long seed : HELD_OUT) outcomes.add(run("held-out", seed));

        int routePass = 0, routeFail = 0, placementFail = 0, hardObstacles = 0;
        long totalExpansions = 0L;
        Vector<Long> successfulSeeds = new Vector<Long>();
        Vector<Long> failedSeeds = new Vector<Long>();
        for (RouteOutcome outcome : outcomes) {
            if (outcome.selectedLayout != null && "ROUTED".equals(outcome.selectedOutcome)) {
                routePass++;
                successfulSeeds.add(Long.valueOf(outcome.seed));
                totalExpansions += outcome.selectedLayout.getRoutingExpansions();
            } else {
                routeFail++;
                failedSeeds.add(Long.valueOf(outcome.seed));
            }
            if (outcome.selectedPlan == null) placementFail++;
            if (outcome.hardRouterObstacle) hardObstacles++;
        }
        double meanExpansions = routePass == 0 ? 0.0 : totalExpansions / (double) routePass;
        System.out.println("Q30_PILOT_SUMMARY {\"rows\":" + outcomes.size() +
            ",\"routePass\":" + routePass + ",\"routeFail\":" + routeFail +
            ",\"placementNoPlan\":" + placementFail + ",\"hardRouterObstacles\":" +
            hardObstacles + ",\"meanSuccessfulExpansions\":" + meanExpansions +
            ",\"successfulSeeds\":\"" + successfulSeeds + "\",\"failedSeeds\":\"" +
            failedSeeds + "\",\"normalAdmission\":\"REJECTED\",\"structuralOnly\":true}");
        System.out.println("PASS: Q30 physical pilot rows=" + outcomes.size() +
            " routePass=" + routePass + " routeFail=" + routeFail +
            " hardRouterObstacles=" + hardObstacles +
            " normalAdmission=REJECTED structuralOnly=true");
    }

    private static RouteOutcome run(String cohort, long seed) throws Exception {
        RouteOutcome result = new RouteOutcome(cohort, seed);
        PcbPlacementPlanner planner = new PcbPlacementPlanner(result.manifest.registry);
        long started = System.nanoTime();
        String lastOutcome = "NO_PLACEMENT";
        for (int candidate = 0; candidate < PcbPlacementPlanner.OUTLINE_CANDIDATES; candidate++) {
            PcbPlacementPlanner.Plan plan;
            try {
                plan = planner.plan(result.manifest.board,
                    result.manifest.board.getPlacementConstraints(), seed, candidate);
                result.placementCandidates++;
            } catch (PcbPlacementPlanner.Rejected rejected) {
                lastOutcome = "PLACEMENT_" + rejected.reason;
                result.candidateOutcomes.add(lastOutcome);
                continue;
            }
            /* Keep the last attempted plan/layout so rejected rows retain real
             * work, outline and access metrics.  A later successful candidate
             * still replaces these with its published private result. */
            result.selectedPlan = plan;
            PcbBoardLayout layout = plan.materialize();
            result.selectedLayout = layout;
            result.routingCandidates++;
            try {
                PcbNetRouter.route(layout, result.manifest.board, plan.outline, candidate,
                    OBSERVER, true, routingSeed(seed, candidate), 0, null);
                layout.validateRoutingGeometry(result.manifest.board);
                layout.validateRouteQuality();
                result.successfulCandidates++;
                result.selectedPlan = plan;
                result.selectedLayout = layout;
                result.selectedOutcome = "ROUTED";
                result.selectedDetail = "candidate=" + candidate;
                result.candidateOutcomes.add("ROUTED@" + candidate);
                break;
            } catch (PcbNetRouter.Rejected rejected) {
                lastOutcome = "ROUTING_" + rejected.reason;
                result.candidateOutcomes.add(lastOutcome);
                if (rejected.reason == PcbNetRouter.Reason.NO_PATH ||
                        rejected.reason == PcbNetRouter.Reason.GRID_LIMIT) {
                    result.hardRouterObstacle = true;
                    if (result.firstHardObstacle.length() == 0)
                        result.firstHardObstacle = rejected.reason +
                            (rejected.blockedNet == null ? "" : ":" + rejected.blockedNet);
                }
            } catch (PcbBoardLayout.RouteQualityRejectedException rejected) {
                lastOutcome = "QUALITY_" + rejected.getKind();
                result.candidateOutcomes.add(lastOutcome);
            }
        }
        if (!"ROUTED".equals(result.selectedOutcome)) {
            result.selectedOutcome = lastOutcome;
            result.selectedDetail = result.candidateOutcomes.toString();
        }
        result.elapsedNanos = System.nanoTime() - started;
        report(result);
        if (seed == 3L || seed == 23L)
            compareP07(result);
        return result;
    }

    private static void runTwoLayerComparison(String[] args) throws Exception {
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        if (count < 1 || count > 12)
            throw new IllegalArgumentException("Invalid candidate count");
        long[] seeds = args.length > 1 && !"all".equals(args[1]) ?
            new long[] { Long.parseLong(args[1]) } :
            new long[] { 0L, 1L, 3L, 17L, 42L, -1L,
                11L, 23L, 37L, 59L, 83L, -23L };
        System.out.println("Q30_TWO_LAYER_BEGIN {\"scope\":\"unapplied structural comparison\",\"source\":\"root-plan-equivalent 33-package pilot\",\"candidatesPerSeed\":" + count + ",\"p07MaxExpansions\":" + PcbLayerRoutingPrototype.MAX_EXPANSIONS + ",\"p07MaxBranchExpansions\":" + PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS + ",\"factoryLinks\":false}");
        for (long seed : seeds) {
            RootPlanManifest manifest = new RootPlanManifest(seed);
            PcbPlacementPlanner planner = new PcbPlacementPlanner(manifest.registry);
            int degree = 0;
            for (String net : manifest.board.getNetIds())
                degree = Math.max(degree, manifest.board.getNet(net).getPadIds().size());
            for (int candidate = 0; candidate < count; candidate++) {
                PcbPlacementPlanner.Plan plan;
                long started = System.nanoTime();
                try {
                    plan = planner.plan(manifest.board,
                        manifest.board.getPlacementConstraints(), manifest.layoutSeed, candidate);
                } catch (PcbPlacementPlanner.Rejected rejected) {
                    System.out.println("Q30_TWO_LAYER_PLACEMENT {\"seed\":\"" + seed +
                        "\",\"candidate\":" + candidate + ",\"outcome\":\"" + rejected.reason +
                        "\",\"elapsedMs\":" + (System.nanoTime()-started)/1000000.0 + "}");
                    continue;
                }
                PcbBoardLayout placement = plan.materialize();
                long area = (long)plan.outline.width * plan.outline.height;
                long signature = 1469598103934665603L;
                for (PcbComponentPlacement part : placement.getComponents()) {
                    signature = (signature ^ part.getComponentId().hashCode()) * 1099511628211L;
                    signature = (signature ^ part.getX()) * 1099511628211L;
                    signature = (signature ^ part.getY()) * 1099511628211L;
                }
                System.out.println("Q30_TWO_LAYER_PLACEMENT {\"seed\":\"" + seed +
                    "\",\"candidate\":" + candidate + ",\"outcome\":\"PLACED\"" +
                    ",\"topology\":\"" + manifest.topology() + "\",\"parts\":" +
                    manifest.board.getComponentIds().size() + ",\"pads\":" +
                    manifest.board.getPadIds().size() + ",\"nets\":" +
                    manifest.board.getNetIds().size() + ",\"maxNetDegree\":" + degree +
                    ",\"layoutSeed\":\"" + manifest.layoutSeed + "\",\"routingSeed\":\"" +
                    rootRoutingSeed(manifest,candidate) + "\",\"placementSignature\":\"" +
                    signature + "\",\"width\":" + plan.outline.width + ",\"height\":" +
                    plan.outline.height + ",\"area\":" + area + ",\"elapsedMs\":" +
                    (System.nanoTime()-started)/1000000.0 + "}");
                compareRoute(manifest, placement, candidate, "P05_SINGLE_FACE", null, signature, area);
                for (PcbLayerRoutingPrototype.Policy policy : new PcbLayerRoutingPrototype.Policy[] {
                        PcbLayerRoutingPrototype.Policy.ONE_LAYER,
                        PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER })
                    compareRoute(manifest, placement, candidate, "P07_"+policy, policy, signature, area);
            }
        }
        System.out.println("Q30_TWO_LAYER_END");
    }

    private static void compareRoute(RootPlanManifest manifest, PcbBoardLayout placement,
            int candidate, String policyName, PcbLayerRoutingPrototype.Policy policy,
            long signature, long area) {
        PcbBoardLayout routed = null;
        String outcome = "UNKNOWN", failedNet = "";
        int expansions = 0;
        long started = System.nanoTime();
        try {
            if (policy == null) {
                routed = placement.copyForRouting();
                PcbNetRouter.route(routed,manifest.board,routed.getBoardOutline(),candidate,
                    OBSERVER,true,rootRoutingSeed(manifest,candidate),0,null);
                outcome = "SUCCESS";
            } else {
                PcbLayerRoutingPrototype.Result result = PcbLayerRoutingPrototype.route(
                    manifest.board,placement,policy,OBSERVER);
                routed = result.layout;
                outcome = result.outcome;
                expansions = result.expansions;
                int colon = outcome.indexOf(':');
                if (colon >= 0) { failedNet = outcome.substring(0,colon); outcome = outcome.substring(colon+1); }
            }
            if (routed != null) {
                routed.validateRoutingGeometry(manifest.board);
                routed.validateRouteQuality();
                expansions = routed.getRoutingExpansions();
            }
        } catch (PcbNetRouter.Rejected rejected) {
            outcome = rejected.reason.toString();
            failedNet = rejected.blockedNet == null ? "" : rejected.blockedNet;
            if (routed != null) expansions = routed.getRoutingExpansions();
            routed = null;
        } catch (PcbBoardLayout.RouteQualityRejectedException rejected) {
            outcome = "QUALITY_" + rejected.getKind(); routed = null;
        } catch (RuntimeException rejected) {
            outcome = "INVALID_" + rejected.getClass().getSimpleName();
            failedNet = rejected.getMessage() == null ? "" : rejected.getMessage();
            routed = null;
        }
        int top = 0, bottom = 0, topSegments = 0, bottomSegments = 0;
        long topLength = 0, bottomLength = 0, unique = 0;
        int vias = 0, bends = 0, segments = 0;
        double score = 0.0;
        if (routed != null) {
            for (PcbTraceGeometry trace : routed.getTraces()) {
                int[] x = trace.getXPoints(), y = trace.getYPoints();
                long length = 0;
                int pieceCount = 0;
                for (int i=1;i<x.length;i++) {
                    long piece = Math.abs(x[i]-x[i-1]) + Math.abs(y[i]-y[i-1]);
                    if (piece > 0) pieceCount++;
                    length += piece;
                }
                if (trace.getLayer() == PcbCopperLayer.TOP) {
                    top++; topLength += length; topSegments += pieceCount;
                } else {
                    bottom++; bottomLength += length; bottomSegments += pieceCount;
                }
            }
            vias = routed.getHoles().size();
            PcbRouteMetrics metrics = PcbRouteMetrics.measure(routed.getTraces());
            unique = metrics.uniqueLength; bends = metrics.bends; segments = metrics.segments;
            score = routed.getRouteQualityScore(manifest.board);
        }
        System.out.println("Q30_TWO_LAYER_ROUTE {\"seed\":\"" + manifest.seed +
            "\",\"candidate\":" + candidate + ",\"placementSignature\":\"" + signature +
            "\",\"policy\":\"" + policyName + "\",\"outcome\":\"" + escape(outcome) +
            "\",\"failedNet\":\"" + escape(failedNet) + "\",\"topTraces\":" + top +
            ",\"bottomTraces\":" + bottom + ",\"topSegments\":" + topSegments +
            ",\"bottomSegments\":" + bottomSegments + ",\"topLength\":" + topLength +
            ",\"bottomLength\":" + bottomLength + ",\"vias\":" + vias +
            ",\"segments\":" + segments + ",\"bends\":" + bends +
            ",\"uniqueCopper\":" + unique + ",\"qualityScore\":" + score +
            ",\"expansions\":" + expansions + ",\"area\":" + area +
            ",\"validConnectivityShortClearance\":" + (routed != null) +
            ",\"genuineTwoLayer\":" + (top > 0 && bottom > 0 && vias > 0) +
            ",\"elapsedMs\":" + (System.nanoTime()-started)/1000000.0 + "}");
    }

    /** Exact selected Q30 medium-policy layout for preliminary face inspection. */
    private static void exportGeometry(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException(
            "Usage: --geometry <signed-long-seed>");
        long seed = Long.parseLong(args[1]);
        RootPlanManifest manifest = new RootPlanManifest(seed);
        MediumBoardPhysicalPolicy.Result result = new SeededPcbLayoutGenerator(
            manifest.registry).generateWithPolicyResult(manifest.board,
                manifest.layoutSeed, manifest.routingSeed);
        if (!result.accepted()) throw new IllegalStateException(
            "No inspectable medium-policy route: " + result.failure);
        int candidate = result.statistics.selectedPlacementAttempt;
        PcbBoardLayout layout = result.layout;
        layout.validateRoutingGeometry(manifest.board);
        layout.validateRouteQuality();
        StringBuilder out = new StringBuilder();
        Rectangle outline = layout.getBoardOutline();
        out.append("{\"schema\":1,\"scope\":\"structural-only Q30 geometry; not production workbench\",");
        out.append("\"seed\":\"").append(seed).append("\",\"candidate\":").append(candidate);
        out.append(",\"topology\":\"").append(manifest.topology()).append("\"");
        out.append(",\"policy\":\"").append(result.statistics.policyIdentity).append("\"");
        out.append(",\"selectedRoutePolicy\":\"")
            .append(result.statistics.selectedRoutePolicy).append("\",\"outline\":");
        appendRectangle(out, outline);
        out.append(",\"components\":[");
        int n = 0;
        for (PcbComponentPlacement component : layout.getComponents()) {
            if (n++ > 0) out.append(',');
            out.append("{\"id\":\"").append(escape(component.getComponentId()));
            out.append("\",\"package\":\"").append(escape(component.getPhysicalPackage().getId()));
            out.append("\",\"region\":\"").append(escape(
                manifest.board.getPlacementConstraints().get(component.getComponentId()).regionId));
            out.append("\",\"courtyard\":");
            appendRectangle(out, component.getRoutingCourtyard());
            out.append('}');
        }
        out.append("],\"pads\":[");
        n = 0;
        for (PcbPadPlacement pad : layout.getPads()) {
            if (n++ > 0) out.append(',');
            out.append("{\"id\":\"").append(escape(pad.getPadId()));
            out.append("\",\"net\":\"").append(escape(
                manifest.board.getPad(pad.getPadId()).getNetId()));
            out.append("\",\"x\":").append(pad.getX());
            out.append(",\"y\":").append(pad.getY()).append('}');
        }
        out.append("],\"traces\":[");
        n = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            if (n++ > 0) out.append(',');
            out.append("{\"net\":\"").append(escape(trace.getNetId()));
            out.append("\",\"layer\":\"").append(trace.getLayer());
            out.append("\",\"x\":");
            appendInts(out, trace.getXPoints());
            out.append(",\"y\":");
            appendInts(out, trace.getYPoints());
            out.append('}');
        }
        out.append("],\"vias\":[");
        n = 0;
        for (PcbBoardHole hole : layout.getHoles()) {
            if (n++ > 0) out.append(',');
            out.append("{\"id\":\"").append(escape(hole.id));
            out.append("\",\"net\":\"").append(escape(hole.netId));
            out.append("\",\"x\":").append(hole.x);
            out.append(",\"y\":").append(hole.y);
            out.append(",\"landRadius\":").append(hole.landRadius).append('}');
        }
        out.append("],\"routeScore\":").append(layout.getRouteQualityScore(manifest.board));
        out.append(",\"expansions\":").append(result.statistics.routingExpansions).append('}');
        System.out.println("Q30_GEOMETRY " + out);
    }

    private static void appendRectangle(StringBuilder out, Rectangle value) {
        out.append('[').append(value.x).append(',').append(value.y).append(',')
            .append(value.width).append(',').append(value.height).append(']');
    }

    private static void appendInts(StringBuilder out, int[] values) {
        out.append('[');
        for (int index = 0; index < values.length; index++) {
            if (index > 0) out.append(',');
            out.append(values[index]);
        }
        out.append(']');
    }

    private static void runP1Policy(String[] args) {
        long[] seeds = args.length > 1 && !"all".equals(args[1]) ?
            new long[] { Long.parseLong(args[1]) } :
            new long[] { 0L, 1L, 3L, 17L, 42L, -1L,
                11L, 23L, 37L, 59L, 83L, -23L };
        for (long seed : seeds) {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            TroubleshootBoard board = plan.board();
            long started = System.nanoTime();
            MediumBoardPhysicalPolicy.Result result =
                new SeededPcbLayoutGenerator(StandardPcbFootprintProviders.createRegistry(),
                    OBSERVER).generateWithPolicyResult(board, plan.layoutSeed,
                    plan.routingSeed, OBSERVER);
            MediumBoardPhysicalPolicy.Statistics stats = result.getStatistics();
            PcbBoardLayout layout = result.getLayout();
            if (layout != null) layout.validateGeometry(board);
            int topSegments = 0, bottomSegments = 0;
            long topLength = 0, bottomLength = 0;
            PcbRouteMetrics route = null;
            int vias = 0;
            double score = 0.0;
            long area = 0;
            if (layout != null) {
                Rectangle outline = layout.getBoardOutline();
                area = (long) outline.width * outline.height;
                vias = layout.getHoles().size();
                for (PcbTraceGeometry trace : layout.getTraces()) {
                    int[] x = trace.getXPoints(), y = trace.getYPoints();
                    for (int index = 1; index < x.length; index++) {
                        long distance = Math.abs(x[index] - x[index - 1]) +
                            Math.abs(y[index] - y[index - 1]);
                        if (trace.getLayer() == PcbCopperLayer.TOP) {
                            topLength += distance;
                            if (distance != 0) topSegments++;
                        } else {
                            bottomLength += distance;
                            if (distance != 0) bottomSegments++;
                        }
                    }
                }
                route = PcbRouteMetrics.measure(layout.getTraces());
                score = layout.getRouteQualityScore(board);
            }
            StringBuilder out = new StringBuilder();
            out.append("{\"seed\":\"").append(seed).append("\",\"topology\":\"");
            out.append(plan.topology()).append("\",\"policy\":\"");
            out.append(board.getPlacementConstraints().getPhysicalPolicyIdentity());
            out.append("\",\"planIdentity\":\"").append(escape(plan.canonical()));
            out.append("\",\"layoutSeed\":\"").append(plan.layoutSeed);
            out.append("\",\"routingSeed\":\"").append(plan.routingSeed);
            out.append("\",\"outcome\":\"").append(stats.outcome);
            out.append("\",\"failure\":\"").append(escape(String.valueOf(result.getFailure())));
            out.append("\",\"selectedRoutePolicy\":\"").append(stats.selectedRoutePolicy);
            out.append("\",\"placementCandidates\":").append(stats.placementCandidates);
            out.append(",\"placementRejections\":").append(stats.placementRejections);
            out.append(",\"placementEvaluations\":").append(stats.placementEvaluations);
            out.append(",\"routeAttempts\":").append(stats.routeAttempts);
            out.append(",\"oneFaceAttempts\":").append(stats.oneFaceAttempts);
            out.append(",\"oneFaceSuccesses\":").append(stats.oneFaceSuccesses);
            out.append(",\"twoLayerAttempts\":").append(stats.twoLayerAttempts);
            out.append(",\"twoLayerSuccesses\":").append(stats.twoLayerSuccesses);
            out.append(",\"selectedPlacementAttempt\":").append(stats.selectedPlacementAttempt);
            out.append(",\"selectedPlacementScore\":").append(stats.selectedPlacementScore);
            out.append(",\"selectedRouteQualityMilli\":").append(stats.selectedRouteQualityMilli);
            out.append(",\"rankedPlacementAttempts\":").append(stats.rankedPlacementAttempts);
            out.append(",\"placementScores\":[");
            for (int index = 0; index < stats.placementScores.size(); index++) {
                if (index > 0) out.append(',');
                out.append('\"').append(escape(stats.placementScores.get(index))).append('\"');
            }
            out.append(']');
            out.append(",\"routeOutcomes\":[");
            for (int index = 0; index < stats.routeOutcomes.size(); index++) {
                if (index > 0) out.append(',');
                out.append('\"').append(escape(stats.routeOutcomes.get(index))).append('\"');
            }
            out.append("],\"parts\":").append(board.getComponentIds().size());
            out.append(",\"pads\":").append(board.getPadIds().size());
            out.append(",\"nets\":").append(board.getNetIds().size());
            out.append(",\"area\":").append(area);
            out.append(",\"topLength\":").append(topLength);
            out.append(",\"bottomLength\":").append(bottomLength);
            out.append(",\"topSegments\":").append(topSegments);
            out.append(",\"bottomSegments\":").append(bottomSegments);
            out.append(",\"vias\":").append(vias);
            out.append(",\"uniqueCopper\":").append(route == null ? 0 : route.uniqueLength);
            out.append(",\"routeScore\":").append(score);
            out.append(",\"routeExpansions\":").append(stats.routingExpansions);
            out.append(",\"routeOrders\":").append(stats.routingOrders);
            out.append(",\"elapsedMs\":").append(
                (System.nanoTime() - started) / 1000000.0);
            out.append('}');
            System.out.println("Q30_P1_POLICY " + out);
        }
    }

    private static void runRootPlanCorpus() throws Exception {
        System.out.println("Q30_ROOT_PILOT_BEGIN {\"schema\":1,\"source\":\"root Rb30Plan copied as task-owned adapter\",\"scope\":\"developer-only structural/physical route probe\",\"policy\":\"THT_SINGLE_FACE@1\",\"normalAdmission\":\"REJECTED\"}");
        Vector<RootRouteOutcome> outcomes = new Vector<RootRouteOutcome>();
        for (long seed : REPRESENTATIVE) outcomes.add(runRoot("representative", seed));
        for (long seed : HELD_OUT) outcomes.add(runRoot("held-out", seed));

        int routePass = 0, hardObstacles = 0;
        for (RootRouteOutcome outcome : outcomes) {
            if ("ROUTED".equals(outcome.selectedOutcome)) routePass++;
            if (outcome.hardRouterObstacle) hardObstacles++;
        }
        System.out.println("Q30_ROOT_PILOT_SUMMARY {\"rows\":" + outcomes.size() +
            ",\"routePass\":" + routePass + ",\"routeFail\":" +
            (outcomes.size() - routePass) + ",\"hardRouterObstacles\":" +
            hardObstacles + ",\"normalAdmission\":\"REJECTED\",\"structuralOnly\":true}");
    }

    private static void runRootAdjustmentPilot() throws Exception {
        System.out.println("Q30_ROOT_ADJUSTMENT_BEGIN {\"schema\":1,\"source\":\"root Rb30Plan equivalent only\",\"scope\":\"generic placement-demand comparison\",\"policy\":\"THT_SINGLE_FACE@1\",\"netsUnchanged\":true,\"clearanceUnchanged\":true,\"routerBudgetUnchanged\":true,\"firstSeeds\":\"[0,3]\",\"heldOutIfAnyRoute\":\"[23,83]\"}");
        RootPlacementVariant[] variants = RootPlacementVariant.values();
        long[] firstSeeds = { 0L, 3L };
        long[] heldOutSeeds = { 23L, 83L };
        int totalRows = 0, totalRoutes = 0;
        for (RootPlacementVariant variant : variants) {
            Vector<RootRouteOutcome> outcomes = new Vector<RootRouteOutcome>();
            for (long seed : firstSeeds) outcomes.add(runRoot("first-two", seed, variant));
            boolean routed = false;
            for (RootRouteOutcome outcome : outcomes)
                routed |= "ROUTED".equals(outcome.selectedOutcome);
            if (routed) {
                for (long seed : heldOutSeeds)
                    outcomes.add(runRoot("held-out", seed, variant));
            }
            int variantRoutes = 0, hardObstacles = 0;
            long minArea = Long.MAX_VALUE, maxArea = 0L, maxExpansions = 0L;
            for (RootRouteOutcome outcome : outcomes) {
                if ("ROUTED".equals(outcome.selectedOutcome)) variantRoutes++;
                if (outcome.hardRouterObstacle) hardObstacles++;
                if (outcome.selectedPlan != null) {
                    long area = (long) outcome.selectedPlan.outline.width *
                        outcome.selectedPlan.outline.height;
                    minArea = Math.min(minArea, area);
                    maxArea = Math.max(maxArea, area);
                }
                if (outcome.selectedLayout != null)
                    maxExpansions = Math.max(maxExpansions,
                        outcome.selectedLayout.getRoutingExpansions());
            }
            if (minArea == Long.MAX_VALUE) minArea = 0L;
            totalRows += outcomes.size();
            totalRoutes += variantRoutes;
            System.out.println("Q30_ROOT_ADJUSTMENT_VARIANT {\"variant\":\"" +
                variant + "\",\"rows\":" + outcomes.size() +
                ",\"routePass\":" + variantRoutes + ",\"routeFail\":" +
                (outcomes.size() - variantRoutes) + ",\"hardRouterObstacles\":" +
                hardObstacles + ",\"areaRange\":\"" + minArea + ".." + maxArea +
                "\",\"maxExpansions\":" + maxExpansions +
                ",\"heldOutRun\":" + routed +
                ",\"normalAdmission\":\"REJECTED\",\"structuralOnly\":true}");
        }
        System.out.println("Q30_ROOT_ADJUSTMENT_SUMMARY {\"variants\":" + variants.length +
            ",\"rows\":" + totalRows + ",\"routePass\":" + totalRoutes +
            ",\"normalAdmission\":\"REJECTED\",\"structuralOnly\":true}");
    }

    private static RootRouteOutcome runRoot(String cohort, long seed) throws Exception {
        return runRoot(cohort, seed, RootPlacementVariant.EXACT_ROOT);
    }

    private static RootRouteOutcome runRoot(String cohort, long seed,
            RootPlacementVariant variant) throws Exception {
        RootRouteOutcome result = new RootRouteOutcome(cohort, seed, variant);
        PcbPlacementPlanner planner = new PcbPlacementPlanner(result.manifest.registry);
        long started = System.nanoTime();
        String lastOutcome = "NO_PLACEMENT";
        for (int candidate = 0; candidate < PcbPlacementPlanner.OUTLINE_CANDIDATES; candidate++) {
            PcbPlacementPlanner.Plan plan;
            try {
                plan = planner.plan(result.manifest.board,
                    result.manifest.board.getPlacementConstraints(),
                    result.manifest.layoutSeed, candidate);
                result.placementCandidates++;
            } catch (PcbPlacementPlanner.Rejected rejected) {
                lastOutcome = "PLACEMENT_" + rejected.reason;
                result.candidateOutcomes.add(lastOutcome);
                continue;
            }
            result.selectedPlan = plan;
            result.selectedLayout = plan.materialize();
            result.routingCandidates++;
            try {
                PcbNetRouter.route(result.selectedLayout, result.manifest.board,
                    plan.outline, candidate, OBSERVER, true,
                    rootRoutingSeed(result.manifest, candidate), 0, null);
                result.selectedLayout.validateRoutingGeometry(result.manifest.board);
                result.selectedLayout.validateRouteQuality();
                result.successfulCandidates++;
                result.selectedOutcome = "ROUTED";
                result.selectedDetail = "candidate=" + candidate;
                result.candidateOutcomes.add("ROUTED@" + candidate);
                break;
            } catch (PcbNetRouter.Rejected rejected) {
                lastOutcome = "ROUTING_" + rejected.reason;
                result.candidateOutcomes.add(lastOutcome);
                if (rejected.reason == PcbNetRouter.Reason.NO_PATH ||
                        rejected.reason == PcbNetRouter.Reason.GRID_LIMIT) {
                    result.hardRouterObstacle = true;
                    if (result.firstHardObstacle.length() == 0)
                        result.firstHardObstacle = rejected.reason +
                            (rejected.blockedNet == null ? "" : ":" + rejected.blockedNet);
                }
            } catch (PcbBoardLayout.RouteQualityRejectedException rejected) {
                lastOutcome = "QUALITY_" + rejected.getKind();
                result.candidateOutcomes.add(lastOutcome);
            }
        }
        if (!"ROUTED".equals(result.selectedOutcome)) {
            result.selectedOutcome = lastOutcome;
            result.selectedDetail = result.candidateOutcomes.toString();
        }
        result.elapsedNanos = System.nanoTime() - started;
        reportRoot(result);
        return result;
    }

    private static long rootRoutingSeed(RootPlanManifest manifest, int candidate) {
        return manifest.routingSeed ^ (0x632be59bd9b4e019L * (candidate + 1L));
    }

    private static void reportRoot(RootRouteOutcome result) {
        TroubleshootBoard board = result.manifest.board;
        PcbBoardLayout layout = result.selectedLayout;
        PcbPlacementPlanner.Plan plan = result.selectedPlan;
        Rectangle outline = plan == null ? null : plan.outline;
        PcbRouteMetrics metrics = layout == null ? null :
            PcbRouteMetrics.measure(layout.getTraces());
        PcbRoutingWork.Statistics work = layout == null ? null :
            layout.getRoutingRecoveryStatistics();
        int maxDegree = 0;
        for (String net : board.getNetIds())
            maxDegree = Math.max(maxDegree, board.getNet(net).getPadIds().size());
        System.out.println("Q30_ROOT_ROW {\"cohort\":\"" + result.cohort +
            "\",\"seed\":\"" + result.seed + "\",\"topology\":\"" +
            result.manifest.topology() + "\",\"placementVariant\":\"" +
            result.manifest.placementVariant + "\",\"parts\":" + board.getComponentIds().size() +
            ",\"pads\":" + board.getPadIds().size() + ",\"nets\":" +
            board.getNetIds().size() + ",\"maxNetDegree\":" + maxDegree +
            ",\"packageCounts\":\"" + result.manifest.packageCounts +
            "\",\"placementCandidates\":" + result.placementCandidates +
            ",\"routingCandidates\":" + result.routingCandidates +
            ",\"successfulCandidates\":" + result.successfulCandidates +
            ",\"outcome\":\"" + result.selectedOutcome + "\",\"detail\":\"" +
            escape(result.selectedDetail) + "\",\"candidateOutcomes\":\"" +
            escape(result.candidateOutcomes.toString()) +
            "\",\"hardRouterObstacle\":" + result.hardRouterObstacle +
            ",\"firstHardObstacle\":\"" + escape(result.firstHardObstacle) +
            "\",\"width\":" + (outline == null ? 0 : outline.width) +
            ",\"height\":" + (outline == null ? 0 : outline.height) +
            ",\"area\":" + (outline == null ? 0L : (long) outline.width * outline.height) +
            ",\"uniqueCopper\":" + (metrics == null ? 0L : metrics.uniqueLength) +
            ",\"routeSegments\":" + (metrics == null ? 0 : metrics.segments) +
            ",\"expansions\":" + (layout == null ? 0 : layout.getRoutingExpansions()) +
            ",\"work\":\"" + escape(workSummary(work)) +
            "\",\"fitProbePixels\":" + (layout == null ? 0.0 : fitProbePixels(layout)) +
            ",\"layerPolicy\":\"" + layerPolicyState(board, layout) +
            "\",\"p09NormalAdmission\":\"" + p09Reason(board, layout) +
            "\",\"p09CapViolations\":\"" + escape(p09Overflow(board, layout)) +
            "\",\"elapsedMs\":" + (result.elapsedNanos / 1000000.0) +
            ",\"structuralOnly\":true}");
    }

    private static long routingSeed(long seed, int candidate) {
        long value = seed ^ (0x632be59bd9b4e019L * (candidate + 1L));
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        return value ^ (value >>> 31);
    }

    private static void report(RouteOutcome result) {
        TroubleshootBoard board = result.manifest.board;
        PcbBoardLayout layout = result.selectedLayout;
        PcbPlacementPlanner.Plan plan = result.selectedPlan;
        Rectangle outline = plan == null ? null : plan.outline;
        PcbRouteMetrics metrics = layout == null ? null : PcbRouteMetrics.measure(layout.getTraces());
        PcbRoutingWork.Statistics work = layout == null ? null : layout.getRoutingRecoveryStatistics();
        int maxDegree = 0;
        for (String net : board.getNetIds())
            maxDegree = Math.max(maxDegree, board.getNet(net).getPadIds().size());
        double fitProbe = layout == null ? 0.0 : fitProbePixels(layout);
        String p09 = p09Reason(board, layout);
        String p09Overflow = p09Overflow(board, layout);
        String layerPolicy = layerPolicyState(board, layout);
        System.out.println("Q30_ROW {\"cohort\":\"" + result.cohort + "\",\"seed\":\"" +
            result.seed + "\",\"driver\":\"" + result.manifest.driver +
            "\",\"referenceArrangement\":\"" + result.manifest.references +
            "\",\"parts\":" + board.getComponentIds().size() +
            ",\"pads\":" + board.getPadIds().size() + ",\"nets\":" + board.getNetIds().size() +
            ",\"maxNetDegree\":" + maxDegree + ",\"packageCounts\":\"" +
            result.manifest.packageCounts + "\",\"placementCandidates\":" +
            result.placementCandidates + ",\"routingCandidates\":" + result.routingCandidates +
            ",\"successfulCandidates\":" + result.successfulCandidates +
            ",\"outcome\":\"" + result.selectedOutcome + "\",\"detail\":\"" +
            escape(result.selectedDetail) + "\",\"candidateOutcomes\":\"" +
            escape(result.candidateOutcomes.toString()) + "\",\"hardRouterObstacle\":" +
            result.hardRouterObstacle + ",\"firstHardObstacle\":\"" +
            escape(result.firstHardObstacle) + "\",\"width\":" + (outline == null ? 0 : outline.width) +
            ",\"height\":" + (outline == null ? 0 : outline.height) +
            ",\"area\":" + (outline == null ? 0L : (long) outline.width * outline.height) +
            ",\"uniqueCopper\":" + (metrics == null ? 0L : metrics.uniqueLength) +
            ",\"routeSegments\":" + (metrics == null ? 0 : metrics.segments) +
            ",\"bends\":" + (metrics == null ? 0 : metrics.bends) +
            ",\"routeScore\":" + (layout == null ? 0.0 : layout.getRouteQualityScore(board)) +
            ",\"expansions\":" + (layout == null ? 0 : layout.getRoutingExpansions()) +
            ",\"congestionRejections\":" + (layout == null ? 0 : layout.getRoutingCongestionRejections()) +
            ",\"work\":\"" + escape(workSummary(work)) + "\",\"fitProbePixels\":" +
            fitProbe + ",\"layerPolicy\":\"" + layerPolicy + "\",\"p09NormalAdmission\":\"" +
            p09 + "\",\"p09CapViolations\":\"" + escape(p09Overflow) +
            "\",\"elapsedMs\":" + (result.elapsedNanos / 1000000.0) +
            ",\"structuralOnly\":true}");
    }

    private static String workSummary(PcbRoutingWork.Statistics work) {
        if (work == null) return "none";
        return "outcome=" + work.outcome + ";reason=" + work.rejectionReason +
            ";orders=" + work.orderingPasses + ";ripPasses=" + work.ripUpPasses +
            ";rerouted=" + work.reroutedNets + ";expansions=" + work.expansions +
            ";failures=" + work.candidateFailures + ";conflicts=" + work.congestionRejections;
    }

    private static String p09Reason(TroubleshootBoard board, PcbBoardLayout layout) {
        if (layout == null) return "REJECTED_NO_LAYOUT";
        try {
            SupportedEnvelope.current().requireBounds(board, layout);
            return "PASS_UNEXPECTED";
        } catch (SupportedEnvelope.Rejected rejected) {
            return "REJECTED_" + rejected.reason;
        }
    }

    private static String p09Overflow(TroubleshootBoard board, PcbBoardLayout layout) {
        Vector<String> reasons = new Vector<String>();
        int parts = board.getComponentIds().size(), pads = board.getPadIds().size(),
            nets = board.getNetIds().size(), maxDegree = 0;
        if (parts > SupportedEnvelope.MAX_PARTS) reasons.add("parts=" + parts + ">" + SupportedEnvelope.MAX_PARTS);
        if (pads > SupportedEnvelope.MAX_PADS) reasons.add("pads=" + pads + ">" + SupportedEnvelope.MAX_PADS);
        if (nets > SupportedEnvelope.MAX_NETS) reasons.add("nets=" + nets + ">" + SupportedEnvelope.MAX_NETS);
        for (String net : board.getNetIds())
            maxDegree = Math.max(maxDegree, board.getNet(net).getPadIds().size());
        if (maxDegree > SupportedEnvelope.MAX_NET_DEGREE)
            reasons.add("maxNetDegree=" + maxDegree + ">" + SupportedEnvelope.MAX_NET_DEGREE);
        if (layout != null) {
            Rectangle r = layout.getBoardOutline();
            if (r.width > SupportedEnvelope.MAX_EDGE || r.height > SupportedEnvelope.MAX_EDGE ||
                    (long) r.width * r.height > SupportedEnvelope.MAX_AREA)
                reasons.add("boardAreaOrEdge=" + ((long) r.width * r.height));
        }
        TreeMap<String, Integer> caps = new TreeMap<String, Integer>();
        caps.put(PhysicalPackages.AXIAL_RESISTOR.getId(), 8);
        caps.put(PhysicalPackages.AXIAL_FUSE.getId(), 1);
        caps.put(PhysicalPackages.AXIAL_DIODE.getId(), 2);
        caps.put(PhysicalPackages.THROUGH_HOLE_LED.getId(), 3);
        caps.put(PhysicalPackages.TO92_NPN.getId(), 2);
        caps.put(PhysicalPackages.TO92_NMOS.getId(), 2);
        caps.put(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR.getId(), 1);
        caps.put(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR.getId(), 2);
        caps.put(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.getId(), 4);
        caps.put(PhysicalPackages.RELAY_SPDT.getId(), 1);
        caps.put(PhysicalPackages.TO220_REGULATOR_4.getId(), 1);
        caps.put(PhysicalPackages.E04_DECISION_CONTROL_5.getId(), 1);
        for (Map.Entry<String, Integer> entry : resultPackageCounts(board).entrySet()) {
            Integer cap = caps.get(entry.getKey());
            if (cap != null && entry.getValue().intValue() > cap.intValue())
                reasons.add(entry.getKey() + "=" + entry.getValue() + ">" + cap);
        }
        return reasons.isEmpty() ? "none" : reasons.toString();
    }

    private static TreeMap<String, Integer> resultPackageCounts(TroubleshootBoard board) {
        TreeMap<String, Integer> counts = new TreeMap<String, Integer>();
        for (String id : board.getComponentIds()) {
            String packageId = board.getComponent(id).getPhysicalPackage().getId();
            Integer old = counts.get(packageId);
            counts.put(packageId, old == null ? 1 : old + 1);
        }
        return counts;
    }

    private static String layerPolicyState(TroubleshootBoard board, PcbBoardLayout layout) {
        if (layout == null) return "UNROUTED_SINGLE_FACE_REQUEST";
        if (!layout.getHoles().isEmpty()) return "FAIL_HOLES";
        for (PcbComponentPlacement component : layout.getComponents()) {
            if (component.getMountingSide() != PcbBoardSide.TOP ||
                    component.getRotation() != PcbRotation.DEG_0)
                return "FAIL_POSE";
        }
        for (PcbPadPlacement pad : layout.getPads()) {
            if (pad.getAttachment() != PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                    !PcbCopperAccess.canProbe(pad, PcbBoardSide.BOTTOM))
                return "FAIL_ACCESS";
        }
        for (PcbTraceGeometry trace : layout.getTraces())
            if (trace.getLayer() != board.getPlacementConstraints().routingLayer)
                return "FAIL_MIXED_FACE";
        return "PASS_SINGLE_DECLARED_BOTTOM_FACE";
    }

    private static double fitProbePixels(PcbBoardLayout layout) {
        Rectangle outline = layout.getBoardOutline();
        double scale = Math.min(1000.0 / outline.width, 700.0 / outline.height);
        double smallest = Double.MAX_VALUE;
        for (PcbPadPlacement pad : layout.getPads()) {
            Rectangle probe = pad.getProbeBounds();
            smallest = Math.min(smallest, Math.min(probe.width, probe.height) * scale);
        }
        return smallest == Double.MAX_VALUE ? 0.0 : smallest;
    }

    private static void compareP07(RouteOutcome result) throws Exception {
        if (result.selectedPlan == null) {
            System.out.println("Q30_P07_ROW {\"seed\":\"" + result.seed +
                "\",\"outcome\":\"NOT_RUN_NO_PLACEMENT\",\"structuralOnly\":true}");
            return;
        }
        for (PcbLayerRoutingPrototype.Policy policy : new PcbLayerRoutingPrototype.Policy[] {
                PcbLayerRoutingPrototype.Policy.ONE_LAYER,
                PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER }) {
            PcbBoardLayout placement = result.selectedPlan.materialize();
            long started = System.nanoTime();
            PcbLayerRoutingPrototype.Result routed = PcbLayerRoutingPrototype.route(
                result.manifest.board, placement, policy, OBSERVER, 1000000);
            PcbRouteMetrics metrics = routed.accepted() ?
                PcbRouteMetrics.measure(routed.layout.getTraces()) : null;
            int top = 0, bottom = 0;
            if (routed.accepted()) for (PcbTraceGeometry trace : routed.layout.getTraces()) {
                if (trace.getLayer() == PcbCopperLayer.TOP) top++; else bottom++;
            }
            System.out.println("Q30_P07_ROW {\"seed\":\"" + result.seed +
                "\",\"policy\":\"" + policy + "\",\"outcome\":\"" + routed.outcome +
                "\",\"expansions\":" + routed.expansions + ",\"orderings\":" +
                routed.orderings + ",\"vias\":" + routed.vias + ",\"links\":" + routed.links +
                ",\"topRoutes\":" + top + ",\"bottomRoutes\":" + bottom +
                ",\"uniqueCopper\":" + (metrics == null ? 0L : metrics.uniqueLength) +
                ",\"segments\":" + (metrics == null ? 0 : metrics.segments) +
                ",\"elapsedMs\":" + ((System.nanoTime() - started) / 1000000.0) +
                ",\"normalAdoption\":false,\"structuralOnly\":true}");
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", " ").replace("\n", " ");
    }
}
