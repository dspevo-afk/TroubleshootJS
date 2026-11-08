package com.lushprojects.circuitjs1.client;

import java.lang.reflect.*;
import java.util.*;

/** Early actual adapter probe; synthetic scalar callback only, no Session or authored entry. */
public final class ObserverMeterHostProbe {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Context good=new Context();
        long copied=good.port.copiedEntries(),chunks=good.port.copyChunks(),units=good.port.units();
        good.sink.onTerminalBranch(snapshot());
        check(good.port.copiedEntries()-copied==33,"actual adapter copies exactly33 scalar slots");
        check(good.port.copyChunks()-chunks==1,"actual adapter admits one33-entry chunk");
        long du=good.port.units()-units;
        check(du>100 && du<512,"actual adapter callback stays within unchanged ceiling");
        check(number(good.observer,"unitsCharged")==du && number(good.observer,"unitsMeasured")==du,
            "actual shared Meter and observer measurement reconcile");
        check(!good.port.insideOwner && good.port.entriesRemaining==0,"actual adapter exits without callback/chunk state");
        check(get(good.observer,"selected")!=null,"positive synthetic NO_PATH snapshot is detached");
        good.setup.finish();
        check(!good.port.isOpen(),"actual group closes");

        Context cap=new Context();
        setLong(cap.setup.meter,"copiedEntries",1048576L-32L);
        long beforeCopies=cap.port.copiedEntries(),beforeChunks=cap.port.copyChunks();
        Throwable caught=null;
        try { cap.sink.onTerminalBranch(snapshot()); } catch(Throwable t) { caught=t; }
        check(caught instanceof IllegalStateException && caught.getMessage().contains("Aggregate copy capacity"),
            "actual selected adapter rejects insufficient33-slot capacity");
        check(cap.port.copiedEntries()==beforeCopies && cap.port.copyChunks()==beforeChunks,
            "capacity rejection happens before any chunk/copy");
        check(Boolean.TRUE.equals(get(cap.observer,"poisoned")) && Boolean.TRUE.equals(get(cap.observer,"sealed")),
            "actual observer poisons and seals rejected request");
        check(!cap.port.insideOwner && cap.port.entriesRemaining==0 && get(cap.observer,"selected")==null,
            "negative adapter restores callback flag without selected tuple");
        cap.setup.finish();
        check(!cap.port.isOpen(),"negative actual group closes");
        System.out.println("PASS ObserverMeterHostProbe assertions="+assertions+
            " positive=1 capacityRejection=1 sessions=0 authored=0 scope=UNCHANGED_ACTUAL_ADAPTER_ONLY");
    }
    private static final class Context {
        final CohortTerminalTelemetryStudy.SetupMeter setup;
        final CohortTerminalTelemetryStudy.ObserverMeter port;
        final CohortTerminalTelemetryStudy.TerminalTelemetryObserver observer;
        final CohortTerminalTelemetryStudy.TerminalSink sink;
        Context() throws Exception {
            CohortTerminalTelemetryStudy.Parent parent=new CohortTerminalTelemetryStudy.Parent();
            setup=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(System.nanoTime()));
            setup.method("reserve",new Class<?>[]{Long.TYPE},Long.valueOf(1024));
            port=new CohortTerminalTelemetryStudy.ObserverMeter(setup);
            Map<String,CohortTerminalTelemetryStudy.PadIdentity> pads=
                new LinkedHashMap<String,CohortTerminalTelemetryStudy.PadIdentity>();
            pads.put("S",new CohortTerminalTelemetryStudy.PadIdentity("N",0,0));
            pads.put("R",new CohortTerminalTelemetryStudy.PadIdentity("N",100,0));
            Map<String,Integer> ordinals=new LinkedHashMap<String,Integer>();ordinals.put("N",1);
            Map<String,Integer> counts=new LinkedHashMap<String,Integer>();counts.put("N",2);
            CohortTerminalTelemetryStudy.ObservationSpec spec=CohortTerminalTelemetryStudy.ObservationSpec.create(
                port,"Q_ADAPTER",2,2,1,pads,ordinals,counts);
            setup.finish();setup.method("reserve",new Class<?>[]{Long.TYPE},Long.valueOf(616));
            observer=new CohortTerminalTelemetryStudy.TerminalTelemetryObserver(port,spec);
            sink=new CohortTerminalTelemetryStudy.TerminalSink(port,observer,"Q_ADAPTER");
        }
    }
    private static PcbLayerRoutingPrototype.BranchSnapshot snapshot() throws Exception {
        Constructor<?> c=PcbLayerRoutingPrototype.BranchSnapshot.class.getDeclaredConstructors()[0];
        c.setAccessible(true);
        Object[] values={2,"N","S","R",0,0,100,0,11,11,1,0,0,1,10,-1,
            0,0,0,0,0,0,"NO_PATH",false,false,false,false,false,false,0,0,0,0};
        check(values.length==33,"literal snapshot field count");
        return (PcbLayerRoutingPrototype.BranchSnapshot)c.newInstance(values);
    }
    private static Object get(Object value,String name) throws Exception {
        Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    private static long number(Object value,String name) throws Exception {return ((Number)get(value,name)).longValue();}
    private static void setLong(Object value,String name,long n) throws Exception {
        Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);f.setLong(value,n);
    }
    private static void check(boolean ok,String message) {assertions++;if(!ok)throw new AssertionError(message);}
}
