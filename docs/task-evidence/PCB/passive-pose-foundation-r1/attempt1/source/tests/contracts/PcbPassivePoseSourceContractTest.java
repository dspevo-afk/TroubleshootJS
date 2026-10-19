package com.lushprojects.circuitjs1.client;

/** Native-only binding of old TOP0 interpretations to sealed accepted literals. */
public final class PcbPassivePoseSourceContractTest {
    private static int assertions;
    private static final Expected[] EXPECTED = {
        new Expected("-1017", 40, 96, "f6f951ff3f8d673456ca82ccf01ffd06736023a36082cc7c4cd71e9a3f8db92c", "ca85c5076f2394903bc34b3e315082cd24a43693f2c665cffdb5e27743257d04", "5631c97db7705de0ed49a70f2c07741b0c2940f4f90e2904722faa5ad8cab5c6", "8535299046786058855", "-2738316251575122434"),
        new Expected("-1004", 44, 104, "39b01caa2d12c9cf4804df39c6fcbcec55f9cac9cd6ab5efcca2a08545aed9cb", "ef38d327664a93715e5e63cb353f6d141b593b3d556b78302a5221e91cff6a3d", "9fcd2315439e7f9b038ee53d6487f40b4e221214fb5b5dbb1a32bf79df5083b1", "-6149459120783346325", "-1137963182477054582"),
        new Expected("-1022", 60, 143, "bb68d08e1d9074488e051aad5e9a3a41dd46e844713b52c4bbf4d593a8feba77", "17552a33d0687f7a126b2be05dce5ec7083d3b48ea0ba4389f226f3968ccc7e4", "8869adea6a77467d2cbbbe7d5efafe3b86aa71b687d6bd13d0ca0fdfac86002c", "-5634176660316136309", "-3812824227946713686"),
        new Expected("0", 42, 100, "33f275c2ea5eaeffe04fae244b026c048d7f71d4e2de28b82d943d15816ae1e9", "c02ebc801a3cc0bbbe578bef2bcd724be7be8c25690b8954b950e7d0d8335700", "2f767c959b3602194a3bafbafbe0db189190af581bbeefd0f32deef77196eecb", "-5379796117093146911", "-824917182082994548"),
        new Expected("17", 53, 129, "631f1641d6b1b8db21166d37ad9d1c53178211de7aa7a05fac07d9293c92b53f", "a9b04c89ff59e0eadb3be4df60fa801c7ba9a9c58390eefc2f765a0cf6d76954", "546f86bef3cd5427d38b5473874c4dd41d383c416e52e614cea62c8380fbba6b", "5184606405683509778", "-5462107092257565683"),
        new Expected("42", 54, 131, "4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59", "840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a", "5be52fd06e5788bbd64793059c26b198d57a4feda89c5c1e8e29a63c110622f7", "5526715536693200536", "4493419427463069983"),
        new Expected("1", 40, 96, "9849daf8b11df85f4745be1759114575c216e75cd5063639869ca100b015cae3", "ca85c5076f2394903bc34b3e315082cd24a43693f2c665cffdb5e27743257d04", "4ac2a86b5c2e95fe90c1de85ac16c93d2a60219b59760114ba71c6aaed082fee", "-5090734189765710466", "6504385070930564385"),
        new Expected("7", 52, 127, "c8c8e369c8db3b9627acf429ba62103bbedb770dc76a28f76d4556300ab2b331", "95eff0719bd75d121e58c61d07326f90743a8b266fcc44c94d8fe3cb4ec410d7", "8f1b88ba1c723244d9ae37f071fac28debe60388100b2cd9f92f861838f5ece4", "-1117647677672080908", "4284983513615419523"),
        new Expected("5", 54, 131, "3418e4324685d2f505eeaf3a771113a06ff05be5b9ba2a85a95a533f505dfc3e", "e28aedf66937422e108a17e80da7a3f761bc31e0095b09cb95e5f7266689b146", "fcbce1338aa8c3027900f388bda7202ebf4bbdf8d88be2e679af452aec1320d9", "-7630701392004902526", "-9106650364266936003"),
        new Expected("14", 54, 131, "841c36f4e5e54bae2762dd5ad6d915714609c913117c6bf3a685079adf7a0d65", "5c6762303c67e9e6595b2c81e333d1e0c05ff54955e201ae9cf6969f9f0344a8", "becb019f1371bd9c3551000757102e6efbc57554f6f7b4ea52067a1b311f70f9", "5215339006663252975", "2390981720173865414"),
        new Expected("2", 56, 135, "d6ff81412f4e255dabe0a917185c77b0bc74135f24d3d22a3de6c526895a6423", "7acc7e848809f5ee35dbdeacc5c77e4536eadfe1dc6ed3b8747cbf4ce51f7c19", "f1310b3c8412636bc7caa7733dddc8cc0fabb47f4063c2d6f52689966a0cac66", "8999105239954772123", "-7578323945011786438"),
        new Expected("3", 49, 121, "72607f483fc6a355ec4619f4f4fd9d7305477ea6511bb4701b9c913faf90669e", "13d5eae2a568241bff82d4957ecb80ad4709cd71c9881220e7e9f6dbc6bdae18", "411df63575d54163abdda1827cfa29cc698ef3cea55c4cd13c0c0ee4bc58a387", "6764929174475377600", "1013603469457857095"),
        new Expected("4", 55, 133, "41bd3e76e75c58e5f84525f83bb9726c7298fb503f442cc6df5965f47a388593", "ef29969271467fb5952c20db368fc519623fe3f290d4df11ea431e4784dc97b7", "553ffb9788203e66a95cdf2b9d5ee04e831aa5faa16a0a349033fede4917b421", "-2177905595674580779", "4743591333661991160"),
        new Expected("6", 54, 131, "95eec5d508dd77f286fc52fedfeb113d5d67e06df1607942f182fb6bfc58bbdc", "f5de111f1911e44789ff90b3fa0e6e0c9eeb86b4561c0516603505cacb185349", "a12f2f7e070a59fa23f57276ed91c48c74f1c485d900563c9839fd374d0de5fd", "8859906766512685727", "-6632852850330236554")
    };
    private PcbPassivePoseSourceContractTest() { }
    public static void main(String[] args) {
        check(Rb56Placement.CURRENT_VERSION == 15, "current RB56 placement stays15");
        check(SeededPcbLayoutGenerator.CURRENT_VERSION == 16, "general layout algorithm stays16");
        for (Expected expected : EXPECTED) {
            Rb56Plan plan = boundPlan(expected);
            System.out.println("PCB_PASSIVE_POSE_SOURCE_CASE " + expected.json());
        }
        System.out.println("PASS: PCB passive pose source bindings roots=14 assertions=" + assertions);
    }
    static Rb56Plan boundPlan(Expected expected) {
        long seed = Long.parseLong(expected.seed);
        Rb56Plan plan = Rb56Plan.resolve(seed, 15);
        check(plan.seed == seed && plan.placementInterpretationVersion == 15, "explicit15 root");
        TroubleshootBoard board = plan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        check(board.getPlacementConstraints().placementDensityVersion == 14, "density14 retained");
        check(board.getComponentIds().size() == expected.parts && board.getPadIds().size() == expected.pads,
            "literal component/pad inventory");
        check(PcbCanvasBoundaryContractTest.sourcePlanSha256(plan).equals(expected.source), "literal source plan");
        check(PcbCanvasBoundaryContractTest.liveGraphSha256(board).equals(expected.graph), "literal live graph");
        check(PcbPostSelectionEdgeTrimContractTest.savedIntentShaForCurrent15(plan).equals(expected.intent),
            "literal source intent");
        check(Long.toString(plan.layoutSeed).equals(expected.layoutSeed), "exact signed-long layout seed");
        check(Long.toString(plan.routingSeed).equals(expected.routingSeed), "exact signed-long routing seed");
        return plan;
    }
    static Expected expected(long seed) {
        for (Expected row : EXPECTED) if (row.seed.equals(Long.toString(seed))) return row;
        throw new AssertionError("Unbound seed");
    }
    static final class Expected {
        final String seed, source, graph, intent, layoutSeed, routingSeed;
        final int parts, pads;
        Expected(String seed, int parts, int pads, String source, String graph, String intent,
                String layoutSeed, String routingSeed) {
            this.seed=seed;this.parts=parts;this.pads=pads;this.source=source;this.graph=graph;
            this.intent=intent;this.layoutSeed=layoutSeed;this.routingSeed=routingSeed;
        }
        String json() {
            return "{\"seed\":\"" + seed + "\",\"parts\":" + parts + ",\"pads\":" + pads +
                ",\"sourcePlanSha256\":\"" + source + "\",\"liveBoardGraphSha256\":\"" + graph +
                "\",\"sourceIntentSha256\":\"" + intent + "\",\"layoutSeed\":\"" + layoutSeed +
                "\",\"routingSeed\":\"" + routingSeed + "\"}";
        }
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
