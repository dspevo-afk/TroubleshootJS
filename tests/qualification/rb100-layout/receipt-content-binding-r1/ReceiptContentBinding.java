package com.lushprojects.circuitjs1.client;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Source-only prerequisite that binds the original op-1 receipt authority to
 * the selected Windows file identity and to one final content readback.
 *
 * The owner must call verifyFinal after result readback and before export
 * finishGroup. It does not finish the Meter or read its counters; prepare binds
 * the actual started field and uses the selected wrapper for charge/copy.
 */
final class ReceiptContentBinding {
    static final long MAX_RECEIPT_BYTES = 8388608L;
    static final int HASH_BLOCK_BYTES = 4096;
    static final int MAX_PATH_ENTRIES = 4096;

    private static final String RECEIPT_NAME = "op-1.jsonl";
    private static final String LOWER_HEX = "0123456789abcdef";
    private static final long PRODUCER_LIMIT_NANOS = 90000000000L;
    private static final long HASH_LIMIT_NANOS = 5000000000L;

    /*
     * Per-hash fixed work is 9 units; each logical 4096-byte block adds one
     * existing hash unit and two deadline controls, for 9 + 3B.
     */
    private static final long HASH_FIXED_UNITS = 9L;
    private static final long HASH_PER_BLOCK_UNITS = 3L;
    private static final long FINAL_FIXED_UNITS = 17L;
    private static final long AUTHORITY_FIXED_UNITS = 71L;

    static final class BindingFailure extends IllegalStateException {
        final String code;

        BindingFailure(String code) {
            super(code);
            this.code = code;
        }
    }

    private final CohortTerminalTelemetryStudy.SetupMeter meter;
    private final long producerStarted;
    private final Path receipt;
    private final long expectedBytes;
    private final String expectedSha;
    private final Path[] paths;
    private final CohortTerminalTelemetryStudy.R5FileIdentity[] identities;
    private boolean finalAttempted;

    private ReceiptContentBinding(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            long producerStarted,
            Path receipt,
            long expectedBytes,
            String expectedSha,
            Path[] paths,
            CohortTerminalTelemetryStudy.R5FileIdentity[] identities) {
        this.meter = meter;
        this.producerStarted = producerStarted;
        this.receipt = receipt;
        this.expectedBytes = expectedBytes;
        this.expectedSha = expectedSha;
        this.paths = paths;
        this.identities = identities;
        this.finalAttempted = false;
    }

