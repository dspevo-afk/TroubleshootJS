package com.lushprojects.circuitjs1.client;

import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identity-only cross-runtime binding. Does not construct a board or execute proofs. */
public final class Rb56ManifestContractTest {
    private static final Pattern PART_VALUE = Pattern.compile(
        "(\\[[A-Z][A-Z0-9_]*:[A-Z0-9_]+:[A-Z0-9_]+:[A-Z0-9_]+:)(-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)(:)");
    private static final String[][] ROOTS = {
        {"-1017", "-1067", "-1022"},
        {"-2230691771308388107", "6702341860553104274", "-8360266000248569150"},
        {"5954648469308924264", "8746043109435501874", "-5914932438307118819",
         "-5623001022168249027", "3437424778755667517", "3219288467305141894",
         "-3242722890621869748", "3274142870939325193"},
        {"6717106128883189437", "-7993534270425766476", "665993870941717295",
         "-1740268088938250233", "-5491370319832732895", "-7785299080753913940",
         "7817806517725629686", "-7945665765811446459"},
        {"-2157433570835955452", "1325853684799777255", "-3798151323707447735",
         "1178227627326218914", "1467570129396149887", "-1964920481438493458",
         "-1089711617266387266", "-4261205899415264525"}
    };
    public static void main(String[] args) throws Exception {
        String[] partitions = {"profile", "development", "holdout", "replacement-blind",
            "replacement-blind-2"};
        int rows = 0;
        for (int partition = 0; partition < ROOTS.length; partition++) {
            for (int ordinal = 0; ordinal < ROOTS[partition].length; ordinal++) {
                String root = ROOTS[partition][ordinal];
                long exact = Long.parseLong(root);
                if (!root.equals(Long.toString(exact))) throw new AssertionError("Signed-long identity changed");
                GenerationRequest request = partition == 0 ?
                    GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, exact) :
                    GenerationRequest.player(PlayerLaunchRequest.random(Rb56Plan.FAMILY_ID, root, "MEDIUM"));
                if (request.candidateCount() != (partition == 0 ? 1 : 4))
                    throw new AssertionError("Candidate population changed");
                StringBuilder output = new StringBuilder("RB56_MANIFEST {\"partition\":\"");
                output.append(partitions[partition]).append("\",\"manifestHashProtocol\":\"rb56-part-decimal-value/1\",\"ordinal\":").append(ordinal)
                    .append(",\"root\":\"").append(root).append("\",\"requestSha256\":\"")
                    .append(sha256(normalizePartValues(request.canonical()))).append("\",\"candidateSha256\":[");
                for (int candidate = 0; candidate < request.candidateCount(); candidate++) {
                    if (candidate > 0) output.append(',');
                    output.append('"').append(sha256(normalizePartValues(request.candidateManifest(candidate)))).append('"');
                }
                System.out.println(output.append("]}").toString());
                rows++;
            }
        }
        if (rows != 30) throw new AssertionError("Finite identity census changed");
        if (!sha256("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
            throw new AssertionError("JDK SHA256 oracle changed");
        String literal = "unchanged=4.7E-5;[C1:CAPACITOR:POWER:RADIAL:4.7E-5:-:-|1=A|2=B]";
        if (!normalizePartValues(literal).equals(
                "unchanged=4.7E-5;[C1:CAPACITOR:POWER:RADIAL:0.000047:-:-|1=A|2=B]"))
            throw new AssertionError("Only exact part-value decimal notation may normalize");
        System.out.println("PASS: RB56 manifest identities rows=30 scope=IDENTITY_ONLY noCircuitConstruction=true");
    }
    /** No tolerance or rounding; every non-value byte remains in the full hash. */
    private static String normalizePartValues(String manifest) {
        Matcher matcher = PART_VALUE.matcher(manifest);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(
            matcher.group(1) + new BigDecimal(matcher.group(2)).stripTrailingZeros().toPlainString() + matcher.group(3)));
        matcher.appendTail(result);
        return result.toString();
    }
    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) {
            int unsigned = item & 255;
            if (unsigned < 16) result.append('0');
            result.append(Integer.toHexString(unsigned));
        }
        return result.toString();
    }
}
