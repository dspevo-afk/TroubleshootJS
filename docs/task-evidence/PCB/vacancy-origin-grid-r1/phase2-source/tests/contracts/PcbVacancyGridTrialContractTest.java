package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;

/** Bounded native16/density15 routed observations against sealed current15 inputs. */
public final class PcbVacancyGridTrialContractTest {
    private static final int SAVED_VERSION = 15;
    private static final int TRIAL_VERSION = 16;
    private static final String BASELINE_SEAL_SHA256 =
        "12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816";
    private static final String[] FIXED_ROOTS = {
        "-1017", "-1004", "-1022", "0", "17", "42", "1", "7", "5",
        "14", "2", "3", "4", "6"
    };
    private static final Baseline[] BASELINES = {
        new Baseline("-1017", 40, 96,
            "f6f951ff3f8d673456ca82ccf01ffd06736023a36082cc7c4cd71e9a3f8db92c",
            "ca85c5076f2394903bc34b3e315082cd24a43693f2c665cffdb5e27743257d04",
            "5631c97db7705de0ed49a70f2c07741b0c2940f4f90e2904722faa5ad8cab5c6",
            "8535299046786058855", "-2738316251575122434"),
        new Baseline("-1004", 44, 104,
            "39b01caa2d12c9cf4804df39c6fcbcec55f9cac9cd6ab5efcca2a08545aed9cb",
            "ef38d327664a93715e5e63cb353f6d141b593b3d556b78302a5221e91cff6a3d",
            "9fcd2315439e7f9b038ee53d6487f40b4e221214fb5b5dbb1a32bf79df5083b1",
            "-6149459120783346325", "-1137963182477054582"),
        new Baseline("-1022", 60, 143,
            "bb68d08e1d9074488e051aad5e9a3a41dd46e844713b52c4bbf4d593a8feba77",
            "17552a33d0687f7a126b2be05dce5ec7083d3b48ea0ba4389f226f3968ccc7e4",
            "8869adea6a77467d2cbbbe7d5efafe3b86aa71b687d6bd13d0ca0fdfac86002c",
            "-5634176660316136309", "-3812824227946713686"),
        new Baseline("0", 42, 100,
            "33f275c2ea5eaeffe04fae244b026c048d7f71d4e2de28b82d943d15816ae1e9",
            "c02ebc801a3cc0bbbe578bef2bcd724be7be8c25690b8954b950e7d0d8335700",
            "2f767c959b3602194a3bafbafbe0db189190af581bbeefd0f32deef77196eecb",
            "-5379796117093146911", "-824917182082994548"),
        new Baseline("17", 53, 129,
            "631f1641d6b1b8db21166d37ad9d1c53178211de7aa7a05fac07d9293c92b53f",
            "a9b04c89ff59e0eadb3be4df60fa801c7ba9a9c58390eefc2f765a0cf6d76954",
            "546f86bef3cd5427d38b5473874c4dd41d383c416e52e614cea62c8380fbba6b",
            "5184606405683509778", "-5462107092257565683"),
        new Baseline("42", 54, 131,
            "4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59",
            "840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a",
            "5be52fd06e5788bbd64793059c26b198d57a4feda89c5c1e8e29a63c110622f7",
            "5526715536693200536", "4493419427463069983"),
        new Baseline("1", 40, 96,
            "9849daf8b11df85f4745be1759114575c216e75cd5063639869ca100b015cae3",
            "ca85c5076f2394903bc34b3e315082cd24a43693f2c665cffdb5e27743257d04",
            "4ac2a86b5c2e95fe90c1de85ac16c93d2a60219b59760114ba71c6aaed082fee",
            "-5090734189765710466", "6504385070930564385"),
        new Baseline("7", 52, 127,
            "c8c8e369c8db3b9627acf429ba62103bbedb770dc76a28f76d4556300ab2b331",
            "95eff0719bd75d121e58c61d07326f90743a8b266fcc44c94d8fe3cb4ec410d7",
            "8f1b88ba1c723244d9ae37f071fac28debe60388100b2cd9f92f861838f5ece4",
            "-1117647677672080908", "4284983513615419523"),
        new Baseline("5", 54, 131,
            "3418e4324685d2f505eeaf3a771113a06ff05be5b9ba2a85a95a533f505dfc3e",
            "e28aedf66937422e108a17e80da7a3f761bc31e0095b09cb95e5f7266689b146",
            "fcbce1338aa8c3027900f388bda7202ebf4bbdf8d88be2e679af452aec1320d9",
            "-7630701392004902526", "-9106650364266936003"),
        new Baseline("14", 54, 131,
            "841c36f4e5e54bae2762dd5ad6d915714609c913117c6bf3a685079adf7a0d65",
            "5c6762303c67e9e6595b2c81e333d1e0c05ff54955e201ae9cf6969f9f0344a8",
            "becb019f1371bd9c3551000757102e6efbc57554f6f7b4ea52067a1b311f70f9",
            "5215339006663252975", "2390981720173865414"),
        new Baseline("2", 56, 135,
            "d6ff81412f4e255dabe0a917185c77b0bc74135f24d3d22a3de6c526895a6423",
            "7acc7e848809f5ee35dbdeacc5c77e4536eadfe1dc6ed3b8747cbf4ce51f7c19",
            "f1310b3c8412636bc7caa7733dddc8cc0fabb47f4063c2d6f52689966a0cac66",
            "8999105239954772123", "-7578323945011786438"),
        new Baseline("3", 49, 121,
            "72607f483fc6a355ec4619f4f4fd9d7305477ea6511bb4701b9c913faf90669e",
            "13d5eae2a568241bff82d4957ecb80ad4709cd71c9881220e7e9f6dbc6bdae18",
            "411df63575d54163abdda1827cfa29cc698ef3cea55c4cd13c0c0ee4bc58a387",
            "6764929174475377600", "1013603469457857095"),
        new Baseline("4", 55, 133,
            "41bd3e76e75c58e5f84525f83bb9726c7298fb503f442cc6df5965f47a388593",
            "ef29969271467fb5952c20db368fc519623fe3f290d4df11ea431e4784dc97b7",
            "553ffb9788203e66a95cdf2b9d5ee04e831aa5faa16a0a349033fede4917b421",
            "-2177905595674580779", "4743591333661991160"),
        new Baseline("6", 54, 131,
            "95eec5d508dd77f286fc52fedfeb113d5d67e06df1607942f182fb6bfc58bbdc",
            "f5de111f1911e44789ff90b3fa0e6e0c9eeb86b4561c0516603505cacb185349",
            "a12f2f7e070a59fa23f57276ed91c48c74f1c485d900563c9839fd374d0de5fd",
            "8859906766512685727", "-6632852850330236554")
    };
    private static final long[] DEFAULT_SEEDS = { -1017L, -1004L };
    private static int assertions;

