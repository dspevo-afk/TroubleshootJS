package com.lushprojects.circuitjs1.client;

/**
 * Current session replay identity. Public identities remain owned by
 * PlayerLaunchRequest; the separate private form carries no difficulty/profile.
 */
final class PlayerSessionReplay {
    private static final String PRIVATE_PREFIX = "tsj-private-session/1/";
    private static final int MAX_LENGTH = 180;

    private final String replay;
    private final PlayerLaunchRequest playerRequest;
    private final String familyId;
    private final long seed;
    private final boolean privateQualification;

    private PlayerSessionReplay(PlayerLaunchRequest request) {
        if (request == null || request.candidateSearch)
            throw invalid("An accepted exact replay is required");
        replay = request.replay();
        playerRequest = request;
        familyId = request.familyId;
        seed = request.seed;
        privateQualification = false;
    }

    private PlayerSessionReplay(String replay, String familyId, long seed) {
        this.replay = replay;
        this.playerRequest = null;
        this.familyId = familyId;
        this.seed = seed;
        privateQualification = true;
    }

    /** Private exact RB56 restart identity; it deliberately assigns no profile. */
    static PlayerSessionReplay forQualification(GenerationRequest request) {
        if (request == null || !request.isPrivateDiagnosticQualification() ||
                request.isCandidateSearch() || !isHeldQualificationFamily(request.getFamilyId()))
            throw invalid("Private session identity requires the held exact qualification request");
        String identity = PRIVATE_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
            "/" + request.getFamilyId() + "/" +
            Long.toString(request.getDescriptor().getRootSeed());
        return parse(identity);
    }

    /** Accept public available-profile replay identities or this one private namespace. */
    static PlayerSessionReplay parse(String text) {
        if (text != null && text.startsWith("tsj-private-session/"))
            return parsePrivate(text);
        PlayerLaunchRequest request = PlayerLaunchRequest.parse(text);
        if (!request.replay().equals(text))
            throw invalid("Unsupported replay identity");
        return new PlayerSessionReplay(request);
    }

    private static PlayerSessionReplay parsePrivate(String text) {
        if (text.length() > MAX_LENGTH) throw invalid("Unsupported replay identity");
        String[] fields = text.split("/", -1);
        if (fields.length != 5 || !"tsj-private-session".equals(fields[0]) ||
                !"1".equals(fields[1]) ||
                !GenerationDependencyContext.INTERPRETATION_EPOCH.equals(fields[2]) ||
                !isHeldQualificationFamily(fields[3]))
            throw invalid("Unsupported private replay identity or epoch");
        long seed = parseSeed(fields[4]);
        String canonical = PRIVATE_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
            "/" + fields[3] + "/" + Long.toString(seed);
        if (!canonical.equals(text)) throw invalid("Use canonical private replay identity");
        return new PlayerSessionReplay(canonical, fields[3], seed);
    }

    private static long parseSeed(String text) {
        if (text == null || text.length() == 0 || text.length() > 20)
            throw invalid("Enter an exact signed 64-bit seed");
        long value;
        try { value = Long.parseLong(text); }
        catch (NumberFormatException invalid) {
            throw invalid("Enter an exact signed 64-bit seed");
        }
        if (!Long.toString(value).equals(text))
            throw invalid("Use canonical decimal seed text");
        return value;
    }

    private static boolean isHeldQualificationFamily(String familyId) {
        if (!Rb56Plan.FAMILY_ID.equals(familyId) || !PlayerFamilyCatalog.isRegistered(familyId) ||
                PlayerFamilyCatalog.isNormalPlayerEnabled(familyId))
            return false;
        StagedFamilyCapability capability = PlayerFamilyCatalog.stagedCapability(familyId);
        return capability != null && capability.supportsPrivateQualification();
    }

    String canonical() { return replay; }
    String familyId() { return familyId; }
    long seed() { return seed; }
    boolean isPrivateQualification() { return privateQualification; }

    PlayerLaunchRequest playerRequest() {
        if (privateQualification) throw invalid("Private identity has no public player request");
        return playerRequest;
    }

    boolean matches(GenerationRequest request) {
        return privateQualification && request != null &&
            request.isPrivateDiagnosticQualification() && !request.isCandidateSearch() &&
            familyId.equals(request.getFamilyId()) &&
            seed == request.getDescriptor().getRootSeed();
    }

    boolean matches(GeneratedBoardInstance candidate) {
        return privateQualification && candidate != null &&
            familyId.equals(candidate.getCircuitFamilyId()) && seed == candidate.getSeed();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
