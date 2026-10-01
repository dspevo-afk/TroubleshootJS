package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Vector;

/**
 * Immutable Q30 device intent. The topology is procedural: one or two
 * independently serviceable channels, one of three explicit reference
 * arrangements, and independently seeded electrical support functions.
 */
final class Rb30Plan {
    static final String FAMILY_ID = "RB30_CONTROL";
    static final int PLAN_VERSION = 4;
    private static final int MAX_PACKAGES = 40;

    final long seed, layoutSeed, routingSeed;
    final int channelCount;
    final boolean driverABjt, driverBBjt;
    final ReferenceArrangement referenceArrangement;
    /** Legacy fixture-readable alias; normal logic should use arrangement. */
    final boolean sharedHystereticReference;
    final boolean hasEntryCapacitor, hasFiveVoltIndicator,
        hasTwelveVoltIndicator, hasFiveVoltBleeder;
    final int sensorInputFilterMask, outputIndicatorMask;
    final String selectedFault;
    /** Only populated by named, explicit legacy-shape regression fixtures. */
    final SupportVariant supportVariant;

    enum ReferenceArrangement { SEPARATE_DIRECT, SHARED_DIRECT, SHARED_HYSTERETIC }

    /** Explicit regression fixture API; not used by normal seeded resolution. */
    enum SupportVariant {
        STANDARD_35("RB30_STANDARD_STATUS_REGRESSION@4", 35, true, false),
        COMPACT_33("RB30_COMPACT_NO_STATUS_REGRESSION@4", 33, false, false),
        FILTERED_37("RB30_FILTERED_SENSOR_INPUTS_REGRESSION@4", 37, true, true);

        private final String identity;
        private final int packageCount;
        private final boolean statusIndicator;
        private final boolean sensorInputFilters;

        SupportVariant(String identity, int packageCount,
                boolean statusIndicator, boolean sensorInputFilters) {
            this.identity = identity;
            this.packageCount = packageCount;
            this.statusIndicator = statusIndicator;
            this.sensorInputFilters = sensorInputFilters;
        }
        String identity() { return identity; }
        int packageCount() { return packageCount; }
        boolean hasStatusIndicator() { return statusIndicator; }
        boolean hasSensorInputFilters() { return sensorInputFilters; }
    }

    private static final String[] TWO_CHANNEL_FAULTS = {
        "DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN",
        "RELAY_B_COIL_OPEN"
    };
    private static final String[] SINGLE_CHANNEL_FAULTS = {
        "DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN",
        "RELAY_A_COIL_OPEN"
    };

    /** Feature vector positions are part of support normalization identity. */
    private static final int ENTRY_CAP = 1;
    private static final int INDICATOR_5V = 1 << 1;
    private static final int INDICATOR_12V = 1 << 2;
    private static final int FILTER_A = 1 << 3;
    private static final int FILTER_B = 1 << 4;
    private static final int OUTPUT_INDICATOR_A = 1 << 5;
    private static final int OUTPUT_INDICATOR_B = 1 << 6;
    private static final int BLEEDER_5V = 1 << 7;

    private static final class SupportFlags {
        final boolean entryCap, fiveVolt, twelveVolt, bleeder;
        final int filterMask, outputMask;
        SupportFlags(int code) {
            entryCap = (code & ENTRY_CAP) != 0;
            fiveVolt = (code & INDICATOR_5V) != 0;
            twelveVolt = (code & INDICATOR_12V) != 0;
            filterMask = ((code & FILTER_A) != 0 ? 1 : 0) |
                ((code & FILTER_B) != 0 ? 2 : 0);
            outputMask = ((code & OUTPUT_INDICATOR_A) != 0 ? 1 : 0) |
                ((code & OUTPUT_INDICATOR_B) != 0 ? 2 : 0);
            bleeder = (code & BLEEDER_5V) != 0;
        }
        int code() {
            return (entryCap ? ENTRY_CAP : 0) |
                (fiveVolt ? INDICATOR_5V : 0) |
                (twelveVolt ? INDICATOR_12V : 0) |
                ((filterMask & 1) != 0 ? FILTER_A : 0) |
                ((filterMask & 2) != 0 ? FILTER_B : 0) |
                ((outputMask & 1) != 0 ? OUTPUT_INDICATOR_A : 0) |
                ((outputMask & 2) != 0 ? OUTPUT_INDICATOR_B : 0) |
                (bleeder ? BLEEDER_5V : 0);
        }
    }

