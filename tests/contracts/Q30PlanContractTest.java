package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Set;

/** Independent package, structural choice and shared-domain canary for Q30. */
public final class Q30PlanContractTest {
    private static int assertions;

    private static final String[] TOPOLOGY_AXES = {
        "RB30_SEPARATE_DIRECT_A_BJT_B_BJT",
        "RB30_SEPARATE_DIRECT_A_NMOS_B_BJT",
        "RB30_SEPARATE_DIRECT_A_BJT_B_NMOS",
        "RB30_SEPARATE_DIRECT_A_NMOS_B_NMOS",
        "RB30_SHARED_HYSTERETIC_A_BJT_B_BJT",
        "RB30_SHARED_HYSTERETIC_A_NMOS_B_BJT",
        "RB30_SHARED_HYSTERETIC_A_BJT_B_NMOS",
        "RB30_SHARED_HYSTERETIC_A_NMOS_B_NMOS"
    };

    /* Fixed before routing: one representative for each axis/support cell. */
    private static final long[][] REPRESENTATIVE_SEEDS = {
        { 0, 1, 15, 14, 44, 8, 10, 12 },
        { 48, 35, 6, 18, 43, 93, 20, 64 },
        { 56, 13, 4, 11, 2, 3, 19, 9 }
    };

    /* The matching held-out cohort is fixed independently of route outcomes. */
    private static final long[][] HELD_OUT_SEEDS = {
        { 7, 42, 24, 21, 75, 22, 105, 27 },
        { 53, 50, 16, 25, 60, 100, 59, 84 },
        { 70, 41, 45, 23, 5, 38, 40, 26 }
    };

    private static final Rb30Plan.SupportVariant[] CORPUS_SUPPORTS = {
        Rb30Plan.SupportVariant.COMPACT_33,
        Rb30Plan.SupportVariant.STANDARD_35,
        Rb30Plan.SupportVariant.FILTERED_37
    };

