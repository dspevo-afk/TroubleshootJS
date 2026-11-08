package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SYNTHETIC_POLICY_FIXTURE_ONLY; never an owner canary or Session. */
public final class R9TextPolicyCanary {
    private static int checks,accepted,rejected;
    private static void check(boolean ok,String why) { checks++; if (!ok) throw new AssertionError(why); }
    private interface Action { void run() throws Exception; }
    private static CohortTerminalTelemetryStudy.SetupMeter meter() throws Exception {
        CohortTerminalTelemetryStudy.Parent p=new CohortTerminalTelemetryStudy.Parent();
        return new CohortTerminalTelemetryStudy.SetupMeter(p,p.meter(System.nanoTime()));
    }
    private static void reject(String reason,Action action) throws Exception {
        try { action.run(); }
        catch (IllegalArgumentException e) { check(reason.equals(e.getMessage()),"formatter rejection "+e); rejected++; return; }
        catch (IOException e) { check(reason.equals(e.getMessage()),"policy rejection "+e); rejected++; return; }
        throw new AssertionError("accepted "+reason);
    }
    private static String repeat(int n,char c) { char[] a=new char[n]; Arrays.fill(a,c); return new String(a); }
    private static Map<String,Object> map() { return new LinkedHashMap<String,Object>(); }
    private static Map<String,Object> body(Object geometry) {
        Map<String,Object> b=map(),arm=map(),fresh=map(),layout=map(),component=map();
        List<Object> components=new ArrayList<Object>(); component.put("geometryCanonical",geometry); components.add(component);
        layout.put("components",components); fresh.put("layout",layout); arm.put("freshRun",fresh);
        b.put("schema","tsj-cohort-terminal-canary-v1"); b.put("status","OWNER_CANARIES_RECORDED_ONLY");
        b.put("runId","00000000-0000-0000-0000-000000000001");
        b.put("sourceManifestSha256",repeat(64,'a')); b.put("wireContractSha256",repeat(64,'b'));
        b.put("complete",arm); b.put("blocked",map()); b.put("nonBranchQuality",null);
        b.put("genericCallbackThrow",map()); b.put("directOwnerAbortReuse",map()); b.put("sourceReview",map());
        return b;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> arm(Map<String,Object> b) { return (Map<String,Object>)b.get("complete"); }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> fresh(Map<String,Object> b) { return (Map<String,Object>)arm(b).get("freshRun"); }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> layout(Map<String,Object> b) { return (Map<String,Object>)fresh(b).get("layout"); }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> component(Map<String,Object> b) {
        return (Map<String,Object>)((List<?>)layout(b).get("components")).get(0);
    }
    /** Test-owned byte sink; native Meter pays block credits, fixture bytes are not production copy proof. */
    private static final class Sink implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final ByteArrayOutputStream out=new ByteArrayOutputStream();
        long position,blocks; boolean ended;
        Sink() throws Exception { meter=meter(); meter.method("beginGroup",new Class<?>[0]); }
        void credit() throws Exception {
            if (ended) throw new IOException("callback after LF");
            if (position/4096L==blocks) { meter.method("charge",new Class<?>[]{long.class},Long.valueOf(1)); blocks++; }
        }
        public void beforeDataOperation() throws Exception { credit(); }
        public void writeByte(int ascii) throws Exception {
            credit(); check(ascii>=0 && ascii<=127,"ASCII byte"); out.write(ascii); position++; if (ascii==10) ended=true;
        }
        byte[] finish() throws Exception {
            check(ended && blocks==(position+4095L)/4096L,"credit boundary/final LF");
            check(((Number)CohortTerminalTelemetryStudy.field(meter.meter,"units")).longValue()==blocks,"actual native block units");
            meter.finish(); return out.toByteArray();
        }
    }
    private static byte[] encode(Object value,boolean canary) throws Exception {
        Sink s=new Sink();
        if (canary) CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(value,s);
        else CohortTerminalTelemetryStudy.writeCanonicalJson(value,s);
        accepted++; return s.finish();
    }
    private static void policy(Object body) throws Exception {
        CohortTerminalTelemetryStudy.SetupMeter m=meter();
        long before=((Number)CohortTerminalTelemetryStudy.field(m.meter,"units")).longValue();
        CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(body,m); m.finish();
        check(((Number)CohortTerminalTelemetryStudy.field(m.meter,"units")).longValue()>before,"actual metadata charges");
        check(((Number)CohortTerminalTelemetryStudy.field(m.meter,"copiedEntries")).longValue()==0,"no metadata data copies");
    }
    private static void policyReject(final Object value,final String reason) throws Exception {
        reject(reason,new Action(){public void run() throws Exception{CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(value,meter());}});
    }
    private static void formatterReject(final Object value,final boolean canary,final String reason) throws Exception {
        reject(reason,new Action(){public void run() throws Exception{
            Sink s=new Sink();
            if (canary) CohortTerminalTelemetryStudy.writeCanonicalJsonCanaryValues(value,s);
            else CohortTerminalTelemetryStudy.writeCanonicalJson(value,s);
        }});
    }
    private static void policyAndFormatterReject(Object value,String policyReason,String formatReason) throws Exception {
        policyReject(value,policyReason); formatterReject(value,true,formatReason);
    }
    private static void positivePublication() throws Exception {
        Map<String,Object> b=body("short"); byte[] expected=encode(b,true);
        long started=System.nanoTime(); CohortTerminalTelemetryStudy.Parent p=new CohortTerminalTelemetryStudy.Parent();
        CohortTerminalTelemetryStudy.SetupMeter m=new CohortTerminalTelemetryStudy.SetupMeter(p,p.meter(started));
        CohortTerminalTelemetryStudy.R9CanaryPublication pre=CohortTerminalTelemetryStudy.prepareR9CanaryPublication(b,m,started);
        long before=((Number)CohortTerminalTelemetryStudy.field(m.meter,"units")).longValue();
        final ByteArrayOutputStream captured=new ByteArrayOutputStream();
        PrintStream saved=System.out,fixture=new PrintStream(captured,true,"UTF-8");
        System.setOut(fixture);
        try {
            CohortTerminalTelemetryStudy.R9CanaryPublicationMetrics metrics=
                CohortTerminalTelemetryStudy.publishR9CanaryStdout(b,m,started,pre);
            long blocks=(expected.length+4095L)/4096L;
            check(Arrays.equals(expected,captured.toByteArray()),"prepared publication bytes");
            check(metrics.unitsBeforeExport==before && metrics.exportUnits==3*blocks &&
                ((Number)CohortTerminalTelemetryStudy.field(m.meter,"units")).longValue()==before+3*blocks,
                "same meter metadata and export accounting");
        } finally { System.setOut(saved); fixture.close(); }
    }
    private static void changedAfterPrepare() throws Exception {
        final Map<String,Object> b=body("before");
        long started=System.nanoTime(); CohortTerminalTelemetryStudy.Parent p=new CohortTerminalTelemetryStudy.Parent();
        final CohortTerminalTelemetryStudy.SetupMeter m=new CohortTerminalTelemetryStudy.SetupMeter(p,p.meter(started));
        final CohortTerminalTelemetryStudy.R9CanaryPublication pre=CohortTerminalTelemetryStudy.prepareR9CanaryPublication(b,m,started);
        arm(b).put("unauthorized",repeat(257,'x')); // changed after metadata validation
        final ByteArrayOutputStream captured=new ByteArrayOutputStream();
        PrintStream saved=System.out,fixture=new PrintStream(captured,true,"UTF-8");
        System.setOut(fixture);
        try {
            boolean failed=false;
            try { CohortTerminalTelemetryStudy.publishR9CanaryStdout(b,m,started,pre); }
            catch (IllegalArgumentException e) { failed="JSON string length".equals(e.getMessage()); }
            catch (IOException e) { failed="R9_TEXT_POLICY_LENGTH".equals(e.getMessage()); }
            check(failed && captured.size()==0,"changed body must reject before stdout"); rejected++;
        } finally { System.setOut(saved); fixture.close(); }
    }
    public static void main(String[] args) throws Exception {
        check(args.length==0,"no arguments");
        encode(repeat(256,'a'),false); formatterReject(repeat(257,'a'),false,"JSON string length");
        Map<String,Object> key256=map(); key256.put(repeat(256,'k'),"v"); encode(key256,false);
        Map<String,Object> key257=map(); key257.put(repeat(257,'k'),"v"); formatterReject(key257,false,"JSON string length");
        Map<String,Object> allowedKey=body("short"); component(allowedKey).put(repeat(256,'k'),repeat(256,'v'));
        policy(allowedKey); encode(allowedKey,true);
        Map<String,Object> max=body(repeat(4096,'g')); policy(max); encode(max,true);
        policyAndFormatterReject(body(repeat(4097,'g')),"R9_TEXT_POLICY_LENGTH","JSON string length");
        Map<String,Object> wrong=body("short"); arm(wrong).put("other",repeat(257,'x'));
        policyAndFormatterReject(wrong,"R9_TEXT_POLICY_LENGTH","JSON string length");
        Map<String,Object> wrongRoot=body("short"); wrongRoot.put("geometryCanonical",repeat(257,'x'));
        policyAndFormatterReject(wrongRoot,"R9_TEXT_POLICY_LENGTH","JSON string length");
        Map<String,Object> keyLong=body("short"); component(keyLong).put(repeat(257,'k'),"v");
        policyAndFormatterReject(keyLong,"R9_TEXT_POLICY_LENGTH","JSON string length");
        Map<String,Object> badFresh=body("short"); arm(badFresh).put("freshRun",new ArrayList<Object>());
        policyAndFormatterReject(badFresh,"R9_TEXT_POLICY_STRUCTURE","JSON canary path type");
        Map<String,Object> badLayout=body("short"); fresh(badLayout).put("layout",new ArrayList<Object>());
        policyAndFormatterReject(badLayout,"R9_TEXT_POLICY_STRUCTURE","JSON canary path type");
        Map<String,Object> wrongList=body("short"); layout(wrongList).put("components",map());
        policyAndFormatterReject(wrongList,"R9_TEXT_POLICY_STRUCTURE","JSON canary path type");
        Map<String,Object> extraList=body("short"); layout(extraList).put("components",Arrays.<Object>asList(Arrays.<Object>asList(map())));
        policyAndFormatterReject(extraList,"R9_TEXT_POLICY_STRUCTURE","JSON canary path type");
        Map<String,Object> badGeometry=body("short"); component(badGeometry).put("geometryCanonical",map());
        policyAndFormatterReject(badGeometry,"R9_TEXT_POLICY_STRUCTURE","JSON canary path type");
        Map<String,Object> nullArm=body("short"); nullArm.put("complete",null);
        policyAndFormatterReject(nullArm,"R9_TEXT_POLICY_STRUCTURE","JSON canary path type");
        Map<String,Object> optional=body("short"); fresh(optional).put("layout",null); policy(optional); encode(optional,true);
        Map<String,Object> unicode=body("bad\u00e9"); policyAndFormatterReject(unicode,"R9_TEXT_POLICY_ASCII","JSON string ASCII");
        Map<String,Object> unicodeKey=body("short"); component(unicodeKey).put("bad\u00e9","x");
        policyAndFormatterReject(unicodeKey,"R9_TEXT_POLICY_ASCII","JSON string ASCII");
        Map<String,Object> unsupported=body("short"); arm(unsupported).put("number",Double.valueOf(1.5));
        policyAndFormatterReject(unsupported,"R9_TEXT_POLICY_TYPE","JSON value");
        Map<String,Object> vector=body(repeat(4096,'"')); policy(vector); byte[] bytes=encode(vector,true);
        check(bytes.length>8192,"escaped vector crosses two chunks");
        positivePublication(); changedAfterPrepare();
        System.out.write(bytes);
        String summary="{\"scope\":\"SYNTHETIC_POLICY_FIXTURE_ONLY\",\"checks\":"+checks+
            ",\"accepted\":"+accepted+",\"rejected\":"+rejected+",\"vectorBytes\":"+bytes.length+"}\n";
        System.out.write(summary.getBytes("US-ASCII")); System.out.flush(); check(!System.out.checkError(),"stdout");
    }
}
