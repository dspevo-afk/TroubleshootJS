package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;

/** Explicit control and isolated load references for the Q30 device intent. */
final class Rb30PowerDomains {
    private Rb30PowerDomains() { }

    static PowerDomainContract create(Rb30Plan plan) {
        if (plan == null) throw new IllegalArgumentException("Missing Q30 plan");
        TroubleshootBoard board = plan.board();
        Vector<PowerDomainContract.Rail> rails =
            new Vector<PowerDomainContract.Rail>();
        for (String id : board.getNetIds()) {
            if ("CTRL_RETURN".equals(id) || "LOAD_RETURN".equals(id))
                continue;
            boolean load = "LOAD12".equals(id) || "OUT_A".equals(id) ||
                "OUT_B".equals(id) || "NC_A".equals(id) ||
                "NC_B".equals(id);
            boolean storage = "FUSED12".equals(id) || "RAIL12".equals(id) ||
                "RAIL5".equals(id);
            rails.add(new PowerDomainContract.Rail(id,
                load ? "LOAD_RETURN" : "CTRL_RETURN",
                storage ? PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED :
                    PowerDomainContract.StorageRequirement.NONE));
        }
        PowerDomainContract contract = new PowerDomainContract(
            "RB30_MULTI_RAIL", Arrays.asList(
                new PowerDomainContract.Reference("CTRL_RETURN", "CONTROL",
                    null, false),
                new PowerDomainContract.Reference("LOAD_RETURN", "LOAD",
                    null, false)), rails,
            Arrays.asList(
                source("MAIN12", "RAW12", 12, .25, .05, .25),
                source("SENSOR_A", "A_RAW", 5, .25, .05, .25),
                source("SENSOR_B", "B_RAW", 5, .25, .05, .25),
                source("LOAD12", "LOAD12", 12, .25, .05, .25)),
            Collections.<PowerDomainContract.BackfeedPath>emptyList());
        contract.requireSourceCoverage(board.getPowerInputIds());
        return contract;
    }

    private static PowerDomainContract.Source source(String id, String rail,
            double volts, double capacity, double resistance, double limit) {
        return new PowerDomainContract.Source(id, rail, Range.known(0, volts),
            Scalar.known(capacity), Scalar.known(resistance),
            Scalar.known(limit), Drive.RESISTIVE_SOURCE);
    }
}
