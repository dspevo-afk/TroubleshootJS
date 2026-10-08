package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** RB56's own bounded device intent; one inventory drives its board and construction. */
final class Rb56Plan {
    static final String FAMILY_ID = "RB56_CONTROL";
    static final int PLAN_VERSION = 1, MIN_PACKAGES = 40, MAX_PACKAGES = 60, POWER_PACKAGES = 20;
    // External customer commands; E04 sensorLow/High classify solved conditioned inputs.
    static final double SENSOR_LOW_VOLTS = 0, SENSOR_HIGH_VOLTS = 5;
    enum ReferenceArrangement { SEPARATE_DIRECT, SHARED_DIRECT, SHARED_HYSTERETIC }
    enum Kind { POWER, SOURCE, REGULATOR, DECISION, DRIVER, RELAY, RESISTOR, CAPACITOR, DIODE, LED, OUTPUT_HEADER }

    static final class Part {
        final String id, type, channel, inputId;
        final Kind kind;
        final PhysicalPackage physicalPackage;
        final double value;
        private final String[] nets;
        private Part(String id, String type, Kind kind, PhysicalPackage pkg, double value,
                String channel, String inputId, String... nets) {
            if (id == null || type == null || kind == null || pkg == null ||
                    nets == null || nets.length != pkg.getTerminalCount() ||
                    Double.isNaN(value) || Double.isInfinite(value) || value < 0)
                throw new IllegalArgumentException("Incomplete RB56 part declaration");
            this.id = id; this.type = type; this.kind = kind; physicalPackage = pkg;
            this.value = value; this.channel = channel; this.inputId = inputId;
            this.nets = new String[nets.length];
            System.arraycopy(nets, 0, this.nets, 0, nets.length);
        }
        String[] netIds() {
            String[] copy = new String[nets.length];
            System.arraycopy(nets, 0, copy, 0, nets.length);
            return copy;
        }
        String[] terminalIds() {
            Vector<String> ids = physicalPackage.getTerminalIds();
            return ids.toArray(new String[ids.size()]);
        }
        String canonical() {
            StringBuilder out = new StringBuilder(id + ":" + type + ":" + kind.name() + ":" +
                physicalPackage.getId() + ":" + Double.toString(value) + ":" +
                (channel == null ? "-" : channel) + ":" + (inputId == null ? "-" : inputId));
            String[] terminals = terminalIds();
            for (int i = 0; i < nets.length; i++) out.append('|').append(terminals[i]).append('=').append(nets[i]);
            return out.toString();
        }
    }

    final long seed, layoutSeed, routingSeed;
    final int channelCount, sensorInputFilterMask, outputIndicatorMask;
    final ReferenceArrangement referenceArrangement;
    final boolean driverABjt, driverBBjt, hasFiveVoltIndicator, hasTwelveVoltIndicator, hasFiveVoltBleeder;
    private final Vector<Part> inventory = new Vector<Part>();

    private Rb56Plan(long seed, int channels, ReferenceArrangement reference, boolean five,
            boolean twelve, int filters, int outputs, boolean bleeder) {
        if (channels < 1 || channels > 2 || reference == null || filters < 0 || outputs < 0 ||
                ((filters | outputs) & ~(channels == 1 ? 1 : 3)) != 0)
            throw new IllegalArgumentException("Unsupported RB56 channel/support declaration");
        this.seed = seed; channelCount = channels; referenceArrangement = reference;
        hasFiveVoltIndicator = five; hasTwelveVoltIndicator = twelve; hasFiveVoltBleeder = bleeder;
        sensorInputFilterMask = filters; outputIndicatorMask = outputs;
        NamedRandomStreams streams = streams(seed);
        driverABjt = streams.openBlock("channel-A", NamedRandomStreams.Concern.TOPOLOGY, 1, "driver").nextInt(2) == 0;
        driverBBjt = streams.openBlock("channel-B", NamedRandomStreams.Concern.TOPOLOGY, 1, "driver").nextInt(2) == 0;
        layoutSeed = streams.deviceSeed(NamedRandomStreams.Concern.PLACEMENT, 1, "board");
        routingSeed = streams.deviceSeed(NamedRandomStreams.Concern.ROUTING, 1, "copper");
        declarePower(); declareTail();
        if (inventory.size() < MIN_PACKAGES || inventory.size() > MAX_PACKAGES)
            throw new IllegalArgumentException("RB56 functional population outside 40-60 packages: " + inventory.size());
        TreeMap<String, Boolean> ids = new TreeMap<String, Boolean>();
        for (Part part : inventory)
            if (ids.put(part.id, Boolean.TRUE) != null) throw new IllegalStateException("Duplicate RB56 package: " + part.id);
    }

