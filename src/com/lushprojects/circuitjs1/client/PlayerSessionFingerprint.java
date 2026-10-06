package com.lushprojects.circuitjs1.client;

/**
 * Bounded synchronous SHA-256 of canonical UTF-8, shared by JDK8 and GWT.
 * This digest avoids exporting private canonical board text; it is not authentication.
 */
final class PlayerSessionFingerprint {
    static final int MAX_CHARACTERS = 1024 * 1024;
    private static final String HEX = "0123456789abcdef";
    private static final int[] ROUND = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };
    private final int[] state = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };
    private final int[] words = new int[64];
    private int blockBytes, byteCount;

    private PlayerSessionFingerprint() { }

    static String of(String text) {
        if (text == null || text.length() > MAX_CHARACTERS)
            throw new IllegalArgumentException("Session fingerprint input exceeds its canonical bound");
        PlayerSessionFingerprint digest = new PlayerSessionFingerprint();
        for (int i = 0; i < text.length(); i++) {
            int code = text.charAt(i);
            if (code < 0x80) digest.put(code);
            else if (code < 0x800) {
                digest.put(0xc0 | (code >>> 6)); digest.put(0x80 | (code & 0x3f));
            } else if (code >= 0xd800 && code <= 0xdbff) {
                if (i + 1 >= text.length()) throw invalidUnicode();
                int low = text.charAt(++i);
                if (low < 0xdc00 || low > 0xdfff) throw invalidUnicode();
                code = 0x10000 + ((code - 0xd800) << 10) + low - 0xdc00;
                digest.put(0xf0 | (code >>> 18)); digest.put(0x80 | ((code >>> 12) & 0x3f));
                digest.put(0x80 | ((code >>> 6) & 0x3f)); digest.put(0x80 | (code & 0x3f));
            } else {
                if (code >= 0xdc00 && code <= 0xdfff) throw invalidUnicode();
                digest.put(0xe0 | (code >>> 12)); digest.put(0x80 | ((code >>> 6) & 0x3f));
                digest.put(0x80 | (code & 0x3f));
            }
        }
        return digest.finish();
    }

    private static IllegalArgumentException invalidUnicode() {
        return new IllegalArgumentException("Session fingerprint requires well-formed Unicode");
    }

    private void put(int value) {
        words[blockBytes >>> 2] |= (value & 0xff) << (24 - ((blockBytes & 3) << 3));
        blockBytes++;
        byteCount++;
        if (blockBytes == 64) {
            compress();
            blockBytes = 0;
            for (int i = 0; i < 16; i++) words[i] = 0;
        }
    }

    private String finish() {
        // The UTF-16 bound permits at most 3 MiB of UTF-8, so the high length word is zero.
        int bits = byteCount << 3;
        put(0x80);
        while (blockBytes != 56) put(0);
        for (int i = 0; i < 4; i++) put(0);
        for (int shift = 24; shift >= 0; shift -= 8) put(bits >>> shift);
        StringBuilder out = new StringBuilder(64);
        for (int word : state)
            for (int shift = 28; shift >= 0; shift -= 4)
                out.append(HEX.charAt((word >>> shift) & 15));
        return out.toString();
    }

    private void compress() {
        for (int i = 16; i < 64; i++) {
            int earlier = words[i - 15], later = words[i - 2];
            int first = rotate(earlier, 7) ^ rotate(earlier, 18) ^ (earlier >>> 3);
            int second = rotate(later, 17) ^ rotate(later, 19) ^ (later >>> 10);
            words[i] = add(add(words[i - 16], first), add(words[i - 7], second));
        }
        int a = state[0], b = state[1], c = state[2], d = state[3];
        int e = state[4], f = state[5], g = state[6], h = state[7];
        for (int i = 0; i < 64; i++) {
            int sigma1 = rotate(e, 6) ^ rotate(e, 11) ^ rotate(e, 25);
            int choose = (e & f) ^ (~e & g);
            int first = add(add(add(h, sigma1), choose), add(ROUND[i], words[i]));
            int sigma0 = rotate(a, 2) ^ rotate(a, 13) ^ rotate(a, 22);
            int majority = (a & b) ^ (a & c) ^ (b & c);
            int second = add(sigma0, majority);
            h = g; g = f; f = e; e = add(d, first);
            d = c; c = b; b = a; a = add(first, second);
        }
        state[0] = add(state[0], a); state[1] = add(state[1], b);
        state[2] = add(state[2], c); state[3] = add(state[3], d);
        state[4] = add(state[4], e); state[5] = add(state[5], f);
        state[6] = add(state[6], g); state[7] = add(state[7], h);
    }

    private static int rotate(int value, int bits) {
        return (value >>> bits) | (value << (32 - bits));
    }

    /** Explicit modular addition also holds in compiled JavaScript's numeric representation. */
    private static int add(int first, int second) {
        int low = (first & 0xffff) + (second & 0xffff);
        int high = (first >>> 16) + (second >>> 16) + (low >>> 16);
        return (high << 16) | (low & 0xffff);
    }
}