    private PcbVacancyGridTrialContractTest() { }

    public static void main(String[] args) {
        long[] seeds = DEFAULT_SEEDS;
        if (args.length > 0) {
            seeds = new long[args.length];
            for (int index = 0; index < args.length; index++)
                seeds[index] = Long.parseLong(args[index]);
        }
        runSeeds(seeds, "PCB vacancy grid trial contracts ");
    }

    /** Root-owned wrappers keep every JVM to at most two declared cases. */
    static void runSeeds(long[] seeds, String marker) {
        check(seeds != null && seeds.length > 0 && seeds.length <= 2,
            "each native JVM owns one or two declared roots");
        TreeSet<Long> unique = new TreeSet<Long>();
        for (long seed : seeds) {
            check(isFixedRoot(seed), "root is in the sealed accepted15 set: " + seed);
            check(unique.add(Long.valueOf(seed)), "root partition has no duplicate: " + seed);
        }
        int failures = 0;
        for (long seed : seeds) {
            try {
                exportRoot(seed);
            } catch (AssertionError failure) {
                failures++;
                reportFailure(seed, failure);
            } catch (RuntimeException failure) {
                failures++;
                reportFailure(seed, failure);
            }
        }
        if (failures != 0)
            throw new AssertionError("PCB vacancy grid trial group failed cases=" +
                failures + " of " + seeds.length);
        System.out.println("PASS: " + marker + "roots=" + seeds.length +
            " rows=" + seeds.length + " assertions=" + assertions +
            " baselineSeal=" + BASELINE_SEAL_SHA256);
    }

