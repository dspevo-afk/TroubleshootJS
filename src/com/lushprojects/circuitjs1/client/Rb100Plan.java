package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.TreeMap;
import java.util.Vector;

/** Developer-only RB100 intent. It declares inventory, but grants no player admission. */
final class Rb100Plan {
    static final String FAMILY_ID = "RB100_CONTROL_PILOT";
    static final int PLAN_VERSION = 1;
    static final int MODEL_VERSION = 4;
    static final double CIN33_ESR_OHMS = 1;
    static final int PACKAGE_COUNT = 100;
    static final double LOAD12_VOLTS = 12;
    static final double LOAD12_MAX_AMPS = 0.5;
    static final double SENSOR_LOW_VOLTS = 0;
    static final double SENSOR_HIGH_VOLTS = 5;
    static final double SENSOR_MAX_AMPS = 0.25;
    static final double EXTERNAL_LOAD_OHMS = 180;
    static final double EXTERNAL_LOAD_HENRIES = 0.01;
    static final double MONITOR_SHUNT_OHMS = 1;
    static final double MONITOR_FILTER_RESISTOR_OHMS = 10000;
    static final double MONITOR_FILTER_FARADS = 100e-9;
    static final double MONITOR_FILTER_SECONDS = 0.001;
    static final double MONITOR_REFERENCE_HIGH_OHMS = 19000;
    static final double MONITOR_REFERENCE_LOW_OHMS = 1000;

    enum Variant { SHARED_HYSTERETIC_LINEAR, SEPARATE_DIRECT_SWITCHING }
    enum Kind { POWER, SOURCE, REGULATOR, DECISION, DRIVER, RELAY, RESISTOR, CAPACITOR, DIODE, OUTPUT_HEADER }

    static final class Part {
        final String id, type, channel, inputId, purpose;
        final Kind kind;
        final PhysicalPackage physicalPackage;
        final double value;
        private final String[] nets;

        private Part(String id, String type, Kind kind, PhysicalPackage physicalPackage,
                double value, String channel, String inputId, String purpose, String... nets) {
            if (id == null || id.trim().length() == 0 || type == null || type.trim().length() == 0 ||
                    kind == null || physicalPackage == null || purpose == null || purpose.trim().length() == 0 ||
                    nets == null || nets.length != physicalPackage.getTerminalCount() ||
                    Double.isNaN(value) || Double.isInfinite(value) || value < 0 ||
                    (inputId != null && inputId.trim().length() == 0) ||
                    (channel != null && channel.trim().length() == 0))
                throw new IllegalArgumentException("Incomplete RB100 package declaration");
            this.id = id;
            this.type = type;
            this.kind = kind;
            this.physicalPackage = physicalPackage;
            this.value = value;
            this.channel = channel;
            this.inputId = inputId;
            this.purpose = purpose;
            this.nets = new String[nets.length];
            System.arraycopy(nets, 0, this.nets, 0, nets.length);
            for (String net : this.nets)
                if (net == null || net.trim().length() == 0)
                    throw new IllegalArgumentException("Missing RB100 net: " + id);
        }

        String[] netIds() {
            String[] copy = new String[nets.length];
            System.arraycopy(nets, 0, copy, 0, nets.length);
            return copy;
        }

        String[] terminalIds() {
            Vector<String> terminals = physicalPackage.getTerminalIds();
            return terminals.toArray(new String[terminals.size()]);
        }

        String canonical() {
            StringBuilder out = new StringBuilder(id + ":" + type + ":" +
                (channel == null ? "-" : channel) + ":" +
                (inputId == null ? "-" : inputId) + ":" + purpose + ":" + kind.name() + ":" +
                physicalPackage.getId() + ":" + Double.toString(value));
            String[] terminals = terminalIds();
            for (int index = 0; index < nets.length; index++)
                out.append('|').append(terminals[index]).append('=').append(nets[index]);
            return out.toString();
        }
    }

    final long seed;
    final Variant variant;
    final Rb56Plan base;

    private final Vector<Part> inventory = new Vector<Part>();

