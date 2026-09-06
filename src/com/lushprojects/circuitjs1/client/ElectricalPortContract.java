package com.lushprojects.circuitjs1.client;

/** Immutable declared evidence. Nothing here reads or predicts a solver value. */
final class ElectricalPortContract {
    enum Role { RAIL, RETURN, CONTROL, ANALOG, DIGITAL, PASSIVE, LOAD }
    enum Direction { INPUT, OUTPUT, BIDIRECTIONAL }
    enum Behavior { SOURCE, SINK, SOURCE_AND_SINK, PASSIVE }
    enum Drive { NONE, STIFF_VOLTAGE, PUSH_PULL, RESISTIVE_SOURCE, OPEN_DRAIN, UNKNOWN }
    enum Loading { NONE, BOUNDED_CURRENT, UNKNOWN }
    enum MergePolicy { ALLOW, FORBID, UNKNOWN }
    enum State { KNOWN, UNKNOWN, NOT_APPLICABLE }
    enum ActiveLevel { HIGH, LOW, UNKNOWN, NOT_APPLICABLE }
    enum AccessRequirement { NONE, PROBEABLE, CONNECTABLE }
    enum AccessProvision { PROBEABLE, CONNECTABLE, BOTH, INACCESSIBLE, UNKNOWN, NOT_APPLICABLE }

    static final class Scalar {
        private final State state;
        private final double value;
        private Scalar(State state, double value) { this.state = state; this.value = value; }
        static Scalar known(double value) {
            if (!finite(value)) throw new ElectricalContractException(ElectricalContractException.Code.INVALID_SCALAR,
                "scalar", "", "A known scalar must be finite");
            return new Scalar(State.KNOWN, value == 0 ? 0 : value);
        }
        static Scalar unknown() { return new Scalar(State.UNKNOWN, 0); }
        static Scalar notApplicable() { return new Scalar(State.NOT_APPLICABLE, 0); }
        State getState() { return state; }
        double getValue() {
            if (state != State.KNOWN) throw new IllegalStateException("Scalar is " + state);
            return value;
        }
    }

    static final class Range {
        private final State state;
        private final double minimum;
        private final double maximum;
        private Range(State state, double minimum, double maximum) {
            this.state = state; this.minimum = minimum; this.maximum = maximum;
        }
        static Range known(double minimum, double maximum) {
            if (!finite(minimum) || !finite(maximum) || minimum > maximum)
                throw new ElectricalContractException(ElectricalContractException.Code.INVALID_RANGE,
                    "range", "", "Known range endpoints must be finite and ordered");
            return new Range(State.KNOWN, minimum == 0 ? 0 : minimum, maximum == 0 ? 0 : maximum);
        }
        static Range unknown() { return new Range(State.UNKNOWN, 0, 0); }
        static Range notApplicable() { return new Range(State.NOT_APPLICABLE, 0, 0); }
        State getState() { return state; }
        double getMinimum() { requireKnown(); return minimum; }
        double getMaximum() { requireKnown(); return maximum; }
        boolean contains(Range other) {
            requireKnown(); other.requireKnown();
            return minimum <= other.minimum && maximum >= other.maximum;
        }
        boolean contains(double value) { requireKnown(); return minimum <= value && value <= maximum; }
        private void requireKnown() {
            if (state != State.KNOWN) throw new IllegalStateException("Range is " + state);
        }
    }

    /** Reference identity is local to the owning block; null isolation ID means UNKNOWN. */
    static final class Domain {
        private final String referenceNetId;
        private final String isolationId;
        private Domain(String referenceNetId, String isolationId) {
            this.referenceNetId = ElectricalContractException.id(referenceNetId, "reference.net");
            this.isolationId = isolationId == null ? null : ElectricalContractException.id(isolationId, "reference.isolation");
        }
        static Domain known(String referenceNetId, String isolationId) {
            ElectricalContractException.required(isolationId, "reference.isolation", referenceNetId);
            return new Domain(referenceNetId, isolationId);
        }
        static Domain unknownIsolation(String referenceNetId) { return new Domain(referenceNetId, null); }
        String getReferenceNetId() { return referenceNetId; }
        String getIsolationId() { return isolationId; }
    }

    static final class Digital {
        private final ActiveLevel activeLevel;
        private final Scalar lowMaximum, highMinimum, inputLowMaximum, inputHighMinimum;
        Digital(ActiveLevel activeLevel, Scalar lowMaximum, Scalar highMinimum,
                Scalar inputLowMaximum, Scalar inputHighMinimum) {
            this.activeLevel = ElectricalContractException.required(activeLevel, "digital.activeLevel", "");
            this.lowMaximum = ElectricalContractException.required(lowMaximum, "digital.lowMaximum", "");
            this.highMinimum = ElectricalContractException.required(highMinimum, "digital.highMinimum", "");
            this.inputLowMaximum = ElectricalContractException.required(inputLowMaximum, "digital.inputLowMaximum", "");
            this.inputHighMinimum = ElectricalContractException.required(inputHighMinimum, "digital.inputHighMinimum", "");
            orderedLevels(lowMaximum, highMinimum, "digital.guarantees");
            orderedLevels(inputLowMaximum, inputHighMinimum, "digital.thresholds");
            if (activeLevel == ActiveLevel.NOT_APPLICABLE &&
                    (lowMaximum.state != State.NOT_APPLICABLE || highMinimum.state != State.NOT_APPLICABLE ||
                    inputLowMaximum.state != State.NOT_APPLICABLE || inputHighMinimum.state != State.NOT_APPLICABLE))
                throw new ElectricalContractException(ElectricalContractException.Code.CONTRADICTORY_FIELD,
                    "digital.activeLevel", "", "Not-applicable logic has no logic evidence");
        }
        static Digital notApplicable() {
            return new Digital(ActiveLevel.NOT_APPLICABLE, Scalar.notApplicable(), Scalar.notApplicable(),
                Scalar.notApplicable(), Scalar.notApplicable());
        }
        static Digital unknownInput() {
            return new Digital(ActiveLevel.UNKNOWN, Scalar.notApplicable(), Scalar.notApplicable(),
                Scalar.unknown(), Scalar.unknown());
        }
        ActiveLevel getActiveLevel() { return activeLevel; }
        Scalar getLowMaximum() { return lowMaximum; }
        Scalar getHighMinimum() { return highMinimum; }
        Scalar getInputLowMaximum() { return inputLowMaximum; }
        Scalar getInputHighMinimum() { return inputHighMinimum; }
        private static void orderedLevels(Scalar low, Scalar high, String field) {
            if (low.state == State.KNOWN && high.state == State.KNOWN && low.value >= high.value)
                throw new ElectricalContractException(ElectricalContractException.Code.INVALID_THRESHOLD,
                    field, "", "LOW maximum must be below HIGH minimum");
        }
    }