    static ReceiptContentBinding prepare(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            long producerStarted,
            Path receipt,
            long expectedBytes,
            String expectedSha) {
        if (meter == null) throw failure("PREPARE_AUTHORITY_INVALID");

        /*
         * Prepay the bounded authority/path checks before inspecting caller
         * values or asking the filesystem to resolve the path.
         */
        admitUnits(meter, 1L);
        if (readMeterStarted(meter) != producerStarted)
            throw failure("PREPARE_AUTHORITY_MISMATCH");

        admitUnits(meter, AUTHORITY_FIXED_UNITS);
        if (expectedBytes < 1L || expectedBytes > MAX_RECEIPT_BYTES)
            throw failure("EXPECTED_LENGTH_OUT_OF_RANGE");
        if (expectedSha == null || expectedSha.length() != 64)
            throw failure("EXPECTED_SHA_INVALID");
        for (int i = 0; i < 64; i++) {
            char c = expectedSha.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f'))
                throw failure("EXPECTED_SHA_INVALID");
        }

        admitUnits(meter, 1L);
        checkProducerDeadline(producerStarted);

        final int pathCount;
        try {
            if (receipt == null || !receipt.isAbsolute() ||
                    !receipt.normalize().equals(receipt) ||
                    !receipt.toRealPath().equals(receipt) ||
                    receipt.getFileName() == null ||
                    !RECEIPT_NAME.equals(receipt.getFileName().toString()))
                throw failure("RECEIPT_PATH_INVALID");
            long count = ((long) receipt.getNameCount()) + 1L;
            if (count < 2L || count > (long) MAX_PATH_ENTRIES)
                throw failure("RECEIPT_PATH_INVALID");
            pathCount = (int) count;
        } catch (BindingFailure failed) {
            throw failed;
        } catch (Exception failed) {
            throw failure("RECEIPT_PATH_INVALID");
        }

        admitUnits(meter, 1L);
        Path[] ownedPaths = new Path[pathCount];
        admitUnits(meter, (long) pathCount);
        admitCopies(meter, pathCount);
        Path part = receipt;
        for (int i = 0; i < pathCount; i++) {
            if (part == null) throw failure("RECEIPT_PATH_INVALID");
            ownedPaths[i] = part;
            part = part.getParent();
        }
        if (part != null) throw failure("RECEIPT_PATH_INVALID");

        admitUnits(meter, 1L);
        CohortTerminalTelemetryStudy.R5FileIdentity[] ownedIdentities =
                new CohortTerminalTelemetryStudy.R5FileIdentity[pathCount];
        admitUnits(meter, (long) pathCount);
        admitCopies(meter, pathCount);
        admitUnits(meter, 2L);
        checkProducerDeadline(producerStarted);
        for (int i = 0; i < pathCount; i++) {
            try {
                CohortTerminalTelemetryStudy.R5FileIdentity identity =
                        new CohortTerminalTelemetryStudy.R5FileIdentity(
                                ownedPaths[i], i != 0);
                ownedIdentities[i] = identity;
            } catch (Exception failed) {
                throw failure("RECEIPT_IDENTITY_MISMATCH");
            }
        }
        checkProducerDeadline(producerStarted);

        admitUnits(meter, 1L);
        if (ownedIdentities[0].size != expectedBytes)
            throw failure("INITIAL_LENGTH_MISMATCH");

        long blocks = blockCount(expectedBytes);
        admitUnits(meter, hashUnits(blocks));
        byte[] initialDigest = hashContent(
                receipt, expectedBytes, producerStarted, "INITIAL_LENGTH_MISMATCH");
        admitUnits(meter, 1L);
        if (!matchesSha(initialDigest, expectedSha))
            throw failure("INITIAL_SHA_MISMATCH");

        admitUnits(meter, (2L * pathCount) + 2L);
        checkProducerDeadline(producerStarted);
        compareIdentities(ownedPaths, ownedIdentities, "RECEIPT_IDENTITY_MISMATCH",
                "INITIAL_LENGTH_MISMATCH");
        checkProducerDeadline(producerStarted);

        /*
         * Final attempt, final two identity sweeps, and final content hash are
         * prepaid now, while the actual owner's metadata group is still open.
         */
        admitUnits(meter, FINAL_FIXED_UNITS + (4L * pathCount) +
                (HASH_PER_BLOCK_UNITS * blocks));
        admitUnits(meter, 1L);
        admitCopies(meter, 8);
        return new ReceiptContentBinding(meter, producerStarted, receipt,
                expectedBytes, expectedSha, ownedPaths, ownedIdentities);
    }

    /**
     * Consume exactly one final attempt. The owner invokes this after result
     * readback and before export finishGroup. This method never accesses meter
     * counters or asks the meter to reserve or charge work.
     */
    synchronized void verifyFinal(
            CohortTerminalTelemetryStudy.SetupMeter suppliedMeter,
            long suppliedProducerStarted) {
        if (finalAttempted) throw failure("FINAL_ATTEMPT_REUSED");
        finalAttempted = true;
        if (suppliedMeter != meter || suppliedProducerStarted != producerStarted)
            throw failure("FINAL_AUTHORITY_MISMATCH");
        if (identities[0].size != expectedBytes)
            throw failure("FINAL_LENGTH_MISMATCH");

        checkProducerDeadline(producerStarted);
        compareIdentities(paths, identities, "RECEIPT_IDENTITY_MISMATCH",
                "FINAL_LENGTH_MISMATCH");
        checkProducerDeadline(producerStarted);

        byte[] finalDigest = hashContent(
                receipt, expectedBytes, producerStarted, "FINAL_LENGTH_MISMATCH");

        checkProducerDeadline(producerStarted);
        compareIdentities(paths, identities, "RECEIPT_IDENTITY_MISMATCH",
                "FINAL_LENGTH_MISMATCH");
        checkProducerDeadline(producerStarted);

        if (!matchesSha(finalDigest, expectedSha))
            throw failure("FINAL_SHA_MISMATCH");
    }

    private static void compareIdentities(
            Path[] paths,
            CohortTerminalTelemetryStudy.R5FileIdentity[] expected,
            String failureCode,
            String lengthFailureCode) {
        for (int i = 0; i < paths.length; i++) {
            try {
                CohortTerminalTelemetryStudy.R5FileIdentity actual =
                        new CohortTerminalTelemetryStudy.R5FileIdentity(
                                paths[i], i != 0);
                CohortTerminalTelemetryStudy.R5FileIdentity original = expected[i];
                if (!original.key.sameIdentity(actual.key) ||
                        !original.created.equals(actual.created))
                    throw failure(failureCode);
                if (i == 0 && original.size != actual.size)
                    throw failure(lengthFailureCode);
                if (i == 0 && !original.modified.equals(actual.modified))
                    throw failure(failureCode);
            } catch (BindingFailure failed) {
                throw failed;
            } catch (Exception failed) {
                throw failure(failureCode);
            }
        }
    }

