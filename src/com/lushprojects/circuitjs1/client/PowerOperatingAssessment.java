package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.State;

/** A snapshot of declared commands and actual rail observations, never authorization. */
final class PowerOperatingAssessment {
    enum Connection { CONNECTED, ISOLATED, UNKNOWN }
    enum DriveState { ACTIVE, DISABLED, UNKNOWN }
    enum SupplyCondition { CONNECTED, PARTIAL, ALL_SOURCES_ISOLATED, UNKNOWN }
    enum RailCondition { DRIVEN, BACKFED, RESIDUAL, DISCHARGED, UNKNOWN }
    enum Readiness { POWER_OFF, WAITING, DISCHARGE, UNKNOWN, READY }
    static final class SourceState {
        private final Connection connection;
        private final DriveState driveState;
        SourceState(Connection connection, DriveState drive) {
            if (connection == null || drive == null) throw new IllegalArgumentException("Missing source state");
            this.connection = connection; driveState = drive;
        }
        Connection getConnection() { return connection; }
        DriveState getDriveState() { return driveState; }
        static SourceState connected() { return new SourceState(Connection.CONNECTED, DriveState.ACTIVE); }
        static SourceState isolated() { return new SourceState(Connection.ISOLATED, DriveState.DISABLED); }
        static SourceState unknown() { return new SourceState(Connection.UNKNOWN, DriveState.UNKNOWN); }
    }
    private final SupplyCondition supplyCondition;
    private final Map<String,RailCondition> railConditions;
    private final Readiness readiness;
    private final List<String> issues;
    private PowerOperatingAssessment(SupplyCondition supply, Map<String,RailCondition> rails,
            Readiness ready, Set<String> issues) {
        supplyCondition = supply; railConditions = Collections.unmodifiableMap(new TreeMap<String,RailCondition>(rails));
        readiness = ready; this.issues = Collections.unmodifiableList(new ArrayList<String>(issues));
    }
    SupplyCondition getSupplyCondition() { return supplyCondition; }
    Map<String,RailCondition> getRailConditions() { return railConditions; }
    Readiness getReadiness() { return readiness; }
    List<String> getIssues() { return issues; }
    boolean areAllSourcesIsolated() { return supplyCondition == SupplyCondition.ALL_SOURCES_ISOLATED; }

