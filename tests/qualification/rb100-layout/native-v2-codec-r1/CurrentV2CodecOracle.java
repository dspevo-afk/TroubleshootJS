package com.lushprojects.circuitjs1.client;

/** Root synthetic fixture; children intentionally fail normal full-D semantics. */
final class CurrentV2CodecOracle {
    static final String RUN_ID="00000000-0000-0000-0000-000000000001";
    static final String SOURCE_SHA="1111111111111111111111111111111111111111111111111111111111111111";
    static final String WIRE_SHA="1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed";
    static final String CONTEXT_SHA="3333333333333333333333333333333333333333333333333333333333333333";
    static final String BODY_JSON="{\"schema\":\"tsj-cohort-terminal-result-v2\",\"mode\":\"OBSERVED_COHORT_TERMINAL_TELEMETRY\",\"status\":\"COMPLETED_DIAGNOSTIC_ONLY\",\"runId\":\"00000000-0000-0000-0000-000000000001\",\"sourceManifestSha256\":\"1111111111111111111111111111111111111111111111111111111111111111\",\"wireContractSha256\":\"1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed\",\"inputContextSha256\":\"3333333333333333333333333333333333333333333333333333333333333333\",\"qualifiedPrior\":{\"synthetic\":true},\"originalCapture\":{\"synthetic\":\"not a genuine receipt\",\"seed\":-9223372036854775808},\"cohort\":{\"synthetic\":true},\"arms\":{\"null\":{\"mode\":\"NULL\",\"synthetic\":true},\"observe\":{\"mode\":\"OBSERVE\",\"synthetic\":true}},\"work\":{\"synthetic\":true,\"integer\":2147483647,\"long\":9223372036854775807,\"negative\":-9223372036854775808,\"nullValue\":null,\"quote\":\"\\\"\",\"backslash\":\"\\\\\",\"ascii256\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\",\"array\":[null,false,0,-1,\"\"]},\"cleanup\":{\"synthetic\":true,\"complete\":true}}\n";
    static final String MAX_MARKER_JSON="{\"schema\":\"tsj-cohort-terminal-marker-v2\",\"status\":\"COMPLETED_DIAGNOSTIC\",\"runId\":\"00000000-0000-0000-0000-000000000001\",\"sourceManifestSha256\":\"1111111111111111111111111111111111111111111111111111111111111111\",\"wireContractSha256\":\"1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed\",\"inputContextSha256\":\"3333333333333333333333333333333333333333333333333333333333333333\",\"resultSha256\":\"a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5\",\"resultBytes\":1048576,\"elapsedNanos\":90000000000,\"finalExportNanos\":5000000000,\"totalGroups\":640,\"totalUnits\":9223372036854775807,\"maxGroupNanos\":5000000000,\"maximumUnitsPerGroup\":32768}\n";
    static byte[] digest() {
        byte[] result=new byte[32];
        java.util.Arrays.fill(result,(byte)0xA5); return result;
    }
    static long[] markerMaxima() {
        return new long[]{1048576L,90000000000L,5000000000L,640L,Long.MAX_VALUE,5000000000L,32768L};
    }
    static java.util.Map<String,Object> body() {
        java.util.LinkedHashMap<String,Object> oracle0=new java.util.LinkedHashMap<String,Object>();
        oracle0.put("schema","tsj-cohort-terminal-result-v2");
        oracle0.put("mode","OBSERVED_COHORT_TERMINAL_TELEMETRY");
        oracle0.put("status","COMPLETED_DIAGNOSTIC_ONLY");
        oracle0.put("runId","00000000-0000-0000-0000-000000000001");
        oracle0.put("sourceManifestSha256","1111111111111111111111111111111111111111111111111111111111111111");
        oracle0.put("wireContractSha256","1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed");
        oracle0.put("inputContextSha256","3333333333333333333333333333333333333333333333333333333333333333");
        java.util.LinkedHashMap<String,Object> oracle1=new java.util.LinkedHashMap<String,Object>();
        oracle1.put("synthetic",Boolean.TRUE);
        oracle0.put("qualifiedPrior",oracle1);
        java.util.LinkedHashMap<String,Object> oracle2=new java.util.LinkedHashMap<String,Object>();
        oracle2.put("synthetic","not a genuine receipt");
        oracle2.put("seed",Long.valueOf(-9223372036854775808L));
        oracle0.put("originalCapture",oracle2);
        java.util.LinkedHashMap<String,Object> oracle3=new java.util.LinkedHashMap<String,Object>();
        oracle3.put("synthetic",Boolean.TRUE);
        oracle0.put("cohort",oracle3);
        java.util.LinkedHashMap<String,Object> oracle4=new java.util.LinkedHashMap<String,Object>();
        java.util.LinkedHashMap<String,Object> oracle5=new java.util.LinkedHashMap<String,Object>();
        oracle5.put("mode","NULL");
        oracle5.put("synthetic",Boolean.TRUE);
        oracle4.put("null",oracle5);
        java.util.LinkedHashMap<String,Object> oracle6=new java.util.LinkedHashMap<String,Object>();
        oracle6.put("mode","OBSERVE");
        oracle6.put("synthetic",Boolean.TRUE);
        oracle4.put("observe",oracle6);
        oracle0.put("arms",oracle4);
        java.util.LinkedHashMap<String,Object> oracle7=new java.util.LinkedHashMap<String,Object>();
        oracle7.put("synthetic",Boolean.TRUE);
        oracle7.put("integer",Integer.valueOf(2147483647));
        oracle7.put("long",Long.valueOf(9223372036854775807L));
        oracle7.put("negative",Long.valueOf(-9223372036854775808L));
        oracle7.put("nullValue",null);
        oracle7.put("quote","\"");
        oracle7.put("backslash","\\");
        oracle7.put("ascii256","xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        java.util.ArrayList<Object> oracle8=new java.util.ArrayList<Object>();
        oracle8.add(null);
        oracle8.add(Boolean.FALSE);
        oracle8.add(Integer.valueOf(0));
        oracle8.add(Integer.valueOf(-1));
        oracle8.add("");
        oracle7.put("array",oracle8);
        oracle0.put("work",oracle7);
        java.util.LinkedHashMap<String,Object> oracle9=new java.util.LinkedHashMap<String,Object>();
        oracle9.put("synthetic",Boolean.TRUE);
        oracle9.put("complete",Boolean.TRUE);
        oracle0.put("cleanup",oracle9);
        return oracle0;
    }
}
