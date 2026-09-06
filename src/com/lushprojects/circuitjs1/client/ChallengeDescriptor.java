package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, versioned identity for one replayable challenge request.
 *
 * <p>This value contains no runtime owner, solver object, or random cursor.
 * It describes what an adapter must resolve.  In particular, a syntactically
 * valid but unsupported identity is retained here so the adapter can reject
 * it before generation.</p>
 */
final class ChallengeDescriptor {
    static final int SCHEMA_VERSION = 1;
    static final String LEGACY_GENERATOR_ID = "legacy-leaf";
    static final int LEGACY_GENERATOR_VERSION = 1;
    static final String LEGACY_DIFFICULTY_ID = "legacy-default";
    static final int LEGACY_DIFFICULTY_VERSION = 1;
    static final int LEGACY_INTENT_VERSION = 1;
    static final int LEGACY_GEOMETRY_VERSION = 3;

    private static final int MAX_ENCODING_LENGTH = 8192;
    private static final String HEADER = "tsj-challenge/1";
    private static final String[] FIELD_ORDER = {
        "constraints",
        "device-intent",
        "difficulty-profile",
        "generator",
        "geometry",
        "root-seed"
    };

    /** Immutable identifier paired with its algorithm/schema version. */
    static final class VersionedId {
        private final String id;
        private final int version;

        VersionedId(String id, int version) {
            this.id = ChallengeContractException.id(id, "id");
            ChallengeContractException.positiveVersion(version, "version");
            this.version = version;
        }

        String getId() {
            return id;
        }

        int getVersion() {
            return version;
        }

        String toCanonical() {
            return id + "@" + Integer.toString(version);
        }

