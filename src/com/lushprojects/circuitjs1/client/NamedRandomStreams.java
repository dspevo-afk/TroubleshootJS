package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Versioned, named deterministic random streams for challenge generation.
 *
 * <p>The derivation tuple is deliberately independent of generator metadata,
 * descriptor encoding, constraints, and collection order.  Each named stream
 * is a fresh SplitMix64 cursor; opening the same stream therefore restarts it
 * at its first value.</p>
 */
final class NamedRandomStreams {
    static final int DERIVATION_VERSION = 1;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private static final long SPLIT_MIX_INCREMENT = 0x9e3779b97f4a7c15L;
    private static final long SPLIT_MIX_MULTIPLIER_1 = 0xbf58476d1ce4e5b9L;
    private static final long SPLIT_MIX_MULTIPLIER_2 = 0x94d049bb133111ebL;

    private final long rootSeed;
    private final String deviceIntentId;
    private final int deviceIntentVersion;

    enum Concern {
        TOPOLOGY("topology"),
        BLOCK("block"),
        VALUES("values"),
        SUPPORT("support"),
        FAULT("fault"),
        SCENARIO("scenario"),
        PLACEMENT("placement"),
        ROUTING("routing"),
        PRESENTATION("presentation");

        private final String token;

        Concern(String token) {
            this.token = token;
        }

        String getToken() {
            return token;
        }
    }

