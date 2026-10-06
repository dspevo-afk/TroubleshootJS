package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/**
 * Current semantic session data, deliberately separate from pristine sharing.
 *
 * <p>Restart mode preserves the declared repairs, inventory, source commands and
 * permanent damage. It does not promise continuation of capacitor charge, relay
 * motion, hysteresis, simulation time or pending solver events. The importing UI
 * must disclose that restart policy before publication. This format contains no
 * solver nodes, matrices, object addresses, executable callbacks or proof grants.
 * Runtime reconstruction still validates every referenced owner and final state.</p>
 *
 * <p>Fields use decimal character lengths, followed by ':' and the exact text,
 * then one newline. Counts and section order are fixed and canonical. Numeric
 * state uses IEEE-754 bit hex, avoiding JVM/GWT decimal-format differences.</p>
 */
final class PlayerSessionSave {
    static final String SCHEMA = "tsj-session/1";
    static final String MODEL = "tsj-session-model/1";
    static final String DYNAMIC_MODE = "restart";
    static final int MAX_CHARACTERS = 2 * 1024 * 1024;
    static final int MAX_OPERATIONS = 8192;
    static final int MAX_PARTS = 96;
    static final int MAX_SOURCES = 96;
    static final int MAX_IDENTIFIER = 512;
    static final int MAX_CANONICAL_TEXT = 1024 * 1024;
    private static final int MAX_BUILD = 256;
    private static final int MAX_NUMBER_TEXT = 64;

    final String replay;
    final String build;
    final String realization;
    final String stateSignature;
    private final Vector<Operation> history;
    private final Vector<Source> sources;
    private final Vector<Stress> stress;
    private final Vector<Fuse> fuses;

    PlayerSessionSave(String replay, String build, String realization,
            String stateSignature, Vector<Operation> history,
            Vector<Source> sources, Vector<Stress> stress, Vector<Fuse> fuses) {
        if (replay == null || !PlayerLaunchRequest.parse(replay).replay().equals(replay))
            throw invalid("Unsupported pristine replay");
        requireText(build, MAX_BUILD, "build");
        requireText(realization, MAX_CANONICAL_TEXT, "realization");
        requireText(stateSignature, MAX_CANONICAL_TEXT, "state signature");
        this.replay = replay;
        this.build = build;
        this.realization = realization;
        this.stateSignature = stateSignature;
        this.history = copy(history, MAX_OPERATIONS, "history");
        this.sources = copy(sources, MAX_SOURCES, "sources");
        this.stress = copy(stress, MAX_PARTS, "stress");
        this.fuses = copy(fuses, MAX_PARTS, "fuses");
        HashSet<String> sourceIds = new HashSet<String>();
        for (Source source : this.sources)
            if (!sourceIds.add(source.id)) throw invalid("Duplicate source identity");
        HashSet<String> partIds = new HashSet<String>();
        for (Stress state : this.stress)
            if (!partIds.add(state.partId)) throw invalid("Duplicate stress part identity");
        HashSet<String> fuseIds = new HashSet<String>();
        for (Fuse fuse : this.fuses)
            if (!fuseIds.add(fuse.partId)) throw invalid("Duplicate fuse part identity");
        // The complete artifact, not just each individual field, is bounded.
        encode();
    }

    Vector<Operation> getHistory() { return new Vector<Operation>(history); }
    Vector<Source> getSources() { return new Vector<Source>(sources); }
    Vector<Stress> getStress() { return new Vector<Stress>(stress); }
    Vector<Fuse> getFuses() { return new Vector<Fuse>(fuses); }

    static final class Operation {
        public final String kind, owner, argument, result;

        Operation(String kind, String owner, String argument, String result) {
            if (kind == null || owner == null || argument == null || result == null)
                throw invalid("Missing operation field");
            if ("REMOVE".equals(kind) || "INSTALL".equals(kind)) {
                requireId(owner); requireId(argument); requireEmpty(result);
            } else if ("ACQUIRE".equals(kind) || "CATALOG".equals(kind)) {
                requireId(owner); requireId(argument); requireId(result);
            } else if ("LEAD".equals(kind)) {
                requireId(owner); requireId(argument); connection(result);
            } else if ("GRAPH_REMOVE".equals(kind) || "GRAPH_RESTORE".equals(kind) ||
                    "INPUT".equals(kind)) {
                requireId(owner); requireEmpty(argument); requireEmpty(result);
                if ("INPUT".equals(kind))
                    GeneratedBoardOperation.requireStableSemanticId(owner, "session input");
            } else if ("SOURCE".equals(kind)) {
                requireId(owner); connection(argument); requireLimitText(result);
            } else if ("RESET".equals(kind)) {
                requireEmpty(owner); requireEmpty(argument); requireEmpty(result);
            } else throw invalid("Unsupported session operation");
            this.kind = kind; this.owner = owner; this.argument = argument; this.result = result;
        }
    }

    static final class Source {
        public final String id;
        public final boolean connected;
        public final double limitAmps;

