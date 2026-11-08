package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual Meter, synthetic values only; not an owner-canary qualification. */
public final class R9TextPolicyLimitsCanary {
    private static int assertions,failed;
    private static String quoted(String value) {
        if (value==null) return "null";
        StringBuilder b=new StringBuilder("\"");
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (c=='"' || c=='\\') b.append('\\').append(c);
            else if (c<32 || c>126) b.append('?'); else b.append(c);
        }
        return b.append('"').toString();
    }
    private static long number(Object o,String name) throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(o,name)).longValue();
    }
    /** Root is depth zero; 63/64 nested lists place terminal at depth 64/65. */
    private static Map<String,Object> depthBody(int wrappers,boolean terminalList) {
        Object child=terminalList?new ArrayList<Object>():new LinkedHashMap<String,Object>();
        for (int i=0;i<wrappers;i++) { List<Object> next=new ArrayList<Object>(); next.add(child); child=next; }
        Map<String,Object> root=new LinkedHashMap<String,Object>(); root.put("ordinary",child); return root;
    }
    /** Root and its list are two VALUE visits; each repeated null contributes one. */
    private static Map<String,Object> visitsBody(int nullCount) {
        Map<String,Object> root=new LinkedHashMap<String,Object>();
        root.put("ordinary",Collections.nCopies(nullCount,(Object)null)); return root;
    }
    private static void run(String name,Map<String,Object> body,String expected) throws Exception {
        long started=System.nanoTime();
        CohortTerminalTelemetryStudy.Parent parent=new CohortTerminalTelemetryStudy.Parent();
        CohortTerminalTelemetryStudy.SetupMeter meter=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
        Throwable thrown=null,cleanup=null;
        try { CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(body,meter); }
        catch (Throwable t) { thrown=t; }
        try { meter.finish(); } catch (Throwable t) { cleanup=t; }
        long elapsed=System.nanoTime()-started;
        long units=number(meter.meter,"units"),groups=number(meter.meter,"groups");
        long copies=number(meter.meter,"copiedEntries"),chunks=number(meter.meter,"copyChunks");
        boolean open=Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter,"open"));
        boolean expectedResult=expected==null?thrown==null:
            thrown instanceof IOException && expected.equals(thrown.getMessage());
        boolean ok=expectedResult && cleanup==null && !open && copies==0 && chunks==0;
        assertions+=4; if (!ok) failed++;
        String line="{\"scope\":\"SYNTHETIC_TEXT_POLICY_LIMITS_REAL_METER_ONLY\",\"case\":"+quoted(name)+
            ",\"status\":"+quoted(ok?"PASS":"FAIL")+",\"expected\":"+quoted(expected)+
            ",\"actualType\":"+quoted(thrown==null?null:thrown.getClass().getName())+
            ",\"actualMessage\":"+quoted(thrown==null?null:thrown.getMessage())+
            ",\"cleanupType\":"+quoted(cleanup==null?null:cleanup.getClass().getName())+
            ",\"cleanupMessage\":"+quoted(cleanup==null?null:cleanup.getMessage())+
            ",\"elapsedNanos\":"+elapsed+",\"units\":"+units+",\"groups\":"+groups+
            ",\"copies\":"+copies+",\"chunks\":"+chunks+",\"open\":"+open+"}\n";
        System.out.write(line.getBytes("US-ASCII")); System.out.flush();
        if (System.out.checkError()) throw new IOException("STDOUT");
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=0) throw new IOException("ARGS");
        run("depth64_empty_map",depthBody(63,false),null);
        run("depth64_empty_list",depthBody(63,true),null);
        run("depth65_empty_map",depthBody(64,false),"R9_TEXT_POLICY_DEPTH");
        run("depth65_empty_list",depthBody(64,true),"R9_TEXT_POLICY_DEPTH");
        run("visits1048576",visitsBody(1048574),null);
        run("visits1048577",visitsBody(1048575),"R9_TEXT_POLICY_VISITS");
        String summary="{\"scope\":\"SYNTHETIC_TEXT_POLICY_LIMITS_REAL_METER_ONLY\",\"assertions\":"+
            assertions+",\"failedCases\":"+failed+",\"ownerSessions\":0,\"captures\":0}\n";
        System.out.write(summary.getBytes("US-ASCII")); System.out.flush();
        if (System.out.checkError()) throw new IOException("STDOUT");
        if (failed!=0) throw new IOException("LIMIT_CANARY_FAILURE");
    }
}