    private static boolean isFixedRoot(long seed) {
        String text = Long.toString(seed);
        for (String fixed : FIXED_ROOTS) if (fixed.equals(text)) return true;
        return false;
    }

    private static void exportRoot(long seed) {
        Baseline baseline = baseline(seed);
        check(baseline != null, "sealed accepted15 baseline exists: " + seed);
        long sealedSeed = Long.parseLong(baseline.seed);
        check(Long.toString(sealedSeed).equals(baseline.seed) && sealedSeed == seed,
            "signed-long root retains its exact canonical decimal text: " + seed);

        Rb56Plan savedPlan = Rb56Plan.resolve(seed, SAVED_VERSION);
        Rb56Plan trialPlan = Rb56Plan.resolve(seed, TRIAL_VERSION);
        TroubleshootBoard savedBoard = savedPlan.board();
        TroubleshootBoard trialBoard = trialPlan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(savedPlan, savedBoard);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trialPlan, trialBoard);
        check(savedPlan.seed == seed && savedPlan.placementInterpretationVersion ==
                SAVED_VERSION,
            "explicit saved15 baseline plan: " + seed);
        check(trialPlan.seed == seed && trialPlan.placementInterpretationVersion ==
                TRIAL_VERSION,
            "explicit native16 trial plan: " + seed);

        PcbPlacementConstraints savedConstraints = savedBoard.getPlacementConstraints();
        PcbPlacementConstraints trialConstraints = trialBoard.getPlacementConstraints();
        check(savedConstraints.placementDensityVersion == 14 &&
                trialConstraints.placementDensityVersion == 15,
            "saved15 retains density14 and trial16 uses density15: " + seed);
        check("ROLE_TRACE@1".equals(savedConstraints.getTraceWidthProfile().getIdentity()) &&
                "ROLE_TRACE@1".equals(trialConstraints.getTraceWidthProfile().getIdentity()) &&
                savedConstraints.requiresTwoLayerRouting &&
                trialConstraints.requiresTwoLayerRouting,
            "both paired plans retain ROLE_TRACE@1 and P07 two-layer routing: " + seed);

        String sourceIntentSha = PcbPostSelectionEdgeTrimContractTest
            .sourceIntentSha(savedPlan);
        String trialIntentSha = PcbPostSelectionEdgeTrimContractTest
            .sourceIntentSha(trialPlan);
        String savedSourceBefore = PcbCanvasBoundaryContractTest
            .sourcePlanSha256(savedPlan);
        String trialSourceBefore = PcbCanvasBoundaryContractTest
            .sourcePlanSha256(trialPlan);
        String savedGraphBefore = PcbCanvasBoundaryContractTest
            .liveGraphSha256(savedBoard);
        String trialGraphBefore = PcbCanvasBoundaryContractTest
            .liveGraphSha256(trialBoard);

        check(savedSourceBefore.equals(baseline.sourcePlanSha256),
            "saved15 source plan matches its sealed literal: " + seed);
        check(savedGraphBefore.equals(baseline.liveBoardGraphSha256) &&
                trialGraphBefore.equals(baseline.liveBoardGraphSha256) &&
                savedGraphBefore.equals(trialGraphBefore),
            "saved15 and trial16 live graphs match the sealed literal before routing: " + seed);
        check(sourceIntentSha.equals(baseline.sourceIntentSha256) &&
                trialIntentSha.equals(baseline.sourceIntentSha256) &&
                sourceIntentSha.equals(trialIntentSha),
            "saved15 and trial16 source intent match the sealed literal: " + seed);
        check(!savedSourceBefore.equals(trialSourceBefore),
            "trial16 source plan differs only in its expected placement metadata: " + seed);