        Source(String id, boolean connected, double limitAmps) {
            requireId(id);
            if (!Double.isNaN(limitAmps)) LowVoltageSourceModel.validate(0, limitAmps);
            this.id = id; this.connected = connected; this.limitAmps = limitAmps;
        }
    }

    static final class Stress {
        public final String partId;
        public final double damage, serviceTime, failureTime;
        public final boolean failed;

        Stress(String partId, double damage, double serviceTime, boolean failed,
                double failureTime) {
            requireId(partId); requireNonnegative(damage); requireNonnegative(serviceTime);
            if (failed) {
                requireNonnegative(failureTime);
                if (damage < 1 || failureTime > serviceTime)
                    throw invalid("Inconsistent failed stress state");
            } else if (damage >= 1 || !Double.isNaN(failureTime)) {
                throw invalid("Inconsistent healthy stress state");
            }
            this.partId = partId; this.damage = damage; this.serviceTime = serviceTime;
            this.failed = failed; this.failureTime = failureTime;
        }
    }

    /** Permanent fuse state. The runtime validates heat against the owned fuse's i2t. */
    static final class Fuse {
        public final String partId;
        public final double heat;
        public final boolean blown;

        Fuse(String partId, double heat, boolean blown) {
            requireId(partId); requireNonnegative(heat);
            this.partId = partId; this.heat = heat; this.blown = blown;
        }
    }

    String encode() {
        Writer out = new Writer();
        out.line(SCHEMA); out.line(MODEL); out.line(DYNAMIC_MODE);
        out.field(replay); out.field(build); out.field(realization); out.field(stateSignature);
        out.line("HISTORY"); out.line(Integer.toString(history.size()));
        for (Operation operation : history) {
            out.field(operation.kind); out.field(operation.owner);
            out.field(operation.argument); out.field(operation.result);
        }
        out.line("SOURCES"); out.line(Integer.toString(sources.size()));
        for (Source source : sources) {
            out.field(source.id); out.field(source.connected ? "CONNECTED" : "DISCONNECTED");
            out.field(Double.isNaN(source.limitAmps) ? "NONE" : number(source.limitAmps));
        }
        out.line("STRESS"); out.line(Integer.toString(stress.size()));
        for (Stress state : stress) {
            out.field(state.partId); out.field(number(state.damage)); out.field(number(state.serviceTime));
            out.field(state.failed ? "FAILED" : "HEALTHY");
            out.field(state.failed ? number(state.failureTime) : "NONE");
        }
        out.line("FUSES"); out.line(Integer.toString(fuses.size()));
        for (Fuse fuse : fuses) {
            out.field(fuse.partId); out.field(number(fuse.heat));
            out.field(fuse.blown ? "BLOWN" : "INTACT");
        }
        out.line("END");
        return out.value.toString();
    }

    static PlayerSessionSave parse(String text) {
        Reader in = new Reader(text);
        in.expect(SCHEMA); in.expect(MODEL); in.expect(DYNAMIC_MODE);
        String replay = in.field(180), build = in.field(MAX_BUILD);
        String realization = in.field(MAX_CANONICAL_TEXT), signature = in.field(MAX_CANONICAL_TEXT);
        // Validate the replay before accepting any potentially large history.
        PlayerLaunchRequest.parse(replay);
        in.expect("HISTORY");
        Vector<Operation> operations = new Vector<Operation>();
        int count = in.count(MAX_OPERATIONS);
        for (int i = 0; i < count; i++)
            operations.add(new Operation(in.field(32), in.field(MAX_IDENTIFIER),
                in.field(MAX_IDENTIFIER), in.field(MAX_IDENTIFIER)));
        in.expect("SOURCES");
        Vector<Source> sources = new Vector<Source>();
        count = in.count(MAX_SOURCES);
        for (int i = 0; i < count; i++) {
            String id = in.field(MAX_IDENTIFIER);
            boolean connected = connection(in.field(12));
            String limit = in.field(MAX_NUMBER_TEXT);
            sources.add(new Source(id, connected, "NONE".equals(limit) ? Double.NaN : parseNumber(limit)));
        }
        in.expect("STRESS");
        Vector<Stress> stress = new Vector<Stress>();
        count = in.count(MAX_PARTS);
        for (int i = 0; i < count; i++) {
            String part = in.field(MAX_IDENTIFIER);
            double damage = parseNumber(in.field(MAX_NUMBER_TEXT));
            double service = parseNumber(in.field(MAX_NUMBER_TEXT));
            String state = in.field(7);
            if (!"FAILED".equals(state) && !"HEALTHY".equals(state))
                throw invalid("Unknown stress state");
            String failure = in.field(MAX_NUMBER_TEXT);
            stress.add(new Stress(part, damage, service, "FAILED".equals(state),
                "NONE".equals(failure) ? Double.NaN : parseNumber(failure)));
        }
        in.expect("FUSES");
        Vector<Fuse> fuses = new Vector<Fuse>();
        count = in.count(MAX_PARTS);
        for (int i = 0; i < count; i++) {
            String part = in.field(MAX_IDENTIFIER);
            double heat = parseNumber(in.field(MAX_NUMBER_TEXT));
            String state = in.field(6);
            if (!"BLOWN".equals(state) && !"INTACT".equals(state))
                throw invalid("Unknown fuse state");
            fuses.add(new Fuse(part, heat, "BLOWN".equals(state)));
        }
        in.expect("END");
        if (in.position != text.length()) throw invalid("Trailing session data");
        return new PlayerSessionSave(replay, build, realization, signature, operations, sources, stress, fuses);
    }