    public static void main(String[] args) {
        Set<String> topologies = new HashSet<String>();
        int bjtA = 0, nmosA = 0, bjtB = 0, nmosB = 0;
        int separate = 0, shared = 0;
        for (long seed : new long[] { Long.MIN_VALUE, Long.MAX_VALUE, -101,
                -17, -1, 0, 1, 2, 3, 4, 5, 11, 19, 31, 47, 83,
                9007199254740993L }) {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            Rb30Plan replay = Rb30Plan.resolve(seed);
            check(plan.canonical().equals(replay.canonical()));
            check(plan.layoutSeed == replay.layoutSeed &&
                plan.routingSeed == replay.routingSeed);
            TroubleshootBoard board = plan.board();
            check(board.getComponentIds().size() == plan.physicalPackageCount());
            check(board.getPadIds().size() >= 82 &&
                board.getPadIds().size() <= 90);
            for (String channel : new String[] { "A", "B" }) {
                String pullDown = "RPIN_" + channel;
                check(board.getComponent(pullDown) != null);
                check(board.getComponent(pullDown).getPhysicalPackage()
                    .isEquivalentTo(PhysicalPackages.AXIAL_RESISTOR));
                check((channel + "_RAW").equals(
                    board.getPad(pullDown + ".1").getNetId()));
                check("CTRL_RETURN".equals(
                    board.getPad(pullDown + ".2").getNetId()));
            }
            check(board.getPowerInputIds().size() == 4);
            for (String netId : board.getNetIds())
                check(!board.getNet(netId).getPadIds().isEmpty());
            PowerDomainContract domains = Rb30PowerDomains.create(plan);
            check(domains.getReferences().size() == 2);
            check(domains.getSources().size() == 4);
            for (String netId : board.getNetIds())
                check(domains.referenceForNet(netId) != null);
            check(MeasurementReferencePolicy.check(domains,
                MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                "RAIL5", "CTRL_RETURN", null).admitsReading());
            check(MeasurementReferencePolicy.check(domains,
                MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                "OUT_A", "LOAD_RETURN", null).admitsReading());
            check(MeasurementReferencePolicy.check(domains,
                MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                "RAIL5", "LOAD_RETURN", null).getDecision() ==
                    MeasurementReferencePolicy.Decision.REJECTED);
            check(board.getComponent("U1").getPhysicalPackage() ==
                PhysicalPackages.TO220_REGULATOR_4);
            check(board.getComponent("CIN").getPhysicalPackage() ==
                PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR);
            for (String channel : new String[] { "A", "B" }) {
                check(board.getComponent("U2" + channel).getPhysicalPackage() ==
                    PhysicalPackages.E04_DECISION_CONTROL_5);
                check(board.getComponent("K" + channel).getPhysicalPackage() ==
                    PhysicalPackages.RELAY_SPDT);
                check(board.getPad("K" + channel + ".A1").getNetId().equals(
                    board.getPad("U1.OUTPUT").getNetId()));
                check(board.getPad("K" + channel + ".COM").getNetId().equals(
                    board.getPad("JLOAD.1").getNetId()));
                check(board.getPad("K" + channel + ".NO").getNetId().equals(
                    board.getPad("JO" + channel + ".1").getNetId()));
                check(!board.getPad("K" + channel + ".A1").getNetId().equals(
                    board.getPad("K" + channel + ".COM").getNetId()));
            }
            check(!board.getPad("J1.2").getNetId().equals(
                board.getPad("JLOAD.2").getNetId()));
            check(board.getPad("U1.INPUT").getNetId().equals(
                board.getPad("DREV.K").getNetId()));
            check(board.getPad("U1.ENABLE").getNetId().equals(
                board.getPad("REN.2").getNetId()));
            check(board.getPad("U1.OUTPUT").getNetId().equals(
                board.getPad("U2A.RAIL").getNetId()));
            check(board.getPad("U2A.RAIL").getNetId().equals(
                board.getPad("U2B.RAIL").getNetId()));
            if (plan.sharedHystereticReference) {
                shared++;
                check(board.getPad("U2A.REFERENCE").getNetId().equals(
                    board.getPad("U2B.REFERENCE").getNetId()));
                check(board.getComponent("RFB_A") != null &&
                    board.getComponent("RFB_B") != null);
                check(board.getComponent("RREF_HA") == null);
                check("sensor-reference".equals(board.getPlacementConstraints()
                    .get("RREF_H").regionId));
                check("sensor-reference".equals(board.getPlacementConstraints()
                    .get("RREF_L").regionId));
            } else {
                separate++;
                check(!board.getPad("U2A.REFERENCE").getNetId().equals(
                    board.getPad("U2B.REFERENCE").getNetId()));
                check(board.getComponent("RFB_A") == null &&
                    board.getComponent("RFB_B") == null);
                check(board.getComponent("RREF_HA") != null);
            }
            bjtA += plan.driverABjt ? 1 : 0;
            nmosA += plan.driverABjt ? 0 : 1;
            bjtB += plan.driverBBjt ? 1 : 0;
            nmosB += plan.driverBBjt ? 0 : 1;
            topologies.add(plan.topologyAxis());
        }
        check(bjtA > 0 && nmosA > 0 && bjtB > 0 && nmosB > 0);
        check(separate > 0 && shared > 0);
        check(topologies.size() >= 4);
        verifyFrozenCorpus();
        verifyReferenceSelection();
        System.out.println("PASS: Q30 plan contracts " + assertions +
            " assertions, topologyAxes=" + topologies.size() +
            ", representativeRows=24, heldOutRows=24" +
            ", separate=" + separate + ", shared=" + shared);
    }

    private static void verifyFrozenCorpus() {
        Set<String> representativeCells = new HashSet<String>();
        Set<String> heldOutCells = new HashSet<String>();
        Set<Long> allSeeds = new HashSet<Long>();
        for (int supportIndex = 0; supportIndex < CORPUS_SUPPORTS.length;
                supportIndex++) {
            Rb30Plan.SupportVariant expectedSupport =
                CORPUS_SUPPORTS[supportIndex];
            for (int axisIndex = 0; axisIndex < TOPOLOGY_AXES.length;
                    axisIndex++) {
                verifyCorpusRow(REPRESENTATIVE_SEEDS[supportIndex][axisIndex],
                    expectedSupport, axisIndex, representativeCells, allSeeds);
                verifyCorpusRow(HELD_OUT_SEEDS[supportIndex][axisIndex],
                    expectedSupport, axisIndex, heldOutCells, allSeeds);
            }
        }
        check(representativeCells.size() == 24 && heldOutCells.size() == 24);
        check(allSeeds.size() == 48);
    }