    /** Named choices remain independent; the single-channel device includes filter and 5 V status. */
    static Rb56Plan resolve(long seed) {
        NamedRandomStreams streams = streams(seed);
        int channels = 1 + streams.openDevice(NamedRandomStreams.Concern.TOPOLOGY, 1, "channel-population").nextInt(2);
        int choice = streams.openDevice(NamedRandomStreams.Concern.TOPOLOGY, 1, "reference-arrangement").nextInt(channels == 1 ? 2 : 3);
        ReferenceArrangement reference = channels == 1 ?
            (choice == 0 ? ReferenceArrangement.SEPARATE_DIRECT : ReferenceArrangement.SHARED_HYSTERETIC) :
            ReferenceArrangement.values()[choice];
        boolean five = channels == 1 || feature(streams, "rail-status-5V");
        boolean twelve = feature(streams, "rail-status-12V"), bleeder = feature(streams, "rail-bleeder-5V");
        int filters = channels == 1 || feature(streams, "sensor-filter-A") ? 1 : 0;
        int outputs = feature(streams, "output-status-A") ? 1 : 0;
        if (channels == 2) {
            if (feature(streams, "sensor-filter-B")) filters |= 2;
            if (feature(streams, "output-status-B")) outputs |= 2;
        }
        return configured(seed, channels, reference, five, twelve, filters, outputs, bleeder);
    }

