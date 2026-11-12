import java.io.File;

public final class VerifierNativeToolReturnCanary {
    private static final byte[] RETURN_MARKER = new byte[] {
        84, 83, 74, 95, 78, 65, 84, 73, 86, 69, 95, 84, 79, 79, 76,
        95, 82, 69, 84, 85, 82, 78, 95, 82, 49, 10
    };
    private static final byte[] REJECT_MARKER = new byte[] {
        84, 83, 74, 95, 78, 65, 84, 73, 86, 69, 95, 84, 79, 79, 76,
        95, 82, 69, 74, 69, 67, 84, 95, 82, 49, 10
    };
    private static final byte[] INVALID_MARKER = new byte[] {
        84, 83, 74, 95, 78, 65, 84, 73, 86, 69, 95, 84, 79, 79, 76,
        95, 73, 78, 86, 65, 76, 73, 68, 95, 82, 49, 10
    };

    private VerifierNativeToolReturnCanary() { }

    public static void main(String[] args) throws java.io.IOException {
        boolean valid = args != null && args.length == 8;
        String mode = valid ? args[0] : "";
        String canonicalWorkingDirectory = "";
        try {
            canonicalWorkingDirectory = new File(".").getCanonicalPath();
        } catch (Exception ignored) {
            valid = false;
        }

        valid = valid && ("accept".equals(mode) || "reject".equals(mode))
            && "".equals(args[1])
            && "space value".equals(args[2])
            && "quote\"value".equals(args[3])
            && "trailing\\".equals(args[4])
            && "#hash;semi".equals(args[5])
            && "-9223372036854775808".equals(args[6])
            && canonicalWorkingDirectory.equals(args[7]);

        if (!valid) {
            System.err.write(INVALID_MARKER);
            System.err.flush();
            System.exit(9);
        }
        if ("accept".equals(mode)) {
            System.out.write(RETURN_MARKER);
            System.out.flush();
            System.exit(0);
        }
        System.err.write(REJECT_MARKER);
        System.err.flush();
        System.exit(7);
    }
}
