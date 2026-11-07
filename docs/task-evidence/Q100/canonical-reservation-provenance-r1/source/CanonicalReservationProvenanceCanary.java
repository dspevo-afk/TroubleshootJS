package com.lushprojects.circuitjs1.client;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Native adapter fixtures only; this entry never captures the authored board. */
public final class CanonicalReservationProvenanceCanary {
    private static final String[] EXPECTED = {
        "NULL_UNARMED", "FORWARD_ONCE", "TARGET_ONE_SHOT", "DETACH_DROP",
        "CONTAIN_SENTINEL", "UNEXPECTED_PROPAGATES", "UNIT_LIMIT",
        "INCLUSIVE_OUTPUT_LIMIT"
    };

    private CanonicalReservationProvenanceCanary() { }

    public static void main(String[] args) {
        try {
            if (args == null || args.length != 0)
                throw new IllegalArgumentException("P9 canary requires zero arguments");
            String[] passed = CanonicalReservationProvenanceStudy.runCanaries();
            if (!Arrays.equals(EXPECTED, passed))
                throw new IllegalStateException("P9 canary case order/count differs");
            String frame = "P9_RESERVATION_CANARY_COMPLETE " +
                "{\"cases\":[\"NULL_UNARMED\",\"FORWARD_ONCE\",\"TARGET_ONE_SHOT\"," +
                "\"DETACH_DROP\",\"CONTAIN_SENTINEL\",\"UNEXPECTED_PROPAGATES\"," +
                "\"UNIT_LIMIT\",\"INCLUSIVE_OUTPUT_LIMIT\"]," +
                "\"layoutQualification\":false,\"passed\":8," +
                "\"schema\":\"tsj-q100-reservation-canary-v1\"," +
                "\"status\":\"PASS_LITERAL_NATIVE_ADAPTER_ONLY\"}\n";
            byte[] bytes = frame.getBytes(StandardCharsets.US_ASCII);
            if (bytes.length > 65536)
                throw new IllegalStateException("P9 canary frame exceeds line limit");
            System.out.write(bytes, 0, bytes.length);
            System.out.flush();
            if (System.out.checkError())
                throw new IllegalStateException("P9 canary stdout failed");
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }
    }
}