    private Rb100Plan(long seed, Variant variant, Rb56Plan base) {
        this.seed = seed;
        this.variant = variant;
        this.base = base;
        declareBase();
        declareAddedChannels();
        declareSwitchedOutputs();
        declareThreeVoltRail();
        declareMonitor();
        declareAuxiliaryInterlock();
        declareInterfaces();
        validateInventory();
    }

    static Rb100Plan create(long seed, Variant variant) {
        if (variant == null) throw new IllegalArgumentException("Missing RB100 structural variant");
        NamedRandomStreams streams = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,
            seed, FAMILY_ID, PLAN_VERSION);
        long baseSeed = streams.deviceSeed(NamedRandomStreams.Concern.TOPOLOGY, 1, "base-control");
        Rb56Plan.ReferenceArrangement arrangement = variant == Variant.SHARED_HYSTERETIC_LINEAR ?
            Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC :
            Rb56Plan.ReferenceArrangement.SEPARATE_DIRECT;
        Rb56Plan base = Rb56Plan.configured(baseSeed, 2, arrangement, false, false, 3, 0, false);
        return new Rb100Plan(seed, variant, base);
    }

    RelayDriverProvider driver(String channel) {
        if ("A".equals(channel) || "B".equals(channel)) return base.driver(channel);
        if ("C".equals(channel) || "D".equals(channel) || "E".equals(channel) ||
                "F".equals(channel) || "AUX".equals(channel))
            return new RelayDriverProvider.Nmos();
        if ("INV".equals(channel)) return new RelayDriverProvider.Bjt();
        throw new IllegalArgumentException("Unknown RB100 driver channel: " + channel);
    }

    String referenceNet(String channel) {
        if ("A".equals(channel) || "B".equals(channel)) return base.referenceNet(channel);
        if ("C".equals(channel)) return base.referenceNet("A");
        if ("D".equals(channel)) return base.referenceNet("B");
        throw new IllegalArgumentException("Unknown RB100 reference channel: " + channel);
    }

    int physicalPackageCount() { return inventory.size(); }
    Vector<Part> parts() { return new Vector<Part>(inventory); }

    Vector<Part> tailParts() {
        Vector<Part> result = new Vector<Part>();
        for (Part part : inventory) if (part.kind != Kind.POWER) result.add(part);
        return result;
    }

    Part part(String id) {
        if (id == null) throw new IllegalArgumentException("Unknown RB100 package: null");
        for (Part part : inventory) if (part.id.equals(id)) return part;
        throw new IllegalArgumentException("Unknown RB100 package: " + id);
    }

    String canonical() {
        StringBuilder out = new StringBuilder("rb100-plan@" + PLAN_VERSION + ";family=" + FAMILY_ID +
            ";electrical-model=" + MODEL_VERSION + ";CIN33-series-ESR=" + CIN33_ESR_OHMS +
            ";seed=" + Long.toString(seed) + ";variant=" + variant.name() +
            ";base=rb56-plan@" + Rb56Plan.PLAN_VERSION + ";base-seed=" + Long.toString(base.seed) +
            ";base-reference=" + base.referenceArrangement.name() +
            ";driver-C-D=NMOS;packages=" + inventory.size() +
            ";model-bounds=LOAD12:12V@0.5A;SENSORS:0V/5V@0.25A;" +
            "RELAY-LOADS:4x180ohm;DC-LOADS:2x(180ohm+10mH);MONITOR:1ohm;" +
            "MONITOR-FILTER:10kohm*100nF=1ms;MON-REF:19kohm/1kohm;" +
            "RB100-SOURCE-MODEL=FAMILY_SCOPED;GLOBAL-DEFAULT=0.25A-UNCHANGED;" +
            "load-topology=shared-LOAD12-positive+CTRL_RETURN-common-non-isolated+E-F-low-side;" +
            "external-loads=relay-resistors-and-two-DC-RL-loads-not-board-packages;");
        for (Part part : inventory) out.append('[').append(part.canonical()).append(']');
        return out.toString();
    }

    /** Basic board identity only; placement and player admission remain separate owners. */
    TroubleshootBoard board() {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID + "_BOARD");
        TreeMap<String, BoardNet.RoutingRole> nets = new TreeMap<String, BoardNet.RoutingRole>();
        for (Part part : inventory) for (String net : part.netIds()) nets.put(net, role(net));
        for (String net : nets.keySet()) board.addNet(new BoardNet(net, nets.get(net)));
        for (Part part : inventory) {
            board.addComponent(new BoardComponent(part.id, part.type, part.physicalPackage));
            String[] terminals = part.terminalIds(), netIds = part.netIds();
            for (int index = 0; index < terminals.length; index++)
                board.addPad(new BoardPad(part.id + "." + terminals[index], part.id,
                    terminals[index], netIds[index]));
            if (part.inputId != null)
                board.addPowerInput(new ExternalBoardPowerInput(part.inputId,
                    part.id + "." + terminals[0], part.id + "." + terminals[1],
                    netIds[0], netIds[1]));
        }
        board.validate();
        return board;
    }

    private void declareBase() {
        for (Rb56Plan.Part source : base.parts()) {
            Kind kind;
            try { kind = Kind.valueOf(source.kind.name()); }
            catch (IllegalArgumentException unsupported) {
                throw new IllegalStateException("RB100 base contains an undeclared package kind: " + source.id);
            }
            add(source.id, source.type, kind, source.physicalPackage, source.value,
                source.channel, source.inputId, basePurpose(source.id), importBaseNets(source));
        }
    }

    private static String[] importBaseNets(Rb56Plan.Part source) {
        String[] nets = source.netIds();
        if ("JLOAD".equals(source.id) || "JOA".equals(source.id) || "JOB".equals(source.id)) {
            String[] terminals = source.terminalIds();
            if (terminals.length != 2 || !"2".equals(terminals[1]) ||
                    !"LOAD_RETURN".equals(nets[1]))
                throw new IllegalStateException("Unexpected RB56 load-return declaration: " + source.id);
            nets[1] = "CTRL_RETURN";
        }
        return nets;
    }

    private void declareAddedChannels() {
        declareAddedChannel("C");
        declareAddedChannel("D");
    }

    private void declareAddedChannel(String channel) {
        add("JS" + channel, "CONNECTOR", Kind.SOURCE, PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            5, channel, "SENSOR_" + channel, "EXTERNAL_SENSOR_INPUT",
            channel + "_RAW", "CTRL_RETURN");
        add("U2" + channel, "SENSOR_CONTROL", Kind.DECISION, PhysicalPackages.E04_DECISION_CONTROL_5,
            0, channel, null, "SENSOR_THRESHOLD_DECISION", channel + "_SENSE",
            referenceNet(channel), "RAIL5", channel + "_CMD", "CTRL_RETURN");
        resistor("RS" + channel, 10000, channel + "_RAW", channel + "_SENSE", channel,
            "SENSOR_SERIES_RESISTOR");
        resistor("RPIN_" + channel, 100000, channel + "_SENSE", "CTRL_RETURN", channel,
            "SENSOR_RETURN_BIAS");
        capacitor("CFLT_" + channel, 100e-9, false, channel + "_SENSE", "CTRL_RETURN",
            channel, "SENSOR_INPUT_FILTER");
        resistor("RD" + channel, 1000, channel + "_CMD", channel + "_DRIVE", channel,
            "DRIVER_INPUT_RESISTOR");
        resistor("RPD_" + channel, 100000, channel + "_DRIVE", "CTRL_RETURN", channel,
            "DRIVER_INPUT_PULLDOWN");
        RelayDriverProvider selected = driver(channel);
        add("Q" + channel, selected.getId(), Kind.DRIVER, selected.getPackage(), 0,
            channel, null, "RELAY_COIL_LOW_SIDE_SWITCH", channel + "_DRIVE",
            channel + "_COIL_LOW", "AUX_RETURN");
        add("D" + channel, "DIODE", Kind.DIODE, PhysicalPackages.AXIAL_DIODE, 0,
            channel, null, "RELAY_COIL_FLYBACK", channel + "_COIL_LOW", "RAIL5");
        add("K" + channel, "RELAY", Kind.RELAY, PhysicalPackages.RELAY_SPDT, 5,
            channel, null, "LOAD_CHANGEOVER_RELAY", "RAIL5", channel + "_COIL_LOW",
            "LOAD12", "NC_" + channel, "OUT_" + channel);
    }

    private void declareSwitchedOutputs() {
        declareSwitchedOutput("E", "C_CMD");
        declareSwitchedOutput("F", "D_CMD");
    }

    private void declareSwitchedOutput(String channel, String commandNet) {
        resistor("RD" + channel, 1000, commandNet, channel + "_DRIVE", channel,
            "SWITCH_GATE_RESISTOR");
        resistor("RPD_" + channel, 100000, channel + "_DRIVE", "MON_SENSE", channel,
            "SWITCH_GATE_MONITOR_BIAS");
        RelayDriverProvider selected = driver(channel);
        add("Q" + channel, selected.getId(), Kind.DRIVER, selected.getPackage(), 0,
            channel, null, "OUTPUT_LOW_SIDE_SWITCH", channel + "_DRIVE",
            channel + "_DRAIN", "MON_SENSE");
        add("D" + channel, "DIODE", Kind.DIODE, PhysicalPackages.AXIAL_DIODE, 0,
            channel, null, "OUTPUT_FLYBACK", "OUT_" + channel, "LOAD12");
        resistor("RSH_" + channel, 1, "OUT_" + channel, channel + "_DRAIN", channel,
            "OUTPUT_CURRENT_SHUNT");
        capacitor("CSTORE_" + channel, 1e-6, false, "LOAD12", "OUT_" + channel,
            channel, "OUTPUT_STORAGE_CAPACITOR");
    }

    private void declareThreeVoltRail() {
        add("U33", "REGULATOR", Kind.REGULATOR, PhysicalPackages.TO220_REGULATOR_4, 3.3,
            null, null, variant == Variant.SHARED_HYSTERETIC_LINEAR ? "3V3_LINEAR_REGULATION" :
                "3V3_AVERAGED_SWITCHING_REGULATION", "RAIL12", "RAIL3V3", "CTRL_RETURN", "RAIL12");
        capacitor("CIN33", 22e-6, true, "RAIL12", "CTRL_RETURN", null,
            "3V3_INPUT_FILTER");
        capacitor("C33", 1e-6, false, "RAIL3V3", "CTRL_RETURN", null,
            "3V3_OUTPUT_FILTER");
    }

    private void declareMonitor() {
        resistor("RMON", MONITOR_SHUNT_OHMS, "MON_SENSE", "CTRL_RETURN", "MON", "MONITOR_SHUNT");
        resistor("RMON_IN", MONITOR_FILTER_RESISTOR_OHMS, "MON_SENSE", "MON_FILTER", "MON",
            "MONITOR_FILTER_SERIES_RESISTOR");
        add("UMON", "MONITOR_CONTROL", Kind.DECISION, PhysicalPackages.E04_DECISION_CONTROL_5,
            0, "MON", null, "MONITOR_DECISION", "MON_FILTER", "MON_REF", "RAIL3V3",
            "MON_CMD", "CTRL_RETURN");
        capacitor("CMON", MONITOR_FILTER_FARADS, false, "MON_FILTER", "CTRL_RETURN", "MON",
            "MONITOR_FILTER");
        resistor("RMON_H", MONITOR_REFERENCE_HIGH_OHMS, "RAIL3V3", "MON_REF", "MON", "MONITOR_REFERENCE_HIGH");
        resistor("RMON_L", MONITOR_REFERENCE_LOW_OHMS, "MON_REF", "CTRL_RETURN", "MON", "MONITOR_REFERENCE_LOW");
    }

    private void declareAuxiliaryInterlock() {
        resistor("RBAUX", 10000, "MON_CMD", "AUX_BASE", "AUX", "INTERLOCK_BASE_RESISTOR");
        resistor("RPUAUX", 10000, "RAIL5", "AUX_GATE", "AUX", "INTERLOCK_GATE_PULLUP");
        RelayDriverProvider inverter = driver("INV");
        add("QINV", inverter.getId(), Kind.DRIVER, inverter.getPackage(), 0,
            "INV", null, "MONITOR_COMMAND_INVERTER", "AUX_BASE", "AUX_GATE", "CTRL_RETURN");
        RelayDriverProvider auxiliary = driver("AUX");
        add("QAUX", auxiliary.getId(), Kind.DRIVER, auxiliary.getPackage(), 0,
            "AUX", null, "AUXILIARY_RETURN_INTERLOCK", "AUX_GATE", "AUX_RETURN", "CTRL_RETURN");
    }

    private void declareInterfaces() {
        outputHeader("JOC", "C", "OUT_C", "CTRL_RETURN");
        outputHeader("JOD", "D", "OUT_D", "CTRL_RETURN");
        outputHeader("JOE", "E", "LOAD12", "OUT_E");
        outputHeader("JOF", "F", "LOAD12", "OUT_F");
    }

    private void outputHeader(String id, String channel, String first, String second) {
        add(id, "OUTPUT_HEADER", Kind.OUTPUT_HEADER, PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            EXTERNAL_LOAD_OHMS, channel, null,
            "E".equals(channel) || "F".equals(channel) ? "EXTERNAL_DC_LOAD_BUS_R_PLUS_L_OUTPUT_INTERFACE" :
                "EXTERNAL_DC_LOAD_BUS_RESISTIVE_OUTPUT_INTERFACE", first, second);
    }

    private void resistor(String id, double value, String first, String second,
            String channel, String purpose) {
        add(id, "RESISTOR", Kind.RESISTOR, PhysicalPackages.AXIAL_RESISTOR,
            value, channel, null, purpose, first, second);
    }

    private void capacitor(String id, double value, boolean electrolytic, String first,
            String second, String channel, String purpose) {
        add(id, "CAPACITOR", Kind.CAPACITOR,
            electrolytic ? PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR :
                PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            value, channel, null, purpose, first, second);
    }

    private void add(String id, String type, Kind kind, PhysicalPackage physicalPackage,
            double value, String channel, String inputId, String purpose, String... nets) {
        inventory.add(new Part(id, type, kind, physicalPackage, value, channel, inputId,
            purpose, nets));
    }

    private void validateInventory() {
        if (inventory.size() != PACKAGE_COUNT)
            throw new IllegalStateException("RB100 inventory must contain exactly 100 packages: " + inventory.size());
        HashSet<String> ids = new HashSet<String>();
        HashSet<String> pads = new HashSet<String>();
        HashSet<String> inputs = new HashSet<String>();
        for (Part part : inventory) {
            if (!ids.add(part.id)) throw new IllegalStateException("Duplicate RB100 package: " + part.id);
            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            if (terminals.length != nets.length || terminals.length != part.physicalPackage.getTerminalCount())
                throw new IllegalStateException("RB100 terminal census mismatch: " + part.id);
            for (int index = 0; index < terminals.length; index++)
                if ("LOAD_RETURN".equals(nets[index]))
                    throw new IllegalStateException("Obsolete isolated load return: " + part.id + "." + terminals[index]);
                else if (!pads.add(part.id + "." + terminals[index]))
                    throw new IllegalStateException("Duplicate RB100 terminal: " + part.id + "." + terminals[index]);
            if (part.inputId != null && !inputs.add(part.inputId))
                throw new IllegalStateException("Duplicate RB100 external input: " + part.inputId);
        }
    }

    private static String basePurpose(String id) {
        if ("JAC".equals(id)) return "AC_INPUT";
        if ("F1".equals(id)) return "AC_FUSING";
        if ("D1".equals(id) || "D2".equals(id) || "D3".equals(id) || "D4".equals(id)) return "AC_RECTIFICATION";
        if ("CBULK".equals(id)) return "PRIMARY_ENERGY_STORAGE";
        if ("RPRIMARY".equals(id)) return "PRIMARY_BLEEDER";
        if ("UAC".equals(id)) return "ISOLATED_POWER_CONVERSION";
        if ("RENAC".equals(id)) return "CONVERTER_ENABLE_BIAS";
        if ("CBIAS".equals(id)) return "CONVERTER_BIAS_FILTER";
        if ("DFREE".equals(id)) return "CONVERTER_FREEWHEEL";
        if ("L1".equals(id)) return "12V_OUTPUT_INDUCTOR";
        if ("COUT".equals(id)) return "12V_OUTPUT_FILTER";
        if ("ROUT".equals(id)) return "12V_OUTPUT_BLEEDER";
        if ("UFB".equals(id)) return "ISOLATED_POWER_FEEDBACK";
        if ("DZ1".equals(id)) return "FEEDBACK_REFERENCE_DIODE";
        if ("RSENSE".equals(id)) return "FEEDBACK_SENSE_RESISTOR";
        if ("RFB".equals(id)) return "CONVERTER_FEEDBACK_PULLUP";
        if ("CFB".equals(id)) return "CONVERTER_FEEDBACK_FILTER";
        if ("U1".equals(id)) return "5V_LINEAR_REGULATOR";
        if ("REN".equals(id)) return "REGULATOR_ENABLE_BIAS";
        if ("CIN".equals(id)) return "5V_REGULATOR_INPUT_FILTER";
        if ("C5".equals(id)) return "5V_REGULATOR_OUTPUT_FILTER";
        if ("JLOAD".equals(id)) return "EXTERNAL_DC_LOAD_BUS_INPUT";
        if ("JOA".equals(id) || "JOB".equals(id)) return "EXTERNAL_DC_LOAD_BUS_RELAY_OUTPUT_INTERFACE";
        if (id.startsWith("JS")) return "EXTERNAL_SENSOR_INPUT";
        if (id.startsWith("U2")) return "SENSOR_THRESHOLD_DECISION";
        if (id.startsWith("RS")) return "SENSOR_SERIES_RESISTOR";
        if (id.startsWith("RPIN_")) return "SENSOR_RETURN_BIAS";
        if (id.startsWith("CFLT_")) return "SENSOR_INPUT_FILTER";
        if (id.startsWith("RD")) return "DRIVER_INPUT_RESISTOR";
        if (id.startsWith("RPD")) return "DRIVER_INPUT_PULLDOWN";
        if (id.startsWith("Q")) return "RELAY_COIL_LOW_SIDE_SWITCH";
        if (id.startsWith("D")) return "RELAY_COIL_FLYBACK";
        if (id.startsWith("K")) return "LOAD_CHANGEOVER_RELAY";
        if (id.startsWith("JO")) return "EXTERNAL_CONTROLLED_OUTPUT";
        if ("RREF_H".equals(id)) return "SHARED_REFERENCE_UPPER_LEG";
        if ("RREF_L".equals(id)) return "SHARED_REFERENCE_LOWER_LEG";
        if ("RREF_HA".equals(id) || "RREF_HB".equals(id)) return "SEPARATE_REFERENCE_UPPER_LEG";
        if ("RREF_LA".equals(id) || "RREF_LB".equals(id)) return "SEPARATE_REFERENCE_LOWER_LEG";
        if (id.startsWith("RFB_")) return "HYSTERETIC_SENSOR_FEEDBACK";
        throw new IllegalStateException("RB100 base package has no declared purpose: " + id);
    }

    private static BoardNet.RoutingRole role(String net) {
        if ("AUX_RETURN".equals(net) || "MON_SENSE".equals(net)) return BoardNet.RoutingRole.HIGH_CURRENT;
        if (net.endsWith("RETURN")) return BoardNet.RoutingRole.RETURN;
        if (net.startsWith("OUT_") || net.endsWith("_COIL_LOW") || net.endsWith("_DRAIN"))
            return BoardNet.RoutingRole.HIGH_CURRENT;
        if (net.startsWith("RAIL") || "AC_LINE".equals(net) || "AC_FUSED".equals(net) ||
                "HV_POS".equals(net) || "LOAD12".equals(net) || "BIAS_AC".equals(net))
            return BoardNet.RoutingRole.SUPPLY;
        if (net.startsWith("NC_")) return BoardNet.RoutingRole.SIGNAL;
        return BoardNet.RoutingRole.CONTROL;
    }
}
