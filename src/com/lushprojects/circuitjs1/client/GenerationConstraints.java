package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Immutable, versioned requests for later challenge admission.
 *
 * <p>These values describe requested dimensions only.  They do not measure a
 * generated board, select a difficulty preset, or admit a challenge.</p>
 */
final class GenerationConstraints {
    static final int VERSION = 1;
    static final int VERSION1 = VERSION;
    private static final int MAX_ENCODING_LENGTH = 4096;
    private static final String HEADER = "tsj-constraints/1";

    private static final String[] CANONICAL_KEYS = {
        "blocks",
        "components",
        "diagnostic-depth",
        "domains",
        "input-transitions",
        "instruments",
        "isolation-actions",
        "parallel-ambiguity",
        "plausible-owners",
        "purposeful-auxiliaries",
        "temporal-evidence",
        "temporal-samples"
    };

    private static final CountMetric[] METRICS_BY_CANONICAL_KEY = {
        CountMetric.BLOCKS,
        CountMetric.COMPONENTS,
        CountMetric.DIAGNOSTIC_DEPTH,
        CountMetric.DOMAINS,
        CountMetric.INPUT_TRANSITIONS,
        null,
        CountMetric.ISOLATION_ACTIONS,
        null,
        CountMetric.PLAUSIBLE_OWNERS,
        CountMetric.PURPOSEFUL_AUXILIARIES,
        null,
        CountMetric.TEMPORAL_SAMPLES
    };

    private final int version;
    private final Map<CountMetric, CountRange> counts;
    private final Requirement parallelAmbiguity;
    private final Requirement temporalEvidence;
    private final AllowedInstruments allowedInstruments;

    enum CountMetric {
        BLOCKS("blocks"),
        COMPONENTS("components"),
        DOMAINS("domains"),
        PLAUSIBLE_OWNERS("plausible-owners"),
        DIAGNOSTIC_DEPTH("diagnostic-depth"),
        INPUT_TRANSITIONS("input-transitions"),
        ISOLATION_ACTIONS("isolation-actions"),
        TEMPORAL_SAMPLES("temporal-samples"),
        PURPOSEFUL_AUXILIARIES("purposeful-auxiliaries");

        private final String token;

        CountMetric(String token) {
            this.token = token;
        }

        String getToken() {
            return token;
        }

