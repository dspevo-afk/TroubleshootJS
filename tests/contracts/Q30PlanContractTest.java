package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Set;

/** Independent structural, scale and shared-domain canary for Q30. */
public final class Q30PlanContractTest {
    private static int assertions;

    private Q30PlanContractTest() { }

    public static void main(String[] args) {
        if (args.length == 2 && "--describe-plans".equals(args[0])) {
            describePlans(args[1]);
            return;
        }
        if (args.length != 0)
            throw new IllegalArgumentException(
                "Usage: [--describe-plans <comma-separated-canonical-signed-longs>]");
        Set<String> topologies = new HashSet<String>();
        int single = 0, dual = 0;
        for (long seed : new long[] { Long.MIN_VALUE, Long.MAX_VALUE, -101,
                -17, -1, 0, 1, 2, 3, 4, 5, 11, 19, 31, 47, 83,
                9007199254740993L }) {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            Rb30Plan replay = Rb30Plan.resolve(seed);
            check(plan.canonical().equals(replay.canonical()));
            check(plan.layoutSeed == replay.layoutSeed &&
                plan.routingSeed == replay.routingSeed);
            check(plan.supportVariant == null,
                "normal plan identity is procedural, not a legacy recipe");
            check(plan.canonical().startsWith("rb30-plan@4;seed=" +
                Long.toString(seed) + ";topology=" + plan.topologyAxis() +
                ";support=" + plan.supportIdentity() + ";"));
            verifyBoard(plan);
            topologies.add(plan.topologyAxis());
            if (plan.channelCount == 1) single++; else dual++;
        }
        check(single > 0 && dual > 0,
            "seeded procedural plans exercise both active channel populations");
        check(topologies.size() >= 4,
            "seeded plans retain real driver/reference topology variation");

        verifyContiguousScale(1, Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT,
            20, 29);
        verifyContiguousScale(2, Rb30Plan.ReferenceArrangement.SHARED_DIRECT,
            30, 40);
        verifyLegacyRegressionFixtures();
        verifyInvalidMasksRejected();
        System.out.println("PASS: Q30 plan contracts " + assertions +
            " assertions, topologies=" + topologies.size() +
            ", oneChannelSeeds=" + single + ", twoChannelSeeds=" + dual +
            ", structuralPackageBand=20..40");
    }

