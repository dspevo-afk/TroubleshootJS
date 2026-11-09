package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Component canary only; no producer, arm, session, routing or original invocation. */
public final class OriginalReceiptDigestCanary {
    static final String[] SMOKE={"file_empty","file_1","file_4096","file_4097",
        "file_8192","file_8mib","file_overcap_9mib","file_expired_before_body",
        "file_capacity_before_body","file_empty_closed_meter","file_unmetered_4097"};
    static final String[] SUITE={"short_one_byte_4097","short_2048_4096",
        "short_2048_4097","chunk_4096_8192","chunk_8192_8192","empty_eof_probe",
        "zero_progress","early_eof","extra_byte","overreturn","read_error",
        "error_after_prefix","expiry_during_read","expiry_during_eof",
        "bounded_final_read","eof_read_error"};
    private OriginalReceiptDigestCanary() { }

    static final class NativeFixture {
        final CohortTerminalTelemetryStudy.Parent p=new CohortTerminalTelemetryStudy.Parent();
        final Object meter=p.meter(System.nanoTime());
        final CohortTerminalTelemetryStudy.SetupMeter m=
            new CohortTerminalTelemetryStudy.SetupMeter(p,meter);
        NativeFixture() throws Exception { m.charge(0); }
        void prepay(long size) throws Exception { m.charge((size+4095L)/4096L); }
        Object file(Path path) throws Exception {
            return p.call("fileDigest",new Class<?>[]{Path.class,p.meterType},path,meter);
        }
        Object read(InputStream in,long size) throws Exception {
            return p.call("fileDigestRead",new Class<?>[]{InputStream.class,long.class,p.meterType},
                in,Long.valueOf(size),meter);
        }
        void expire() throws Exception { set(meter,"groupStarted",Long.valueOf(System.nanoTime()-6000000000L)); }
        void state(long debit) throws Exception {
            equal(number(meter,"units"),debit,"units");
            equal(number(meter,"groupUnits"),debit,"groupUnits");
            equal(number(meter,"copiedEntries"),0,"copiedEntries");
            equal(number(meter,"copyChunks"),0,"copyChunks");
            equal(number(meter,"groups"),0,"groups");
            require(Boolean.TRUE.equals(get(meter,"open")),"group must remain open");
        }
    }