    private final String id;
    private final Role role;
    private final Direction direction;
    private final Behavior behavior;
    private final Drive drive;
    private final Domain domain;
    private final Scalar nominalVoltage, capacityAmps, demandAmps;
    private final Range guaranteedVoltage, allowedVoltage;
    private final Loading loading;
    private final Digital digital;
    private final MergePolicy mergePolicy;
    private final AccessRequirement accessRequirement;
    private final AccessProvision accessProvision;

    ElectricalPortContract(String id, Role role, Direction direction, Behavior behavior, Drive drive,
            Domain domain, Scalar nominalVoltage, Range guaranteedVoltage, Range allowedVoltage,
            Loading loading, Scalar capacityAmps, Scalar demandAmps, Digital digital,
            MergePolicy mergePolicy, AccessRequirement accessRequirement, AccessProvision accessProvision) {
        this.id = ElectricalContractException.id(id, "port.id");
        this.role = ElectricalContractException.required(role, "role", id);
        this.direction = ElectricalContractException.required(direction, "direction", id);
        this.behavior = ElectricalContractException.required(behavior, "behavior", id);
        this.drive = ElectricalContractException.required(drive, "drive", id);
        this.domain = ElectricalContractException.required(domain, "reference", id);
        this.nominalVoltage = ElectricalContractException.required(nominalVoltage, "nominalVoltage", id);
        this.guaranteedVoltage = ElectricalContractException.required(guaranteedVoltage, "guaranteedVoltage", id);
        this.allowedVoltage = ElectricalContractException.required(allowedVoltage, "allowedVoltage", id);
        this.loading = ElectricalContractException.required(loading, "loading", id);
        this.capacityAmps = ElectricalContractException.required(capacityAmps, "capacityAmps", id);
        this.demandAmps = ElectricalContractException.required(demandAmps, "demandAmps", id);
        this.digital = ElectricalContractException.required(digital, "digital", id);
        this.mergePolicy = ElectricalContractException.required(mergePolicy, "mergePolicy", id);
        this.accessRequirement = ElectricalContractException.required(accessRequirement, "accessRequirement", id);
        this.accessProvision = ElectricalContractException.required(accessProvision, "accessProvision", id);
        nonnegative(capacityAmps, "capacityAmps"); nonnegative(demandAmps, "demandAmps");
        within(nominalVoltage, guaranteedVoltage, "nominalVoltage.guaranteed");
        within(nominalVoltage, allowedVoltage, "nominalVoltage.allowed");
        within(digital.lowMaximum, guaranteedVoltage, "digital.lowMaximum");
        within(digital.highMinimum, guaranteedVoltage, "digital.highMinimum");
        within(digital.inputLowMaximum, allowedVoltage, "digital.inputLowMaximum");
        within(digital.inputHighMinimum, allowedVoltage, "digital.inputHighMinimum");
    }
    String getId() { return id; }
    Role getRole() { return role; }
    Direction getDirection() { return direction; }
    Behavior getBehavior() { return behavior; }
    Drive getDrive() { return drive; }
    Domain getDomain() { return domain; }
    Scalar getNominalVoltage() { return nominalVoltage; }
    Range getGuaranteedVoltage() { return guaranteedVoltage; }
    Range getAllowedVoltage() { return allowedVoltage; }
    Loading getLoading() { return loading; }
    Scalar getCapacityAmps() { return capacityAmps; }
    Scalar getDemandAmps() { return demandAmps; }
    Digital getDigital() { return digital; }
    MergePolicy getMergePolicy() { return mergePolicy; }
    AccessRequirement getAccessRequirement() { return accessRequirement; }
    AccessProvision getAccessProvision() { return accessProvision; }

    private void nonnegative(Scalar value, String field) {
        if (value.state == State.KNOWN && value.value < 0)
            throw new ElectricalContractException(ElectricalContractException.Code.INVALID_SCALAR, field, id, "Current bound cannot be negative");
    }
    private void within(Scalar value, Range range, String field) {
        if (value.state == State.KNOWN && range.state == State.KNOWN && !range.contains(value.value))
            throw new ElectricalContractException(ElectricalContractException.Code.CONTRADICTORY_FIELD,
                field, id, "Declared value falls outside declared range");
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
