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
        System.out.println("PASS: PlayerSessionFingerprintContractTest assertions=" + assertions);
    }

    private static void published(String text, String expected) throws Exception {
        check(expected.equals(PlayerSessionFingerprint.of(text)), "published SHA-256 vector");
        compare(text, "published vector JDK cross-check");
    }

    private static void compare(String text, String label) throws Exception {
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(64);
        for (byte value : expected) {
            String token = Integer.toHexString(value & 255);
            if (token.length() == 1) hex.append('0');
            hex.append(token);
        }
        String actual = PlayerSessionFingerprint.of(text);
        check(actual.length() == 64 && actual.matches("[0-9a-f]{64}"), label + " encoding");
        check(hex.toString().equals(actual), label + " digest");
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
