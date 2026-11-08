package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.*;

/** Separate native negative gate; never participates in producer stdout. */
public final class SelectedObservationMaterializerGuard {
    private static final String REQUEST="materializer-guard";
    private final SetupMeter meter;
    private int assertions;
    private SelectedObservationMaterializerGuard(SetupMeter m) { meter=m; }

    public static void main(String[] args) throws Exception {
        long started=System.nanoTime();
        if (args==null || args.length!=0) throw new IOException("MATERIALIZER_GUARD_ARGS");
        Parent parent=null;
        Object nativeMeter=null;
        SetupMeter meter=null;
        SelectedObservationMaterializerGuard guard=null;
        TerminalSink unsealed=null,poisoned=null;
        int passedAssertions=0;
        try {
            parent=new Parent();
            nativeMeter=parent.meter(started);
            meter=new SetupMeter(parent,nativeMeter);
            meter.charge(1); meter.copy(2);
            guard=new SelectedObservationMaterializerGuard(meter);
            guard.noSessions();
            unsealed=guard.sink();
            guard.rejectMaterialization(unsealed,"Seal observer before result access");
            // A separate live observer receives the supported malformed callback;
            // this guard never writes its sealed/poisoned flags or other state.
            poisoned=guard.sink();
            guard.rejectActiveNullCallback(poisoned);
            guard.rejectMaterialization(poisoned,"Observer request had an active rejection");
            guard.noSessions();
            meter.charge(1);
            passedAssertions=guard.assertions;
            // Prepay the bounded separate-test status construction/write before close.
            meter.charge(2);
        } finally {
            unsealed=null; poisoned=null; guard=null;
            if (meter!=null) meter.finish();
            meter=null; nativeMeter=null; parent=null;
        }
        System.out.println("PASS SelectedObservationMaterializerGuard assertions="+
            passedAssertions+" cases=2 sessions=0");
    }

    private TerminalSink sink() throws Exception {
        Map<String,PadIdentity> pads=null;
        Map<String,Integer> ordinals=null,counts=null;
        ObserverMeter port=null;
        ObservationSpec spec=null;
        TerminalTelemetryObserver observer=null;
        try {
            meter.charge(1); meter.copy(2);
            pads=new LinkedHashMap<String,PadIdentity>();
            meter.charge(1); meter.copy(3);
            PadIdentity start=new PadIdentity("N",1,2);
            meter.charge(1); pads.put("S",start); start=null;
            meter.charge(1); meter.copy(3);
            PadIdentity root=new PadIdentity("N",8,9);
            meter.charge(1); pads.put("R",root); root=null;
            meter.charge(1); meter.copy(1);
            ordinals=new LinkedHashMap<String,Integer>();
            meter.charge(1); ordinals.put("N",Integer.valueOf(1));
            meter.charge(1); meter.copy(1);
            counts=new LinkedHashMap<String,Integer>();
            meter.charge(1); counts.put("N",Integer.valueOf(2));
            meter.charge(1); meter.copy(1);
            port=new ObserverMeter(meter);
            spec=ObservationSpec.create(port,REQUEST,2,2,1,pads,ordinals,counts);
            meter.charge(1); meter.copy(2);
            observer=new TerminalTelemetryObserver(port,spec);
            meter.charge(1); meter.copy(3);
            return new TerminalSink(port,observer,REQUEST);
        } finally { pads=null; ordinals=null; counts=null; port=null; spec=null; observer=null; }
    }

    private void rejectMaterialization(TerminalSink sink,String message) throws Exception {
        IllegalStateException caught=null;
        Map<String,Object> unexpected=null;
        try {
            meter.charge(1);
            try { unexpected=CohortTerminalTelemetryStudy.materializeObservation(sink,meter); }
            catch (IllegalStateException failure) { caught=failure; }
            require(caught!=null,"common materializer must reject");
            meter.charge(1);
            require(message.equals(caught.getMessage()),"specific materializer rejection message");
            require(unexpected==null,"rejection must not return a map");
            meter.charge(2);
            require(!sink.meter.insideOwner && sink.meter.entriesRemaining==0,
                "materializer rejection preserves real adapter state");
        } finally { caught=null; unexpected=null; sink=null; }
    }

    private void rejectActiveNullCallback(TerminalSink sink) throws Exception {
        IllegalArgumentException caught=null;
        try {
            meter.charge(1);
            try { sink.onTerminalBranch(null); }
            catch (IllegalArgumentException failure) { caught=failure; }
            require(caught!=null,"supported active null callback must reject");
            meter.charge(1);
            require("Null terminal snapshot".equals(caught.getMessage()),
                "specific active callback rejection message");
            meter.charge(2);
            require(!sink.meter.insideOwner && sink.meter.entriesRemaining==0,
                "active rejection restores real adapter state");
            // resultMap tests sealed before poisoned: this exact rejection therefore
            // proves both states were established by the active callback owner.
        } finally { caught=null; sink=null; }
    }

    private void noSessions() throws Exception {
        meter.charge(1);
        Object count=CohortTerminalTelemetryStudy.field(meter.meter,"sessionConstructorEntries");
        require(count instanceof Integer && ((Integer)count).intValue()==0,
            "real Meter constructor count remains zero");
        count=null;
    }
    private void require(boolean ok,String message) throws Exception {
        meter.charge(1); assertions++;
        if (!ok) throw new IOException("MATERIALIZER_GUARD_ASSERT: "+message);
    }
}
