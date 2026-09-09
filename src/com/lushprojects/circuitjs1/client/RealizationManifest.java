package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable, data-only identity envelope for one resolved realization.
 *
 * <p>This value deliberately contains no runtime owner, solver object,
 * coordinate, random cursor, or mutable save state.  The descriptor remains
 * the authoritative challenge request; this envelope records the additional
 * choices needed to identify a resolved realization.</p>
 */
final class RealizationManifest {
    static final int VERSION = 1;
    static final int VERSION1 = VERSION;
    private static final String HEADER = "tsj-realization/1";
    private static final int MAX_ENCODING_LENGTH = 262144;
    private static final int MAX_FIELD_LENGTH = 32768;
    private static final int MAX_ID_LENGTH = 512;
    private static final int MAX_BLOCKS = 256;
    private static final int MAX_CHOICES = 1024;
    private static final int MAX_NETS = 4096;
    private static final int MAX_TARGETS = 8192;
    private static final int REQUIRED_PIN_COUNT = 8;

    private final int schemaVersion;
    private final ChallengeDescriptor descriptor;
    private final List<BlockRealizationIdentity> blocks;
    private final List<VersionPin> versions;
    private final List<Choice> choices;
    private final List<NetBinding> nets;
    private final List<String> targets;
    private final FutureStateContract futureState;
    private final ImportOrigin origin;
    private final String identityEncoding;
    private final String completeEncoding;

