package com.lushprojects.circuitjs1.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Independent JVM oracle for the portable fingerprint consumed by production and GWT. */
public final class PlayerSessionFingerprintContractTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        // NIST, SHA-256 worked examples: one-block and two-block messages.
        // https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Standards-and-Guidelines/documents/examples/SHA256.pdf
        published("abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        published("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1");
        published("", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        compare("\u0000\u0001\u007f\u0080\u07ff\u0800\ud7ff\ue000\uffff", "UTF-8 boundaries");
        compare("caf\u00e9/\u03a9/\u4e2d\u6587/\ud83d\udd27/\udbff\udfff", "Unicode supplementary text");
        compare("e\u0301", "decomposed Unicode remains distinct");
        check(!PlayerSessionFingerprint.of("e\u0301").equals(PlayerSessionFingerprint.of("\u00e9")),
            "fingerprint does not normalize canonical input");
        int[] lengths = { 1, 2, 3, 54, 55, 56, 57, 63, 64, 65, 119, 120, 127, 128, 129, 4095, 4096 };
        for (int length : lengths) {
            compare(repeat('x', length), "padding boundary " + length);
            compare(repeat('x', length) + "\ud83d\udd27\u4e2d", "multibyte block boundary " + length);
        }
        compare(repeat('a', 1000000), "million-byte input");
        compare(repeat('\u4e2d', PlayerSessionFingerprint.MAX_CHARACTERS), "maximum UTF-8 byte population");
        String first = PlayerSessionFingerprint.of("first");
        PlayerSessionFingerprint.of("different");
        check(first.equals(PlayerSessionFingerprint.of("first")), "calls have independent state");
        reject(null, "null");
        reject("\ud800", "unpaired high surrogate");
        reject("\udc00", "unpaired low surrogate");
        reject("\ud800x", "high surrogate followed by ordinary character");
        reject("\ud800\ud800", "adjacent high surrogates");
        reject(repeat('a', PlayerSessionFingerprint.MAX_CHARACTERS + 1), "input above bound");
        generationReceiptDigest();
        diagnosticContextDigest();
        System.out.println("PASS: PlayerSessionFingerprintContractTest assertions=" + assertions);
    }

    private static void published(String text, String expected) throws Exception {
        check(expected.equals(PlayerSessionFingerprint.of(text)), "published SHA-256 vector");
        compare(text, "published vector JDK cross-check");
    }

    private static void compare(String text, String label) throws Exception {
        compare(text, PlayerSessionFingerprint.of(text), label);
    }

    private static void compare(String text, String actual, String label) throws Exception {
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(64);
        for (byte value : expected) {
            String token = Integer.toHexString(value & 255);
            if (token.length() == 1) hex.append('0');
            hex.append(token);
        }
        check(actual.length() == 64 && actual.matches("[0-9a-f]{64}"), label + " encoding");
        check(hex.toString().equals(actual), label + " digest");
    }

    private static void generationReceiptDigest() throws Exception {
        // The actual GenerationReceipt owner repeats a legitimate bounded dependency
        // in its hypotheses stage; non-ASCII text exercises the shared UTF-8 engine.
        String dependencies = repeat('x', PlayerSessionFingerprint.MAX_CHARACTERS + 37) + "caf\u00e9/\ud83d\udd27";
        String[] stages = { "resolved", "healthy", "physical",
            "workUnits=554;complete=true;dependencies=" + dependencies,
            "selected-fault-validated;complete-hypotheses=5;scenario-compatible;answer-private",
            "complete=true;atomic=true" };
        GenerationReceipt receipt = receipt(dependencies, stages);
        String canonical = receipt.canonical();
        check(canonical.length() > PlayerSessionFingerprint.MAX_CHARACTERS,
            "real receipt aggregate exceeds ordinary session bound");
        reject(canonical, "aggregate receipt remains invalid ordinary session input");
        String actual = PlayerSessionFingerprint.ofGenerationReceipt(receipt);
        compare(canonical, actual, "full oversized receipt UTF-8 versus independent JDK SHA-256");
        stages[5] += ";tail=changed";
        GenerationReceipt changed = receipt(dependencies, stages);
        String changedDigest = PlayerSessionFingerprint.ofGenerationReceipt(changed);
        check(!actual.equals(changedDigest), "last stage beyond the ordinary bound remains bound by digest");
        compare(changed.canonical(), changedDigest, "changed receipt tail versus independent JDK SHA-256");
        boolean rejected = false;
        try { PlayerSessionFingerprint.ofGenerationReceipt(receipt(
            repeat('x', GenerationDependencyContext.MAX_CANONICAL_LENGTH + 1), stages)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "receipt entrypoint rejects oversized dependency field");
        stages[0] = repeat('x', GenerationDependencyContext.MAX_CANONICAL_LENGTH + 129);
        rejected = false;
        try { PlayerSessionFingerprint.ofGenerationReceipt(receipt(dependencies, stages)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "receipt entrypoint rejects oversized stage field");
    }

    private static void diagnosticContextDigest() throws Exception {
        String complete = repeat('x', PlayerSessionFingerprint.MAX_CHARACTERS + 17) + "caf\u00e9/\ud83d\udd27";
        GeneratedDiagnosticContextKey key = new GeneratedDiagnosticContextKey(complete);
        check(key.canonical().length() > PlayerSessionFingerprint.MAX_CHARACTERS,
            "typed full context exceeds ordinary session input bound");
        reject(key.canonical(), "large context remains invalid ordinary session input");
        String actual = PlayerSessionFingerprint.ofDiagnosticContext(key);
        compare(key.canonical(), actual, "full large context UTF-8 versus independent JDK SHA-256");
        check(!key.isTrustedCapture(), "hashing a fixture key does not grant proof-capture authority");
        GeneratedDiagnosticContextKey changed = new GeneratedDiagnosticContextKey(complete + ";tail=changed");
        check(!key.equals(changed), "context equality includes the tail beyond the former cap");
        String changedDigest = PlayerSessionFingerprint.ofDiagnosticContext(changed);
        check(!actual.equals(changedDigest), "context digest includes the tail beyond the former cap");
        compare(changed.canonical(), changedDigest, "changed context tail versus independent JDK SHA-256");
        GeneratedDiagnosticContextKey maximum = new GeneratedDiagnosticContextKey(
            repeat('\u4e2d', GenerationDependencyContext.MAX_CANONICAL_LENGTH));
        check(maximum.canonical().length() == GeneratedDiagnosticContextKey.MAX_CANONICAL_LENGTH,
            "maximum context retains its complete version prefix");
        compare(maximum.canonical(), PlayerSessionFingerprint.ofDiagnosticContext(maximum),
            "maximum typed context UTF-8 versus independent JDK SHA-256");
        boolean rejected = false;
        try { PlayerSessionFingerprint.ofDiagnosticContext(null); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "typed context entrypoint rejects null");
        rejected = false;
        try { PlayerSessionFingerprint.ofDiagnosticContext(new GeneratedDiagnosticContextKey("\ud800")); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "typed context entrypoint preserves malformed Unicode rejection");
        rejected = false;
        try { new GeneratedDiagnosticContextKey(repeat('x', GenerationDependencyContext.MAX_CANONICAL_LENGTH + 1)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "context constructor rejects one character above the typed input cap");
    }

    private static GenerationReceipt receipt(String dependencies, String[] stages) {
        return GenerationReceipt.issue(new Object(), "rb56-qualification-seed77", dependencies,
            stages, 554, 89392, new long[6], new int[6], 90000, 5000, 640);
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static void reject(String value, String label) {
        try { PlayerSessionFingerprint.of(value); }
        catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Accepted " + label);
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