        @Override
        public String toString() {
            return toCanonical();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof VersionedId)) {
                return false;
            }
            VersionedId that = (VersionedId) other;
            return version == that.version && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return 31 * id.hashCode() + version;
        }

        static VersionedId parse(String value, String fieldId) {
            if (value == null || value.length() == 0) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        fieldId, "Versioned ID is required");
            }
            int at = value.indexOf('@');
            if (at <= 0 || at != value.lastIndexOf('@')
                    || at == value.length() - 1) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        fieldId, "Versioned ID must be id@positiveVersion");
            }
            String id = value.substring(0, at);
            String versionText = value.substring(at + 1);
            int version = parsePositiveInt(versionText, fieldId);
            try {
                return new VersionedId(id, version);
            } catch (ChallengeContractException ex) {
                if (ex.getCode() == ChallengeContractException.Code.INVALID_ID) {
                    throw new ChallengeContractException(ex.getCode(), fieldId,
                            "Invalid versioned ID");
                }
                throw ex;
            }
        }

        static VersionedId parse(String value) {
            return parse(value, "id");
        }
    }

    private final int schemaVersion;
    private final long rootSeed;
    private final VersionedId generator;
    private final VersionedId deviceIntent;
    private final VersionedId difficultyProfile;
    private final PcbGeometryContractVersion geometryVersion;
    private final GenerationConstraints constraints;

    ChallengeDescriptor(int schemaVersion, long rootSeed,
            VersionedId generator, VersionedId deviceIntent,
            VersionedId difficultyProfile,
            PcbGeometryContractVersion geometryVersion,
            GenerationConstraints constraints) {
        ChallengeContractException.positiveVersion(schemaVersion,
                "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "schemaVersion", "Unsupported challenge schema version "
                            + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        this.rootSeed = rootSeed;
        this.generator = ChallengeContractException.required(generator,
                "generator");
        this.deviceIntent = ChallengeContractException.required(deviceIntent,
                "deviceIntent");
        this.difficultyProfile = ChallengeContractException.required(
                difficultyProfile, "difficultyProfile");
        this.geometryVersion = ChallengeContractException.required(
                geometryVersion, "geometry");
        this.constraints = ChallengeContractException.required(constraints,
                "constraints");
    }

    static ChallengeDescriptor legacy(String familyId, long seed) {
        return new ChallengeDescriptor(
                SCHEMA_VERSION,
                seed,
                new VersionedId(LEGACY_GENERATOR_ID,
                        LEGACY_GENERATOR_VERSION),
                new VersionedId(familyId, LEGACY_INTENT_VERSION),
                new VersionedId(LEGACY_DIFFICULTY_ID,
                        LEGACY_DIFFICULTY_VERSION),
                new PcbGeometryContractVersion(LEGACY_GEOMETRY_VERSION),
                GenerationConstraints.unspecified());
    }

    int getSchemaVersion() {
        return schemaVersion;
    }

    long getRootSeed() {
        return rootSeed;
    }

    VersionedId getGenerator() {
        return generator;
    }

    VersionedId getDeviceIntent() {
        return deviceIntent;
    }

    VersionedId getDifficultyProfile() {
        return difficultyProfile;
    }

    PcbGeometryContractVersion getGeometryVersion() {
        return geometryVersion;
    }

    GenerationConstraints getConstraints() {
        return constraints;
    }

    String toCanonical() {
        StringBuilder result = new StringBuilder();
        result.append(HEADER);
        result.append('\n').append("constraints=")
                .append(constraints.toCanonical());
        result.append('\n').append("device-intent=")
                .append(deviceIntent.toCanonical());
        result.append('\n').append("difficulty-profile=")
                .append(difficultyProfile.toCanonical());
        result.append('\n').append("generator=")
                .append(generator.toCanonical());
        result.append('\n').append("geometry=")
                .append(Integer.toString(geometryVersion.getValue()));
        result.append('\n').append("root-seed=")
                .append(Long.toString(rootSeed));
        if (result.length() > MAX_ENCODING_LENGTH) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING,
                    "descriptor", "Canonical descriptor exceeds 8192 characters");
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return toCanonical();
    }

    static ChallengeDescriptor parse(String value) {
        requireAscii(value, "descriptor", MAX_ENCODING_LENGTH);
        if (value.length() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING,
                    "descriptor", "Descriptor is empty");
        }

        int firstNewline = value.indexOf('\n');
        if (firstNewline < 0) {
            parseHeader(value);
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD,
                    "descriptor", "Descriptor fields are missing");
        }
        String header = value.substring(0, firstNewline);
        parseHeader(header);

        Map<String, String> fields = new HashMap<String, String>();
        int start = firstNewline + 1;
        if (start >= value.length()) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD,
                    "descriptor", "Descriptor fields are missing");
        }
        while (start <= value.length()) {
            int end = value.indexOf('\n', start);
            if (end < 0) {
                end = value.length();
            }
            if (end == start) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        "descriptor", "Blank descriptor field");
            }
            String line = value.substring(start, end);
            int equals = line.indexOf('=');
            if (equals <= 0 || equals == line.length() - 1) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        "descriptor", "Each field must be key=value");
            }
            String key = line.substring(0, equals);
            String fieldValue = line.substring(equals + 1);
            if (!isKnownField(key)) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNKNOWN_FIELD, key,
                        "Unknown descriptor field");
            }
            if (fields.put(key, fieldValue) != null) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.DUPLICATE_DECLARATION,
                        key, "Duplicate descriptor field");
            }
            if (end == value.length()) {
                break;
            }
            start = end + 1;
            if (start == value.length()) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        "descriptor", "Descriptor must not have trailing LF");
            }
        }

        for (String field : FIELD_ORDER) {
            if (!fields.containsKey(field)) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        field, "Required descriptor field is missing");
            }
        }

        VersionedId generator = VersionedId.parse(fields.get("generator"),
                "generator");
        VersionedId intent = VersionedId.parse(fields.get("device-intent"),
                "device-intent");
        VersionedId profile = VersionedId.parse(
                fields.get("difficulty-profile"), "difficulty-profile");
        int geometry = parsePositiveInt(fields.get("geometry"), "geometry");
        long seed = parseLong(fields.get("root-seed"), "root-seed");
        return new ChallengeDescriptor(
                SCHEMA_VERSION, seed, generator, intent, profile,
                new PcbGeometryContractVersion(geometry),
                GenerationConstraints.parse(fields.get("constraints")));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChallengeDescriptor)) {
            return false;
        }
        ChallengeDescriptor that = (ChallengeDescriptor) other;
        return schemaVersion == that.schemaVersion && rootSeed == that.rootSeed
                && generator.equals(that.generator)
                && deviceIntent.equals(that.deviceIntent)
                && difficultyProfile.equals(that.difficultyProfile)
                && geometryVersion.equals(that.geometryVersion)
                && constraints.equals(that.constraints);
    }

    @Override
    public int hashCode() {
        int result = schemaVersion;
        result = 31 * result + (int) (rootSeed ^ (rootSeed >>> 32));
        result = 31 * result + generator.hashCode();
        result = 31 * result + deviceIntent.hashCode();
        result = 31 * result + difficultyProfile.hashCode();
        result = 31 * result + geometryVersion.hashCode();
        result = 31 * result + constraints.hashCode();
        return result;
    }

    private static boolean isKnownField(String field) {
        for (String known : FIELD_ORDER) {
            if (known.equals(field)) {
                return true;
            }
        }
        return false;
    }

    private static void parseHeader(String header) {
        if (HEADER.equals(header)) {
            return;
        }
        String prefix = "tsj-challenge/";
        if (header.startsWith(prefix)) {
            String versionText = header.substring(prefix.length());
            if (versionText.length() == 0) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        "schemaVersion", "Challenge schema version is required");
            }
            int version = parsePositiveInt(versionText, "schemaVersion");
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "schemaVersion", "Unsupported challenge schema version "
                            + version);
        }
        throw new ChallengeContractException(
                ChallengeContractException.Code.INVALID_ENCODING, "descriptor",
                "Invalid challenge descriptor header");
    }

    private static int parsePositiveInt(String text, String fieldId) {
        if (text == null || text.length() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Positive decimal integer is required");
        }
        if (text.charAt(0) == '+' || text.charAt(0) == '-') {
            if (text.charAt(0) == '-') {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_VERSION, fieldId,
                        "Version must be positive");
            }
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Leading plus is not canonical");
        }
        if (!allDigits(text) || (text.length() > 1 && text.charAt(0) == '0')) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Integer is not canonical decimal");
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, fieldId,
                    "Integer is outside the signed 32-bit range");
        }
        ChallengeContractException.positiveVersion(value, fieldId);
        return value;
    }

    private static long parseLong(String text, String fieldId) {
        if (text == null || text.length() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Signed decimal integer is required");
        }
        char first = text.charAt(0);
        if (first == '+') {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Leading plus is not canonical");
        }
        boolean negative = first == '-';
        int digitStart = negative ? 1 : 0;
        if (digitStart == text.length() || !allDigits(text, digitStart)
                || (text.length() - digitStart > 1
                    && text.charAt(digitStart) == '0')) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Seed is not canonical decimal");
        }
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, fieldId,
                    "Seed is outside the signed 64-bit range");
        }
        if (value == 0L && negative) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Negative zero is not canonical");
        }
        return value;
    }

    private static boolean allDigits(String value) {
        return allDigits(value, 0);
    }

    private static boolean allDigits(String value, int start) {
        for (int index = start; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static void requireAscii(String value, String fieldId, int maximum) {
        if (value == null) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD, fieldId,
                    "Required encoding is missing");
        }
        if (value.length() > maximum) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Encoding exceeds " + maximum + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f || character == '\r') {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                        "Encoding must be ASCII LF-delimited text");
            }
        }
    }
}