    RealizationManifest(int schemaVersion, ChallengeDescriptor descriptor,
            Collection<BlockRealizationIdentity> blocks,
            Collection<VersionPin> versions, Collection<Choice> choices,
            Collection<NetBinding> nets, Collection<String> targets,
            FutureStateContract futureState, ImportOrigin origin) {
        ChallengeContractException.positiveVersion(schemaVersion, "schemaVersion");
        if (schemaVersion != VERSION) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "schemaVersion", "Unsupported realization manifest schema");
        }
        this.schemaVersion = schemaVersion;
        this.descriptor = ChallengeContractException.required(descriptor,
                "descriptor");
        this.blocks = freezeBlocks(blocks);
        this.versions = freezeVersions(versions);
        this.choices = freezeChoices(choices);
        this.nets = freezeNets(nets);
        this.targets = freezeTargets(targets);
        validateNetTargets(this.nets, this.targets);
        this.futureState = futureState;
        this.origin = origin;
        this.identityEncoding = canonical(false, true);
        this.completeEncoding = origin == null ? identityEncoding : canonical(true, false);
    }

    int getSchemaVersion() { return schemaVersion; }
    ChallengeDescriptor getDescriptor() { return descriptor; }
    List<BlockRealizationIdentity> getBlocks() { return blocks; }
    List<VersionPin> getVersions() { return versions; }
    List<VersionPin> getVersionPins() { return versions; }
    List<Choice> getChoices() { return choices; }
    List<NetBinding> getNets() { return nets; }
    List<NetBinding> getNetBindings() { return nets; }
    List<String> getTargets() { return targets; }
    FutureStateContract getFutureState() { return futureState; }
    ImportOrigin getOrigin() { return origin; }
    ImportOrigin getImportOrigin() { return origin; }

    /** Canonical identity excluding source-origin ID and source hash. */
    String identityCanonical() {
        return identityEncoding;
    }

    /** Canonical complete manifest including informational provenance. */
    String toCanonical() {
        return completeEncoding;
    }

    @Override
    public String toString() {
        return toCanonical();
    }

    private String canonical(boolean includeOrigin,
            boolean identityProjection) {
        StringBuilder result = new StringBuilder(HEADER);
        result.append('\n');
        boolean first = true;
        first = appendRecord(result, "descriptor", descriptor.toCanonical(), first);
        for (BlockRealizationIdentity block : blocks)
            first = appendRecord(result, "block", block.toCanonical(), first);
        for (VersionPin pin : versions)
            first = appendRecord(result, "version", pin.toCanonical(), first);
        for (Choice choice : choices)
            first = appendRecord(result, "choice", choice.toCanonical(), first);
        for (NetBinding net : nets)
            first = appendRecord(result, "net", net.toCanonical(), first);
        for (String target : targets)
            first = appendRecord(result, "target", target, first);
        if (futureState != null)
            first = appendRecord(result, "future", futureState.toCanonical(), first);
        else
            first = appendRecord(result, "future", "~", first);
        if (includeOrigin && origin != null)
            first = appendRecord(result, "origin", origin.toCanonical(), first);
        else if (identityProjection && origin != null
                && origin.getInterpretation() != null)
            first = appendRecord(result, "origin",
                    origin.identityProjectionCanonical(), first);
        if (result.length() > MAX_ENCODING_LENGTH) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING,
                    "manifest", "Canonical manifest exceeds bound");
        }
        return result.toString();
    }

    private static boolean appendRecord(StringBuilder result, String key,
            String payload, boolean first) {
        requireAsciiPayload(payload, key);
        if (payload.length() > MAX_FIELD_LENGTH)
            throw invalid(key, "Field exceeds bounded length");
        if (!first) result.append('\n');
        result.append(key).append('=').append(payload.length()).append(':')
                .append(payload);
        return false;
    }

    private static List<BlockRealizationIdentity> freezeBlocks(
            Collection<BlockRealizationIdentity> source) {
        requireCollection(source, "blocks");
        if (source.isEmpty())
            throw missing("blocks", "At least one block identity is required");
        if (source.size() > MAX_BLOCKS)
            throw invalid("blocks", "Too many block identities");
        TreeMap<String, BlockRealizationIdentity> sorted =
                new TreeMap<String, BlockRealizationIdentity>();
        for (BlockRealizationIdentity block : source) {
            if (block == null)
                throw missing("blocks", "Block identity is required");
            String key = requireDurableId(block.getInstanceKey(),
                    "blocks.instanceKey");
            if (sorted.put(key, block) != null)
                throw duplicate("blocks.instanceKey", key);
        }
        return Collections.unmodifiableList(
                new ArrayList<BlockRealizationIdentity>(sorted.values()));
    }

    private static List<VersionPin> freezeVersions(
            Collection<VersionPin> source) {
        requireCollection(source, "versions");
        EnumMap<VersionPin.Concern, VersionPin> byConcern =
                new EnumMap<VersionPin.Concern, VersionPin>(
                        VersionPin.Concern.class);
        for (VersionPin pin : source) {
            if (pin == null)
                throw missing("versions", "Version pin is required");
            if (byConcern.put(pin.getConcern(), pin) != null)
                throw duplicate("versions.concern",
                        pin.getConcern().getToken());
        }
        if (byConcern.size() != REQUIRED_PIN_COUNT)
            throw missing("versions", "All eight version concerns are required");
        ArrayList<VersionPin> result = new ArrayList<VersionPin>();
        for (VersionPin.Concern concern : VersionPin.Concern.values())
            result.add(byConcern.get(concern));
        Collections.sort(result, new Comparator<VersionPin>() {
            @Override
            public int compare(VersionPin first, VersionPin second) {
                return first.getConcern().getToken().compareTo(
                        second.getConcern().getToken());
            }
        });
        return Collections.unmodifiableList(result);
    }

    private static List<Choice> freezeChoices(Collection<Choice> source) {
        requireCollection(source, "choices");
        if (source.size() > MAX_CHOICES)
            throw invalid("choices", "Too many resolved choices");
        TreeMap<String, Choice> sorted = new TreeMap<String, Choice>();
        for (Choice choice : source) {
            if (choice == null)
                throw missing("choices", "Choice is required");
            if (sorted.put(choice.getKey(), choice) != null)
                throw duplicate("choices.key", choice.getKey());
        }
        return Collections.unmodifiableList(
                new ArrayList<Choice>(sorted.values()));
    }

    private static List<NetBinding> freezeNets(Collection<NetBinding> source) {
        requireCollection(source, "nets");
        if (source.size() > MAX_NETS)
            throw invalid("nets", "Too many net bindings");
        TreeMap<String, NetBinding> sorted = new TreeMap<String, NetBinding>();
        for (NetBinding net : source) {
            if (net == null)
                throw missing("nets", "Net binding is required");
            if (sorted.put(net.getAliasId(), net) != null)
                throw duplicate("nets.aliasId", net.getAliasId());
        }
        return Collections.unmodifiableList(
                new ArrayList<NetBinding>(sorted.values()));
    }

    private static List<String> freezeTargets(Collection<String> source) {
        requireCollection(source, "targets");
        if (source.size() > MAX_TARGETS)
            throw invalid("targets", "Too many durable targets");
        TreeSet<String> sorted = new TreeSet<String>();
        for (String target : source) {
            String value = requireDurableId(target, "targets");
            if (value.startsWith("tsj-bus-v1/") && !isBusId(value))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ID, "targets",
                        "Malformed device bus ID");
            if (!sorted.add(value))
                throw duplicate("targets", value);
        }
        return Collections.unmodifiableList(new ArrayList<String>(sorted));
    }

    private static void validateNetTargets(List<NetBinding> nets,
            List<String> targets) {
        TreeSet<String> targetSet = new TreeSet<String>(targets);
        for (NetBinding net : nets) {
            if (!targetSet.contains(net.getAliasId()))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "targets", "Net alias is absent from durable target inventory: "
                                + net.getAliasId());
            String conductor = net.getConductorId();
            if (net.getAliasId().equals(conductor))
                continue;
            if (!isBusId(conductor))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ID,
                        "nets.conductorId",
                        "Non-bus conductor must equal its alias");
            if (!targetSet.contains(conductor))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "targets",
                        "Net bus conductor is absent from durable target inventory: "
                                + conductor);
        }
    }

    private static void requireCollection(Collection<?> source, String field) {
        if (source == null)
            throw missing(field, "Collection is required");
    }

    private static boolean isBusId(String value) {
        if (value == null || !value.startsWith("tsj-bus-v1/"))
            return false;
        String body = value.substring("tsj-bus-v1/".length());
        int slash = body.indexOf('/');
        if (slash <= 0 || slash == body.length() - 1
                || body.indexOf('/', slash + 1) >= 0)
            return false;
        String owner = body.substring(0, slash);
        String semanticKey = body.substring(slash + 1);
        int at = owner.indexOf('@');
        if (at <= 0 || at == owner.length() - 1
                || owner.indexOf('@', at + 1) >= 0)
            return false;
        String schemaId = owner.substring(0, at);
        String version = owner.substring(at + 1);
        if (!isStrictId(schemaId) || !isStrictId(semanticKey))
            return false;
        try {
            parsePositiveInt(version, "bus.version");
        } catch (ChallengeContractException ex) {
            return false;
        }
        return true;
    }

    private static boolean isStrictId(String value) {
        if (value == null || value.length() == 0 || value.length() > 128)
            return false;
        char first = value.charAt(0);
        if (!isAsciiLetter(first) && !isAsciiDigit(first) && first != '_')
            return false;
        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!isAsciiLetter(character) && !isAsciiDigit(character)
                    && character != '_' && character != '.' && character != '-')
                return false;
        }
        return true;
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z');
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static String requireDurableId(String value, String field) {
        if (value == null || value.length() == 0 || value.length() > MAX_ID_LENGTH)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ID, field,
                    "Durable ID is missing or exceeds the bound");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f || character < 0x21 || character == '|'
                    || character == ',' || character == '=') {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ID, field,
                        "Durable ID has unsupported characters");
            }
        }
        return value;
    }

    private static void requireAsciiPayload(String value, String field) {
        if (value == null)
            throw missing(field, "Payload is required");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f || character == '\r'
                    || (character < 0x20 && character != '\n'))
                throw invalid(field, "Payload is not bounded ASCII");
        }
    }

    static RealizationManifest parse(String value) {
        requireAsciiPayload(value, "manifest");
        if (value.length() > MAX_ENCODING_LENGTH)
            throw invalid("manifest", "Manifest exceeds bounded length");
        if (!value.startsWith(HEADER))
            parseUnsupportedHeader(value);
        int cursor = HEADER.length();
        if (cursor == value.length())
            throw missing("manifest", "Manifest fields are missing");
        if (value.charAt(cursor) != '\n')
            throw invalid("manifest", "Header is not followed by a field");
        cursor++;

        ChallengeDescriptor descriptor = null;
        ArrayList<BlockRealizationIdentity> blocks =
                new ArrayList<BlockRealizationIdentity>();
        ArrayList<VersionPin> versions = new ArrayList<VersionPin>();
        ArrayList<Choice> choices = new ArrayList<Choice>();
        ArrayList<NetBinding> nets = new ArrayList<NetBinding>();
        ArrayList<String> targets = new ArrayList<String>();
        FutureStateContract futureState = null;
        ImportOrigin origin = null;
        boolean futureSeen = false;
        boolean originSeen = false;
        int fieldCount = 0;
        while (cursor < value.length()) {
            int equals = value.indexOf('=', cursor);
            if (equals <= cursor)
                throw invalid("manifest", "Malformed field name");
            String field = value.substring(cursor, equals);
            if (!isField(field))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNKNOWN_FIELD,
                        field, "Unknown realization manifest field");
            int colon = value.indexOf(':', equals + 1);
            if (colon <= equals + 1)
                throw invalid(field, "Length frame is missing");
            int length = parseFrameLength(value.substring(equals + 1, colon),
                    field);
            int start = colon + 1;
            int end = start + length;
            if (end < start || end > value.length())
                throw invalid(field, "Length frame exceeds manifest");
            String payload = value.substring(start, end);
            requireAsciiPayload(payload, field);
            cursor = end;
            if (cursor < value.length()) {
                if (value.charAt(cursor) != '\n')
                    throw invalid(field, "Field is not line terminated");
                cursor++;
                if (cursor == value.length())
                    throw invalid("manifest", "Trailing line feed");
            }
            fieldCount++;
            if (fieldCount > MAX_BLOCKS + MAX_CHOICES + MAX_NETS
                    + MAX_TARGETS + 64)
                throw invalid("manifest", "Too many manifest fields");

            if ("descriptor".equals(field)) {
                if (descriptor != null)
                    throw duplicate("descriptor", "descriptor");
                descriptor = ChallengeDescriptor.parse(payload);
            } else if ("block".equals(field)) {
                if (blocks.size() >= MAX_BLOCKS)
                    throw invalid("blocks", "Too many block identities");
                blocks.add(BlockRealizationIdentity.parse(payload));
            } else if ("version".equals(field)) {
                if (versions.size() >= REQUIRED_PIN_COUNT)
                    throw invalid("versions", "Too many version pins");
                versions.add(VersionPin.parse(payload));
            } else if ("choice".equals(field)) {
                if (choices.size() >= MAX_CHOICES)
                    throw invalid("choices", "Too many choices");
                choices.add(Choice.parse(payload));
            } else if ("net".equals(field)) {
                if (nets.size() >= MAX_NETS)
                    throw invalid("nets", "Too many net bindings");
                nets.add(NetBinding.parse(payload));
            } else if ("target".equals(field)) {
                if (targets.size() >= MAX_TARGETS)
                    throw invalid("targets", "Too many durable targets");
                targets.add(requireDurableId(payload, "target"));
            } else if ("future".equals(field)) {
                if (futureSeen)
                    throw duplicate("future", "future");
                futureSeen = true;
                if (!"~".equals(payload))
                    futureState = FutureStateContract.parse(payload);
            } else if ("origin".equals(field)) {
                if (originSeen)
                    throw duplicate("origin", "origin");
                originSeen = true;
                if (!"~".equals(payload))
                    origin = ImportOrigin.parse(payload);
            }
        }
        if (descriptor == null)
            throw missing("descriptor", "Manifest descriptor is required");
        return new RealizationManifest(VERSION, descriptor, blocks, versions,
                choices, nets, targets, futureState, origin);
    }

    private static int parseFrameLength(String value, String field) {
        if (value == null || value.length() == 0 || value.length() > 6
                || (value.length() > 1 && value.charAt(0) == '0')) {
            throw invalid(field, "Frame length is not canonical");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9')
                throw invalid(field, "Frame length is not decimal");
        }
        int length;
        try {
            length = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, field,
                    "Frame length is outside the supported range");
        }
        if (length > MAX_FIELD_LENGTH)
            throw invalid(field, "Frame length exceeds bound");
        return length;
    }

    private static boolean isField(String field) {
        return "descriptor".equals(field) || "block".equals(field)
                || "version".equals(field) || "choice".equals(field)
                || "net".equals(field) || "target".equals(field)
                || "future".equals(field) || "origin".equals(field);
    }

    private static void parseUnsupportedHeader(String value) {
        String prefix = "tsj-realization/";
        if (value.startsWith(prefix)) {
            int end = value.indexOf('\n');
            String version = value.substring(prefix.length(),
                    end < 0 ? value.length() : end);
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "schemaVersion", "Unsupported realization schema " + version);
        }
        throw invalid("manifest", "Invalid realization manifest header");
    }

    private static ChallengeContractException invalid(String field,
            String detail) {
        return new ChallengeContractException(
                ChallengeContractException.Code.INVALID_ENCODING, field, detail);
    }

    private static ChallengeContractException missing(String field,
            String detail) {
        return new ChallengeContractException(
                ChallengeContractException.Code.MISSING_FIELD, field, detail);
    }

    private static ChallengeContractException duplicate(String field,
            String detail) {
        return new ChallengeContractException(
                ChallengeContractException.Code.DUPLICATE_DECLARATION, field,
                "Duplicate declaration " + detail);
    }

    private static String[] split(String value, char separator, String field,
            int expected) {
        if (value == null)
            throw missing(field, "Value is required");
        ArrayList<String> pieces = new ArrayList<String>();
        int start = 0;
        while (true) {
            int end = value.indexOf(separator, start);
            if (end < 0) {
                pieces.add(value.substring(start));
                break;
            }
            pieces.add(value.substring(start, end));
            start = end + 1;
        }
        if (pieces.size() != expected)
            throw invalid(field, "Wrong field count");
        return pieces.toArray(new String[pieces.size()]);
    }

    private static int parsePositiveInt(String value, String field) {
        if (value == null || value.length() == 0
                || (value.length() > 1 && value.charAt(0) == '0'))
            throw invalid(field, "Integer is not canonical");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9')
                throw invalid(field, "Integer is not canonical");
        }
        int result;
        try {
            result = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, field,
                    "Integer is outside range");
        }
        ChallengeContractException.positiveVersion(result, field);
        return result;
    }

    private static long parseLong(String value, String field) {
        if (value == null || value.length() == 0 || value.charAt(0) == '+')
            throw invalid(field, "Long is not canonical");
        boolean negative = value.charAt(0) == '-';
        int start = negative ? 1 : 0;
        if (start == value.length()
                || (value.length() - start > 1 && value.charAt(start) == '0'))
            throw invalid(field, "Long is not canonical");
        for (int index = start; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9')
                throw invalid(field, "Long is not canonical");
        }
        long result;
        try {
            result = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, field,
                    "Long is outside signed 64-bit range");
        }
        if (negative && result == 0L)
            throw invalid(field, "Negative zero is not canonical");
        return result;
    }

    private static String requireAtom(String value, String field) {
        if (value == null || value.length() == 0 || value.length() > MAX_ID_LENGTH)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ID, field,
                    "ASCII atom is missing or exceeds bound");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f || character < 0x21 || character == '|'
                    || character == ',' || character == '\r'
                    || character == '\n')
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ID, field,
                        "ASCII atom contains unsupported characters");
        }
        return value;
    }

    /** One closed version pin owned by a named realization concern. */
    static final class VersionPin {
        enum Concern {
            LAYOUT("layout"),
            ROUTING("routing"),
            VALUES("values"),
            MODELS("models"),
            PACKAGES("packages"),
            GEOMETRY("geometry"),
            DIAGNOSTIC("diagnostic"),
            NAMED_STREAMS("named-streams");

            private final String token;
            Concern(String token) { this.token = token; }
            String getToken() { return token; }

            static Concern parse(String value) {
                for (Concern concern : values())
                    if (concern.token.equals(value)) return concern;
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_ID,
                        "version.concern", "Unknown version concern " + value);
            }
        }

        private final Concern concern;
        private final ChallengeDescriptor.VersionedId owner;

        VersionPin(Concern concern, ChallengeDescriptor.VersionedId owner) {
            this.concern = ChallengeContractException.required(concern,
                    "version.concern");
            this.owner = ChallengeContractException.required(owner,
                    "version.owner");
            validateSupported(this.concern, this.owner);
        }

        Concern getConcern() { return concern; }
        ChallengeDescriptor.VersionedId getOwner() { return owner; }
        ChallengeDescriptor.VersionedId getOwnerVersion() { return owner; }
        String getOwnerId() { return owner.getId(); }
        int getOwnerVersionNumber() { return owner.getVersion(); }

        String toCanonical() {
            return concern.getToken() + "|" + owner.toCanonical();
        }

        static VersionPin parse(String value) {
            String[] fields = split(value, '|', "version", 2);
            return new VersionPin(Concern.parse(fields[0]),
                    ChallengeDescriptor.VersionedId.parse(fields[1],
                            "version.owner"));
        }

        private static void validateSupported(Concern concern,
                ChallengeDescriptor.VersionedId owner) {
            String id = owner.getId();
            int version = owner.getVersion();
            boolean knownId = false;
            boolean knownVersion = false;
            switch (concern) {
            case LAYOUT:
                knownId = "pcb-layout".equals(id);
                knownVersion = version == 3;
                break;
            case GEOMETRY:
                knownId = "pcb-geometry".equals(id);
                knownVersion = version == 3;
                break;
            case DIAGNOSTIC:
                knownId = "generated-diagnostic-solvability".equals(id);
                knownVersion = version == 1;
                break;
            case NAMED_STREAMS:
                knownId = "named-random-streams".equals(id);
                knownVersion = version == 1;
                break;
            case ROUTING:
            case MODELS:
            case PACKAGES:
                knownId = "bounded-assembler".equals(id);
                knownVersion = version >= 1 && version <= 3;
                break;
            case VALUES:
                knownId = "bounded-assembler".equals(id)
                        || "controlled-led-load-e12".equals(id);
                knownVersion = "controlled-led-load-e12".equals(id)
                        ? version == 1 : version >= 1 && version <= 2;
                break;
            default:
                break;
            }
            if (!knownId)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_ID,
                        "version.owner", "Unsupported owner " + id);
            if (!knownVersion)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_VERSION,
                        "version.owner", "Unsupported owner version " + version);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VersionPin)) return false;
            VersionPin that = (VersionPin) other;
            return concern == that.concern && owner.equals(that.owner);
        }

        @Override
        public int hashCode() {
            return 31 * concern.hashCode() + owner.hashCode();
        }

        @Override
        public String toString() { return toCanonical(); }
    }

    /** A typed, resolved choice with a bounded canonical value. */
    static final class Choice {
        enum Kind {
            TOKEN("token"),
            NUMBER("number"),
            INTEGER("integer"),
            IDS("ids"),
            VERSION("version");

            private final String token;
            Kind(String token) { this.token = token; }
            String getToken() { return token; }

            static Kind parse(String value) {
                for (Kind kind : values())
                    if (kind.token.equals(value)) return kind;
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_ID,
                        "choice.kind", "Unknown choice kind " + value);
            }
        }

        private final String key;
        private final Kind kind;
        private final String tokenValue;
        private final double numberValue;
        private final long integerValue;
        private final List<String> idsValue;
        private final ChallengeDescriptor.VersionedId versionValue;

        private Choice(String key, Kind kind, String tokenValue,
                double numberValue, long integerValue, List<String> idsValue,
                ChallengeDescriptor.VersionedId versionValue) {
            this.key = requireAtom(key, "choice.key");
            this.kind = ChallengeContractException.required(kind, "choice.kind");
            this.tokenValue = tokenValue;
            this.numberValue = numberValue;
            this.integerValue = integerValue;
            this.idsValue = idsValue == null ? null
                    : Collections.unmodifiableList(
                            new ArrayList<String>(idsValue));
            this.versionValue = versionValue;
            if (kind == Kind.TOKEN) requireAtom(tokenValue, "choice.value");
            if (kind == Kind.NUMBER) validateNumber(numberValue, "choice.value");
            if (kind == Kind.IDS && this.idsValue == null)
                throw missing("choice.value", "ID set is required");
            if (kind == Kind.VERSION)
                ChallengeContractException.required(versionValue,
                        "choice.value");
        }

        static Choice token(String key, String value) {
            return new Choice(key, Kind.TOKEN, requireAtom(value, "choice.value"),
                    0.0, 0L, null, null);
        }

        static Choice number(String key, double value) {
            validateNumber(value, "choice.value");
            return new Choice(key, Kind.NUMBER, null, value, 0L, null, null);
        }

        static Choice integer(String key, long value) {
            return new Choice(key, Kind.INTEGER, null, 0.0, value, null, null);
        }

        static Choice ids(String key, Collection<String> values) {
            if (values == null || values.isEmpty())
                throw new ChallengeContractException(
                        ChallengeContractException.Code.EMPTY_CANDIDATES,
                        "choice.value", "ID set must not be empty");
            TreeSet<String> sorted = new TreeSet<String>();
            for (String value : values) {
                String atom = requireAtom(value, "choice.value");
                if (!sorted.add(atom))
                    throw duplicate("choice.value", atom);
            }
            return new Choice(key, Kind.IDS, null, 0.0, 0L,
                    new ArrayList<String>(sorted), null);
        }

        static Choice version(String key,
                ChallengeDescriptor.VersionedId value) {
            return new Choice(key, Kind.VERSION, null, 0.0, 0L, null,
                    ChallengeContractException.required(value, "choice.value"));
        }

        String getKey() { return key; }
        Kind getKind() { return kind; }
        String getTokenValue() { requireKind(Kind.TOKEN); return tokenValue; }
        double getNumberValue() { requireKind(Kind.NUMBER); return numberValue; }
        long getIntegerValue() { requireKind(Kind.INTEGER); return integerValue; }
        List<String> getIds() { requireKind(Kind.IDS); return idsValue; }
        ChallengeDescriptor.VersionedId getVersionValue() {
            requireKind(Kind.VERSION); return versionValue;
        }
        String getCanonicalValue() {
            switch (kind) {
            case TOKEN: return tokenValue;
            case NUMBER: return hexBits(numberValue);
            case INTEGER: return Long.toString(integerValue);
            case IDS: return joinIds(idsValue);
            case VERSION: return versionValue.toCanonical();
            default: throw new IllegalStateException("Unknown choice kind");
            }
        }

        String toCanonical() {
            return key + "|" + kind.getToken() + "|" + getCanonicalValue();
        }

        static Choice parse(String value) {
            String[] fields = split(value, '|', "choice", 3);
            String key = requireAtom(fields[0], "choice.key");
            Kind kind = Kind.parse(fields[1]);
            switch (kind) {
            case TOKEN:
                return token(key, fields[2]);
            case NUMBER:
                return number(key, parseHexBits(fields[2], "choice.value"));
            case INTEGER:
                return integer(key, parseLong(fields[2], "choice.value"));
            case IDS:
                return ids(key, parseIds(fields[2]));
            case VERSION:
                return version(key, ChallengeDescriptor.VersionedId.parse(
                        fields[2], "choice.value"));
            default:
                throw invalid("choice", "Unknown kind");
            }
        }

        private void requireKind(Kind expected) {
            if (kind != expected)
                throw new IllegalStateException("Choice kind is " + kind
                        + ", not " + expected);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Choice)) return false;
            Choice that = (Choice) other;
            return key.equals(that.key) && kind == that.kind
                    && getCanonicalValue().equals(that.getCanonicalValue());
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + 17 * kind.hashCode()
                    + getCanonicalValue().hashCode();
        }

        @Override
        public String toString() { return toCanonical(); }
    }

    /** One durable local-net alias and its explicitly owned conductor. */
    static final class NetBinding {
        private final String aliasId;
        private final String conductorId;

        NetBinding(String aliasId, String conductorId) {
            this.aliasId = requireDurableId(aliasId, "net.aliasId");
            this.conductorId = requireDurableId(conductorId,
                    "net.conductorId");
            if (!this.aliasId.equals(this.conductorId)
                    && !isBusId(this.conductorId))
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ID,
                        "net.conductorId",
                        "Conductor must be a tsj-bus-v1 ID or the alias itself");
        }

        String getAliasId() { return aliasId; }
        String getConductorId() { return conductorId; }

        String toCanonical() { return aliasId + "|" + conductorId; }

        static NetBinding parse(String value) {
            String[] fields = split(value, '|', "net", 2);
            return new NetBinding(fields[0], fields[1]);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NetBinding)) return false;
            NetBinding that = (NetBinding) other;
            return aliasId.equals(that.aliasId)
                    && conductorId.equals(that.conductorId);
        }

        @Override
        public int hashCode() {
            return 31 * aliasId.hashCode() + conductorId.hashCode();
        }

        @Override
        public String toString() { return toCanonical(); }
    }

    /** Data-only reservation for later sequential/model providers. */
    static final class FutureStateContract {
        static final int VERSION = 1;
        static final int VERSION1 = VERSION;
        private final int schemaVersion;
        private final ChallengeDescriptor.VersionedId model;
        private final ChallengeDescriptor.VersionedId stateSchema;
        private final ChallengeDescriptor.VersionedId program;
        private final ChallengeDescriptor.VersionedId provider;

        FutureStateContract(int schemaVersion,
                ChallengeDescriptor.VersionedId model,
                ChallengeDescriptor.VersionedId stateSchema,
                ChallengeDescriptor.VersionedId program,
                ChallengeDescriptor.VersionedId provider) {
            ChallengeContractException.positiveVersion(schemaVersion,
                    "future.schemaVersion");
            if (schemaVersion != VERSION)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_VERSION,
                        "future.schemaVersion", "Unsupported future state schema");
            this.schemaVersion = schemaVersion;
            this.model = ChallengeContractException.required(model,
                    "future.model");
            this.stateSchema = ChallengeContractException.required(stateSchema,
                    "future.stateSchema");
            this.program = program;
            this.provider = provider;
        }

        int getSchemaVersion() { return schemaVersion; }
        ChallengeDescriptor.VersionedId getModel() { return model; }
        ChallengeDescriptor.VersionedId getStateSchema() { return stateSchema; }
        ChallengeDescriptor.VersionedId getProgram() { return program; }
        ChallengeDescriptor.VersionedId getProvider() { return provider; }

        String toCanonical() {
            return Integer.toString(schemaVersion) + "|" + model.toCanonical()
                    + "|" + stateSchema.toCanonical() + "|"
                    + optionalVersion(program) + "|" + optionalVersion(provider);
        }

        static FutureStateContract parse(String value) {
            String[] fields = split(value, '|', "future", 5);
            int version = parsePositiveInt(fields[0], "future.schemaVersion");
            return new FutureStateContract(version,
                    ChallengeDescriptor.VersionedId.parse(fields[1],
                            "future.model"),
                    ChallengeDescriptor.VersionedId.parse(fields[2],
                            "future.stateSchema"),
                    parseOptionalVersion(fields[3], "future.program"),
                    parseOptionalVersion(fields[4], "future.provider"));
        }

        private static String optionalVersion(
                ChallengeDescriptor.VersionedId value) {
            return value == null ? "~" : value.toCanonical();
        }

        private static ChallengeDescriptor.VersionedId parseOptionalVersion(
                String value, String field) {
            return "~".equals(value) ? null
                    : ChallengeDescriptor.VersionedId.parse(value, field);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FutureStateContract)) return false;
            FutureStateContract that = (FutureStateContract) other;
            return schemaVersion == that.schemaVersion && model.equals(that.model)
                    && stateSchema.equals(that.stateSchema)
                    && equal(program, that.program) && equal(provider, that.provider);
        }

        @Override
        public int hashCode() {
            int result = 31 * schemaVersion + model.hashCode();
            result = 31 * result + stateSchema.hashCode();
            result = 31 * result + (program == null ? 0 : program.hashCode());
            return 31 * result + (provider == null ? 0 : provider.hashCode());
        }

        private static boolean equal(Object first, Object second) {
            return first == null ? second == null : first.equals(second);
        }
    }

    /** Informational import provenance, with an optional identity-bearing interpretation. */
    static final class ImportOrigin {
        static final int VERSION = 1;
        static final int VERSION1 = VERSION;
        private final int schemaVersion;
        private final String originId;
        private final String sourceSha256;
        private final ChallengeDescriptor.VersionedId interpretation;

        ImportOrigin(int schemaVersion, String originId, String sourceSha256,
                ChallengeDescriptor.VersionedId interpretation) {
            ChallengeContractException.positiveVersion(schemaVersion,
                    "origin.schemaVersion");
            if (schemaVersion != VERSION)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_VERSION,
                        "origin.schemaVersion", "Unsupported import origin schema");
            this.schemaVersion = schemaVersion;
            this.originId = ChallengeContractException.id(originId,
                    "origin.originId");
            this.sourceSha256 = validateHash(sourceSha256);
            this.interpretation = interpretation;
        }

        int getSchemaVersion() { return schemaVersion; }
        String getOriginId() { return originId; }
        String getSourceSha256() { return sourceSha256; }
        String getSourceHash() { return sourceSha256; }
        ChallengeDescriptor.VersionedId getInterpretation() {
            return interpretation;
        }

        String toCanonical() {
            return Integer.toString(schemaVersion) + "|" + originId + "|"
                    + (sourceSha256 == null ? "~" : sourceSha256) + "|"
                    + optionalVersion(interpretation);
        }

        private String identityProjectionCanonical() {
            return Integer.toString(schemaVersion) + "|~|~|"
                    + optionalVersion(interpretation);
        }

        static ImportOrigin parse(String value) {
            String[] fields = split(value, '|', "origin", 4);
            int version = parsePositiveInt(fields[0], "origin.schemaVersion");
            if ("~".equals(fields[1]))
                throw invalid("origin.originId", "Origin ID is required");
            String hash = "~".equals(fields[2]) ? null
                    : validateHash(fields[2]);
            return new ImportOrigin(version, fields[1], hash,
                    parseOptionalVersion(fields[3], "origin.interpretation"));
        }

        private static String optionalVersion(
                ChallengeDescriptor.VersionedId value) {
            return value == null ? "~" : value.toCanonical();
        }

        private static ChallengeDescriptor.VersionedId parseOptionalVersion(
                String value, String field) {
            return "~".equals(value) ? null
                    : ChallengeDescriptor.VersionedId.parse(value, field);
        }

        private static String validateHash(String value) {
            if (value == null) return null;
            if (value.length() != 64)
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        "origin.sourceSha256", "SHA-256 must be 64 lowercase hex characters");
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (!((character >= '0' && character <= '9')
                        || (character >= 'a' && character <= 'f')))
                    throw new ChallengeContractException(
                            ChallengeContractException.Code.INVALID_ENCODING,
                            "origin.sourceSha256", "SHA-256 must be lowercase hex");
            }
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ImportOrigin)) return false;
            ImportOrigin that = (ImportOrigin) other;
            return schemaVersion == that.schemaVersion
                    && originId.equals(that.originId)
                    && equal(sourceSha256, that.sourceSha256)
                    && equal(interpretation, that.interpretation);
        }

        @Override
        public int hashCode() {
            int result = 31 * schemaVersion + originId.hashCode();
            result = 31 * result + (sourceSha256 == null ? 0 : sourceSha256.hashCode());
            return 31 * result + (interpretation == null ? 0 : interpretation.hashCode());
        }

        private static boolean equal(Object first, Object second) {
            return first == null ? second == null : first.equals(second);
        }
    }

    private static void validateNumber(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)
                || (value == 0.0 && Double.doubleToLongBits(value) < 0L))
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, field,
                    "Number must be finite and must not be negative zero");
    }

    private static String hexBits(double value) {
        validateNumber(value, "choice.value");
        long bits = Double.doubleToLongBits(value);
        char[] result = new char[16];
        for (int index = 15; index >= 0; index--) {
            int nibble = (int) (bits & 0x0fL);
            result[index] = "0123456789abcdef".charAt(nibble);
            bits >>>= 4;
        }
        return new String(result);
    }

    private static double parseHexBits(String value, String field) {
        if (value == null || value.length() != 16)
            throw invalid(field, "Binary64 value must be 16 lowercase hex digits");
        long bits = 0L;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            int digit;
            if (character >= '0' && character <= '9')
                digit = character - '0';
            else if (character >= 'a' && character <= 'f')
                digit = character - 'a' + 10;
            else
                throw invalid(field, "Binary64 value is not lowercase hex");
            bits = (bits << 4) | digit;
        }
        double result = Double.longBitsToDouble(bits);
        validateNumber(result, field);
        if (Double.doubleToLongBits(result) != bits)
            throw invalid(field, "Binary64 value is not canonical");
        return result;
    }

    private static String joinIds(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index != 0) result.append(',');
            result.append(values.get(index));
        }
        return result.toString();
    }

    private static List<String> parseIds(String value) {
        if (value == null || value.length() == 0)
            throw new ChallengeContractException(
                    ChallengeContractException.Code.EMPTY_CANDIDATES,
                    "choice.value", "ID set is empty");
        String[] pieces = split(value, ',', "choice.value",
                countSeparators(value, ',') + 1);
        TreeSet<String> sorted = new TreeSet<String>();
        for (String piece : pieces) {
            String atom = requireAtom(piece, "choice.value");
            if (!sorted.add(atom))
                throw duplicate("choice.value", atom);
        }
        ArrayList<String> result = new ArrayList<String>(sorted);
        return result;
    }

    private static int countSeparators(String value, char separator) {
        int result = 0;
        for (int index = 0; index < value.length(); index++)
            if (value.charAt(index) == separator) result++;
        return result;
    }

    private static String optionalVersion(
            ChallengeDescriptor.VersionedId value) {
        return value == null ? "~" : value.toCanonical();
    }

    private static void parseUnsupportedVersionedId(String value, String field) {
        throw new ChallengeContractException(
                ChallengeContractException.Code.UNSUPPORTED_ID, field,
                "Unsupported versioned identity " + value);
    }
}
