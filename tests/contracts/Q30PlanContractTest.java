package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Set;

/** Independent package, structural choice and shared-domain canary for Q30. */
public final class Q30PlanContractTest {
    private static int assertions;

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
            check(board.getComponentIds().size() == 33);
            check(board.getPadIds().size() >= 75 &&
                board.getPadIds().size() <= 90);
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
            topologies.add(plan.topology());
        }
        check(bjtA > 0 && nmosA > 0 && bjtB > 0 && nmosB > 0);
        check(separate > 0 && shared > 0);
        check(topologies.size() >= 4);
        System.out.println("PASS: Q30 plan contracts " + assertions +
            " assertions, topologies=" + topologies.size() +
            ", separate=" + separate + ", shared=" + shared);
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Q30 plan assertion " + assertions);
    }
}