    /** Pure v4 identity export for independent readers; performs no board work. */
    private static void describePlans(String seedsText) {
        if (seedsText == null || seedsText.length() == 0)
            throw new IllegalArgumentException("Missing Q30 plan seeds");
        Set<Long> seen = new HashSet<Long>();
        String[] values = seedsText.split(",", -1);
        if (values.length > 128)
            throw new IllegalArgumentException("Q30 plan export exceeds 128 seeds");
        for (String value : values) {
            final long seed;
            try { seed = Long.parseLong(value); }
            catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Invalid Q30 canonical seed: " + value,
                    invalid);
            }
            if (!Long.toString(seed).equals(value))
                throw new IllegalArgumentException("Noncanonical Q30 seed: " + value);
            if (!seen.add(Long.valueOf(seed)))
                throw new IllegalArgumentException("Duplicate Q30 seed: " + value);
            System.out.println("Q30_PLAN_CANONICAL|seed=" + Long.toString(seed) +
                "|plan=" + Rb30Plan.resolve(seed).canonical());
        }
        System.out.println("PASS: Q30 plan contracts exported=" + seen.size());
    }

    /**
     * Enumerate independent support functions, not package counts or named
     * recipes. Every count in each natural topology band must be constructible.
     */
    private static void verifyContiguousScale(int channels,
            Rb30Plan.ReferenceArrangement arrangement, int minimum, int maximum) {
        Rb30Plan[] examples = new Rb30Plan[maximum + 1];
        for (int flags = 0; flags < 256; flags++) {
            int filters = ((flags & (1 << 3)) != 0 ? 1 : 0) |
                (channels == 2 && (flags & (1 << 4)) != 0 ? 2 : 0);
            int outputs = ((flags & (1 << 5)) != 0 ? 1 : 0) |
                (channels == 2 && (flags & (1 << 6)) != 0 ? 2 : 0);
            int references = arrangement ==
                    Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT ? 2 * channels :
                arrangement == Rb30Plan.ReferenceArrangement.SHARED_DIRECT ? 2 :
                    2 + channels;
            int expectedCount = 8 + 10 * channels + references +
                ((flags & 1) != 0 ? 1 : 0) +
                ((flags & 2) != 0 ? 2 : 0) +
                ((flags & 4) != 0 ? 2 : 0) + bitCount(filters) +
                2 * bitCount(outputs) +
                ((flags & (1 << 7)) != 0 ? 1 : 0);
            if (expectedCount > 40) {
                boolean rejected = false;
                try {
                    Rb30Plan.configured(123456789L, channels, arrangement,
                        (flags & 1) != 0, (flags & 2) != 0,
                        (flags & 4) != 0, filters, outputs,
                        (flags & (1 << 7)) != 0);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                check(rejected, "configured plan rejects unsupported size " +
                    expectedCount);
                continue;
            }
            Rb30Plan plan = Rb30Plan.configured(123456789L, channels,
                arrangement, (flags & 1) != 0, (flags & 2) != 0,
                (flags & 4) != 0, filters, outputs,
                (flags & (1 << 7)) != 0);
            int count = plan.physicalPackageCount();
            check(count == expectedCount,
                "independent support count formula for flags " + flags);
            if (count >= minimum && count <= maximum && examples[count] == null)
                examples[count] = plan;
        }
        for (int count = minimum; count <= maximum; count++) {
            check(examples[count] != null,
                "purposeful support combinations reach package count " + count);
            check(examples[count].physicalPackageCount() == count);
            verifyBoard(examples[count]);
        }
    }

    private static void verifyBoard(Rb30Plan plan) {
        TroubleshootBoard board = plan.board();
        int channels = plan.channelCount;
        check(board.getComponentIds().size() == plan.physicalPackageCount());
        check(plan.physicalPackageCount() >= 20 &&
            plan.physicalPackageCount() <= 40);
        check(plan.physicalPackageCount() == 8 + 10 * channels +
            plan.referencePackageCount() +
            (plan.hasEntryCapacitor ? 1 : 0) +
            (plan.hasFiveVoltIndicator ? 2 : 0) +
            (plan.hasTwelveVoltIndicator ? 2 : 0) +
            bitCount(plan.sensorInputFilterMask) +
            2 * bitCount(plan.outputIndicatorMask) +
            (plan.hasFiveVoltBleeder ? 1 : 0));
        check(board.getPadIds().size() == 18 + 27 * channels +
            2 * plan.referencePackageCount() +
            (plan.hasEntryCapacitor ? 2 : 0) +
            (plan.hasFiveVoltIndicator ? 4 : 0) +
            (plan.hasTwelveVoltIndicator ? 4 : 0) +
            2 * bitCount(plan.sensorInputFilterMask) +
            4 * bitCount(plan.outputIndicatorMask) +
            (plan.hasFiveVoltBleeder ? 2 : 0));
        check(board.getPowerInputIds().size() == 2 + channels);
        for (String netId : board.getNetIds())
            check(!board.getNet(netId).getPadIds().isEmpty());

        check(board.getComponent("J1") != null &&
            board.getComponent("F1") != null &&
            board.getComponent("DREV") != null &&
            board.getComponent("U1") != null &&
            board.getComponent("CIN") != null &&
            board.getComponent("C5") != null &&
            board.getComponent("REN") != null &&
            board.getComponent("JLOAD") != null);
        check((board.getComponent("C12") != null) == plan.hasEntryCapacitor);
        check((board.getComponent("RLED") != null) == plan.hasFiveVoltIndicator &&
            (board.getComponent("LED1") != null) == plan.hasFiveVoltIndicator);
        check((board.getComponent("RLED12") != null) == plan.hasTwelveVoltIndicator &&
            (board.getComponent("LED12") != null) == plan.hasTwelveVoltIndicator);
        check((board.getComponent("RBLEED5") != null) == plan.hasFiveVoltBleeder);
        if (plan.hasFiveVoltIndicator) {
            check("RAIL5".equals(board.getPad("RLED.1").getNetId()));
            check("LED_FEED".equals(board.getPad("RLED.2").getNetId()));
            check("LED_FEED".equals(board.getPad("LED1.A").getNetId()));
            check("CTRL_RETURN".equals(board.getPad("LED1.K").getNetId()));
        }
        if (plan.hasTwelveVoltIndicator) {
            check("RAIL12".equals(board.getPad("RLED12.1").getNetId()));
            check("LED12_FEED".equals(board.getPad("RLED12.2").getNetId()));
            check("LED12_FEED".equals(board.getPad("LED12.A").getNetId()));
            check("CTRL_RETURN".equals(board.getPad("LED12.K").getNetId()));
        }
        check(board.getComponent("U1").getPhysicalPackage() ==
            PhysicalPackages.TO220_REGULATOR_4);
        check(board.getComponent("CIN").getPhysicalPackage() ==
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR);

        for (String channel : plan.channels()) {
            String raw = channel + "_RAW";
            String output = "OUT_" + channel;
            check(board.getComponent("U2" + channel) != null &&
                board.getComponent("K" + channel) != null &&
                board.getComponent("JS" + channel) != null &&
                board.getComponent("JO" + channel) != null);
            check(board.getComponent("RPIN_" + channel).getPhysicalPackage()
                .isEquivalentTo(PhysicalPackages.AXIAL_RESISTOR));
            check(raw.equals(board.getPad("RPIN_" + channel + ".1").getNetId()));
            check("CTRL_RETURN".equals(
                board.getPad("RPIN_" + channel + ".2").getNetId()));
            check((board.getComponent("CFLT_" + channel) != null) ==
                plan.hasSensorInputFilter(channel));
            check((board.getComponent("LEDOUT_" + channel) != null) ==
                plan.hasOutputIndicator(channel));
            if (plan.hasOutputIndicator(channel)) {
                check(output.equals(board.getPad(
                    "RLEDOUT_" + channel + ".1").getNetId()));
                check(("LED_OUT_" + channel + "_FEED").equals(board.getPad(
                    "LEDOUT_" + channel + ".A").getNetId()));
                check("LOAD_RETURN".equals(board.getPad(
                    "LEDOUT_" + channel + ".K").getNetId()));
            }
        }
        check((board.getComponent("U2B") != null) == (channels == 2));
        check((board.getComponent("KB") != null) == (channels == 2));
        if (channels == 1)
            for (String absent : new String[] { "U2B", "RSB", "RPIN_B",
                    "JSB", "JOB", "KB", "QB", "DB", "RDB", "RPDB" })
                check(board.getComponent(absent) == null);
        check(!board.getPad("J1.2").getNetId().equals(
            board.getPad("JLOAD.2").getNetId()));
        check(board.getPad("U1.INPUT").getNetId().equals(
            board.getPad("DREV.K").getNetId()));
        check(board.getPad("U1.ENABLE").getNetId().equals(
            board.getPad("REN.2").getNetId()));

        if (plan.sharedReference()) {
            for (String channel : plan.channels())
                check("REF_SHARED".equals(board.getPad(
                    "U2" + channel + ".REFERENCE").getNetId()));
        } else for (String channel : plan.channels())
            check((channel + "_REF").equals(board.getPad(
                "U2" + channel + ".REFERENCE").getNetId()));
        if (plan.sharedHystereticReference()) {
            check(board.getComponent("RREF_H") != null &&
                board.getComponent("RREF_L") != null);
            for (String channel : plan.channels())
                check(board.getComponent("RFB_" + channel) != null);
        } else if (plan.referenceArrangement ==
                Rb30Plan.ReferenceArrangement.SHARED_DIRECT) {
            check(board.getComponent("RREF_H") != null &&
                board.getComponent("RREF_L") != null);
            for (String channel : plan.channels())
                check(board.getComponent("RFB_" + channel) == null);
        } else for (String channel : plan.channels()) {
            check(board.getComponent("RREF_H" + channel) != null &&
                board.getComponent("RREF_L" + channel) != null);
        }

        PowerDomainContract domains = Rb30PowerDomains.create(plan);
        check(domains.getReferences().size() == 2);
        check(domains.getSources().size() == 2 + channels);
        for (String netId : board.getNetIds())
            check(domains.referenceForNet(netId) != null);
        check(MeasurementReferencePolicy.check(domains,
            MeasurementReferencePolicy.Mode.DIFFERENTIAL,
            "RAIL5", "CTRL_RETURN", null).admitsReading());
        for (String channel : plan.channels())
            check(MeasurementReferencePolicy.check(domains,
                MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                "OUT_" + channel, "LOAD_RETURN", null).admitsReading());
        check(MeasurementReferencePolicy.check(domains,
            MeasurementReferencePolicy.Mode.DIFFERENTIAL,
            "RAIL5", "LOAD_RETURN", null).getDecision() ==
                MeasurementReferencePolicy.Decision.REJECTED);
        String relayFault = channels == 1 ? "RELAY_A_COIL_OPEN" :
            "RELAY_B_COIL_OPEN";
        check(contains(new String[] { "DREV_OPEN", "REN_OPEN",
            "SENSOR_A_OPEN", "DRIVE_A_OPEN", relayFault },
            plan.selectedFault));
    }

    private static int bitCount(int value) {
        int result = 0;
        while (value != 0) { result += value & 1; value >>>= 1; }
        return result;
    }

    private static boolean contains(String[] values, String expected) {
        for (String value : values)
            if (value.equals(expected)) return true;
        return false;
    }

    private static void verifyLegacyRegressionFixtures() {
        Rb30Plan.SupportVariant[] fixtures = {
            Rb30Plan.SupportVariant.COMPACT_33,
            Rb30Plan.SupportVariant.STANDARD_35,
            Rb30Plan.SupportVariant.FILTERED_37
        };
        int[] counts = { 33, 35, 37 };
        for (int index = 0; index < fixtures.length; index++) {
            Rb30Plan plan = Rb30Plan.withSupport(83L + index, fixtures[index]);
            check(plan.channelCount == 2);
            check(plan.supportVariant == fixtures[index]);
            check(plan.physicalPackageCount() == counts[index]);
            check(plan.board().getComponentIds().size() == counts[index]);
            check(plan.board().getComponent("C12") != null);
            check(plan.hasStatusIndicator() == (index != 0));
            check(plan.hasSensorInputFilters() == (index == 2));
            check(plan.canonical().startsWith("rb30-plan@4;seed=" +
                Long.toString(plan.seed) + ";topology=" + plan.topologyAxis() +
                ";support=C12:true"));
            check(plan.topology().endsWith("_B5_N"));
            verifyBoard(plan);
        }
        for (long seed : new long[] { 0L, 37L, Long.MIN_VALUE,
                Long.MAX_VALUE, 9007199254740993L }) {
            Rb30Plan reference = Rb30Plan.reference(seed);
            check(reference.canonical().equals(
                Rb30Plan.withSupport(seed,
                    Rb30Plan.SupportVariant.STANDARD_35).canonical()));
            check(reference.physicalPackageCount() == 35);
        }
    }

    private static void verifyInvalidMasksRejected() {
        boolean rejected = false;
        try {
            Rb30Plan.configured(1L, 1,
                Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT,
                false, false, false, 4, 0, false);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected);
        rejected = false;
        try {
            Rb30Plan.configured(1L, 1,
                Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT,
                false, false, false, 2, 0, false);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected);
        rejected = false;
        try {
            Rb30Plan.configured(1L, 2,
                Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT,
                true, true, true, 3, 3, true);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected);
    }

    private static void check(boolean condition) {
        check(condition, "Q30 plan assertion");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message + " (assertion " +
            assertions + ")");
    }
}
