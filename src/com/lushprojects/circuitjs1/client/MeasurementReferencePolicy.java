package com.lushprojects.circuitjs1.client;

/** Declared reference/connection policy. A numeric ground is never physical earth. */
final class MeasurementReferencePolicy {
    enum Mode { DIFFERENTIAL, EARTH_REFERENCED }
    enum Decision { ADMITTED, REJECTED, UNPROVEN, CONNECTION_REQUIRED, NOT_APPLICABLE }
    static final class Result {
        private final Decision decision;
        private final String reason;
        Result(Decision decision, String reason) {
            if (decision == null || reason == null) throw new IllegalArgumentException("Reference result required");
            this.decision = decision; this.reason = reason;
        }
        Decision getDecision() { return decision; }
        String getReason() { return reason; }
        boolean admitsReading() { return decision == Decision.ADMITTED; }
        boolean requiresEarthConnection() { return decision == Decision.CONNECTION_REQUIRED; }
    }
    private MeasurementReferencePolicy() { }
    static Result notApplicable() { return new Result(Decision.NOT_APPLICABLE, "NO_DOMAIN_POLICY"); }
    static Result check(PowerDomainContract contract, Mode mode,
            String redNet, String blackNet, String earthId) {
        if (contract == null || mode == null || redNet == null || blackNet == null)
            return new Result(Decision.UNPROVEN, "MISSING_REFERENCE");
        PowerDomainContract.Reference red = contract.referenceForNet(redNet), black = contract.referenceForNet(blackNet);
        if (red == null || black == null) return new Result(Decision.UNPROVEN, "UNDECLARED_REFERENCE");
        if (red.getIsolationId() == null || black.getIsolationId() == null)
            return new Result(Decision.UNPROVEN, "UNKNOWN_ISOLATION");
        if (!red.getIsolationId().equals(black.getIsolationId()))
            return new Result(Decision.REJECTED, "ISOLATION_BOUNDARY");
        if (!red.getId().equals(black.getId()))
            return new Result(Decision.UNPROVEN, "REFERENCES_NOT_JOINED");
        if (mode == Mode.DIFFERENTIAL) return new Result(Decision.ADMITTED, "SAME_RESOLVED_REFERENCE");
        if (earthId == null || red.getEarthId() == null || !earthId.equals(red.getEarthId()))
            return new Result(Decision.UNPROVEN, "EARTH_NOT_DECLARED");
        if (!blackNet.equals(red.getId()) || !red.isEarthBondPermitted())
            return new Result(Decision.REJECTED, "EARTH_RETURN_NOT_PERMITTED");
        // A request is not an installed, observed graph connection. No scope is constructed here.
        return new Result(Decision.CONNECTION_REQUIRED, "OWNED_EARTH_CONNECTION_REQUIRED");
    }
    static Result combine(Result a, Result b) {
        if (a == null || b == null) return new Result(Decision.UNPROVEN, "MISSING_CAPABILITY_RESULT");
        return rank(a.decision) >= rank(b.decision) ? a : b;
    }
    private static int rank(Decision d) {
        switch (d) {
        case REJECTED: return 4;
        case UNPROVEN: return 3;
        case CONNECTION_REQUIRED: return 2;
        case ADMITTED: return 1;
        default: return 0;
        }
    }
}