    static Object get(Object o,String name) throws Exception {
        return CohortTerminalTelemetryStudy.field(o,name);
    }
    static long number(Object o,String name) throws Exception { return ((Number)get(o,name)).longValue(); }
    static void set(Object o,String name,Object value) throws Exception {
        Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o,value);
    }
    static void require(boolean ok,String label) { if(!ok) throw new AssertionError(label); }
    static void equal(long actual,long expected,String label) {
        require(actual==expected,label+": expected "+expected+", observed "+actual);
    }
    static byte[] bytes(int n) {
        byte[] b=new byte[n];
        for(int i=0;i<n;i++) b[i]=(byte)((i*31+(i>>>8)*17+91)&255);
        return b;
    }
    static String oracle(byte[] b) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(b);
        StringBuilder s=new StringBuilder();
        for(byte x:hash) { int v=x&255; if(v<16) s.append('0'); s.append(Integer.toHexString(v)); }
        return s.toString();
    }
    static void digest(Object value,byte[] b) throws Exception {
        equal(number(value,"bytes"),b.length,"digest byte count");
        require(oracle(b).equals(get(value,"sha")),"independent byte SHA-256 mismatch");
    }
    interface Action { void run() throws Exception; }
    static void rejected(Action a,Class<?> type,String message) throws Exception {
        try { a.run(); }
        catch(Exception e) {
            require(type.isInstance(e),"wrong exception: "+e.getClass().getName());
            require(e.getMessage()!=null && e.getMessage().contains(message),"wrong rejection: "+e);
            return;
        }
        throw new AssertionError("expected rejection: "+message);
    }

    // Deliberately synthetic stream for the prepaid private read loop only.
    static final class Stream extends InputStream {
        final byte[] body;
        final int chunk;
        final String behavior;
        final NativeFixture f;
        int offset,calls,eofCalls,maxRequest,lastRequest;
        boolean closed;
        Stream(byte[] body,int chunk,String behavior,NativeFixture f) {
            this.body=body; this.chunk=chunk; this.behavior=behavior; this.f=f;
        }
        public int read(byte[] b,int off,int len) throws IOException {
            require(len>0 && len<=8192,"bounded positive request");
            calls++; maxRequest=Math.max(maxRequest,len); lastRequest=len;
            if("zero".equals(behavior)) return 0;
            if("overreturn".equals(behavior)) return len+1;
            if("error".equals(behavior) || ("prefix_error".equals(behavior) && calls>1))
                throw new IOException("SYNTHETIC_READ_FAILURE");
            if(offset==body.length) return -1;
            int n=Math.min(len,Math.min(chunk,body.length-offset));
            System.arraycopy(body,offset,b,off,n); offset+=n;
            if("expire".equals(behavior)) expire();
            return n;
        }
        public int read() throws IOException {
            eofCalls++;
            if("eof_error".equals(behavior)) throw new IOException("SYNTHETIC_EOF_FAILURE");
            if("eof_expire".equals(behavior)) expire();
            return offset<body.length?body[offset++]&255:-1;
        }
        void expire() throws IOException {
            try { f.expire(); } catch(Exception e) { throw new IOException(e); }
        }
        public void close() { closed=true; }
    }

    static String successStream(int size,int chunk,boolean checkTail) throws Exception {
        NativeFixture f=new NativeFixture();
        byte[] b=bytes(size);
        long fee=(size+4095L)/4096L;
        f.prepay(size);
        Stream s=new Stream(b,chunk,"ok",f);
        digest(f.read(s,size),b); f.state(fee);
        equal(s.offset,size,"stream bytes"); equal(s.eofCalls,1,"EOF probe count");
        require(!s.closed,"read helper must not own caller stream");
        if(checkTail) equal(s.lastRequest,1,"final bounded request");
        return "\"debit\":"+fee+",\"bytes\":"+size+",\"readCalls\":"+s.calls;
    }
    static String failureStream(final String mode,int actual,final long expected,int chunk,
            Class<?> type,String message) throws Exception {
        final NativeFixture f=new NativeFixture();
        f.prepay(expected);
        final Stream s=new Stream(bytes(actual),chunk,mode,f);
        rejected(new Action(){ public void run() throws Exception { f.read(s,expected); }},type,message);
        long fee=(expected+4095L)/4096L;
        f.state(fee); require(!s.closed,"read helper does not close on failure");
        if("expire".equals(mode)) equal(s.calls,1,"expiry stops before successor read");
        return "\"debit\":"+fee+",\"readCalls\":"+s.calls+",\"eofCalls\":"+s.eofCalls;
    }
    static String suite(String name) throws Exception {
        if("short_one_byte_4097".equals(name)) return successStream(4097,1,false);
        if("short_2048_4096".equals(name)) return successStream(4096,2048,false);
        if("short_2048_4097".equals(name)) return successStream(4097,2048,true);
        if("chunk_4096_8192".equals(name)) return successStream(8192,4096,false);
        if("chunk_8192_8192".equals(name)) return successStream(8192,8192,false);
        if("empty_eof_probe".equals(name)) return successStream(0,8192,false);
        if("bounded_final_read".equals(name)) return successStream(8193,8192,true);
        if("zero_progress".equals(name))
            return failureStream("zero",4096,4096,2048,IOException.class,"zero progress");
        if("early_eof".equals(name))
            return failureStream("ok",2048,4096,2048,IOException.class,"early EOF");
        if("extra_byte".equals(name))
            return failureStream("ok",4097,4096,2048,IOException.class,"extra byte");
        if("overreturn".equals(name))
            return failureStream("overreturn",4096,4096,4096,IOException.class,"invalid read length");
        if("read_error".equals(name))
            return failureStream("error",4096,4096,2048,IOException.class,"SYNTHETIC_READ_FAILURE");
        if("error_after_prefix".equals(name))
            return failureStream("prefix_error",4096,4096,2048,IOException.class,"SYNTHETIC_READ_FAILURE");
        if("expiry_during_read".equals(name))
            return failureStream("expire",4096,4096,2048,IllegalStateException.class,"five seconds");
        if("expiry_during_eof".equals(name))
            return failureStream("eof_expire",0,0,8192,IllegalStateException.class,"five seconds");
        if("eof_read_error".equals(name))
            return failureStream("eof_error",0,0,8192,IOException.class,"SYNTHETIC_EOF_FAILURE");
        throw new AssertionError("unknown suite case");
    }

    static String smoke(String name,Path dir) throws Exception {
        int size;
        if("file_empty".equals(name) || "file_empty_closed_meter".equals(name)) size=0;
        else if("file_1".equals(name)) size=1;
        else if("file_4096".equals(name)) size=4096;
        else if("file_8192".equals(name)) size=8192;
        else if("file_8mib".equals(name)) size=8*1024*1024;
        else if("file_overcap_9mib".equals(name)) size=9*1024*1024;
        else size=4097;
        final Path path=dir.resolve(name+".bin");
        byte[] b=bytes(size);
        Files.write(path,b,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        try {
            final NativeFixture f=new NativeFixture();
            if("file_overcap_9mib".equals(name)) {
                rejected(new Action(){ public void run() throws Exception { f.file(path); }},
                    IOException.class,"receipt exceeds 8 MiB"); f.state(0);
                return "\"debit\":0,\"bytes\":"+size;
            }
            if("file_expired_before_body".equals(name)) {
                f.expire();
                rejected(new Action(){ public void run() throws Exception { f.file(path); }},
                    IllegalStateException.class,"five seconds"); f.state(0);
                return "\"debit\":0,\"bytes\":"+size;
            }
            if("file_capacity_before_body".equals(name)) {
                f.m.charge(32768);
                rejected(new Action(){ public void run() throws Exception { f.file(path); }},
                    IOException.class,"32768 units"); f.state(32768);
                return "\"debit\":0,\"existingUnits\":32768,\"bytes\":"+size;
            }
            if("file_empty_closed_meter".equals(name)) {
                f.m.finish();
                rejected(new Action(){ public void run() throws Exception { f.file(path); }},
                    IllegalStateException.class,"outside a measured group");
                equal(number(f.meter,"units"),0,"closed-empty units");
                equal(number(f.meter,"copiedEntries"),0,"closed-empty copy entries");
                equal(number(f.meter,"copyChunks"),0,"closed-empty copy chunks");
                require(Boolean.FALSE.equals(get(f.meter,"open")),"closed meter stays closed");
                return "\"debit\":0,\"bytes\":0";
            }
            if("file_unmetered_4097".equals(name)) {
                digest(f.p.call("fileDigest",new Class<?>[]{Path.class},path),b); f.state(0);
                return "\"debit\":0,\"bytes\":"+size;
            }
            digest(f.file(path),b);
            long fee=(size+4095L)/4096L; f.state(fee);
            return "\"debit\":"+fee+",\"bytes\":"+size;
        } finally { Files.delete(path); }
    }
    static String quote(String value) {
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='"' || c=='\\') b.append('\\').append(c);
            else if(c<32 || c>126) {
                String h=Integer.toHexString(c); b.append("\\u");
                for(int j=h.length();j<4;j++) b.append('0'); b.append(h);
            } else b.append(c);
        }
        return b.append('"').toString();
    }
    public static void main(String[] args) throws Exception {
        if(args==null || args.length<1 ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0])))
            throw new IllegalArgumentException("SMOKE <existing-empty-task-directory> | SUITE");
        boolean smoke="SMOKE".equals(args[0]);
        if(args.length!=(smoke?2:1)) throw new IllegalArgumentException("wrong argument count");
        Path dir=null;
        if(smoke) {
            require(java.io.File.separatorChar=='\\',"SMOKE requires actual Windows files");
            dir=Paths.get(args[1]).toAbsolutePath().normalize();
            require(Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(dir),
                "root must provide an existing task-owned directory");
            try(DirectoryStream<Path> entries=Files.newDirectoryStream(dir)) {
                require(!entries.iterator().hasNext(),"smoke directory must be empty");
            }
        }
        String[] names=smoke?SMOKE:SUITE;
        List<String> rows=new ArrayList<String>();
        boolean passed=true;
        for(String name:names) {
            try {
                String observations=smoke?smoke(name,dir):suite(name);
                rows.add("{\"name\":"+quote(name)+",\"status\":\"PASS\","+observations+"}");
            } catch(Throwable failed) {
                passed=false;
                rows.add("{\"name\":"+quote(name)+",\"status\":\"FAIL\",\"error\":"+
                    quote(failed.getClass().getName()+": "+failed.getMessage())+"}");
            }
        }
        StringBuilder out=new StringBuilder("{\"mode\":").append(quote(args[0]));
        out.append(",\"status\":").append(quote(passed?"PASS":"FAIL")).append(",\"names\":[");
        for(int i=0;i<names.length;i++) { if(i>0) out.append(','); out.append(quote(names[i])); }
        out.append("],\"cases\":[");
        for(int i=0;i<rows.size();i++) { if(i>0) out.append(','); out.append(rows.get(i)); }
        System.out.println(out.append("]}"));
        if(!passed) System.exit(1);
    }
}