        static CountMetric fromToken(String token) {
            for (CountMetric metric : values()) {
                if (metric.token.equals(token)) {
                    return metric;
                }
            }
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_CONSTRAINT,
                    "metric", "Unsupported count metric " + token);
        }
    }

    enum Requirement {
        UNSPECIFIED("~"),
        REQUIRED("required"),
        FORBIDDEN("forbidden");

        private final String token;

        Requirement(String token) {
            this.token = token;
        }

        String getToken() {
            return token;
        }

        static Requirement fromToken(String token) {
            for (Requirement requirement : values()) {
                if (requirement.token.equals(token)) {
                    return requirement;
                }
            }
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_CONSTRAINT,
                    "requirement", "Unsupported requirement " + token);
        }
    }

    /** Inclusive nonnegative count range, or an explicit unspecified value. */
    static final class CountRange {
        private static final CountRange UNSPECIFIED =
                new CountRange(false, 0, 0);

        private final boolean specified;
        private final int minimum;
        private final int maximum;

        private CountRange(boolean specified, int minimum, int maximum) {
            this.specified = specified;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        static CountRange unspecified() {
            return UNSPECIFIED;
        }

        static CountRange between(int minimum, int maximum) {
            validateBound(minimum, "minimum");
            validateBound(maximum, "maximum");
            if (minimum > maximum) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_RANGE,
                        "range", "Minimum exceeds maximum");
            }
            return new CountRange(true, minimum, maximum);
        }

        private static CountRange between(int minimum, int maximum,
                String fieldId) {
            validateBound(minimum, fieldId + ".minimum");
            validateBound(maximum, fieldId + ".maximum");
            if (minimum > maximum) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_RANGE, fieldId,
                        "Minimum exceeds maximum");
            }
            return new CountRange(true, minimum, maximum);
        }

        static CountRange exact(int value) {
            validateBound(value, "value");
            return new CountRange(true, value, value);
        }

        boolean isSpecified() {
            return specified;
        }

        int getMinimum() {
            requireSpecified();
            return minimum;
        }

        int getMaximum() {
            requireSpecified();
            return maximum;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CountRange)) {
                return false;
            }
            CountRange that = (CountRange) other;
            return specified == that.specified && (!specified
                    || (minimum == that.minimum && maximum == that.maximum));
        }

        @Override
        public int hashCode() {
            if (!specified) {
                return 0;
            }
            return 31 * minimum + maximum + 1;
        }

        @Override
        public String toString() {
            return specified ? Integer.toString(minimum) + ":"
                    + Integer.toString(maximum) : "~";
        }

        private void requireSpecified() {
            if (!specified) {
                throw new IllegalStateException("Count range is unspecified");
            }
        }

        private static void validateBound(int value, String fieldId) {
            if (value < 0) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_BOUND, fieldId,
                        "Count bounds must be nonnegative");
            }
        }
    }

    /** One declared count dimension. */
    static final class CountRequest {
        private final CountMetric metric;
        private final CountRange range;

        CountRequest(CountMetric metric, CountRange range) {
            this.metric = ChallengeContractException.required(metric, "metric");
            this.range = ChallengeContractException.required(range, "range");
        }

        CountMetric getMetric() {
            return metric;
        }

        CountRange getRange() {
            return range;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CountRequest)) {
                return false;
            }
            CountRequest that = (CountRequest) other;
            return metric == that.metric && range.equals(that.range);
        }

        @Override
        public int hashCode() {
            return 31 * metric.hashCode() + range.hashCode();
        }
    }

    /** Immutable allow-list for the exact Task 41 instrument identifiers. */
    static final class AllowedInstruments {
        private static final String[] SUPPORTED = {
            "DC_VOLTAGE", "RESISTANCE", "CONTINUITY", "DIODE"
        };
        private static final AllowedInstruments UNSPECIFIED =
                new AllowedInstruments(false, Collections.<String>emptyList());

        private final boolean specified;
        private final java.util.List<String> ids;

        private AllowedInstruments(boolean specified, Collection<String> ids) {
            this.specified = specified;
            this.ids = Collections.unmodifiableList(
                    new ArrayList<String>(ids));
        }

        static AllowedInstruments unspecified() {
            return UNSPECIFIED;
        }

        static AllowedInstruments only(Collection<String> ids) {
            if (ids == null) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD,
                        "instruments", "Instrument collection is required");
            }
            TreeSet<String> sorted = new TreeSet<String>();
            for (String id : ids) {
                if (id == null) {
                    throw new ChallengeContractException(
                            ChallengeContractException.Code.INVALID_ID,
                            "instruments", "Instrument ID is required");
                }
                String validated = ChallengeContractException.id(id,
                        "instruments");
                if (!isSupported(validated)) {
                    throw new ChallengeContractException(
                            ChallengeContractException.Code.UNSUPPORTED_ID,
                            "instruments", "Unsupported instrument " + validated);
                }
                if (!sorted.add(validated)) {
                    throw new ChallengeContractException(
                            ChallengeContractException.Code.DUPLICATE_DECLARATION,
                            "instruments", "Duplicate instrument " + validated);
                }
            }
            return new AllowedInstruments(true, sorted);
        }

        boolean isSpecified() {
            return specified;
        }

        java.util.List<String> getIds() {
            return ids;
        }

        String toCanonical() {
            if (!specified) {
                return "~";
            }
            StringBuilder result = new StringBuilder();
            result.append('[');
            for (int index = 0; index < ids.size(); index++) {
                if (index != 0) {
                    result.append(',');
                }
                result.append(ids.get(index));
            }
            result.append(']');
            return result.toString();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AllowedInstruments)) {
                return false;
            }
            AllowedInstruments that = (AllowedInstruments) other;
            return specified == that.specified && ids.equals(that.ids);
        }

        @Override
        public int hashCode() {
            return 31 * (specified ? 1 : 0) + ids.hashCode();
        }

        @Override
        public String toString() {
            return toCanonical();
        }

        private static boolean isSupported(String id) {
            for (String supported : SUPPORTED) {
                if (supported.equals(id)) {
                    return true;
                }
            }
            return false;
        }
    }

    GenerationConstraints(int version, Collection<CountRequest> counts,
            Requirement parallelAmbiguity, Requirement temporalEvidence,
            AllowedInstruments instruments) {
        ChallengeContractException.positiveVersion(version, "version");
        if (version != VERSION) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "version", "Unsupported constraints version " + version);
        }
        this.version = version;
        if (counts == null) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD, "counts",
                    "Count declarations are required");
        }
        EnumMap<CountMetric, CountRange> copied =
                new EnumMap<CountMetric, CountRange>(CountMetric.class);
        for (CountRequest request : counts) {
            if (request == null) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD, "counts",
                        "Count declaration is required");
            }
            if (copied.containsKey(request.getMetric())) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.DUPLICATE_DECLARATION,
                        request.getMetric().getToken(),
                        "Duplicate count metric declaration");
            }
            copied.put(request.getMetric(), request.getRange());
        }
        for (CountMetric metric : CountMetric.values()) {
            if (!copied.containsKey(metric)) {
                copied.put(metric, CountRange.unspecified());
            }
        }
        this.counts = Collections.unmodifiableMap(copied);
        this.parallelAmbiguity = ChallengeContractException.required(
                parallelAmbiguity, "parallelAmbiguity");
        this.temporalEvidence = ChallengeContractException.required(
                temporalEvidence, "temporalEvidence");
        this.allowedInstruments = ChallengeContractException.required(
                instruments, "instruments");
        validateContradictions();
    }

    static GenerationConstraints unspecified() {
        return new GenerationConstraints(VERSION,
                Collections.<CountRequest>emptyList(), Requirement.UNSPECIFIED,
                Requirement.UNSPECIFIED, AllowedInstruments.unspecified());
    }

    int getVersion() {
        return version;
    }

    CountRange getCount(CountMetric metric) {
        ChallengeContractException.required(metric, "metric");
        return counts.get(metric);
    }

    Map<CountMetric, CountRange> getCounts() {
        return counts;
    }

    Requirement getParallelAmbiguity() {
        return parallelAmbiguity;
    }

    Requirement getTemporalEvidence() {
        return temporalEvidence;
    }

    AllowedInstruments getAllowedInstruments() {
        return allowedInstruments;
    }

    boolean isUnspecified() {
        if (parallelAmbiguity != Requirement.UNSPECIFIED
                || temporalEvidence != Requirement.UNSPECIFIED
                || allowedInstruments.isSpecified()) {
            return false;
        }
        for (CountMetric metric : CountMetric.values()) {
            if (counts.get(metric).isSpecified()) {
                return false;
            }
        }
        return true;
    }

    String toCanonical() {
        StringBuilder result = new StringBuilder(128);
        result.append(HEADER);
        for (int index = 0; index < CANONICAL_KEYS.length; index++) {
            result.append(';').append(CANONICAL_KEYS[index]).append('=');
            CountMetric metric = METRICS_BY_CANONICAL_KEY[index];
            if (metric != null) {
                result.append(counts.get(metric).toString());
            } else if ("parallel-ambiguity".equals(CANONICAL_KEYS[index])) {
                result.append(parallelAmbiguity.getToken());
            } else if ("temporal-evidence".equals(CANONICAL_KEYS[index])) {
                result.append(temporalEvidence.getToken());
            } else {
                result.append(allowedInstruments.toCanonical());
            }
        }
        if (result.length() > MAX_ENCODING_LENGTH) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING,
                    "constraints", "Canonical constraints exceed 4096 characters");
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return toCanonical();
    }

    static GenerationConstraints parse(String value) {
        requireAscii(value, "constraints", MAX_ENCODING_LENGTH);
        if (value.length() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING,
                    "constraints", "Constraints encoding is empty");
        }
        String header = "tsj-constraints/";
        int firstSeparator = value.indexOf(';');
        if (firstSeparator < 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD, "constraints",
                    "Constraint fields are missing");
        }
        String versionHeader = value.substring(0, firstSeparator);
        if (!HEADER.equals(versionHeader)) {
            if (versionHeader.startsWith(header)) {
                String versionText = versionHeader.substring(header.length());
                int parsed = parsePositiveInt(versionText, "version");
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNSUPPORTED_VERSION,
                        "version", "Unsupported constraints version " + parsed);
            }
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, "constraints",
                    "Invalid constraints header");
        }

        Map<String, String> fields = new HashMap<String, String>();
        int start = firstSeparator + 1;
        if (start >= value.length()) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.MISSING_FIELD, "constraints",
                    "Constraint fields are missing");
        }
        while (start <= value.length()) {
            int end = value.indexOf(';', start);
            if (end < 0) {
                end = value.length();
            }
            if (end == start) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        "constraints", "Blank constraint field");
            }
            String field = value.substring(start, end);
            int equals = field.indexOf('=');
            if (equals <= 0 || equals != field.lastIndexOf('=')
                    || equals == field.length() - 1) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        "constraints", "Each constraint must be key=value");
            }
            String key = field.substring(0, equals);
            String fieldValue = field.substring(equals + 1);
            if (!isKnownKey(key)) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.UNKNOWN_FIELD, key,
                        "Unknown constraint field");
            }
            if (fields.put(key, fieldValue) != null) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.DUPLICATE_DECLARATION,
                        key, "Duplicate constraint field");
            }
            if (end == value.length()) {
                break;
            }
            start = end + 1;
            if (start == value.length()) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        "constraints", "Constraints must not have trailing semicolon");
            }
        }

        for (String key : CANONICAL_KEYS) {
            if (!fields.containsKey(key)) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.MISSING_FIELD, key,
                        "Required constraint field is missing");
            }
        }

        ArrayList<CountRequest> requests = new ArrayList<CountRequest>();
        for (CountMetric metric : CountMetric.values()) {
            requests.add(new CountRequest(metric,
                    parseRange(fields.get(metric.getToken()), metric.getToken())));
        }
        Requirement parallel = parseRequirement(fields.get("parallel-ambiguity"),
                "parallel-ambiguity");
        Requirement temporal = parseRequirement(fields.get("temporal-evidence"),
                "temporal-evidence");
        AllowedInstruments instruments = parseInstruments(
                fields.get("instruments"));
        return new GenerationConstraints(VERSION, requests, parallel, temporal,
                instruments);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GenerationConstraints)) {
            return false;
        }
        GenerationConstraints that = (GenerationConstraints) other;
        return version == that.version && counts.equals(that.counts)
                && parallelAmbiguity == that.parallelAmbiguity
                && temporalEvidence == that.temporalEvidence
                && allowedInstruments.equals(that.allowedInstruments);
    }

    @Override
    public int hashCode() {
        int result = version;
        result = 31 * result + counts.hashCode();
        result = 31 * result + parallelAmbiguity.hashCode();
        result = 31 * result + temporalEvidence.hashCode();
        result = 31 * result + allowedInstruments.hashCode();
        return result;
    }

    private void validateContradictions() {
        CountRange temporalSamples = counts.get(CountMetric.TEMPORAL_SAMPLES);
        if (temporalEvidence == Requirement.FORBIDDEN
                && temporalSamples.isSpecified()
                && temporalSamples.getMinimum() > 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT,
                    "temporal-evidence",
                    "Temporal evidence is forbidden while temporal samples are required");
        }
        if (temporalEvidence == Requirement.REQUIRED
                && temporalSamples.isSpecified()
                && temporalSamples.getMaximum() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT,
                    "temporal-evidence",
                    "Temporal evidence is required while temporal samples are forbidden");
        }

        CountRange depth = counts.get(CountMetric.DIAGNOSTIC_DEPTH);
        if (depth.isSpecified()) {
            long minimumSum = specifiedMinimum(CountMetric.INPUT_TRANSITIONS)
                    + specifiedMinimum(CountMetric.ISOLATION_ACTIONS);
            long temporalMinimum = specifiedMinimum(CountMetric.TEMPORAL_SAMPLES);
            if (temporalEvidence == Requirement.REQUIRED) {
                temporalMinimum = temporalMinimum < 1L ? 1L : temporalMinimum;
            }
            minimumSum += temporalMinimum;
            if ((long) depth.getMaximum() < minimumSum) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT,
                        "diagnostic-depth",
                        "Diagnostic depth is below required transition, isolation, and temporal minima");
            }
        }
    }

    private long specifiedMinimum(CountMetric metric) {
        CountRange range = counts.get(metric);
        return range.isSpecified() ? (long) range.getMinimum() : 0L;
    }

    private static CountRange parseRange(String text, String fieldId) {
        if ("~".equals(text)) {
            return CountRange.unspecified();
        }
        if (text == null || text.length() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Count range is required");
        }
        int colon = text.indexOf(':');
        if (colon <= 0 || colon != text.lastIndexOf(':')
                || colon == text.length() - 1) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Count range must be min:max");
        }
        int minimum = parseBound(text.substring(0, colon), fieldId);
        int maximum = parseBound(text.substring(colon + 1), fieldId);
        if (minimum > maximum) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, fieldId,
                    "Count range must not be inverted");
        }
        return CountRange.between(minimum, maximum, fieldId);
    }

    private static int parseBound(String text, String fieldId) {
        if (text == null || text.length() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Count bound is required");
        }
        if (text.charAt(0) == '-' || text.charAt(0) == '+') {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, fieldId,
                    "Count bounds must be nonnegative canonical integers");
        }
        if (!allDigits(text) || (text.length() > 1 && text.charAt(0) == '0')) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Count bound is not canonical decimal");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, fieldId,
                    "Count bound exceeds signed 32-bit range");
        }
    }

    private static Requirement parseRequirement(String text, String fieldId) {
        if (text == null || text.length() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Requirement is required");
        }
        for (Requirement requirement : Requirement.values()) {
            if (requirement.getToken().equals(text)) {
                return requirement;
            }
        }
        throw new ChallengeContractException(
                ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                "Unknown requirement token");
    }

    private static AllowedInstruments parseInstruments(String text) {
        if ("~".equals(text)) {
            return AllowedInstruments.unspecified();
        }
        if (text == null || text.length() < 2
                || text.charAt(0) != '['
                || text.charAt(text.length() - 1) != ']') {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING,
                    "instruments", "Instrument list must be ~ or [ids]");
        }
        String body = text.substring(1, text.length() - 1);
        ArrayList<String> ids = new ArrayList<String>();
        if (body.length() != 0) {
            int start = 0;
            while (start <= body.length()) {
                int comma = body.indexOf(',', start);
                if (comma < 0) {
                    comma = body.length();
                }
                if (comma == start) {
                    throw new ChallengeContractException(
                            ChallengeContractException.Code.INVALID_ENCODING,
                            "instruments", "Instrument ID is empty");
                }
                ids.add(body.substring(start, comma));
                if (comma == body.length()) {
                    break;
                }
                start = comma + 1;
                if (start == body.length()) {
                    throw new ChallengeContractException(
                            ChallengeContractException.Code.INVALID_ENCODING,
                            "instruments", "Instrument ID is empty");
                }
            }
        }
        return AllowedInstruments.only(ids);
    }

    private static boolean isKnownKey(String key) {
        for (String known : CANONICAL_KEYS) {
            if (known.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static int parsePositiveInt(String text, String fieldId) {
        if (text == null || text.length() == 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Version is not canonical decimal");
        }
        if (text.charAt(0) == '-') {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_VERSION, fieldId,
                    "Version must be positive");
        }
        if (text.charAt(0) == '+') {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Leading plus is not canonical");
        }
        if (!allDigits(text)
                || (text.length() > 1 && text.charAt(0) == '0')) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                    "Version is not canonical decimal");
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_RANGE, fieldId,
                    "Version exceeds signed 32-bit range");
        }
        ChallengeContractException.positiveVersion(value, fieldId);
        return value;
    }

    private static boolean allDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
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
            if (value.charAt(index) > 0x7f || value.charAt(index) == '\r') {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING, fieldId,
                        "Encoding must be ASCII semicolon-delimited text");
            }
        }
    }
}