    private static void verifyCorpusRow(long seed,
            Rb30Plan.SupportVariant expectedSupport, int axisIndex,
            Set<String> cohortCells, Set<Long> allSeeds) {
        Rb30Plan plan = Rb30Plan.resolve(seed);
        check(plan.supportVariant == expectedSupport);
        check(plan.topologyAxis().equals(TOPOLOGY_AXES[axisIndex]));
        check(plan.physicalPackageCount() == expectedSupport.packageCount());
        check(plan.canonical().startsWith("rb30-plan@3;seed=" +
            Long.toString(seed) + ";topology=" + TOPOLOGY_AXES[axisIndex] +
            ";support=" + expectedSupport.identity()));
        check(plan.canonical().contains(
            ";sensorPullDowns=RPIN_A:1000ohm(A_RAW,CTRL_RETURN)," +
            "RPIN_B:1000ohm(B_RAW,CTRL_RETURN)"));
        check(plan.topology().endsWith("_" + expectedSupport.name()));
        TroubleshootBoard board = plan.board();
        check(board.getComponentIds().size() == expectedSupport.packageCount());
        int expectedPadCount = 82 +
            (expectedSupport.hasStatusIndicator() ? 4 : 0) +
            (expectedSupport.hasSensorInputFilters() ? 4 : 0);
        check(board.getPadIds().size() == expectedPadCount);
        check(cohortCells.add(axisIndex + "|" + expectedSupport.name()));
        check(allSeeds.add(Long.valueOf(seed)));
        if (expectedSupport.hasStatusIndicator()) {
            check(board.getComponent("RLED") != null &&
                board.getComponent("LED1") != null);
        } else {
            check(board.getComponent("RLED") == null &&
                board.getComponent("LED1") == null);
        }
        if (expectedSupport.hasSensorInputFilters()) {
            check(board.getComponent("CFLT_A") != null &&
                board.getComponent("CFLT_B") != null);
            check("A_SENSE".equals(board.getPad("CFLT_A.1").getNetId()) &&
                "CTRL_RETURN".equals(board.getPad("CFLT_A.2").getNetId()));
            check("B_SENSE".equals(board.getPad("CFLT_B.1").getNetId()) &&
                "CTRL_RETURN".equals(board.getPad("CFLT_B.2").getNetId()));
            PowerDomainContract domains = Rb30PowerDomains.create(plan);
            check(domains.getRails().get("A_SENSE").getStorageRequirement() ==
                PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED);
            check(domains.getRails().get("B_SENSE").getStorageRequirement() ==
                PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED);
        } else {
            check(board.getComponent("CFLT_A") == null &&
                board.getComponent("CFLT_B") == null);
        }
        check(Rb30Plan.resolve(seed).canonical().equals(plan.canonical()));
    }

    private static void verifyReferenceSelection() {
        for (long seed : new long[] { 0L, 37L, 83L, Long.MIN_VALUE,
                Long.MAX_VALUE, 9007199254740993L }) {
            Rb30Plan reference = Rb30Plan.reference(seed);
            Rb30Plan pinned = Rb30Plan.withSupport(seed,
                Rb30Plan.SupportVariant.STANDARD_35);
            Rb30Plan seeded = Rb30Plan.resolve(seed);
            check(reference.canonical().equals(pinned.canonical()));
            check(reference.physicalPackageCount() == 35);
            check(reference.topologyAxis().equals(seeded.topologyAxis()));
            check(reference.selectedFault.equals(seeded.selectedFault));
            check(reference.layoutSeed == seeded.layoutSeed &&
                reference.routingSeed == seeded.routingSeed);
            check(reference.hasStatusIndicator() &&
                !reference.hasSensorInputFilters());
            check(reference.board().getComponentIds().size() == 35);
            check(reference.topology().endsWith("_STANDARD_35"));
        }
        boolean nullSupportRejected = false;
        try {
            Rb30Plan.withSupport(0L, null);
        } catch (IllegalArgumentException expected) {
            nullSupportRejected = true;
        }
        check(nullSupportRejected);
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Q30 plan assertion " + assertions);
    }
}
