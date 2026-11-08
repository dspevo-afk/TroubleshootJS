package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.Parent;
import com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.SetupMeter;

/** Actual R9 text-policy calls only: no fixture, routing, input or publication. */
public final class GenericReferenceTextPolicyContract {
    private static int assertions;
    private static int testCases;

    private GenericReferenceTextPolicyContract() { }

    public static void main(String[] args) throws Exception {
        long started=System.nanoTime();
        // The inherited bootstrap precedes the first usable Meter. Its original
        // clock and unchanged owner budgets cover all subsequent test work.
        Parent parent=new Parent();
        Object actualMeter=parent.meter(started);
        SetupMeter m=new SetupMeter(parent,actualMeter);
        Object integer=null;
        String ordinary=null,longText=null,geometry=null,overGeometry=null,summary=null;
        StringBuilder line=null;
        try {
            m.charge(2); assertions=0; testCases=0;
            constructorsZero(m,actualMeter);
            ordinary=text(m,256,'x');
            longText=text(m,257,'x');
            geometry=text(m,4096,'x');
            overGeometry=text(m,4097,'x');
            // Both named arms and both real List positions must receive the
            // exception. Expectations here are source literals, not DTO data.
            for (int arm=0;arm<2;arm++) {
                m.charge(1); String caseId=arm==0?"OPEN":"BLOCKED";
                for (int index=0;index<2;index++) {
                    m.charge(1);
                    accepts(m,geometryBody(m,"references",caseId,"run",
                        "geometryCanonical",index,longText));
                    accepts(m,geometryBody(m,"references",caseId,"run",
                        "geometryCanonical",index,geometry));
                    rejects(m,geometryBody(m,"references",caseId,"run",
                        "geometryCanonical",index,overGeometry),"R9_TEXT_POLICY_LENGTH");
                }
            }
            accepts(m,one(m,"ordinary",ordinary));
            rejects(m,one(m,"ordinary",longText),"R9_TEXT_POLICY_LENGTH");
            rejects(m,geometryBody(m,"references","UNKNOWN","run",
                "geometryCanonical",0,longText),"R9_TEXT_POLICY_LENGTH");
            rejects(m,geometryBody(m,"references","open","run",
                "geometryCanonical",0,longText),"R9_TEXT_POLICY_LENGTH");
            rejects(m,geometryBody(m,"references","Blocked","run",
                "geometryCanonical",0,longText),"R9_TEXT_POLICY_LENGTH");
            rejects(m,geometryBody(m,"References","OPEN","run",
                "geometryCanonical",0,longText),"R9_TEXT_POLICY_LENGTH");
            rejects(m,geometryBody(m,"references","OPEN","freshRun",
                "geometryCanonical",0,longText),"R9_TEXT_POLICY_LENGTH");
            rejects(m,geometryBody(m,"references","OPEN","run",
                "otherField",0,longText),"R9_TEXT_POLICY_LENGTH");
            rejects(m,one(m,"references.OPEN.run.layout.components[0].geometryCanonical",
                longText),"R9_TEXT_POLICY_LENGTH");
            rejects(m,one(m,"references",one(m,"OPEN.run.layout.components",
                one(m,"geometryCanonical",longText))),"R9_TEXT_POLICY_LENGTH");
            // Retired complete/freshRun root must not retain the allowance.
            rejects(m,one(m,"complete",one(m,"freshRun",one(m,"layout",
                one(m,"components",rows(m,0,one(m,"geometryCanonical",longText)))))),
                "R9_TEXT_POLICY_LENGTH");
            rejects(m,one(m,longText,"x"),"R9_TEXT_POLICY_LENGTH");

            // Structural states are tested through the actual recursive owner,
            // including null and scalar values that its ordinary state accepts.
            rejects(m,one(m,"references",null),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,one(m,"references",rows(m,0,empty(m))),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,one(m,"references",Boolean.TRUE),"R9_TEXT_POLICY_STRUCTURE");
            m.charge(1); integer=Integer.valueOf(7);
            rejects(m,one(m,"references",integer),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atCase(m,null),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atCase(m,rows(m,0,empty(m))),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atCase(m,Boolean.TRUE),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atCase(m,integer),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atRun(m,null),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atRun(m,rows(m,0,empty(m))),"R9_TEXT_POLICY_STRUCTURE");
            accepts(m,atLayout(m,null)); // inherited optional layout state
            rejects(m,atLayout(m,Boolean.TRUE),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atLayout(m,rows(m,0,empty(m))),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atComponents(m,one(m,"0",one(m,"geometryCanonical",longText))),
                "R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atComponents(m,null),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atComponents(m,"x"),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atComponents(m,rows(m,0,null)),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atComponents(m,rows(m,0,Boolean.TRUE)),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atComponents(m,rows(m,0,integer)),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atComponents(m,rows(m,0,rows(m,0,empty(m)))),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,atComponents(m,rows(m,0,"x")),"R9_TEXT_POLICY_STRUCTURE");
            rejects(m,geometryBody(m,"references","OPEN","run","geometryCanonical",0,null),
                "R9_TEXT_POLICY_STRUCTURE");
            rejects(m,geometryBody(m,"references","OPEN","run","geometryCanonical",0,Boolean.TRUE),
                "R9_TEXT_POLICY_STRUCTURE");
            rejects(m,geometryBody(m,"references","OPEN","run","geometryCanonical",0,integer),
                "R9_TEXT_POLICY_STRUCTURE");
            rejects(m,geometryBody(m,"references","OPEN","run","geometryCanonical",0,empty(m)),
                "R9_TEXT_POLICY_STRUCTURE");
            rejects(m,geometryBody(m,"references","OPEN","run","geometryCanonical",0,rows(m,0,"x")),
                "R9_TEXT_POLICY_STRUCTURE");
            integer=null;
            rejects(m,geometryBody(m,"references","OPEN","run","geometryCanonical",0,text(m,1,'\u001f')),
                "R9_TEXT_POLICY_ASCII");
            rejects(m,geometryBody(m,"references","OPEN","run","geometryCanonical",0,text(m,1,'\u007f')),
                "R9_TEXT_POLICY_ASCII");
            rejects(m,geometryBody(m,"references","OPEN","run","geometryCanonical",0,text(m,1,'\u0080')),
                "R9_TEXT_POLICY_ASCII");
            rejects(m,one(m,"ordinary",text(m,1,'\n')),"R9_TEXT_POLICY_ASCII");
            accepts(m,depth(m,64));
            rejects(m,depth(m,65),"R9_TEXT_POLICY_DEPTH");
            constructorsZero(m,actualMeter);

            // Bounded summary only; never serialize or publish any test tree.
            m.copy(96); m.charge(1); line=new StringBuilder(96);
            m.copy(16); m.charge(1); line.append("PASS assertions=");
            m.copy(10); m.charge(1); line.append(assertions);
            m.copy(11); m.charge(1); line.append(" testCases=");
            m.copy(10); m.charge(1); line.append(testCases);
            m.copy(11); m.charge(1); line.append(" sessions=0");
            m.charge(1); int length=line.length();
            m.copy(length); m.charge(2); summary=line.toString();
            // Precharge the sole final output API before closing the Meter.
            m.charge(1);
        } finally {
            ordinary=null; longText=null; geometry=null; overGeometry=null; line=null; integer=null;
            try { m.finish(); }
            finally { parent=null; actualMeter=null; m=null; }
        }
        System.out.println(summary);
        summary=null;
    }

    private static Map<String,Object> empty(SetupMeter m) throws Exception {
        m.copy(1); m.charge(1);
        return new LinkedHashMap<String,Object>(1);
    }

    private static Map<String,Object> one(SetupMeter m,String key,Object value) throws Exception {
        Map<String,Object> result=empty(m);
        m.copy(2); m.charge(1); result.put(key,value);
        return result;
    }

    private static List<Object> rows(SetupMeter m,int index,Object row) throws Exception {
        m.copy(index+1); m.charge(1);
        List<Object> result=new ArrayList<Object>(index+1);
        for (int i=0;i<index;i++) {
            m.charge(1); Object preceding=empty(m);
            m.copy(1); m.charge(1); result.add(preceding);
        }
        m.copy(1); m.charge(1); result.add(row);
        return result;
    }

    private static Object atCase(SetupMeter m,Object value) throws Exception {
        return one(m,"references",one(m,"OPEN",value));
    }

    private static Object atRun(SetupMeter m,Object value) throws Exception {
        return atCase(m,one(m,"run",value));
    }

    private static Object atLayout(SetupMeter m,Object value) throws Exception {
        return atRun(m,one(m,"layout",value));
    }

    private static Object atComponents(SetupMeter m,Object value) throws Exception {
        return atLayout(m,one(m,"components",value));
    }

    private static Object geometryBody(SetupMeter m,String referencesKey,String caseId,
            String runKey,String field,int index,Object value) throws Exception {
        return one(m,referencesKey,one(m,caseId,one(m,runKey,one(m,"layout",
            one(m,"components",rows(m,index,one(m,field,value)))))));
    }

    private static String text(SetupMeter m,int length,char value) throws Exception {
        copyChars(m,length); m.charge(1); char[] chars=new char[length];
        m.charge(length+1);
        for (int i=0;i<length;i++) chars[i]=value;
        copyChars(m,length); m.charge(1);
        String result=new String(chars);
        chars=null;
        return result;
    }

    private static void copyChars(SetupMeter m,int count) throws Exception {
        // reserveCopy admits at most 4096 entries per call, including 4097 tests.
        while (count>0) {
            m.charge(1); int chunk=count>4096?4096:count;
            m.copy(chunk); count-=chunk;
        }
    }

    private static Object depth(SetupMeter m,int depth) throws Exception {
        Object value="x";
        for (int i=0;i<depth;i++) {
            m.charge(1); value=one(m,"ordinary",value);
        }
        return value;
    }

    private static void accepts(SetupMeter m,Object body) throws Exception {
        try {
            CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(body,m);
            m.charge(2); assertions++; testCases++;
        } finally { body=null; }
    }

    private static void rejects(SetupMeter m,Object body,String code) throws Exception {
        boolean rejected=false;
        try {
            try {
                CohortTerminalTelemetryStudy.validateR9CanaryTextPolicy(body,m);
            } catch (IOException expected) {
                m.charge(3);
                // Exact owner class/code: deadline, quota and unrelated errors
                // cannot accidentally satisfy this negative policy assertion.
                require(expected.getClass()==IOException.class && code.equals(expected.getMessage()),
                    "TEXT_CONTRACT_WRONG_REJECTION");
                rejected=true;
            }
            m.charge(1); require(rejected,"TEXT_CONTRACT_EXPECTED_REJECTION");
            m.charge(2); assertions++; testCases++;
        } finally { body=null; }
    }

    private static void constructorsZero(SetupMeter m,Object actualMeter) throws Exception {
        // Read only the actual native Meter field through the inherited helper.
        // No field writes, graph/session access or substitute counter exists.
        m.charge(4);
        Object count=CohortTerminalTelemetryStudy.field(actualMeter,"sessionConstructorEntries");
        m.charge(2);
        require(count instanceof Integer && ((Integer)count).intValue()==0,
            "TEXT_CONTRACT_SESSION_CONSTRUCTED");
        m.charge(1); assertions++;
        count=null;
    }

    private static void require(boolean condition,String code) throws IOException {
        if (!condition) throw new IOException(code);
    }
}