    NamedRandomStreams(int derivationVersion, long rootSeed,
            String deviceIntentId, int deviceIntentVersion) {
        if (derivationVersion <= 0) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_VERSION,
                    "seed.derivationVersion", "Version must be positive");
        }
        if (derivationVersion != DERIVATION_VERSION) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.UNSUPPORTED_VERSION,
                    "seed.derivationVersion",
                    "Unsupported named-stream derivation version "
                            + derivationVersion);
        }
        this.rootSeed = rootSeed;
        this.deviceIntentId = ChallengeContractException.id(deviceIntentId,
                "seed.deviceIntentId");
        ChallengeContractException.positiveVersion(deviceIntentVersion,
                "seed.deviceIntentVersion");
        this.deviceIntentVersion = deviceIntentVersion;
    }

    long deviceSeed(Concern concern, int concernRevision,
            String semanticKey) {
        return derive("device", "-", concern, concernRevision, semanticKey);
    }

    long blockSeed(String blockKey, Concern concern, int concernRevision,
            String semanticKey) {
        String validatedBlockKey = ChallengeContractException.id(blockKey,
                "stream.blockKey");
        return derive("block", validatedBlockKey, concern, concernRevision,
                semanticKey);
    }

    Stream openDevice(Concern concern, int concernRevision,
            String semanticKey) {
        return new Stream(deviceSeed(concern, concernRevision, semanticKey));
    }

    Stream openBlock(String blockKey, Concern concern, int concernRevision,
            String semanticKey) {
        return new Stream(blockSeed(blockKey, concern, concernRevision,
                semanticKey));
    }

    /**
     * Copy and canonically order candidate IDs before any stream draw.
     */
    static List<String> canonicalCandidates(Collection<String> candidates) {
        ChallengeContractException.required(candidates,
                "selection.candidates");
        ArrayList<String> copy = new ArrayList<String>(candidates.size());
        for (String candidate : candidates) {
            if (candidate == null) {
                ChallengeContractException.required(candidate,
                        "selection.candidates.element");
            }
            copy.add(ChallengeContractException.id(candidate,
                    "selection.candidates.element"));
        }
        if (copy.isEmpty()) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.EMPTY_CANDIDATES,
                    "selection.candidates", "At least one candidate is required");
        }
        Collections.sort(copy);
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).equals(copy.get(index))) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.DUPLICATE_DECLARATION,
                        "selection.candidates",
                        "Duplicate candidate " + copy.get(index));
            }
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * Select one candidate using a newly opened stream.
     */
    static String select(long streamSeed, Collection<String> candidates) {
        List<String> canonical = canonicalCandidates(candidates);
        return canonical.get(new Stream(streamSeed).nextInt(canonical.size()));
    }

    /** Fisher-Yates order; GWT 2.7 does not emulate Collections.shuffle. */
    static <T> void shuffle(List<T> values, java.util.Random random) {
        for (int size = values.size(); size > 1; size--) {
            int other = random.nextInt(size);
            T value = values.get(size - 1);
            values.set(size - 1, values.get(other));
            values.set(other, value);
        }
    }

    private long derive(String scope, String blockKey, Concern concern,
            int concernRevision, String semanticKey) {
        ChallengeContractException.required(concern, "stream.concern");
        ChallengeContractException.positiveVersion(concernRevision,
                "stream.concernRevision");
        String validatedSemanticKey = ChallengeContractException.id(semanticKey,
                "stream.semanticKey");
        if ("block".equals(scope)
                && (concern == Concern.BLOCK || concern == Concern.VALUES)) {
            // The block key has already been validated by blockSeed.
        } else if ("device".equals(scope)
                && (concern == Concern.BLOCK || concern == Concern.VALUES)) {
            throw new ChallengeContractException(
                    ChallengeContractException.Code.INVALID_SCOPE,
                    "stream.scope",
                    "Concern " + concern.getToken()
                            + " requires block scope");
        }

        long hash = FNV_OFFSET_BASIS;
        hash = hashField(hash, "tsj-named-seed", "seed.domain");
        hash = hashField(hash, "1", "seed.derivationVersion");
        hash = hashField(hash, Long.toString(rootSeed), "seed.rootSeed");
        hash = hashField(hash, deviceIntentId, "seed.deviceIntentId");
        hash = hashField(hash, Integer.toString(deviceIntentVersion),
                "seed.deviceIntentVersion");
        hash = hashField(hash, scope, "stream.scope");
        hash = hashField(hash, blockKey, "stream.blockKey");
        hash = hashField(hash, concern.getToken(), "stream.concern");
        hash = hashField(hash, Integer.toString(concernRevision),
                "stream.concernRevision");
        hash = hashField(hash, validatedSemanticKey, "stream.semanticKey");
        return hash;
    }

    private static long hashField(long hash, String field, String fieldId) {
        int byteLength = field.length();
        for (int index = 0; index < field.length(); index++) {
            if (field.charAt(index) > 0x7f) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_ENCODING,
                        fieldId, "Tuple fields must be ASCII");
            }
        }
        hash = hashAscii(hash, Integer.toString(byteLength));
        hash = hashByte(hash, ':');
        for (int index = 0; index < field.length(); index++) {
            hash = hashByte(hash, field.charAt(index));
        }
        return hash;
    }

    private static long hashAscii(long hash, String value) {
        for (int index = 0; index < value.length(); index++) {
            hash = hashByte(hash, value.charAt(index));
        }
        return hash;
    }

    private static long hashByte(long hash, int value) {
        return (hash ^ (value & 0xff)) * FNV_PRIME;
    }

    /** A fresh SplitMix64 cursor over one derived stream seed. */
    static final class Stream {
        private long state;

        Stream(long seed) {
            this.state = seed;
        }

        long nextLong() {
            state += SPLIT_MIX_INCREMENT;
            long value = state;
            value = (value ^ (value >>> 30)) * SPLIT_MIX_MULTIPLIER_1;
            value = (value ^ (value >>> 27)) * SPLIT_MIX_MULTIPLIER_2;
            return value ^ (value >>> 31);
        }

        int nextInt(int bound) {
            if (bound <= 0) {
                throw new ChallengeContractException(
                        ChallengeContractException.Code.INVALID_BOUND,
                        "stream.bound", "Bound must be positive");
            }
            for (;;) {
                long random = nextLong() >>> 1;
                long value = random % bound;
                if (random - value + (bound - 1) >= 0) {
                    return (int) value;
                }
            }
        }
    }
}