    private Rb30Plan(long seed, int channels, ReferenceArrangement reference,
            SupportFlags support, SupportVariant regressionFixture) {
        if (channels < 1 || channels > 2)
            throw new IllegalArgumentException("Q30 channel population must be one or two");
        if (reference == null || support == null)
            throw new IllegalArgumentException("Q30 plan is missing a structural choice");
        if (((support.filterMask | support.outputMask) & ~3) != 0)
            throw new IllegalArgumentException("Q30 support mask exceeds two channel bits");
        if (physicalPackageCount(channels, reference, support) > MAX_PACKAGES)
            throw new IllegalArgumentException("Q30 configured plan exceeds 40 physical packages");
        if (channels == 1 && ((support.filterMask | support.outputMask) & 2) != 0)
            throw new IllegalArgumentException("Q30 support flag names an absent channel");

        this.seed = seed;
        channelCount = channels;
        referenceArrangement = reference;
        sharedHystereticReference =
            reference == ReferenceArrangement.SHARED_HYSTERETIC;
        hasEntryCapacitor = support.entryCap;
        hasFiveVoltIndicator = support.fiveVolt;
        hasTwelveVoltIndicator = support.twelveVolt;
        sensorInputFilterMask = support.filterMask;
        outputIndicatorMask = support.outputMask;
        hasFiveVoltBleeder = support.bleeder;
        supportVariant = regressionFixture;

        // Existing independent topology/fault/layout streams retain their
        // named derivations. Support and channel population use separate
        // version-2 concerns and cannot shift these values.
        NamedRandomStreams streams = new NamedRandomStreams(
            NamedRandomStreams.DERIVATION_VERSION, seed, FAMILY_ID, 1);
        driverABjt = streams.openBlock("output-a",
            NamedRandomStreams.Concern.TOPOLOGY, 1, "driver").nextInt(2) == 0;
        driverBBjt = streams.openBlock("output-b",
            NamedRandomStreams.Concern.TOPOLOGY, 1, "driver").nextInt(2) == 0;
        selectedFault = (channels == 1 ? SINGLE_CHANNEL_FAULTS :
            TWO_CHANNEL_FAULTS)[streams.openDevice(
                NamedRandomStreams.Concern.FAULT, 1,
                "serviceable-region").nextInt(5)];
        layoutSeed = streams.deviceSeed(NamedRandomStreams.Concern.PLACEMENT,
            1, "board");
        routingSeed = streams.deviceSeed(NamedRandomStreams.Concern.ROUTING,
            1, "copper");
    }

    /** Seed-selected procedural family with independently named choices. */
    static Rb30Plan resolve(long seed) {
        NamedRandomStreams streams = new NamedRandomStreams(
            NamedRandomStreams.DERIVATION_VERSION, seed, FAMILY_ID, 1);
        int channels = streams.openDevice(NamedRandomStreams.Concern.TOPOLOGY,
            2, "channel-population").nextInt(2) + 1;
        int arrangementChoice = streams.openDevice(
            NamedRandomStreams.Concern.TOPOLOGY, 2,
            "reference-arrangement").nextInt(3);
        ReferenceArrangement arrangement = ReferenceArrangement.values()[arrangementChoice];

        int requested = 0;
        if (feature(streams, "entry-capacitor")) requested |= ENTRY_CAP;
        if (feature(streams, "five-volt-indicator")) requested |= INDICATOR_5V;
        if (feature(streams, "twelve-volt-indicator")) requested |= INDICATOR_12V;
        if (feature(streams, "sensor-filter-A")) requested |= FILTER_A;
        if (channels == 2 && feature(streams, "sensor-filter-B")) requested |= FILTER_B;
        if (feature(streams, "output-indicator-A")) requested |= OUTPUT_INDICATOR_A;
        if (channels == 2 && feature(streams, "output-indicator-B"))
            requested |= OUTPUT_INDICATOR_B;
        if (feature(streams, "five-volt-bleeder")) requested |= BLEEDER_5V;

        SupportFlags support = normalizeSupport(seed, channels, arrangement, requested);
        return new Rb30Plan(seed, channels, arrangement, support, null);
    }