    private static byte[] hashContent(
            Path receipt,
            long expectedBytes,
            long producerStarted,
            String lengthFailureCode) {
        long hashStarted = System.nanoTime();
        MessageDigest digest;
        byte[] buffer;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            buffer = new byte[HASH_BLOCK_BYTES];
        } catch (NoSuchAlgorithmException unavailable) {
            throw failure("RECEIPT_IO_FAILURE");
        }

        long count = 0L;
        int trailing;
        try (InputStream input = Files.newInputStream(
                receipt, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            while (count < expectedBytes) {
                checkHashDeadline(producerStarted, hashStarted);
                int wanted = (int) Math.min(
                        (long) HASH_BLOCK_BYTES, expectedBytes - count);
                int filled = 0;
                while (filled < wanted) {
                    int amount = input.read(buffer, filled, wanted - filled);
                    if (amount < 0) throw failure(lengthFailureCode);
                    if (amount == 0) throw failure("RECEIPT_IO_FAILURE");
                    filled += amount;
                }
                digest.update(buffer, 0, filled);
                count += (long) filled;
                checkHashDeadline(producerStarted, hashStarted);
            }
            checkHashDeadline(producerStarted, hashStarted);
            trailing = input.read();
        } catch (BindingFailure failed) {
            throw failed;
        } catch (Exception failed) {
            throw failure("RECEIPT_IO_FAILURE");
        }

        byte[] result = digest.digest();
        checkHashDeadline(producerStarted, hashStarted);
        if (trailing != -1 || count != expectedBytes)
            throw failure(lengthFailureCode);
        return result;
    }

    private static long readMeterStarted(
            CohortTerminalTelemetryStudy.SetupMeter meter) {
        try {
            Object raw = CohortTerminalTelemetryStudy.field(meter.meter, "started");
            if (!(raw instanceof Long)) throw failure("METER_STARTED_INVALID");
            return ((Long) raw).longValue();
        } catch (BindingFailure failed) {
            throw failed;
        } catch (Exception failed) {
            throw failure("METER_STARTED_INVALID");
        }
    }

    private static void checkProducerDeadline(long producerStarted) {
        checkElapsed(producerStarted, System.nanoTime(),
                PRODUCER_LIMIT_NANOS, "PRODUCER_DEADLINE");
    }

    private static void checkHashDeadline(long producerStarted, long hashStarted) {
        long now = System.nanoTime();
        checkElapsed(producerStarted, now, PRODUCER_LIMIT_NANOS, "PRODUCER_DEADLINE");
        checkElapsed(hashStarted, now, HASH_LIMIT_NANOS, "HASH_DEADLINE");
    }

    private static void checkElapsed(
            long started, long now, long limit, String failureCode) {
        long elapsed = now - started;
        if (elapsed < 0L || elapsed > limit) throw failure(failureCode);
    }

    private static long blockCount(long bytes) {
        return (bytes + (long) HASH_BLOCK_BYTES - 1L) /
                (long) HASH_BLOCK_BYTES;
    }

    private static long hashUnits(long blocks) {
        return HASH_FIXED_UNITS + (HASH_PER_BLOCK_UNITS * blocks);
    }

    private static boolean matchesSha(byte[] digest, String expectedSha) {
        if (digest == null || digest.length != 32 || expectedSha.length() != 64)
            return false;
        for (int i = 0; i < 32; i++) {
            int value = digest[i] & 255;
            if (expectedSha.charAt(i * 2) != LOWER_HEX.charAt(value >>> 4) ||
                    expectedSha.charAt((i * 2) + 1) != LOWER_HEX.charAt(value & 15))
                return false;
        }
        return true;
    }

    private static void admitUnits(
            CohortTerminalTelemetryStudy.SetupMeter meter, long units) {
        if (units <= 0L) return;
        try {
            meter.charge(units);
        } catch (Exception rejected) {
            throw failure("METER_REJECTION");
        }
    }

    private static void admitCopies(
            CohortTerminalTelemetryStudy.SetupMeter meter, int entries) {
        if (entries <= 0) return;
        try {
            meter.copy(entries);
        } catch (Exception rejected) {
            throw failure("METER_REJECTION");
        }
    }

    private static BindingFailure failure(String code) {
        return new BindingFailure(code);
    }
}
