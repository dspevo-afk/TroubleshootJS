package com.lushprojects.circuitjs1.client;

/** Public replay identity. Parsing never normalizes an unknown seed, family or epoch. */
final class PlayerLaunchRequest {
    static final String EPOCH = "tsj-alpha/1";
    final String familyId;
    final long seed;
    final DifficultyProfile profile;

    PlayerLaunchRequest(String familyId, String seedText, String profileText) {
        if (!PlayerFamilyCatalog.contains(familyId))
            throw new IllegalArgumentException("Unsupported board family");
        seed = parseSeed(seedText);
        profile = DifficultyProfile.parseAvailable(profileText);
        this.familyId = familyId;
    }

    /** Entropy selection is explicit. Exact constructors and replay never call it. */
    static PlayerLaunchRequest random(String familyId, String entropyText, String profileText) {
        long selected = PlayerFamilyCatalog.selectNormalPlayerSeed(familyId, parseSeed(entropyText));
        return new PlayerLaunchRequest(familyId, Long.toString(selected), profileText);
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

    String replay() { return EPOCH + "/" + profile.name() + "/" + familyId + "/" + seed; }
    GenerationRequest generation() { return GenerationRequest.player(this); }
}