    private static boolean feature(NamedRandomStreams streams, String name) {
        return streams.openDevice(NamedRandomStreams.Concern.SUPPORT, 2,
            name).nextInt(2) == 0;
    }

    /**
     * Independently drawn flags are retained when they fit. If they exceed
     * the package ceiling, a finite canonical enumeration selects the valid
     * vector at minimum Hamming distance; a named stream breaks exact ties.
     * This has no retry loop or count-specific recipe.
     */
    private static SupportFlags normalizeSupport(long seed, int channels,
            ReferenceArrangement reference, int requestedCode) {
        SupportFlags requested = new SupportFlags(requestedCode);
        if (physicalPackageCount(channels, reference, requested) <= MAX_PACKAGES)
            return requested;
        ArrayList<Integer> nearest = new ArrayList<Integer>();
        int bestDistance = Integer.MAX_VALUE;
        for (int code = 0; code < 256; code++) {
            SupportFlags candidate = new SupportFlags(code);
            if (channels == 1 &&
                    ((candidate.filterMask | candidate.outputMask) & 2) != 0)
                continue;
            if (physicalPackageCount(channels, reference, candidate) > MAX_PACKAGES)
                continue;
            int distance = bitCount(code ^ requestedCode);
            if (distance < bestDistance) {
                nearest.clear();
                bestDistance = distance;
            }
            if (distance == bestDistance) nearest.add(Integer.valueOf(code));
        }
        if (nearest.isEmpty())
            throw new IllegalStateException("Q30 has no support vector within package ceiling");
        int tieIndex = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,
            seed, FAMILY_ID, 1).openDevice(NamedRandomStreams.Concern.SUPPORT,
                2, "support-ceiling-tie").nextInt(nearest.size());
        return new SupportFlags(nearest.get(tieIndex).intValue());
    }

    private static int bitCount(int value) {
        int count = 0;
        while (value != 0) { count += value & 1; value >>>= 1; }
        return count;
    }

    private static int physicalPackageCount(int channels,
            ReferenceArrangement reference, SupportFlags support) {
        int references = reference == ReferenceArrangement.SEPARATE_DIRECT ?
            2 * channels : reference == ReferenceArrangement.SHARED_DIRECT ?
                2 : 2 + channels;
        return 8 + 10 * channels + references +
            (support.entryCap ? 1 : 0) + (support.fiveVolt ? 2 : 0) +
            (support.twelveVolt ? 2 : 0) + bitCount(support.filterMask) +
            2 * bitCount(support.outputMask) + (support.bleeder ? 1 : 0);
    }

    /** Construct a precise bounded shape for independent structural fixtures. */
    static Rb30Plan configured(long seed, int channels, ReferenceArrangement reference,
            boolean entryCapacitor, boolean fiveVoltIndicator, boolean twelveVoltIndicator,
            int filterMask, int outputMask, boolean fiveVoltBleeder) {
        if (((filterMask | outputMask) & ~3) != 0)
            throw new IllegalArgumentException("Q30 configured support mask exceeds two channel bits");
        int code = (entryCapacitor ? ENTRY_CAP : 0) |
            (fiveVoltIndicator ? INDICATOR_5V : 0) |
            (twelveVoltIndicator ? INDICATOR_12V : 0) |
            ((filterMask & 1) != 0 ? FILTER_A : 0) |
            ((filterMask & 2) != 0 ? FILTER_B : 0) |
            ((outputMask & 1) != 0 ? OUTPUT_INDICATOR_A : 0) |
            ((outputMask & 2) != 0 ? OUTPUT_INDICATOR_B : 0) |
            (fiveVoltBleeder ? BLEEDER_5V : 0);
        return new Rb30Plan(seed, channels, reference,
            new SupportFlags(code), null);
    }

    /** Explicit legacy-shape constructor reserved for regression fixtures. */
    static Rb30Plan withSupport(long seed, SupportVariant fixture) {
        if (fixture == null)
            throw new IllegalArgumentException("Missing Q30 regression fixture");
        NamedRandomStreams streams = new NamedRandomStreams(
            NamedRandomStreams.DERIVATION_VERSION, seed, FAMILY_ID, 1);
        boolean hysteretic = streams.openDevice(NamedRandomStreams.Concern.TOPOLOGY,
            1, "sensor-reference-arrangement").nextInt(2) == 0;
        ReferenceArrangement arrangement = hysteretic ?
            ReferenceArrangement.SHARED_HYSTERETIC :
            ReferenceArrangement.SEPARATE_DIRECT;
        SupportFlags support = new SupportFlags(
            ENTRY_CAP |
            (fixture.hasStatusIndicator() ? INDICATOR_5V : 0) |
            (fixture.hasSensorInputFilters() ? FILTER_A | FILTER_B : 0));
        Rb30Plan plan = new Rb30Plan(seed, 2, arrangement, support, fixture);
        if (plan.physicalPackageCount() != fixture.packageCount())
            throw new IllegalStateException("Q30 regression fixture package identity changed");
        return plan;
    }

    static Rb30Plan reference(long seed) {
        return withSupport(seed, SupportVariant.STANDARD_35);
    }

    boolean hasStatusIndicator() { return hasFiveVoltIndicator; }
    String supportIdentity() {
        return "C12:" + hasEntryCapacitor + ",S5:" + hasFiveVoltIndicator +
            ",S12:" + hasTwelveVoltIndicator + ",filters:" +
            sensorInputFilterMask + ",outputIndicators:" + outputIndicatorMask +
            ",bleeder5:" + hasFiveVoltBleeder;
    }
    boolean hasSensorInputFilters() { return sensorInputFilterMask != 0; }
    boolean hasSensorInputFilter(String channel) {
        return (sensorInputFilterMask & channelBit(channel)) != 0;
    }
    boolean hasOutputIndicator(String channel) {
        return (outputIndicatorMask & channelBit(channel)) != 0;
    }
    String[] channels() {
        return channelCount == 1 ? new String[] { "A" } :
            new String[] { "A", "B" };
    }
    private int channelBit(String channel) {
        if ("A".equals(channel)) return 1;
        if ("B".equals(channel) && channelCount == 2) return 2;
        throw new IllegalArgumentException("Unknown Q30 channel: " + channel);
    }
    boolean sharedHystereticReference() {
        return referenceArrangement == ReferenceArrangement.SHARED_HYSTERETIC;
    }
    boolean sharedReference() {
        return referenceArrangement != ReferenceArrangement.SEPARATE_DIRECT;
    }
    int referencePackageCount() {
        return referenceArrangement == ReferenceArrangement.SEPARATE_DIRECT ?
            2 * channelCount : referenceArrangement == ReferenceArrangement.SHARED_DIRECT ?
                2 : 2 + channelCount;
    }
    int physicalPackageCount() {
        return physicalPackageCount(channelCount, referenceArrangement,
            new SupportFlags(supportCode()));
    }
    private int supportCode() {
        return (hasEntryCapacitor ? ENTRY_CAP : 0) |
            (hasFiveVoltIndicator ? INDICATOR_5V : 0) |
            (hasTwelveVoltIndicator ? INDICATOR_12V : 0) |
            ((sensorInputFilterMask & 1) != 0 ? FILTER_A : 0) |
            ((sensorInputFilterMask & 2) != 0 ? FILTER_B : 0) |
            ((outputIndicatorMask & 1) != 0 ? OUTPUT_INDICATOR_A : 0) |
            ((outputIndicatorMask & 2) != 0 ? OUTPUT_INDICATOR_B : 0) |
            (hasFiveVoltBleeder ? BLEEDER_5V : 0);
    }

    RelayDriverProvider driverA() {
        return driverABjt ? new RelayDriverProvider.Bjt() :
            new RelayDriverProvider.Nmos();
    }
    RelayDriverProvider driverB() {
        return driverBBjt ? new RelayDriverProvider.Bjt() :
            new RelayDriverProvider.Nmos();
    }

    String topologyAxis() {
        return "RB30_CH" + channelCount + "_" + referenceArrangement.name() + "_A_" +
            (driverABjt ? "BJT" : "NMOS") + (channelCount == 2 ? "_B_" +
            (driverBBjt ? "BJT" : "NMOS") : "");
    }
    String topology() {
        return topologyAxis() + "_C12" + (hasEntryCapacitor ? "_Y" : "_N") +
            "_S5" + (hasFiveVoltIndicator ? "_Y" : "_N") +
            "_S12" + (hasTwelveVoltIndicator ? "_Y" : "_N") +
            "_F" + sensorInputFilterMask + "_O" + outputIndicatorMask +
            "_B5" + (hasFiveVoltBleeder ? "_Y" : "_N");
    }
    String canonical() {
        StringBuilder pullDowns = new StringBuilder();
        for (String channel : channels()) {
            if (pullDowns.length() > 0) pullDowns.append(',');
            pullDowns.append("RPIN_").append(channel).append(":1000ohm(")
                .append(channel).append("_RAW,CTRL_RETURN)");
        }
        return "rb30-plan@" + PLAN_VERSION + ";seed=" + Long.toString(seed) +
            ";topology=" + topologyAxis() + ";support=C12:" + hasEntryCapacitor +
            ",S5:" + hasFiveVoltIndicator + ",S12:" + hasTwelveVoltIndicator +
            ",filters:" + sensorInputFilterMask + ",outputIndicators:" +
            outputIndicatorMask + ",bleeder5:" + hasFiveVoltBleeder +
            ";layout=" + Long.toString(layoutSeed) +
            ";routing=" + Long.toString(routingSeed) +
            ";physicalPolicy=" + MediumBoardPhysicalPolicy.identity() +
            ";fault=" + selectedFault + ";packages=" + physicalPackageCount() +
            ";main=12V;regulator=5V-E02;coil=5V-E03" +
            ";load=isolated-12V-180ohm-per-channel;channels=" + channelCount +
            ";sensors=" + channelCount + "-E04-decisions;sensorPullDowns=" +
            pullDowns + ";loadReference=isolated";
    }

    /** One logical CircuitJS graph with its selected real package inventory. */
    TroubleshootBoard board() {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID + "_BOARD");
        board.setSilkscreenTitle("TSJ MULTI-RAIL CONTROL");
        for (String id : new String[] {
                "RAW12", "FUSED12", "RAIL12", "EN5", "RAIL5", "LOAD12"
            }) net(board, id, BoardNet.RoutingRole.SUPPLY);
        for (String id : new String[] { "CTRL_RETURN", "LOAD_RETURN" })
            net(board, id, BoardNet.RoutingRole.RETURN);
        for (String channel : channels()) {
            for (String suffix : new String[] { "_RAW", "_SENSE", "_CMD", "_DRIVE" })
                net(board, channel + suffix, BoardNet.RoutingRole.CONTROL);
            net(board, channel + "_COIL_LOW", BoardNet.RoutingRole.HIGH_CURRENT);
            net(board, "OUT_" + channel, BoardNet.RoutingRole.HIGH_CURRENT);
            net(board, "NC_" + channel, BoardNet.RoutingRole.SIGNAL);
        }
        if (sharedReference())
            net(board, "REF_SHARED", BoardNet.RoutingRole.CONTROL);
        else for (String channel : channels())
            net(board, channel + "_REF", BoardNet.RoutingRole.CONTROL);
        if (hasFiveVoltIndicator)
            net(board, "LED_FEED", BoardNet.RoutingRole.SIGNAL);
        if (hasTwelveVoltIndicator)
            net(board, "LED12_FEED", BoardNet.RoutingRole.SIGNAL);
        for (String channel : channels())
            if (hasOutputIndicator(channel))
                net(board, "LED_OUT_" + channel + "_FEED",
                    BoardNet.RoutingRole.SIGNAL);

        // Fixed entry and regulation packages. C12 is optional, so its net
        // attachment and physical package both disappear together.
        part(board, "J1", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "RAW12", "CTRL_RETURN");
        part(board, "F1", "FUSE", PhysicalPackages.AXIAL_FUSE,
            "RAW12", "FUSED12");
        part(board, "DREV", "DIODE", PhysicalPackages.AXIAL_DIODE,
            "FUSED12", "RAIL12");
        if (hasEntryCapacitor)
            part(board, "C12", "CAPACITOR",
                PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
                "FUSED12", "CTRL_RETURN");
        part(board, "U1", "REGULATOR", PhysicalPackages.TO220_REGULATOR_4,
            "RAIL12", "RAIL5", "CTRL_RETURN", "EN5");
        part(board, "CIN", "CAPACITOR",
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            "RAIL12", "CTRL_RETURN");
        part(board, "C5", "CAPACITOR",
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "RAIL5", "CTRL_RETURN");
        part(board, "REN", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            "RAIL12", "EN5");
        if (hasFiveVoltBleeder)
            part(board, "RBLEED5", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "RAIL5", "CTRL_RETURN");

        for (String channel : channels()) {
            sensor(board, channel, sharedReference() ? "REF_SHARED" :
                channel + "_REF");
            part(board, "RPIN_" + channel, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR,
                channel + "_RAW", "CTRL_RETURN");
            if (hasSensorInputFilter(channel))
                part(board, "CFLT_" + channel, "CAPACITOR",
                    PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
                    channel + "_SENSE", "CTRL_RETURN");
        }
        if (sharedHystereticReference()) {
            part(board, "RREF_H", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "RAIL5", "REF_SHARED");
            part(board, "RREF_L", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "REF_SHARED", "CTRL_RETURN");
            for (String channel : channels())
                part(board, "RFB_" + channel, "RESISTOR",
                    PhysicalPackages.AXIAL_RESISTOR,
                    channel + "_CMD", channel + "_SENSE");
        } else if (referenceArrangement == ReferenceArrangement.SHARED_DIRECT) {
            part(board, "RREF_H", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "RAIL5", "REF_SHARED");
            part(board, "RREF_L", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "REF_SHARED", "CTRL_RETURN");
        } else {
            for (String channel : channels()) {
                part(board, "RREF_H" + channel, "RESISTOR",
                    PhysicalPackages.AXIAL_RESISTOR,
                    "RAIL5", channel + "_REF");
                part(board, "RREF_L" + channel, "RESISTOR",
                    PhysicalPackages.AXIAL_RESISTOR,
                    channel + "_REF", "CTRL_RETURN");
            }
        }
        for (String channel : channels()) output(board, channel,
            "A".equals(channel) ? driverA() : driverB());

        if (hasFiveVoltIndicator) {
            part(board, "RLED", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "RAIL5", "LED_FEED");
            part(board, "LED1", "LED", PhysicalPackages.THROUGH_HOLE_LED,
                "LED_FEED", "CTRL_RETURN");
        }
        if (hasTwelveVoltIndicator) {
            part(board, "RLED12", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "RAIL12", "LED12_FEED");
            part(board, "LED12", "LED", PhysicalPackages.THROUGH_HOLE_LED,
                "LED12_FEED", "CTRL_RETURN");
        }
        for (String channel : channels()) if (hasOutputIndicator(channel)) {
            part(board, "RLEDOUT_" + channel, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR,
                "OUT_" + channel, "LED_OUT_" + channel + "_FEED");
            part(board, "LEDOUT_" + channel, "LED",
                PhysicalPackages.THROUGH_HOLE_LED,
                "LED_OUT_" + channel + "_FEED", "LOAD_RETURN");
        }

        // Sensor and output terminals are per active channel. The load
        // connector is common, while all output indicators return to the
        // isolated load-return domain.
        for (String channel : channels()) {
            String sensorConnector = "JS" + channel;
            part(board, sensorConnector, "CONNECTOR",
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                channel + "_RAW", "CTRL_RETURN");
            part(board, "JO" + channel, "OUTPUT_HEADER",
                PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
                "OUT_" + channel, "LOAD_RETURN");
        }
        part(board, "JLOAD", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "LOAD12", "LOAD_RETURN");

        board.addPowerInput(new ExternalBoardPowerInput("MAIN12", "J1.1",
            "J1.2", "RAW12", "CTRL_RETURN"));
        for (String channel : channels()) {
            board.addPowerInput(new ExternalBoardPowerInput("SENSOR_" + channel,
                "JS" + channel + ".1", "JS" + channel + ".2",
                channel + "_RAW", "CTRL_RETURN"));
        }
        board.addPowerInput(new ExternalBoardPowerInput("LOAD12", "JLOAD.1",
            "JLOAD.2", "LOAD12", "LOAD_RETURN"));
        board.setPlacementConstraints(placement(board));
        board.validate();
        if (board.getComponentIds().size() != physicalPackageCount())
            throw new IllegalStateException("Q30 support package accounting changed");
        return board;
    }

    private static void sensor(TroubleshootBoard board, String channel,
            String referenceNet) {
        part(board, "U2" + channel, "SENSOR_CONTROL",
            PhysicalPackages.E04_DECISION_CONTROL_5,
            channel + "_SENSE", referenceNet, "RAIL5",
            channel + "_CMD", "CTRL_RETURN");
        part(board, "RS" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_RAW", channel + "_SENSE");
    }

    private static void output(TroubleshootBoard board, String channel,
            RelayDriverProvider driver) {
        part(board, "RD" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_CMD", channel + "_DRIVE");
        part(board, "RPD" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_DRIVE", "CTRL_RETURN");
        part(board, "Q" + channel, driver.getId(), driver.getPackage(),
            channel + "_DRIVE", channel + "_COIL_LOW", "CTRL_RETURN");
        part(board, "D" + channel, "DIODE", PhysicalPackages.AXIAL_DIODE,
            channel + "_COIL_LOW", "RAIL5");
        part(board, "K" + channel, "RELAY", PhysicalPackages.RELAY_SPDT,
            "RAIL5", channel + "_COIL_LOW", "LOAD12",
            "NC_" + channel, "OUT_" + channel);
    }

    private static void net(TroubleshootBoard board, String id,
            BoardNet.RoutingRole role) {
        board.addNet(new BoardNet(id, role));
    }
    private static void part(TroubleshootBoard board, String id, String type,
            PhysicalPackage physicalPackage, String... nets) {
        Vector<String> terminals = physicalPackage.getTerminalIds();
        if (terminals.size() != nets.length)
            throw new IllegalArgumentException("Q30 terminal count: " + id);
        board.addComponent(new BoardComponent(id, type, physicalPackage));
        for (int index = 0; index < nets.length; index++)
            board.addPad(new BoardPad(id + "." + terminals.get(index), id,
                terminals.get(index), nets[index]));
    }

    private static PcbPlacementConstraints placement(TroubleshootBoard board) {
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        for (String id : board.getComponentIds()) {
            String region = placementRegion(id);
            String label = "entry".equals(region) ? "12 V entry" :
                "regulation".equals(region) ? "5 V regulation" :
                "sensor-a".equals(region) ? "Sensor A" :
                "sensor-b".equals(region) ? "Sensor B" :
                "sensor-reference".equals(region) ? "Sensor reference" :
                "status".equals(region) ? "Rail status" :
                "output-a".equals(region) ? "Output A" :
                "output-b".equals(region) ? "Output B" : "Load supply";
            PcbPlacementConstraints.Anchor anchor =
                id.equals("J1") || id.equals("JSA") || id.equals("JSB") ?
                    PcbPlacementConstraints.Anchor.LEFT :
                id.equals("JLOAD") ? PcbPlacementConstraints.Anchor.RIGHT :
                    PcbPlacementConstraints.Anchor.NONE;
            parts.add(new PcbPlacementConstraints.Part(id, region, label,
                "low-voltage-board", anchor, 20));
        }
        return new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
            MediumBoardPhysicalPolicy.ID, MediumBoardPhysicalPolicy.VERSION);
    }

    private static String placementRegion(String id) {
        if (id.equals("J1") || id.equals("F1") || id.equals("DREV") ||
                id.equals("C12")) return "entry";
        if (id.equals("U1") || id.equals("CIN") || id.equals("C5") ||
                id.equals("REN") || id.equals("RBLEED5")) return "regulation";
        if (id.equals("JLOAD")) return "output-common";
        if (id.equals("RLED") || id.equals("LED1") ||
                id.equals("RLED12") || id.equals("LED12")) return "status";
        if (id.equals("RREF_H") || id.equals("RREF_L"))
            return "sensor-reference";
        if (id.startsWith("RLEDOUT_") || id.startsWith("LEDOUT_"))
            return id.endsWith("A") ? "output-a" : "output-b";
        for (String channel : new String[] { "A", "B" }) {
            String outputRegion = "A".equals(channel) ? "output-a" : "output-b";
            String sensorRegion = "A".equals(channel) ? "sensor-a" : "sensor-b";
            if (id.equals("RD" + channel) || id.equals("RPD" + channel) ||
                    id.equals("Q" + channel) || id.equals("D" + channel) ||
                    id.equals("K" + channel) || id.equals("JO" + channel))
                return outputRegion;
            if (id.equals("JS" + channel) || id.equals("U2" + channel) ||
                    id.equals("RS" + channel) || id.equals("RPIN_" + channel) ||
                    id.equals("CFLT_" + channel) || id.equals("RFB_" + channel) ||
                    id.equals("RREF_H" + channel) || id.equals("RREF_L" + channel))
                return sensorRegion;
        }
        return "output-common";
    }
}
