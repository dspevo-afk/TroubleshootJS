package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Formatter/credit protocol only. Output arrays are preallocated test fixtures. */
public final class CanonicalJsonCanary {
    private static int checks,cases,rejections,numericFixtures;
    private static long copiedBytes;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static class Sink implements CanonicalJsonHolder.CanonicalJsonSink {
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final byte[] output;
        final long expected;
        long position,blocks,operations;
        boolean terminated;
        Exception failure;
        long failOperation;
        Sink(byte[] fixture,long expected)throws Exception {
            output=fixture;this.expected=expected;
            CohortTerminalTelemetryStudy.Parent p=new CohortTerminalTelemetryStudy.Parent();
            meter=new CohortTerminalTelemetryStudy.SetupMeter(p,p.meter(System.nanoTime()));
            meter.method("beginGroup",new Class<?>[0]);
        }
        long n(String name)throws Exception{return ((Number)CohortTerminalTelemetryStudy.field(meter.meter,name)).longValue();}
        void credit()throws Exception {
            check(!terminated,"callback after final LF");
            if(position>=1048576L)throw new IOException("JSON byte bound");
            if(position/4096L==blocks) {
                if(output==null)meter.method("charge",new Class<?>[]{long.class},Long.valueOf(1));
                else {
                    long remaining=expected-position;check(remaining>0,"emitter exceeds count");
                    CohortTerminalTelemetryStudy.exportCopyBlock(meter,(int)Math.min(4096L,remaining));
                }
                blocks++;
            }
        }
        public void beforeDataOperation()throws Exception {
            operations++;
            if(failure!=null && operations==failOperation)throw failure;
            credit();
        }
        public void writeByte(int ascii)throws Exception {
            credit();check(ascii>=0 && ascii<=127,"non-ASCII output byte");
            if(output!=null){check(position<output.length,"fixture array bound");output[(int)position]=(byte)ascii;}
            position++;if(ascii==10)terminated=true;
        }
        void finish()throws Exception {
            check(terminated,"missing terminal LF");
            check(blocks==(position+4095L)/4096L && n("units")==blocks,"actual native credit count");
            check(n("groups")==0,"formatter rolled group");
            if(output==null)check(n("copiedEntries")==0 && n("copyChunks")==0,"counter copied data");
            else check(n("copiedEntries")==position && n("copyChunks")==blocks,"byte entries/chunks mismatch");
            meter.finish();check(n("groups")==1,"actual measured group count");
        }
    }
    private static String repeat(int count){char[] chars=new char[count];Arrays.fill(chars,'a');return new String(chars);}
    private static Object nested(int depth){Object v=null;for(int i=0;i<depth;i++){List<Object> a=new ArrayList<Object>();a.add(v);v=a;}return v;}
    private static List<Object> sized(int bytes){
        List<Object> list=new ArrayList<Object>();int used=3;
        while(bytes-used>259){list.add(repeat(256));used+=list.size()==1?258:259;}
        int length=bytes-used-(list.isEmpty()?2:3);
        check(length>=0 && length<=256,"sized fixture remainder");list.add(repeat(length));return list;
    }
    private static String hash(byte[] bytes)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format("%02x",b&255));return out.toString();
    }
    private static void positive(String id,Object value)throws Exception {
        Sink count=new Sink(null,0);CanonicalJsonHolder.writeCanonicalJson(value,count);count.finish();
        byte[] fixture=new byte[(int)count.position]; // preallocated test storage, not production allocation proof
        Sink emit=new Sink(fixture,count.position);CanonicalJsonHolder.writeCanonicalJson(value,emit);emit.finish();
        check(emit.position==count.position,"count/emitter byte disagreement");copiedBytes+=emit.position;cases++;
        String header="{\"case\":\""+id+"\",\"bytes\":"+emit.position+",\"blocks\":"+emit.blocks+
            ",\"sha256\":\""+hash(fixture)+"\",\"raw\":"+(fixture.length<=16384)+"}\n";
        System.out.write(header.getBytes("US-ASCII"));if(fixture.length<=16384)System.out.write(fixture);
    }
    private static void reject(Object value,String message)throws Exception {
        Sink sink=new Sink(null,0);
        try{CanonicalJsonHolder.writeCanonicalJson(value,sink);throw new AssertionError("accepted "+message);}
        catch(IllegalArgumentException e){check(message.equals(e.getMessage()),"wrong formatter rejection "+e);}
        check(!sink.terminated && sink.n("copiedEntries")==0,"failed count copied/published data");rejections++;
    }
    public static void main(String[] args)throws Exception {
        check(args.length==0,"args");
        positive("null",null);positive("true",Boolean.TRUE);positive("false",Boolean.FALSE);
        long[] integers={0,-1,9,10,99,100,Long.MIN_VALUE,Long.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE};
        for(int i=0;i<integers.length;i++) {
            if(i>=8)positive("integer"+i,Integer.valueOf((int)integers[i]));
            else positive("integer"+i,Long.valueOf(integers[i]));
        }
        positive("empty","");positive("maxString",repeat(256));
        char[] ascii=new char[95];for(int i=0;i<95;i++)ascii[i]=(char)(32+i);positive("allAscii",new String(ascii));
        positive("literalEscape",new String(new char[]{92,117,48,48,52,49}));
        Map<String,Object> map=new LinkedHashMap<String,Object>();
        map.put("","");map.put("quoted\"key",Arrays.<Object>asList(Boolean.TRUE,null,Long.valueOf(-123),"plus+slash\\"));
        positive("map",map);positive("depth64",nested(64));
        for(int size:new int[]{4095,4096,4097,8191,8192,8193,1048576})positive("size"+size,sized(size));
        reject(repeat(257),"JSON string length");
        for(char c:new char[]{0,10,31,127,128,0x20ac,0xd800})reject(new String(new char[]{c}),"JSON string ASCII");
        reject(Double.valueOf(1.5),"JSON value");reject(Float.valueOf(Float.NaN),"JSON value");
        reject(new Object(),"JSON value");reject(nested(65),"JSON depth");
        List<Object> cycle=new ArrayList<Object>();cycle.add(cycle);reject(cycle,"JSON depth");
        Map<Object,Object> badKey=new LinkedHashMap<Object,Object>();badKey.put(Integer.valueOf(1),null);reject(badKey,"JSON key");
        Sink tooLarge=new Sink(null,0);
        try{CanonicalJsonHolder.writeCanonicalJson(sized(1048577),tooLarge);throw new AssertionError("byte limit accepted");}
        catch(IOException e){check("JSON byte bound".equals(e.getMessage()),"wrong byte bound");rejections++;}
        Sink visitsSink=new Sink(null,0);CanonicalJsonHolder.CanonicalJsonVisits visits=new CanonicalJsonHolder.CanonicalJsonVisits();
        visits.count=1048576L;numericFixtures++;
        try{CanonicalJsonHolder.writeCanonicalValue(null,visitsSink,visits,0);throw new AssertionError("visits accepted");}
        catch(IllegalArgumentException e){check("JSON visits".equals(e.getMessage()),"visit bound");rejections++;}
        IOException sentinel=new IOException("SINK_SENTINEL");Sink fault=new Sink(null,0);
        fault.failure=sentinel;fault.failOperation=5;
        try{CanonicalJsonHolder.writeCanonicalJson(map,fault);throw new AssertionError("sink failure swallowed");}
        catch(Exception e){check(e==sentinel && fault.operations==5,"sink failure identity/callback continuation");rejections++;}
        String summary="{\"schema\":\"tsj-canonical-json-canary-v1\",\"status\":\"PASS_FORMATTER_AND_NATIVE_CREDITS_ONLY\",\"cases\":"+
            cases+",\"rejections\":"+rejections+",\"numericVisitFixtures\":"+numericFixtures+
            ",\"copiedBytesAcrossFrames\":"+copiedBytes+",\"checks\":"+checks+
            ",\"fixtureOutputAllocation\":\"PREALLOCATED_NOT_PRODUCTION_PROOF\",\"authoredCaptures\":0,\"authoredSessions\":0}\n";
        System.out.write(summary.getBytes("US-ASCII"));System.out.flush();check(!System.out.checkError(),"stdout");
    }
}