        check(Long.toString(savedPlan.layoutSeed).equals(baseline.layoutSeed) &&
                Long.toString(trialPlan.layoutSeed).equals(baseline.layoutSeed) &&
                savedPlan.layoutSeed == trialPlan.layoutSeed &&
                Long.toString(savedPlan.routingSeed).equals(baseline.routingSeed) &&
                Long.toString(trialPlan.routingSeed).equals(baseline.routingSeed) &&
                savedPlan.routingSeed == trialPlan.routingSeed,
            "placement and routing streams match the exact signed-long baseline literals: " + seed);
        check(savedBoard.getComponentIds().size() == baseline.parts &&
                trialBoard.getComponentIds().size() == baseline.parts &&
                savedBoard.getPadIds().size() == baseline.pads &&
                trialBoard.getPadIds().size() == baseline.pads &&
                new TreeSet<String>(savedBoard.getComponentIds()).equals(
                    new TreeSet<String>(trialBoard.getComponentIds())) &&
                new TreeSet<String>(savedBoard.getPadIds()).equals(
                    new TreeSet<String>(trialBoard.getPadIds())),
            "both plans retain the sealed package and pad inventory: " + seed);

        System.out.println("PCB_VACANCY_GRID_INPUT {\"seed\":\"" +
            Long.toString(seed) + "\",\"baselineSealSha256\":\"" +
            BASELINE_SEAL_SHA256 + "\",\"sourcePlan15Sha256\":\"" +
            savedSourceBefore + "\",\"sourcePlan16Sha256\":\"" +
            trialSourceBefore + "\",\"liveBoardGraphSha256\":\"" +
            trialGraphBefore + "\",\"sourceIntentSha256\":\"" +
            trialIntentSha + "\",\"layoutSeed\":\"" +
            Long.toString(trialPlan.layoutSeed) + "\",\"routingSeed\":\"" +
            Long.toString(trialPlan.routingSeed) + "\",\"parts\":" +
            trialBoard.getComponentIds().size() + ",\"pads\":" +
            trialBoard.getPadIds().size() + "}");

        String trialRow = PcbPostSelectionEdgeTrimContractTest
            .routeAndObserveVacancyGridTrial(seed, trialPlan, trialBoard,
                trialIntentSha, trialSourceBefore, trialGraphBefore);
        System.out.println(trialRow);
    }

    private static Baseline baseline(long seed) {
        String text = Long.toString(seed);
        for (Baseline row : BASELINES) if (row.seed.equals(text)) return row;
        return null;
    }

    private static void reportFailure(long seed, Throwable failure) {
        String message = String.valueOf(failure.getMessage())
            .replace('\r', ' ').replace('\n', ' ');
        System.out.println("PCB_VACANCY_GRID_FAILURE seed=" + Long.toString(seed) +
            " type=" + failure.getClass().getName() + " message=" + message);
        failure.printStackTrace(System.out);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Baseline {
        final String seed;
        final int parts, pads;
        final String sourcePlanSha256, liveBoardGraphSha256, sourceIntentSha256;
        final String layoutSeed, routingSeed;

        Baseline(String seed, int parts, int pads, String sourcePlanSha256,
                String liveBoardGraphSha256, String sourceIntentSha256,
                String layoutSeed, String routingSeed) {
            this.seed = seed;
            this.parts = parts;
            this.pads = pads;
            this.sourcePlanSha256 = sourcePlanSha256;
            this.liveBoardGraphSha256 = liveBoardGraphSha256;
            this.sourceIntentSha256 = sourceIntentSha256;
            this.layoutSeed = layoutSeed;
            this.routingSeed = routingSeed;
        }
    }
}