    /** Canonical finite, nonnegative numeric state, also usable by state signatures. */
    static String number(double value) {
        requireNonnegative(value);
        return Long.toHexString(Double.doubleToLongBits(value));
    }

    private static double parseNumber(String text) {
        if (text.length() == 0 || text.length() > 16)
            throw invalid("Invalid numeric state");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f'))
                throw invalid("Invalid numeric state");
        }
        long bits;
        try { bits = Long.parseLong(text, 16); }
        catch (NumberFormatException invalid) { throw invalid("Invalid numeric state"); }
        double value = Double.longBitsToDouble(bits);
        if (!number(value).equals(text)) throw invalid("Noncanonical numeric state");
        return value;
    }

    private static void requireLimitText(String text) {
        if ("NONE".equals(text)) return;
        if (text.length() == 0 || text.length() > MAX_NUMBER_TEXT ||
                !text.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))
            throw invalid("Invalid operation current limit");
        double value;
        try { value = Double.parseDouble(text); }
        catch (NumberFormatException invalid) { throw invalid("Invalid operation current limit"); }
        // The current model uses the same bounded bench source as live controls.
        LowVoltageSourceModel.validate(0, value);
    }

    private static boolean connection(String value) {
        if ("CONNECTED".equals(value)) return true;
        if ("DISCONNECTED".equals(value)) return false;
        throw invalid("Invalid source or lead connection");
    }
    private static void requireId(String value) {
        requireText(value, MAX_IDENTIFIER, "semantic identity");
        if (!value.matches("[A-Za-z0-9_./:@+-]+")) throw invalid("Invalid semantic identity");
    }
    private static void requireText(String value, int maximum, String field) {
        if (value == null || value.length() == 0 || value.length() > maximum || value.indexOf('\0') >= 0)
            throw invalid("Invalid " + field);
    }
    private static void requireEmpty(String value) {
        if (value.length() != 0) throw invalid("Unexpected operation argument");
    }
    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
    private static void requireNonnegative(double value) {
        if (!finite(value) || value < 0 || Double.doubleToLongBits(value) < 0)
            throw invalid("Invalid nonnegative numeric state");
    }
    private static <T> Vector<T> copy(Vector<T> values, int maximum, String field) {
        if (values == null || values.size() > maximum) throw invalid("Invalid " + field + " count");
        Vector<T> copy = new Vector<T>(values);
        for (T value : copy) if (value == null) throw invalid("Missing " + field + " entry");
        return copy;
    }
    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static final class Writer {
        final StringBuilder value = new StringBuilder();
        void line(String text) {
            capacity(text.length() + 1); value.append(text).append('\n');
        }
        void field(String text) {
            String length = Integer.toString(text.length());
            capacity(length.length() + 1 + text.length() + 1);
            value.append(length).append(':').append(text).append('\n');
        }
        void capacity(int additional) {
            if (additional > MAX_CHARACTERS - value.length()) throw invalid("Session artifact is too large");
        }
    }
    private static final class Reader {
        final String text;
        int position;
        Reader(String text) {
            if (text == null || text.length() == 0 || text.length() > MAX_CHARACTERS)
                throw invalid("Invalid session artifact size");
            this.text = text;
        }
        void expect(String value) {
            String line = value + "\n";
            if (!text.startsWith(line, position)) throw invalid("Unsupported or incomplete session section");
            position += line.length();
        }
        int count(int maximum) {
            int end = text.indexOf('\n', position);
            if (end < 0) throw invalid("Incomplete session count");
            int value = decimal(end, maximum); position = end + 1; return value;
        }
        String field(int maximum) {
            int colon = text.indexOf(':', position);
            if (colon < 0) throw invalid("Missing session field length");
            int length = decimal(colon, maximum);
            int start = colon + 1;
            if (length >= text.length() - start || text.charAt(start + length) != '\n')
                throw invalid("Incomplete session field");
            position = start + length + 1;
            return text.substring(start, start + length);
        }
        int decimal(int end, int maximum) {
            int digits = end - position;
            if (digits < 1 || digits > 7 || (digits > 1 && text.charAt(position) == '0'))
                throw invalid("Noncanonical session length or count");
            int value = 0;
            for (int i = position; i < end; i++) {
                char c = text.charAt(i);
                if (c < '0' || c > '9') throw invalid("Invalid session length or count");
                value = value * 10 + c - '0';
                if (value > maximum) throw invalid("Session field or count exceeds limit");
            }
            return value;
        }
    }
}
