package com.lushprojects.circuitjs1.client;

class PcbLayoutDeveloperVerifier {
    static void verify(CirSim sim) {
        verifyFamily("LED_INDICATOR");
        verifyFamily("DIODE_PROTECTED_INDICATOR");
        GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
        PcbBoardLayout regenerated = generate(current.getCircuitFamilyId(), current.getSeed())
            .getPcbLayout();
        require(current.getPcbLayout().geometryFingerprint().equals(regenerated.geometryFingerprint()),
            "installed PCB geometry does not match deterministic regeneration");
    }

    private static void verifyFamily(String familyId) {
        PcbBoardLayout seed0 = generate(familyId, 0).getPcbLayout();
        PcbBoardLayout seed0Repeat = generate(familyId, 0).getPcbLayout();
        PcbBoardLayout seed2 = generate(familyId, 2).getPcbLayout();
        PcbBoardLayout seed2Repeat = generate(familyId, 2).getPcbLayout();
        PcbBoardLayout seed3 = generate(familyId, 3).getPcbLayout();
        PcbBoardLayout seed3Repeat = generate(familyId, 3).getPcbLayout();
        require(seed0.geometryFingerprint().equals(seed0Repeat.geometryFingerprint()),
            familyId + " seed 0 is not reproducible");
        require(seed2.geometryFingerprint().equals(seed2Repeat.geometryFingerprint()),
            familyId + " seed 2 is not reproducible");
        require(seed3.geometryFingerprint().equals(seed3Repeat.geometryFingerprint()),
            familyId + " seed 3 is not reproducible");
        require(meaningfulDifferences(seed0, seed2) >= 2,
            familyId + " seeds 0 and 2 lack meaningful geometry variation");
        require(meaningfulDifferences(seed0, seed3) >= 2,
            familyId + " seeds 0 and 3 lack meaningful geometry variation");
        require(meaningfulDifferences(seed2, seed3) >= 2,
            familyId + " seeds 2 and 3 lack meaningful geometry variation");
    }

    private static int meaningfulDifferences(PcbBoardLayout first, PcbBoardLayout second) {
        int differences = 0;
        if (!first.getBoardOutline().equals(second.getBoardOutline()))
            differences++;
        if (!first.componentGeometryFingerprint().equals(second.componentGeometryFingerprint()))
            differences++;
        if (!first.traceGeometryFingerprint().equals(second.traceGeometryFingerprint()))
            differences++;
        return differences;
    }

    private static GeneratedBoardInstance generate(String familyId, long seed) {
        if ("LED_INDICATOR".equals(familyId))
            return new LedIndicatorGenerator().generate(seed);
        if ("DIODE_PROTECTED_INDICATOR".equals(familyId))
            return new DiodeProtectedIndicatorGenerator().generate(seed);
        throw new IllegalArgumentException("Unsupported PCB verifier family: " + familyId);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