    /** Explicit representative, not a normal-player admission or seeded-remapping rule. */
    static Rb56Plan reference(long seed) {
        return configured(seed, 2, ReferenceArrangement.SHARED_HYSTERETIC, true, true, 3, 0, false);
    }
    static Rb56Plan configured(long seed, int channels, ReferenceArrangement reference,
            boolean five, boolean twelve, int filters, int outputs, boolean bleeder) {
        return new Rb56Plan(seed, channels, reference, five, twelve, filters, outputs, bleeder);
    }
    private static NamedRandomStreams streams(long seed) {
        return new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION, seed, FAMILY_ID, PLAN_VERSION);
    }
    private static boolean feature(NamedRandomStreams streams, String key) {
        return streams.openDevice(NamedRandomStreams.Concern.SUPPORT, 1, key).nextInt(2) == 0;
    }
    String[] channels() { return channelCount == 1 ? new String[] {"A"} : new String[] {"A", "B"}; }
    RelayDriverProvider driver(String channel) {
        if (!"A".equals(channel) && !(channelCount == 2 && "B".equals(channel)))
            throw new IllegalArgumentException("Absent RB56 channel: " + channel);
        boolean bjt = "A".equals(channel) ? driverABjt : driverBBjt;
        return bjt ? new RelayDriverProvider.Bjt() : new RelayDriverProvider.Nmos();
    }
    boolean sharedReference() { return referenceArrangement != ReferenceArrangement.SEPARATE_DIRECT; }
    boolean sharedHystereticReference() { return referenceArrangement == ReferenceArrangement.SHARED_HYSTERETIC; }
    String referenceNet(String channel) { driver(channel); return sharedReference() ? "REF_SHARED" : channel + "_REF"; }
    int physicalPackageCount() { return inventory.size(); }
    Vector<Part> parts() { return new Vector<Part>(inventory); }
    Vector<Part> powerParts() { return selected(true); }
    Vector<Part> tailParts() { return selected(false); }
    private Vector<Part> selected(boolean power) {
        Vector<Part> result = new Vector<Part>();
        for (Part part : inventory) if ((part.kind == Kind.POWER) == power) result.add(part);
        return result;
    }
    Part part(String id) {
        for (Part part : inventory) if (part.id.equals(id)) return part;
        throw new IllegalArgumentException("Unknown RB56 package: " + id);
    }

    String topology() {
        return "RB56_CH" + channelCount + "_" + referenceArrangement.name() + "_A_" + driver("A").getId() +
            (channelCount == 2 ? "_B_" + driver("B").getId() : "") + "_S5_" + hasFiveVoltIndicator +
            "_S12_" + hasTwelveVoltIndicator + "_F" + sensorInputFilterMask + "_O" + outputIndicatorMask + "_B5_" + hasFiveVoltBleeder;
    }
    String canonical() {
        StringBuilder out = new StringBuilder("rb56-plan@" + PLAN_VERSION + ";family=" + FAMILY_ID +
            ";seed=" + Long.toString(seed) + ";layout=" + Long.toString(layoutSeed) + ";routing=" + Long.toString(routingSeed) +
            ";topology=" + topology() + ";packages=" + inventory.size() +
            ";power=E05-AC/E06-averaged;regulator=E02-linear5V;control=E04;relay=E03-5V;tail-capacitors=Q60-BE0@1;" +
            "sensor-commands=Q60-EXTERNAL-LOW:" + Double.toString(SENSOR_LOW_VOLTS) + ",HIGH:" + Double.toString(SENSOR_HIGH_VOLTS) + ";" +
            "single-channel-minimum=sensor-filter,5V-status;scope=UNQUALIFIED_DEVICE_INTENT;");
        for (Part part : inventory) out.append('[').append(part.canonical()).append(']');
        return out.toString();
    }

    /** No second hand-maintained board inventory or construction topology. */
    TroubleshootBoard board() {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID + "_BOARD");
        board.setSilkscreenTitle("TSJ AC / ISOLATED CONTROL");
        TreeMap<String, BoardNet.RoutingRole> nets = new TreeMap<String, BoardNet.RoutingRole>();
        for (Part part : inventory) for (String net : part.netIds()) nets.put(net, role(net));
        for (String net : nets.keySet()) board.addNet(new BoardNet(net, nets.get(net)));
        for (Part part : inventory) {
            board.addComponent(new BoardComponent(part.id, part.type, part.physicalPackage));
            String[] terminals = part.terminalIds(), netIds = part.netIds();
            for (int i = 0; i < terminals.length; i++)
                board.addPad(new BoardPad(part.id + "." + terminals[i], part.id, terminals[i], netIds[i]));
            if (part.inputId != null)
                board.addPowerInput(new ExternalBoardPowerInput(part.inputId, part.id + "." + terminals[0],
                    part.id + "." + terminals[1], netIds[0], netIds[1]));
        }
        board.setPlacementConstraints(Rb56Placement.create(this));
        board.validate(); return board;
    }
    private static BoardNet.RoutingRole role(String net) {
        if (net.endsWith("RETURN")) return BoardNet.RoutingRole.RETURN;
        if (net.startsWith("OUT_") || net.endsWith("_COIL_LOW") || "PRE_L".equals(net)) return BoardNet.RoutingRole.HIGH_CURRENT;
        if (net.startsWith("LED") || net.startsWith("NC_")) return BoardNet.RoutingRole.SIGNAL;
        if (net.equals("AC_LINE") || net.equals("AC_FUSED") || net.equals("HV_POS") || net.startsWith("RAIL") ||
                net.equals("LOAD12") || net.equals("BIAS_AC")) return BoardNet.RoutingRole.SUPPLY;
        return BoardNet.RoutingRole.CONTROL;
    }

    private void add(String id, String type, Kind kind, PhysicalPackage pkg, double value,
            String channel, String inputId, String... nets) {
        inventory.add(new Part(id, type, kind, pkg, value, channel, inputId, nets));
    }
    private void power(String id, String type, PhysicalPackage pkg, double value, String... nets) {
        add(id, type, Kind.POWER, pkg, value, null, "JAC".equals(id) ? "MAINAC" : null, nets);
    }
    private void resistor(String id, double value, String first, String second) {
        add(id, "RESISTOR", Kind.RESISTOR, PhysicalPackages.AXIAL_RESISTOR, value, null, null, first, second);
    }
    private void capacitor(String id, double value, boolean electrolytic, String first, String second) {
        add(id, "CAPACITOR", Kind.CAPACITOR, electrolytic ? PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR :
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR, value, null, null, first, second);
    }
    private void indicator(String resistor, String led, double ohms, String supply, String feed, String returned) {
        resistor(resistor, ohms, supply, feed);
        add(led, "LED", Kind.LED, PhysicalPackages.THROUGH_HOLE_LED, 0, null, null, feed, returned);
    }
    private void declarePower() {
        power("JAC", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, E05AcInputModel.RMS_VOLTS, "AC_LINE", "AC_RETURN");
        power("F1", "FUSE", PhysicalPackages.AXIAL_FUSE, E05AcInputModel.FUSE_OHMS, "AC_LINE", "AC_FUSED");
        power("D1", "DIODE", PhysicalPackages.AXIAL_DIODE, 0, "AC_FUSED", "HV_POS");
        power("D2", "DIODE", PhysicalPackages.AXIAL_DIODE, 0, "AC_RETURN", "HV_POS");
        power("D3", "DIODE", PhysicalPackages.AXIAL_DIODE, 0, "HV_RETURN", "AC_FUSED");
        power("D4", "DIODE", PhysicalPackages.AXIAL_DIODE, 0, "HV_RETURN", "AC_RETURN");
        power("CBULK", "CAPACITOR", PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR, Rb56PowerStage.PRIMARY_FARADS, "HV_POS", "HV_RETURN");
        power("RPRIMARY", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR, Rb56PowerStage.PRIMARY_BLEED_OHMS, "HV_POS", "HV_RETURN");
        power("UAC", "CONVERTER", PhysicalPackages.ISOLATED_CONVERTER_7, 0, "HV_POS", "HV_RETURN", "PRE_L", "CTRL_RETURN", "ENABLE_AC", "FB_AC", "BIAS_AC");
        power("RENAC", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR, Rb56PowerStage.ENABLE_PULLUP_OHMS, "BIAS_AC", "ENABLE_AC");
        power("CBIAS", "CAPACITOR", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR, Rb56PowerStage.BIAS_FARADS, "BIAS_AC", "HV_RETURN");
        power("DFREE", "DIODE", PhysicalPackages.AXIAL_DIODE, 0, "CTRL_RETURN", "PRE_L");
        power("L1", "INDUCTOR", PhysicalPackages.RADIAL_INDUCTOR_2, E06ConverterContract.OUTPUT_HENRIES, "PRE_L", "RAIL12");
        power("COUT", "CAPACITOR", PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR, E06ConverterContract.OUTPUT_FARADS, "RAIL12", "CTRL_RETURN");
        power("ROUT", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR, Rb56PowerStage.OUTPUT_BLEED_OHMS, "RAIL12", "CTRL_RETURN");
        power("UFB", "OPTOCOUPLER", PhysicalPackages.OPTOCOUPLER_4, 0, "SENSE_LED", "CTRL_RETURN", "FB_AC", "HV_RETURN");
        power("DZ1", "ZENER", PhysicalPackages.AXIAL_DIODE, E06ConverterContract.ZENER_VOLTS, "SENSE_LED", "SENSE_ZENER");
        power("RSENSE", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR, E06ConverterContract.SENSE_OHMS, "RAIL12", "SENSE_ZENER");
        power("RFB", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR, E06ConverterContract.PULLUP_OHMS, "BIAS_AC", "FB_AC");
        power("CFB", "CAPACITOR", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR, E06ConverterContract.COMPENSATION_FARADS, "FB_AC", "HV_RETURN");
    }
    private void declareTail() {
        add("U1", "REGULATOR", Kind.REGULATOR, PhysicalPackages.TO220_REGULATOR_4, 5, null, null, "RAIL12", "RAIL5", "CTRL_RETURN", "EN5");
        resistor("REN", 10000, "RAIL12", "EN5");
        capacitor("CIN", 2.2e-5, true, "RAIL12", "CTRL_RETURN");
        capacitor("C5", 1e-6, false, "RAIL5", "CTRL_RETURN");
        add("JLOAD", "CONNECTOR", Kind.SOURCE, PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, 12, null, "LOAD12", "LOAD12", "LOAD_RETURN");
        for (String channel : channels()) {
            add("JS" + channel, "CONNECTOR", Kind.SOURCE, PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, 5, channel,
                "SENSOR_" + channel, channel + "_RAW", "CTRL_RETURN");
            add("U2" + channel, "SENSOR_CONTROL", Kind.DECISION, PhysicalPackages.E04_DECISION_CONTROL_5, 0, channel, null,
                channel + "_SENSE", referenceNet(channel), "RAIL5", channel + "_CMD", "CTRL_RETURN");
            resistor("RS" + channel, 10000, channel + "_RAW", channel + "_SENSE");
            // Bias the conditioned input so a high-resistance sensor lead has a defined LOW state.
            resistor("RPIN_" + channel, 100000, channel + "_SENSE", "CTRL_RETURN");
            if ((sensorInputFilterMask & ("A".equals(channel) ? 1 : 2)) != 0)
                capacitor("CFLT_" + channel, 100e-9, false, channel + "_SENSE", "CTRL_RETURN");
            resistor("RD" + channel, 1000, channel + "_CMD", channel + "_DRIVE");
            resistor("RPD" + channel, 100000, channel + "_DRIVE", "CTRL_RETURN");
            RelayDriverProvider driver = driver(channel);
            add("Q" + channel, driver.getId(), Kind.DRIVER, driver.getPackage(), 0, channel, null,
                channel + "_DRIVE", channel + "_COIL_LOW", "CTRL_RETURN");
            add("D" + channel, "DIODE", Kind.DIODE, PhysicalPackages.AXIAL_DIODE, 0, channel, null, channel + "_COIL_LOW", "RAIL5");
            add("K" + channel, "RELAY", Kind.RELAY, PhysicalPackages.RELAY_SPDT, 5, channel, null,
                "RAIL5", channel + "_COIL_LOW", "LOAD12", "NC_" + channel, "OUT_" + channel);
            add("JO" + channel, "OUTPUT_HEADER", Kind.OUTPUT_HEADER, PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
                180, channel, null, "OUT_" + channel, "LOAD_RETURN");
        }
        if (sharedReference()) {
            resistor("RREF_H", 10000, "RAIL5", "REF_SHARED"); resistor("RREF_L", 10000, "REF_SHARED", "CTRL_RETURN");
        } else for (String channel : channels()) {
            resistor("RREF_H" + channel, 10000, "RAIL5", channel + "_REF");
            resistor("RREF_L" + channel, 10000, channel + "_REF", "CTRL_RETURN");
        }
        if (sharedHystereticReference()) for (String channel : channels())
            // Retain regenerative feedback without holding a disconnected sensor above the falling threshold.
            resistor("RFB_" + channel, 220000, channel + "_CMD", channel + "_SENSE");
        if (hasFiveVoltIndicator) indicator("RLED", "LED1", 3300, "RAIL5", "LED_FEED", "CTRL_RETURN");
        if (hasTwelveVoltIndicator) indicator("RLED12", "LED12", 10000, "RAIL12", "LED12_FEED", "CTRL_RETURN");
        for (String channel : channels()) if ((outputIndicatorMask & ("A".equals(channel) ? 1 : 2)) != 0)
            indicator("RLEDOUT_" + channel, "LEDOUT_" + channel, 10000, "OUT_" + channel, "LED_OUT_" + channel + "_FEED", "LOAD_RETURN");
        if (hasFiveVoltBleeder) resistor("RBLEED5", 100000, "RAIL5", "CTRL_RETURN");
    }
}
