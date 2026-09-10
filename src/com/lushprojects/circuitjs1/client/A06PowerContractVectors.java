package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.PowerDomainContract.*;
import com.lushprojects.circuitjs1.client.PowerOperatingAssessment.*;
import com.lushprojects.circuitjs1.client.PoweredProviderOperatingContract.SignalState;
import com.lushprojects.circuitjs1.client.PoweredProviderOperatingContract.Status;

/** Handwritten pure expectations shared by native and compiled developer checks. */
final class A06PowerContractVectors {
    static int assertions;
    private static StringBuilder vectors;
    static String run() {
        assertions = 0; vectors = new StringBuilder("A06-POWER-VECTORS-1;");
        PowerDomainContract c = fixture(StorageRequirement.OBSERVATION_REQUIRED, true);
        Map<String,SourceState> s = states(SourceState.connected(), SourceState.isolated());
        PowerOperatingAssessment a = PowerOperatingAssessment.assess(c,s,volts(5,2.5),true);
        eq(a.getSupplyCondition(), SupplyCondition.PARTIAL, "partial");
        eq(a.getRailConditions().get("B"), RailCondition.BACKFED, "backfed");
        eq(a.getReadiness(), Readiness.POWER_OFF, "backfed-not-ready");
        s.put("SB", new SourceState(Connection.CONNECTED,DriveState.DISABLED));
        a = PowerOperatingAssessment.assess(c,s,volts(5,2.5),true);
        eq(a.getSupplyCondition(), SupplyCondition.CONNECTED, "disabled-is-connected");
        eq(a.getRailConditions().get("B"), RailCondition.BACKFED, "disabled-backfed");
        s = states(SourceState.isolated(),SourceState.isolated());
        a = PowerOperatingAssessment.assess(c,s,volts(0,2),true);
        eq(a.getSupplyCondition(),SupplyCondition.ALL_SOURCES_ISOLATED,"isolated-not-discharged");
        eq(a.getReadiness(),Readiness.DISCHARGE,"residual");
        eq(a.getRailConditions().get("B"),RailCondition.RESIDUAL,"stored-rail");
        eq(PowerOperatingAssessment.assess(c,s,volts(0,.25),true).getReadiness(),Readiness.READY,"threshold");
        eq(PowerOperatingAssessment.assess(c,s,volts(0,-.251),true).getReadiness(),Readiness.DISCHARGE,"negative-residual");
        eq(PowerOperatingAssessment.assess(c,s,volts(0,0),false).getReadiness(),Readiness.WAITING,"stale");
        for (double bad : new double[] {Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            eq(PowerOperatingAssessment.assess(c,s,volts(0,bad),true).getReadiness(),Readiness.UNKNOWN,"nonfinite");
        Map<String,Double> missing = volts(0,0);missing.remove("B");
        eq(PowerOperatingAssessment.assess(c,s,missing,true).getReadiness(),Readiness.UNKNOWN,"missing-rail");
        s.remove("SB");
        eq(PowerOperatingAssessment.assess(c,s,volts(0,0),true).getReadiness(),Readiness.UNKNOWN,"missing-source");
        eq(PowerOperatingAssessment.assess(fixture(StorageRequirement.UNKNOWN,true),
            states(SourceState.isolated(),SourceState.isolated()),volts(0,0),true).getReadiness(),Readiness.UNKNOWN,"storage-unknown");
        eq(PowerOperatingAssessment.assess(fixture(StorageRequirement.NONE,true),
            states(SourceState.isolated(),SourceState.isolated()),volts(0,2),true).getReadiness(),Readiness.UNKNOWN,"unexplained-energy");
        a = PowerOperatingAssessment.assess(fixture(StorageRequirement.NONE,false),
            states(SourceState.connected(),SourceState.isolated()),volts(5,2.5),true);
        check(a.getIssues().contains("BACKFEED_FORBIDDEN:AB"),"forbidden-path-not-hidden");
        PowerDomainContract contended = new PowerDomainContract("same-voltage-sources",c.getReferences().values(),c.getRails().values(),
            Arrays.asList(source("SA","A"),source("SB","A")),Collections.<BackfeedPath>emptyList());
        check(PowerOperatingAssessment.assess(contended,states(SourceState.connected(),SourceState.connected()),
            volts(5,0),true).getIssues().contains("SOURCE_CONTENTION:A"),"equal-voltage-contention");
        a = PowerOperatingAssessment.assess(c,states(SourceState.connected(),SourceState.isolated()),volts(0,0),true);
        eq(a.getRailConditions().get("A"),RailCondition.DRIVEN,"LOW-is-a-drive");
        eq(a.getReadiness(),Readiness.POWER_OFF,"LOW-is-not-isolation");
        ArrayList<Source> reversed = new ArrayList<Source>(c.getSources().values());Collections.reverse(reversed);
        eq(new PowerDomainContract(c.getDesignId(),c.getReferences().values(),c.getRails().values(),reversed,
            c.getBackfeedPaths().values()).toCanonical(),c.toCanonical(),"source-order");
        referenceVectors(c);
        providerVectors();
        uncertaintyVectors(c);
        for (ActiveMeasurementReadiness x : ActiveMeasurementReadiness.values())
            for (ActiveMeasurementReadiness y : ActiveMeasurementReadiness.values())
                eq(ActiveMeasurementReadiness.combine(x,y),ActiveMeasurementReadiness.combine(y,x),"policy-order");
        eq(ActiveMeasurementReadiness.combine(null,ActiveMeasurementReadiness.READY),ActiveMeasurementReadiness.UNKNOWN,"null-policy");
        eq(StoredEnergyMeasurementReadinessCapability.voltageReadiness(Double.NaN),ActiveMeasurementReadiness.UNKNOWN,"RC-NaN");
        eq(StoredEnergyMeasurementReadinessCapability.voltageReadiness(Double.POSITIVE_INFINITY),ActiveMeasurementReadiness.UNKNOWN,"RC-infinity");
        eq(StoredEnergyMeasurementReadinessCapability.voltageReadiness(.25),ActiveMeasurementReadiness.READY,"RC-threshold");
        return vectors.toString();
    }
    static PowerDomainContract fixture(StorageRequirement storage, boolean pathPermitted) {
        return new PowerDomainContract("two-source",
            Collections.singletonList(new Reference("GND","isolated-low-voltage",null,false)),
            Arrays.asList(new Rail("A","GND",StorageRequirement.NONE),new Rail("B","GND",storage)),
            Arrays.asList(source("SA","A"),source("SB","B")),
            Collections.singletonList(new BackfeedPath("AB","A","B",Scalar.known(.005),pathPermitted)));
    }
    static Source source(String id,String rail) {
        return new Source(id,rail,Range.known(0,5),Scalar.known(.05),Scalar.known(0),Scalar.notApplicable(),Drive.STIFF_VOLTAGE);
    }
    static Map<String,SourceState> states(SourceState a,SourceState b) {
        TreeMap<String,SourceState> result = new TreeMap<String,SourceState>();result.put("SA",a);result.put("SB",b);return result;
    }
    static Map<String,Double> volts(double a,double b) {
        TreeMap<String,Double> result = new TreeMap<String,Double>();result.put("A",a);result.put("B",b);return result;
    }
    private static void referenceVectors(PowerDomainContract c) {
        eq(MeasurementReferencePolicy.check(c,MeasurementReferencePolicy.Mode.DIFFERENTIAL,"A","B",null).getDecision(),
            MeasurementReferencePolicy.Decision.ADMITTED,"differential-no-earth");
        eq(MeasurementReferencePolicy.check(c,MeasurementReferencePolicy.Mode.EARTH_REFERENCED,"A","GND","PE").getDecision(),
            MeasurementReferencePolicy.Decision.UNPROVEN,"numeric-ground-not-earth");
        PowerDomainContract separate = new PowerDomainContract("references",Arrays.asList(
            new Reference("A/GND","IA","PE",true),new Reference("B/GND","IB",null,false),new Reference("C/GND","IA",null,false)),
            Arrays.asList(new Rail("A","A/GND",StorageRequirement.NONE),new Rail("B","B/GND",StorageRequirement.NONE),new Rail("C","C/GND",StorageRequirement.NONE)),
            Arrays.asList(source("SA","A"),source("SB","B")),Collections.<BackfeedPath>emptyList());
        eq(MeasurementReferencePolicy.check(separate,MeasurementReferencePolicy.Mode.DIFFERENTIAL,"A","B",null).getDecision(),
            MeasurementReferencePolicy.Decision.REJECTED,"isolation");
        eq(MeasurementReferencePolicy.check(separate,MeasurementReferencePolicy.Mode.DIFFERENTIAL,"A","C",null).getDecision(),
            MeasurementReferencePolicy.Decision.UNPROVEN,"labels-not-joins");
        eq(MeasurementReferencePolicy.check(separate,MeasurementReferencePolicy.Mode.EARTH_REFERENCED,"A","A","PE").getDecision(),
            MeasurementReferencePolicy.Decision.REJECTED,"scope-return-short");
        MeasurementReferencePolicy.Result legal = MeasurementReferencePolicy.check(separate,MeasurementReferencePolicy.Mode.EARTH_REFERENCED,"A","A/GND","PE");
        eq(legal.getDecision(),MeasurementReferencePolicy.Decision.CONNECTION_REQUIRED,"earth-needs-connection");
        check(!legal.admitsReading() && legal.requiresEarthConnection(),"no-fake-earth-readout");
    }
    private static void uncertaintyVectors(PowerDomainContract c) {
        PowerDomainContract unknownReference = new PowerDomainContract("unknown-reference",
            Collections.singletonList(new Reference("GND",null,null,false)),c.getRails().values(),
            c.getSources().values(),Collections.<BackfeedPath>emptyList());
        PowerOperatingAssessment a = PowerOperatingAssessment.assess(unknownReference,
            states(SourceState.isolated(),SourceState.isolated()),volts(0,0),true);
        eq(a.getReadiness(),Readiness.UNKNOWN,"unknown-reference-is-not-ready");
        eq(a.getRailConditions().get("A"),RailCondition.UNKNOWN,"unknown-reference-rail");
        check(a.getIssues().contains("REFERENCE_UNKNOWN:A"),"unknown-reference-diagnostic");
        a = PowerOperatingAssessment.assess(c,states(SourceState.connected(),SourceState.isolated()),
            volts(120,0),true);
        eq(a.getSupplyCondition(),SupplyCondition.PARTIAL,"bounds-do-not-rewrite-contact-state");
        eq(a.getRailConditions().get("A"),RailCondition.UNKNOWN,"outside-source-envelope");
        check(a.getIssues().contains("SOURCE_VOLTAGE_OUT_OF_ENVELOPE:SA"),"outside-envelope-diagnostic");
        PowerDomainContract unknownEnvelope = new PowerDomainContract("unknown-source-envelope",
            c.getReferences().values(),c.getRails().values(),Arrays.asList(
                new Source("SA","A",Range.unknown(),Scalar.known(.05),Scalar.known(0),
                    Scalar.notApplicable(),Drive.STIFF_VOLTAGE),source("SB","B")),
            Collections.<BackfeedPath>emptyList());
        a = PowerOperatingAssessment.assess(unknownEnvelope,states(SourceState.connected(),SourceState.isolated()),
            volts(5,0),true);
        eq(a.getRailConditions().get("A"),RailCondition.UNKNOWN,"unknown-source-envelope");
        check(a.getIssues().contains("SOURCE_ENVELOPE_UNKNOWN:SA"),"unknown-envelope-diagnostic");
        PoweredProviderOperatingContract unknown = new PoweredProviderOperatingContract(
            Range.known(4.5,5.5),Scalar.unknown(),Scalar.known(.1),Scalar.known(4.75),
            Scalar.known(.002),Scalar.known(.02),false,false);
        eq(unknown.evaluate(5,SignalState.NOT_REQUIRED,SignalState.NOT_REQUIRED,true),
            Status.UNKNOWN,"unknown-brownout-is-not-absent");
        PoweredProviderOperatingContract absent = new PoweredProviderOperatingContract(
            Range.known(4.5,5.5),Scalar.notApplicable(),Scalar.known(.1),Scalar.known(4.75),
            Scalar.known(.002),Scalar.known(.02),false,false);
        eq(absent.evaluate(5,SignalState.NOT_REQUIRED,SignalState.NOT_REQUIRED,true),
            Status.OPERABLE,"explicit-no-brownout-consumer");
    }
    private static void providerVectors() {
        PoweredProviderOperatingContract p = new PoweredProviderOperatingContract(Range.known(3.3,5.5),Scalar.known(3),
            Scalar.known(.8),Scalar.known(2),Scalar.known(.002),Scalar.known(.02),true,true);
        eq(p.evaluate(5,SignalState.DEASSERTED,SignalState.PRESENT,true),Status.OPERABLE,"provider-live");
        eq(p.evaluate(0,SignalState.DEASSERTED,SignalState.PRESENT,true),Status.UNPOWERED,"provider-off");
        eq(p.evaluate(2.8,SignalState.DEASSERTED,SignalState.PRESENT,true),Status.BROWNOUT,"brownout");
        eq(p.evaluate(6,SignalState.DEASSERTED,SignalState.PRESENT,true),Status.OUT_OF_ENVELOPE,"overvoltage");
        eq(p.evaluate(5,SignalState.ASSERTED,SignalState.PRESENT,true),Status.RESET_HELD,"reset");
        eq(p.evaluate(5,SignalState.DEASSERTED,SignalState.MISSING,true),Status.CLOCK_MISSING,"clock");
        eq(p.evaluate(5,SignalState.NOT_REQUIRED,SignalState.PRESENT,true),Status.UNKNOWN,"reset-unknown");
        eq(p.evaluate(5,SignalState.DEASSERTED,SignalState.PRESENT,false),Status.UNKNOWN,"partial-provider");
        eq(p.evaluate(Double.NaN,SignalState.DEASSERTED,SignalState.PRESENT,true),Status.UNKNOWN,"provider-NaN");
        check(!p.acceptsDriveCapacity(Scalar.known(.001)),"drive-shortfall");
        check(!p.acceptsDriveCapacity(Scalar.unknown()),"drive-unknown");
        check(p.acceptsDriveCapacity(Scalar.known(.002)),"drive-boundary");
        check(!p.acceptsSupplyRange(Range.known(3,5)),"supply-envelope");
    }
    static void check(boolean passed,String label) {
        assertions++;if (!passed) throw new AssertionError("A06 " + label);
        vectors.append(label).append("=PASS;");
    }
    private static void eq(Object actual,Object expected,String label) {
        check(expected.equals(actual),label + ":" + actual);
    }
}
