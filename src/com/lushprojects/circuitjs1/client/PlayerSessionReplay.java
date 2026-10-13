package com.lushprojects.circuitjs1.client;

/**
 * Current session replay identity. Public identities remain owned by
 * PlayerLaunchRequest; the separate private form carries no difficulty/profile.
 */
final class PlayerSessionReplay {
    private static final String PRIVATE_PREFIX = "tsj-private-session/1/";
    private static final String PRIVATE_COMPACT_PREFIX = "tsj-private-session/2/";
    private static final String PRIVATE_REALISM_PREFIX = "tsj-private-session/3/";
    private static final String PRIVATE_DENSE_PREFIX = "tsj-private-session/4/";
    private static final String PRIVATE_CURRENT_PREFIX = "tsj-private-session/5/";
    private static final String COMPACT_PLACEMENT_TOKEN = "rb56-placement-2";
    private static final String REALISM_PLACEMENT_TOKEN = "rb56-placement-3";
    private static final String DENSE_PLACEMENT_TOKEN = "rb56-placement-4";
    private static final String CURRENT_PLACEMENT_TOKEN = "rb56-placement-5";
    private static final int MAX_LENGTH = 180;

    private final String replay;
    private final PlayerLaunchRequest playerRequest;
    private final String familyId;
    private final long seed;
    private final boolean privateQualification;
    private final int rb56PlacementInterpretationVersion;

    private PlayerSessionReplay(PlayerLaunchRequest request) {
        if (request == null || request.candidateSearch)
            throw invalid("An accepted exact replay is required");
        replay = request.replay();
        playerRequest = request;
        familyId = request.familyId;
        seed = request.seed;
        privateQualification = false;
        rb56PlacementInterpretationVersion = 0;
    }

    private PlayerSessionReplay(String replay, String familyId, long seed,
            int rb56PlacementInterpretationVersion) {
        this.replay = replay;
        this.playerRequest = null;
        this.familyId = familyId;
        this.seed = seed;
        privateQualification = true;
        this.rb56PlacementInterpretationVersion = rb56PlacementInterpretationVersion;
    }

