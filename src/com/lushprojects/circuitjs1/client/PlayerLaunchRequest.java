package com.lushprojects.circuitjs1.client;

/** Public replay identity. Parsing never normalizes an unknown seed, family or epoch. */
final class PlayerLaunchRequest {
    static final String EPOCH = "tsj-alpha/2";
    final String familyId;
    final long seed;
    final DifficultyProfile profile;
    final boolean candidateSearch;

    PlayerLaunchRequest(String familyId, String seedText, String profileText) {
        this(familyId, seedText, profileText, false);
    }
    private PlayerLaunchRequest(String familyId, String seedText, String profileText, boolean search) {
        if (!PlayerFamilyCatalog.contains(familyId))
            throw new IllegalArgumentException("Unsupported board family");
        seed = parseSeed(seedText);
        profile = DifficultyProfile.parseAvailable(profileText);
        this.familyId = familyId;
        if (search && !QuickPlayAdmission.supports(familyId, profile))
            throw new IllegalArgumentException("This family/profile has no broad-seed qualification");
        candidateSearch = search;
    }

    /** Entropy selection is explicit. Exact constructors and replay never call it. */
    static PlayerLaunchRequest random(String familyId, String entropyText, String profileText) {
        DifficultyProfile profile = DifficultyProfile.parseAvailable(profileText);
        if (Rb15Plan.FAMILY_ID.equals(familyId))
            return new PlayerLaunchRequest(familyId, entropyText, profileText, true);
        long selected = PlayerFamilyCatalog.selectNormalPlayerSeed(familyId, parseSeed(entropyText));
        return new PlayerLaunchRequest(familyId, Long.toString(selected), profile.name());
    }

    private static long parseSeed(String text) {
        if (text == null || text.length() > 20)
            throw new IllegalArgumentException("Enter an exact signed 64-bit seed");
        long result;
        try { result = Long.parseLong(text); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Enter an exact signed 64-bit seed"); }
        if (!Long.toString(result).equals(text))
            throw new IllegalArgumentException("Use canonical decimal seed text");
        return result;
    }

    static PlayerLaunchRequest parse(String text) {
        if (text == null || text.length() > 180)
            throw new IllegalArgumentException("Unsupported replay identity");
        String[] fields = text.split("/", -1);
        if (fields.length != 5 || !(fields[0] + "/" + fields[1]).equals(EPOCH))
            throw new IllegalArgumentException("Unsupported replay identity or epoch");
        return new PlayerLaunchRequest(fields[3], fields[4], fields[2]);
    }

    PlayerLaunchRequest accepted(long acceptedSeed) {
        int count = candidateSearch ? QuickPlayAdmission.MAX_CANDIDATES : 1;
        for (int i = 0; i < count; i++) {
            long expected = candidateSearch ? QuickPlayAdmission.candidateSeed(seed, i) : seed;
            if (expected == acceptedSeed)
                return candidateSearch ? new PlayerLaunchRequest(familyId, Long.toString(acceptedSeed), profile.name()) : this;
        }
        throw new IllegalStateException("Published seed does not belong to this launch");
    }
    boolean accepts(PlayerLaunchRequest exact) {
        if (exact == null || exact.candidateSearch || !familyId.equals(exact.familyId) || profile != exact.profile) return false;
        try { accepted(exact.seed); return true; } catch (IllegalStateException mismatch) { return false; }
    }
    String replay() {
        if (candidateSearch) throw new IllegalStateException("A candidate search is not an accepted replay");
        return EPOCH + "/" + profile.name() + "/" + familyId + "/" + seed;
    }
    GenerationRequest generation() { return GenerationRequest.player(this); }
}