    static PowerOperatingAssessment assess(PowerDomainContract contract,
            Map<String,SourceState> states, Map<String,Double> volts, boolean current) {
        if (contract == null || states == null || volts == null)
            throw new IllegalArgumentException("Power assessment inputs required");
        if (!contract.getSources().keySet().containsAll(states.keySet()) ||
                !contract.getRails().keySet().containsAll(volts.keySet()))
            throw new IllegalArgumentException("Foreign power observations");
        TreeSet<String> issues = new TreeSet<String>();
        TreeSet<String> driven = new TreeSet<String>(), reachable = new TreeSet<String>();
        TreeMap<String,Integer> stiff = new TreeMap<String,Integer>();
        int connected = 0, isolated = 0; boolean unknown = false;
        for (PowerDomainContract.Source source : contract.getSources().values()) {
            SourceState state = states.get(source.getId());
            if (state == null || state.connection == Connection.UNKNOWN) {
                unknown = true; issues.add("SOURCE_UNKNOWN:" + source.getId()); continue;
            }
            if (state.connection == Connection.ISOLATED) { isolated++; continue; }
            connected++;
            if (state.driveState == DriveState.UNKNOWN) {
                issues.add("DRIVE_UNKNOWN:" + source.getId()); unknown = true;
            }
            if (state.driveState != DriveState.DISABLED) {
                reachable.add(source.getRailId());
                if (state.driveState == DriveState.ACTIVE) {
                    driven.add(source.getRailId());
                    if (source.getDrive() == Drive.STIFF_VOLTAGE || source.getDrive() == Drive.PUSH_PULL) {
                        Integer n = stiff.get(source.getRailId());
                        stiff.put(source.getRailId(), n == null ? 1 : n + 1);
                    }
                }
            }
        }
        for (String rail : stiff.keySet())
            if (stiff.get(rail) > 1) issues.add("SOURCE_CONTENTION:" + rail);
        // Monotonic reachability terminates even for cycles. A forbidden or unknown
        // path is not erased: it can still explain an energized victim rail.
        for (int pass = 0; pass < contract.getRails().size(); pass++) {
            boolean changed = false;
            for (PowerDomainContract.BackfeedPath path : contract.getBackfeedPaths().values()) {
                if (!reachable.contains(path.getFromRailId())) continue;
                if (path.getMaximumCurrentAmps().getState() == State.KNOWN &&
                        path.getMaximumCurrentAmps().getValue() == 0) continue;
                if (!path.isPermitted()) issues.add("BACKFEED_FORBIDDEN:" + path.getId());
                if (path.getMaximumCurrentAmps().getState() != State.KNOWN)
                    issues.add("BACKFEED_UNBOUNDED:" + path.getId());
                PowerDomainContract.Reference a = contract.referenceForNet(path.getFromRailId());
                PowerDomainContract.Reference b = contract.referenceForNet(path.getToRailId());
                if (a.getIsolationId() == null || b.getIsolationId() == null ||
                        !a.getId().equals(b.getId()))
                    issues.add("BACKFEED_REFERENCE_UNPROVEN:" + path.getId());
                if (reachable.add(path.getToRailId())) changed = true;
            }
            if (!changed) break;
        }
        SupplyCondition supply = unknown ? SupplyCondition.UNKNOWN :
            connected == contract.getSources().size() ? SupplyCondition.CONNECTED :
            isolated == contract.getSources().size() ? SupplyCondition.ALL_SOURCES_ISOLATED : SupplyCondition.PARTIAL;
        TreeMap<String,RailCondition> rails = new TreeMap<String,RailCondition>();
        boolean residual = false, invalid = false;
        for (PowerDomainContract.Rail rail : contract.getRails().values()) {
            Double sample = volts.get(rail.getId());
            RailCondition condition = RailCondition.UNKNOWN;
            PowerDomainContract.Reference reference = contract.referenceForNet(rail.getId());
            boolean referenceKnown = reference != null && reference.getIsolationId() != null;
            boolean sourceEnvelopeKnown = true;
            if (!referenceKnown) {
                invalid = true; issues.add("REFERENCE_UNKNOWN:" + rail.getId());
            }
            if (current && sample != null && PowerDomainContract.finite(sample))
                for (PowerDomainContract.Source source : contract.getSources().values()) {
                    SourceState sourceState = states.get(source.getId());
                    if (!rail.getId().equals(source.getRailId()) || sourceState == null ||
                            sourceState.connection != Connection.CONNECTED ||
                            sourceState.driveState != DriveState.ACTIVE) continue;
                    if (source.getVoltageEnvelope().getState() != State.KNOWN) {
                        sourceEnvelopeKnown = false; invalid = true;
                        issues.add("SOURCE_ENVELOPE_UNKNOWN:" + source.getId());
                    } else if (!source.getVoltageEnvelope().contains(sample)) {
                        sourceEnvelopeKnown = false; invalid = true;
                        issues.add("SOURCE_VOLTAGE_OUT_OF_ENVELOPE:" + source.getId());
                    }
                }
            if (!current) { issues.add("OBSERVATION_STALE:" + rail.getId()); }
            else if (sample == null || !PowerDomainContract.finite(sample)) {
                invalid = true; issues.add("OBSERVATION_UNKNOWN:" + rail.getId());
            } else if (referenceKnown && sourceEnvelopeKnown) {
                boolean energized = Math.abs(sample) > ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS;
                if (driven.contains(rail.getId())) condition = RailCondition.DRIVEN;
                else if (energized && reachable.contains(rail.getId())) condition = RailCondition.BACKFED;
                else if (energized && rail.getStorageRequirement() == PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED) {
                    condition = RailCondition.RESIDUAL; residual = true;
                } else if (energized) {
                    invalid = true; issues.add("UNEXPLAINED_ENERGIZATION:" + rail.getId());
                } else if (rail.getStorageRequirement() == PowerDomainContract.StorageRequirement.UNKNOWN) {
                    invalid = true; issues.add("STORAGE_UNKNOWN:" + rail.getId());
                } else condition = RailCondition.DISCHARGED;
            }
            rails.put(rail.getId(), condition);
        }
        Readiness ready = connected > 0 ? Readiness.POWER_OFF : unknown ? Readiness.UNKNOWN :
            !current ? Readiness.WAITING : invalid ? Readiness.UNKNOWN : residual ? Readiness.DISCHARGE : Readiness.READY;
        return new PowerOperatingAssessment(supply, rails, ready, issues);
    }
}