    /** Private exact RB56 restart identity; it deliberately assigns no profile. */
    static PlayerSessionReplay forQualification(GenerationRequest request) {
        if (request == null || !request.isPrivateDiagnosticQualification() ||
                request.isCandidateSearch() || !isHeldQualificationFamily(request.getFamilyId()))
            throw invalid("Private session identity requires the held exact qualification request");
        int placementVersion = request.getRb56PlacementInterpretationVersion();
        String identity;
        if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_LEGACY) {
            identity = PRIVATE_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + request.getFamilyId() + "/" +
                Long.toString(request.getDescriptor().getRootSeed());
        } else if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_COMPACT) {
            identity = PRIVATE_COMPACT_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + COMPACT_PLACEMENT_TOKEN + "/" + request.getFamilyId() + "/" +
                Long.toString(request.getDescriptor().getRootSeed());
        } else if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_REALISM) {
            identity = PRIVATE_REALISM_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + REALISM_PLACEMENT_TOKEN + "/" + request.getFamilyId() + "/" +
                Long.toString(request.getDescriptor().getRootSeed());
        } else if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_DENSE) {
            identity = PRIVATE_DENSE_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + DENSE_PLACEMENT_TOKEN + "/" + request.getFamilyId() + "/" +
                Long.toString(request.getDescriptor().getRootSeed());
        } else if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CURRENT) {
            identity = PRIVATE_CURRENT_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + CURRENT_PLACEMENT_TOKEN + "/" + request.getFamilyId() + "/" +
                Long.toString(request.getDescriptor().getRootSeed());
        } else {
            throw invalid("Private RB56 session has no supported placement interpretation");
        }
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
        int placementVersion;
        String familyId;
        String seedText;
        String canonical;
        if (fields.length == 5 && "tsj-private-session".equals(fields[0]) &&
                "1".equals(fields[1]) &&
                GenerationDependencyContext.INTERPRETATION_EPOCH.equals(fields[2]) &&
                isHeldQualificationFamily(fields[3])) {
            placementVersion = GenerationRequest.RB56_PLACEMENT_INTERPRETATION_LEGACY;
            familyId = fields[3];
            seedText = fields[4];
        } else if (fields.length == 6 && "tsj-private-session".equals(fields[0]) &&
                "2".equals(fields[1]) &&
                GenerationDependencyContext.INTERPRETATION_EPOCH.equals(fields[2]) &&
                COMPACT_PLACEMENT_TOKEN.equals(fields[3]) &&
                isHeldQualificationFamily(fields[4])) {
            placementVersion = GenerationRequest.RB56_PLACEMENT_INTERPRETATION_COMPACT;
            familyId = fields[4];
            seedText = fields[5];
        } else if (fields.length == 6 && "tsj-private-session".equals(fields[0]) &&
                "3".equals(fields[1]) &&
                GenerationDependencyContext.INTERPRETATION_EPOCH.equals(fields[2]) &&
                REALISM_PLACEMENT_TOKEN.equals(fields[3]) &&
                isHeldQualificationFamily(fields[4])) {
            placementVersion = GenerationRequest.RB56_PLACEMENT_INTERPRETATION_REALISM;
            familyId = fields[4];
            seedText = fields[5];
        } else if (fields.length == 6 && "tsj-private-session".equals(fields[0]) &&
                "4".equals(fields[1]) &&
                GenerationDependencyContext.INTERPRETATION_EPOCH.equals(fields[2]) &&
                DENSE_PLACEMENT_TOKEN.equals(fields[3]) &&
                isHeldQualificationFamily(fields[4])) {
            placementVersion = GenerationRequest.RB56_PLACEMENT_INTERPRETATION_DENSE;
            familyId = fields[4];
            seedText = fields[5];
        } else if (fields.length == 6 && "tsj-private-session".equals(fields[0]) &&
                "5".equals(fields[1]) &&
                GenerationDependencyContext.INTERPRETATION_EPOCH.equals(fields[2]) &&
                CURRENT_PLACEMENT_TOKEN.equals(fields[3]) &&
                isHeldQualificationFamily(fields[4])) {
            placementVersion = GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CURRENT;
            familyId = fields[4];
            seedText = fields[5];
        } else {
            throw invalid("Unsupported private replay identity, placement interpretation, or epoch");
        }
        long seed = parseSeed(seedText);
        if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_LEGACY) {
            canonical = PRIVATE_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + familyId + "/" + Long.toString(seed);
        } else if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_COMPACT) {
            canonical = PRIVATE_COMPACT_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + COMPACT_PLACEMENT_TOKEN + "/" + familyId + "/" + Long.toString(seed);
        } else if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_REALISM) {
            canonical = PRIVATE_REALISM_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + REALISM_PLACEMENT_TOKEN + "/" + familyId + "/" + Long.toString(seed);
        } else if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_DENSE) {
            canonical = PRIVATE_DENSE_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + DENSE_PLACEMENT_TOKEN + "/" + familyId + "/" + Long.toString(seed);
        } else {
            canonical = PRIVATE_CURRENT_PREFIX + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/" + CURRENT_PLACEMENT_TOKEN + "/" + familyId + "/" + Long.toString(seed);
        }
        if (!canonical.equals(text)) throw invalid("Use canonical private replay identity");
        return new PlayerSessionReplay(canonical, familyId, seed, placementVersion);
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
    int rb56PlacementInterpretationVersion() { return rb56PlacementInterpretationVersion; }

    PlayerLaunchRequest playerRequest() {
        if (privateQualification) throw invalid("Private identity has no public player request");
        return playerRequest;
    }

    boolean matches(GenerationRequest request) {
        return privateQualification && request != null &&
            request.isPrivateDiagnosticQualification() && !request.isCandidateSearch() &&
            familyId.equals(request.getFamilyId()) &&
            seed == request.getDescriptor().getRootSeed() &&
            rb56PlacementInterpretationVersion == request.getRb56PlacementInterpretationVersion();
    }

    boolean matches(GeneratedBoardInstance candidate) {
        TroubleshootBoard board = candidate == null ? null : candidate.getBoard();
        PcbPlacementConstraints constraints = board == null ? null : board.getPlacementConstraints();
        return privateQualification && candidate != null && board != null && constraints != null &&
            familyId.equals(candidate.getCircuitFamilyId()) && seed == candidate.getSeed() &&
            constraints.placementDensityVersion == rb56PlacementInterpretationVersion;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
